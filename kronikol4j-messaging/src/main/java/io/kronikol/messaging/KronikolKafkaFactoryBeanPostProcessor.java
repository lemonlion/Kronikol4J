package io.kronikol.messaging;

import io.kronikol.core.context.TestInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

/**
 * A Spring {@link BeanPostProcessor} that decorates Spring Kafka's {@link ProducerFactory}/
 * {@link ConsumerFactory} beans so every {@code Producer}/{@code Consumer} they create is auto-wrapped with
 * {@link TrackingKafkaProducer}/{@link TrackingKafkaConsumer} — zero-call-site-change Kafka tracking under
 * Spring. This is the Java seam analogous to .NET's Harmony {@code ConsumerBuilder.Build()} swap (which has no
 * faithful Java auto-analog — a JVM constructor cannot return a substitute, but the Spring factories return
 * the {@code Producer}/{@code Consumer} interfaces, which can be decorated).
 *
 * <p>Register it as a {@code @Bean} in a test/configuration; Spring Kafka + Spring beans are {@code compileOnly}
 * (the user brings them). The factories are wrapped with a dynamic {@link Proxy} so all their other methods
 * pass through unchanged.
 */
public final class KronikolKafkaFactoryBeanPostProcessor implements BeanPostProcessor {

    private final MessageTracker tracker;
    private final Supplier<TestInfo> identityFetcher;
    private final String consumerName;

    public KronikolKafkaFactoryBeanPostProcessor(MessageTracker tracker, Supplier<TestInfo> identityFetcher,
                                                 String consumerName) {
        this.tracker = tracker;
        this.identityFetcher = identityFetcher;
        this.consumerName = consumerName;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ProducerFactory<?, ?>) {
            return wrap(bean, ProducerFactory.class, "createProducer", this::wrapProducer);
        }
        if (bean instanceof ConsumerFactory<?, ?>) {
            return wrap(bean, ConsumerFactory.class, "createConsumer", this::wrapConsumer);
        }
        return bean;
    }

    /** Proxies {@code factory} so calls to {@code createMethod} have their returned client wrapped. */
    private static Object wrap(Object factory, Class<?> factoryType, String createMethod,
                               java.util.function.UnaryOperator<Object> clientWrapper) {
        return Proxy.newProxyInstance(factory.getClass().getClassLoader(), new Class<?>[] {factoryType},
            (proxy, method, args) -> {
                Object result = invokeDirect(factory, method, args);
                return createMethod.equals(method.getName()) && result != null
                    ? clientWrapper.apply(result) : result;
            });
    }

    @SuppressWarnings("unchecked")
    private Object wrapProducer(Object producer) {
        return producer instanceof Producer<?, ?>
            ? TrackingKafkaProducer.wrap((Producer<Object, Object>) producer, tracker, identityFetcher)
            : producer;
    }

    @SuppressWarnings("unchecked")
    private Object wrapConsumer(Object consumer) {
        return consumer instanceof Consumer<?, ?>
            ? TrackingKafkaConsumer.wrap((Consumer<Object, Object>) consumer, tracker, consumerName)
            : consumer;
    }

    private static Object invokeDirect(Object target, java.lang.reflect.Method method, Object[] args)
        throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
