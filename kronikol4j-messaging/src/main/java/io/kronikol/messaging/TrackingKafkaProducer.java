package io.kronikol.messaging;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Wraps a Kafka {@link Producer} so each {@code send} stamps the current test identity onto the record's
 * headers (via {@link KafkaTestHeaders}) — enabling a downstream tracked consumer to correlate the message
 * back to the test — and records the send as a tracked interaction through {@link MessageTracker}. The
 * Java analog of the .NET {@code TrackingKafkaProducer}. The kafka-clients dependency is {@code compileOnly}.
 */
public final class TrackingKafkaProducer {

    private TrackingKafkaProducer() {
    }

    /**
     * Wraps {@code delegate}. {@code identityFetcher} resolves the current test (combined with the ambient
     * scope cascade); when a test is active the record headers are stamped and the send is tracked.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Producer<K, V> wrap(Producer<K, V> delegate, MessageTracker tracker,
                                             Supplier<TestInfo> identityFetcher) {
        return (Producer<K, V>) Proxy.newProxyInstance(
            Producer.class.getClassLoader(),
            new Class<?>[] {Producer.class},
            (proxy, method, args) -> {
                if ("send".equals(method.getName()) && args != null && args.length >= 1
                    && args[0] instanceof ProducerRecord<?, ?> record) {
                    TestInfo who = TestInfoResolver.resolve(identityFetcher);
                    if (who != null) {
                        KafkaTestHeaders.stamp(record.headers(), who);
                        // Drive the diagram label + kafka:// URI through the classifier (offset is assigned
                        // by the broker post-ack, so it is unknown at send time).
                        var verbosity = tracker.effectiveVerbosity();
                        KafkaOperationInfo op = new KafkaOperationInfo(
                            KafkaOperation.PRODUCE, record.topic(), record.partition(), null);
                        tracker.trackSendMessage(
                            KafkaOperationClassifier.getDiagramLabel(op, verbosity), record.topic(),
                            KafkaOperationClassifier.buildUri(op, verbosity), record.value());
                    }
                }
                KafkaOperation lifecycle = PRODUCER_LIFECYCLE.get(method.getName());
                Object result;
                try {
                    result = method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if (lifecycle != null) {
                    // Flush / transaction ops are self-contained events (no topic, no body) — tracked after
                    // the real call succeeds, mirroring the .NET KafkaTracker LogFlush/LogTransaction.
                    var verbosity = tracker.effectiveVerbosity();
                    KafkaOperationInfo op = new KafkaOperationInfo(lifecycle);
                    tracker.trackEvent(KafkaOperationClassifier.getDiagramLabel(op, verbosity),
                        KafkaOperationClassifier.buildUri(op, verbosity));
                }
                return result;
            });
    }

    /** Producer lifecycle methods that emit a tracked event (the .NET {@code TrackFlush}/{@code TrackTransactions}). */
    private static final java.util.Map<String, KafkaOperation> PRODUCER_LIFECYCLE = java.util.Map.of(
        "flush", KafkaOperation.FLUSH,
        "initTransactions", KafkaOperation.INIT_TRANSACTIONS,
        "beginTransaction", KafkaOperation.BEGIN_TRANSACTION,
        "commitTransaction", KafkaOperation.COMMIT_TRANSACTION,
        "abortTransaction", KafkaOperation.ABORT_TRANSACTION,
        "sendOffsetsToTransaction", KafkaOperation.SEND_OFFSETS_TO_TRANSACTION);
}
