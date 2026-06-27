package io.kronikol.bigtable;

/**
 * Classified Cloud Bigtable operation types. Java port of the .NET {@code BigtableOperation} enum; each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used in diagram labels.
 */
public enum BigtableOperation {

    READ_ROWS("ReadRows"),
    MUTATE_ROW("MutateRow"),
    MUTATE_ROWS("MutateRows"),
    CHECK_AND_MUTATE_ROW("CheckAndMutateRow"),
    READ_MODIFY_WRITE_ROW("ReadModifyWriteRow"),
    SAMPLE_ROW_KEYS("SampleRowKeys"),
    OTHER("Other");

    private final String displayName;

    BigtableOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
