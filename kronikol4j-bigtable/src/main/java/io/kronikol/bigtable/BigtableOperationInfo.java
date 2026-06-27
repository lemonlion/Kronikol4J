package io.kronikol.bigtable;

/**
 * The result of classifying a Bigtable operation. Java port of the .NET {@code BigtableOperationInfo} record.
 *
 * @param operation     the classified operation
 * @param tableName     the full Bigtable table resource (or {@code null})
 * @param rowKey        the row key (or {@code null})
 * @param mutationCount the mutation count for batch mutations (or {@code null})
 */
public record BigtableOperationInfo(BigtableOperation operation, String tableName, String rowKey,
                                    Integer mutationCount) {

    public BigtableOperationInfo(BigtableOperation operation) {
        this(operation, null, null, null);
    }
}
