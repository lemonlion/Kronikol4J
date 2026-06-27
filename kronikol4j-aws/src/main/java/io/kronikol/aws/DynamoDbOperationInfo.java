package io.kronikol.aws;

/**
 * The result of classifying a DynamoDB request: the operation type, the table name(s) (nullable), and the
 * PartiQL statement text for {@code ExecuteStatement}-family operations (nullable). Java port of the .NET
 * {@code DynamoDbOperationInfo} record.
 */
public record DynamoDbOperationInfo(DynamoDbOperation operation, String tableName, String statementText) {

    public DynamoDbOperationInfo(DynamoDbOperation operation, String tableName) {
        this(operation, tableName, null);
    }
}
