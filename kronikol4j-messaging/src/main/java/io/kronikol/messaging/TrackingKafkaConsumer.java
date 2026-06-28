package io.kronikol.messaging;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
                } else {
                    KafkaOperation lifecycle = CONSUMER_LIFECYCLE.get(method.getName());
                    if (lifecycle != null) {
                        // Subscribe / Unsubscribe / Commit are self-contained events (the .NET KafkaTracker
                        // LogSubscribe/LogCommit/LogUnsubscribe), tracked after the real call succeeds.
                        // Subscribe carries the topic (its label/URI use it); the others do not.
                        var verbosity = tracker.effectiveVerbosity();
                        KafkaOperationInfo op = lifecycle == KafkaOperation.SUBSCRIBE
                            ? new KafkaOperationInfo(lifecycle, firstTopic(args))
                            : new KafkaOperationInfo(lifecycle);
                        tracker.trackEvent(KafkaOperationClassifier.getDiagramLabel(op, verbosity),
                            KafkaOperationClassifier.buildUri(op, verbosity));
                    }
                }
                return result;
            });
    }

    /** Consumer lifecycle methods that emit a tracked event (the .NET {@code TrackSubscribe}/{@code TrackCommit}). */
    private static final java.util.Map<String, KafkaOperation> CONSUMER_LIFECYCLE = java.util.Map.of(
        "subscribe", KafkaOperation.SUBSCRIBE,
        "unsubscribe", KafkaOperation.UNSUBSCRIBE,
        "commitSync", KafkaOperation.COMMIT,
        "commitAsync", KafkaOperation.COMMIT);

    /** The first topic from a {@code subscribe(Collection<String>)} argument, or {@code null}. */
    private static String firstTopic(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof java.util.Collection<?> topics
            && !topics.isEmpty()) {
            Object first = topics.iterator().next();
            return first == null ? null : first.toString();
        }
        return null;
    }

    private static void trackPolled(ConsumerRecords<?, ?> records, MessageTracker tracker, String consumerName) {
        for (ConsumerRecord<?, ?> record : records) {
            TestInfo who = KafkaTestHeaders.read(record.headers());
            if (who == null) {
                continue; // no identity header -> not a tracked message
            }
            try (var ignored = TestIdentityScope.begin(who.name(), who.id())) {
                // Drive the diagram label + kafka:// URI through the classifier — the consume record carries
                // the topic, partition and offset (all rendered at Raw verbosity).
                TrackingVerbosity verbosity = tracker.effectiveVerbosity();
                KafkaOperationInfo op = new KafkaOperationInfo(KafkaOperation.CONSUME,
                    record.topic(), record.partition(), record.offset());
                tracker.trackConsumeEvent(
                    KafkaOperationClassifier.getDiagramLabel(op, verbosity), consumerName,
                    KafkaOperationClassifier.buildUri(op, verbosity), record.value());
            }
        }
    }
}
