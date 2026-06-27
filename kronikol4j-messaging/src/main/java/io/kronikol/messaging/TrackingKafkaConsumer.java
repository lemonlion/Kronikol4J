package io.kronikol.messaging;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * Wraps a Kafka {@link Consumer} so each polled record is auto-captured as a consume event. For every record
 * carrying the test-identity headers (stamped by a {@link TrackingKafkaProducer} on the producing side), the
 * wrapper reads the identity (via {@link KafkaTestHeaders}), opens a {@link TestIdentityScope} for it, and
 * records the delivery via {@link MessageTracker#trackConsumeEvent} — the receiving half of cross-service,
 * event-driven correlation. The Java analog of the .NET {@code TrackingKafkaConsumer}. The kafka-clients
 * dependency is {@code compileOnly}.
 *
 * <p>The scope is opened only for the {@code trackConsumeEvent} call; attributing the application's
 * subsequent per-record processing uses the data-keyed {@code TestCorrelationStore} /
 * {@code ProcessingCorrelation}.
 */
public final class TrackingKafkaConsumer {

    private TrackingKafkaConsumer() {
    }

    /** Wraps {@code delegate}; consume events are labelled with {@code consumerName} as the target. */
    @SuppressWarnings("unchecked")
    public static <K, V> Consumer<K, V> wrap(Consumer<K, V> delegate, MessageTracker tracker,
                                             String consumerName) {
        return (Consumer<K, V>) Proxy.newProxyInstance(
            Consumer.class.getClassLoader(),
            new Class<?>[] {Consumer.class},
            (proxy, method, args) -> {
                Object result;
                try {
                    result = method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if ("poll".equals(method.getName()) && result instanceof ConsumerRecords<?, ?> records) {
                    trackPolled(records, tracker, consumerName);
                }
                return result;
            });
    }

    private static void trackPolled(ConsumerRecords<?, ?> records, MessageTracker tracker, String consumerName) {
        for (ConsumerRecord<?, ?> record : records) {
            TestInfo who = KafkaTestHeaders.read(record.headers());
            if (who == null) {
                continue; // no identity header -> not a tracked message
            }
            try (var ignored = TestIdentityScope.begin(who.name(), who.id())) {
                tracker.trackConsumeEvent("Consume (Kafka)", consumerName,
                    URI.create("kafka:///" + record.topic()), record.value());
            }
        }
    }
}
