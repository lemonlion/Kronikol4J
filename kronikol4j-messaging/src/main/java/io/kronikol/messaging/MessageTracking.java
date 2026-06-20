package io.kronikol.messaging;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import java.net.URI;
import java.util.function.Supplier;

/**
 * Records messaging publish/consume as fire-and-forget {@code EVENT} interactions (plan §1 — the
 * Kafka/ServiceBus pattern). The reusable core a Kafka/JMS/RabbitMQ wrapper delegates to; those
 * wrappers also stamp/read the {@code kronikol-test-*} headers for cross-boundary identity (§3.2).
 */
public final class MessageTracking {

    private static final URI BUS_URI = URI.create("amqp://bus/");

    private MessageTracking() {
    }

    /** Records publishing {@code message} to {@code topic}. */
    public static void publish(MessageTrackingOptions options, String topic, String message) {
        event(options, "PUBLISH", topic, message);
    }

    /** Records consuming {@code message} from {@code topic}. */
    public static void consume(MessageTrackingOptions options, String topic, String message) {
        event(options, "RECEIVE", topic, message);
    }

    private static void event(MessageTrackingOptions options, String verb, String topic, String message) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        String content = "topic: " + topic + "\n" + (message == null ? "" : message);
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.MESSAGE_QUEUE, Method.of(verb), BUS_URI, null, content,
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    /** Configuration for messaging tracking. */
    public record MessageTrackingOptions(String serviceName, String callerName,
                                         Supplier<TestInfo> testInfoFetcher) {
        public static MessageTrackingOptions forBroker(String serviceName) {
            return new MessageTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null);
        }
    }
}
