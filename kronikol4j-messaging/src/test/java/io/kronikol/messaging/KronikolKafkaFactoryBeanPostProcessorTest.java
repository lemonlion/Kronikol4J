package io.kronikol.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.registry.TrackingComponentRegistry;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import java.lang.reflect.Proxy;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Verifies the Spring-Kafka {@link KronikolKafkaFactoryBeanPostProcessor} decorates {@code ProducerFactory}/
 * {@code ConsumerFactory} beans so the {@code Producer}/{@code Consumer} they create are auto-wrapped — the
 * zero-call-site-change Kafka tracking path. Fakes the factories via dynamic proxies (no broker).
 */
class KronikolKafkaFactoryBeanPostProcessorTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
    }

    private static MessageTracker tracker() {
        return new MessageTracker(MessageTrackerOptions.builder()
            .serviceName("Kafka")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1)).build());
    }

    private static KronikolKafkaFactoryBeanPostProcessor bpp() {
        return new KronikolKafkaFactoryBeanPostProcessor(
            tracker(), () -> new TestInfo("MyTest", "id-1"), "order-service");
    }

    @Test
    void producerFactoryBeanIsDecoratedSoCreatedProducersAreTracked() {
        MockProducer<String, String> mock =
            new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        Object decorated = bpp().postProcessAfterInitialization(fakeProducerFactory(mock), "pf");
        assertThat(decorated).isInstanceOf(ProducerFactory.class);

        @SuppressWarnings("unchecked")
        Producer<String, String> producer = ((ProducerFactory<String, String>) decorated).createProducer();
        producer.send(new ProducerRecord<>("orders", "k", "v"));

        assertThat(mock.history()).hasSize(1); // still really sent
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("Produce → orders"); // auto-tracked
    }

    @Test
    void consumerFactoryBeanIsDecoratedSoCreatedConsumersAreTracked() {
        MockConsumer<String, String> mock = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        Object decorated = bpp().postProcessAfterInitialization(fakeConsumerFactory(mock), "cf");
        assertThat(decorated).isInstanceOf(ConsumerFactory.class);

        @SuppressWarnings("unchecked")
        Consumer<String, String> consumer = ((ConsumerFactory<String, String>) decorated).createConsumer();
        try (var ignored = TestIdentityScope.begin("MyTest", "id-1")) {
            consumer.subscribe(List.of("orders"));
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2); // subscribe lifecycle event → consumer is wrapped
        assertThat(logs.get(0).method().value()).isEqualTo("Subscribe orders");
    }

    @Test
    void nonFactoryBeanIsPassedThroughUnchanged() {
        Object bean = "not a factory";
        assertThat(bpp().postProcessAfterInitialization(bean, "x")).isSameAs(bean);
    }

    private static ProducerFactory<String, String> fakeProducerFactory(Producer<String, String> producer) {
        return (ProducerFactory<String, String>) Proxy.newProxyInstance(
            ProducerFactory.class.getClassLoader(), new Class<?>[] {ProducerFactory.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "createProducer" -> producer;
                case "toString" -> "fakeProducerFactory";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }

    private static ConsumerFactory<String, String> fakeConsumerFactory(Consumer<String, String> consumer) {
        return (ConsumerFactory<String, String>) Proxy.newProxyInstance(
            ConsumerFactory.class.getClassLoader(), new Class<?>[] {ConsumerFactory.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "createConsumer" -> consumer;
                case "toString" -> "fakeConsumerFactory";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }
}
