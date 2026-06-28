package io.kronikol.messaging;

/**
 * Classified Apache Kafka operation types — the Java port of the .NET {@code KafkaOperation} enum. Each
 * constant carries the .NET {@code ToString()} form as its {@link #displayName()}, used verbatim in the
 * Raw / fallback diagram labels so the rendered output matches .NET.
 */
public enum KafkaOperation {
    PRODUCE("Produce"),
    PRODUCE_ASYNC("ProduceAsync"),
    CONSUME("Consume"),
    SUBSCRIBE("Subscribe"),
    UNSUBSCRIBE("Unsubscribe"),
    COMMIT("Commit"),
    FLUSH("Flush"),
    INIT_TRANSACTIONS("InitTransactions"),
    BEGIN_TRANSACTION("BeginTransaction"),
    COMMIT_TRANSACTION("CommitTransaction"),
    ABORT_TRANSACTION("AbortTransaction"),
    SEND_OFFSETS_TO_TRANSACTION("SendOffsetsToTransaction"),
    OTHER("Other");

    private final String displayName;

    KafkaOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET enum {@code ToString()} form (e.g. {@code "ProduceAsync"}) used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
