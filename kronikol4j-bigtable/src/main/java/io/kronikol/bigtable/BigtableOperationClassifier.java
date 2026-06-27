package io.kronikol.bigtable;

import io.kronikol.core.tracking.TrackingVerbosity;

/**
 * Classifies Cloud Bigtable operations from the SDK method name and labels them for the diagram. Java port of
 * the .NET {@code BigtableOperationClassifier} (pure logic — no Bigtable SDK dependency). The {@code …Async}
 * method-name variants fold onto the same operation.
 */
public final class BigtableOperationClassifier {

    private BigtableOperationClassifier() {
    }

    /** Classifies {@code methodName} (e.g. {@code "ReadRows"}/{@code "MutateRowAsync"}) into an operation. */
    public static BigtableOperationInfo classify(String methodName, String tableName, String rowKey,
                                                 Integer mutationCount) {
        BigtableOperation operation = switch (methodName == null ? "" : methodName) {
            case "ReadRows", "ReadRowsAsync" -> BigtableOperation.READ_ROWS;
            case "MutateRow", "MutateRowAsync" -> BigtableOperation.MUTATE_ROW;
            case "MutateRows", "MutateRowsAsync" -> BigtableOperation.MUTATE_ROWS;
            case "CheckAndMutateRow", "CheckAndMutateRowAsync" -> BigtableOperation.CHECK_AND_MUTATE_ROW;
            case "ReadModifyWriteRow", "ReadModifyWriteRowAsync" -> BigtableOperation.READ_MODIFY_WRITE_ROW;
            case "SampleRowKeys", "SampleRowKeysAsync" -> BigtableOperation.SAMPLE_ROW_KEYS;
            default -> BigtableOperation.OTHER;
        };
        return new BigtableOperationInfo(operation, tableName, rowKey, mutationCount);
    }

    /** The diagram label for an operation at the given verbosity (matches the .NET {@code GetDiagramLabel}). */
    public static String getDiagramLabel(BigtableOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName() + " table=" + op.tableName() + " row=" + op.rowKey();
            case DETAILED -> switch (op.operation()) {
                case READ_ROWS -> "ReadRows ← " + shortTableName(op.tableName());
                case MUTATE_ROW -> "MutateRow → " + shortTableName(op.tableName());
                case MUTATE_ROWS -> op.mutationCount() != null
                    ? "MutateRows (×" + op.mutationCount() + ") → " + shortTableName(op.tableName())
                    : "MutateRows → " + shortTableName(op.tableName());
                case CHECK_AND_MUTATE_ROW -> "CheckAndMutate → " + shortTableName(op.tableName());
                case READ_MODIFY_WRITE_ROW -> "ReadModifyWrite → " + shortTableName(op.tableName());
                case SAMPLE_ROW_KEYS -> "SampleRowKeys ← " + shortTableName(op.tableName());
                default -> op.operation().displayName();
            };
            case SUMMARISED -> switch (op.operation()) {
                case MUTATE_ROWS -> "MutateRow";
                case CHECK_AND_MUTATE_ROW -> "CheckAndMutate";
                case READ_MODIFY_WRITE_ROW -> "ReadModifyWrite";
                default -> op.operation().displayName();
            };
        };
    }

    /** The short table name from a {@code projects/…/instances/…/tables/{table}} resource. */
    static String shortTableName(String fullName) {
        if (fullName == null) {
            return "?";
        }
        int lastSlash = fullName.lastIndexOf('/');
        return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
    }
}
