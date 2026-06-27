package io.kronikol.core.sql;

/**
 * The result of classifying a SQL command via {@link UnifiedSqlClassifier#classify}. Java port of the
 * .NET {@code UnifiedSqlOperationInfo} record: the classified operation, the detected target table/proc
 * name (nullable), and the original command text (nullable).
 */
public record UnifiedSqlOperationInfo(UnifiedSqlOperation operation, String tableName, String commandText) {
}
