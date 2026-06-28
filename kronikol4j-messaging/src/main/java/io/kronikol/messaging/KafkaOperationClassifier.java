package io.kronikol.messaging;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;

/**
 * Classifies Apache Kafka operations into diagram labels and {@code kafka://} URIs — a faithful Java port
 * of the .NET {@code KafkaOperationClassifier}. Pure logic over a {@link KafkaOperationInfo} (no Kafka
 * client dependency); uses the shared {@link TrackingVerbosity} (the .NET {@code KafkaTrackingVerbosity}
 * Raw/Detailed/Summarised levels unify into the project-wide scale).
 */
public final class KafkaOperationClassifier {

    private KafkaOperationClassifier() {
    }

    /**
     * The diagram label for {@code op} at the given verbosity, matching .NET byte-for-byte:
     * <ul>
     *   <li><b>Raw</b> — {@code "<Op> <topic>"} plus {@code [partition]} and {@code @offset} when present;</li>
     *   <li><b>Detailed</b> — {@code "Produce → <topic>"} / {@code "Consume ← <topic>"} /
     *       {@code "Subscribe <topic>"}, else the operation name;</li>
     *   <li><b>Summarised</b> — terse forms ({@code "Produce"}, {@code "Init Txn"}, {@code "Send Offsets"}, …).</li>
     * </ul>
     */
    public static String getDiagramLabel(KafkaOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName() + " " + nullToEmpty(op.topic())
                + (op.partition() != null ? "[" + op.partition() + "]" : "")
                + (op.offset() != null ? "@" + op.offset() : "");
            case DETAILED -> switch (op.operation()) {
                case PRODUCE, PRODUCE_ASYNC -> "Produce → " + op.topic();
                case CONSUME -> "Consume ← " + op.topic();
                case SUBSCRIBE -> "Subscribe " + op.topic();
                default -> op.operation().displayName();
            };
            case SUMMARISED -> switch (op.operation()) {
                case PRODUCE, PRODUCE_ASYNC -> "Produce";
                case CONSUME -> "Consume";
                case INIT_TRANSACTIONS -> "Init Txn";
                case BEGIN_TRANSACTION -> "Begin Txn";
                case COMMIT_TRANSACTION -> "Commit Txn";
                case ABORT_TRANSACTION -> "Abort Txn";
                case SEND_OFFSETS_TO_TRANSACTION -> "Send Offsets";
                default -> op.operation().displayName();
            };
        };
    }

    /**
     * The {@code kafka://} URI for {@code op}: {@code kafka:///<topic>[/partition][@offset]} at Raw (topic
     * present), {@code kafka:///<topic>} at Detailed, else {@code kafka:///}. Mirrors .NET {@code BuildUri}.
     */
    public static URI buildUri(KafkaOperationInfo op, TrackingVerbosity verbosity) {
        if (verbosity == TrackingVerbosity.RAW && op.topic() != null) {
            return URI.create("kafka:///" + op.topic()
                + (op.partition() != null ? "/" + op.partition() : "")
                + (op.offset() != null ? "@" + op.offset() : ""));
        }
        if (verbosity == TrackingVerbosity.DETAILED && op.topic() != null) {
            return URI.create("kafka:///" + op.topic());
        }
        return URI.create("kafka:///");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
