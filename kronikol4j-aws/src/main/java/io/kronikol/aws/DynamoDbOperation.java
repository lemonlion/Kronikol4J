package io.kronikol.aws;

/**
 * Classified Amazon DynamoDB operation types. Java port of the .NET {@code DynamoDbOperation} enum. Each
 * constant carries the AWS operation name (PascalCase), used as the diagram label.
 */
public enum DynamoDbOperation {

    PUT_ITEM("PutItem"),
    GET_ITEM("GetItem"),
    UPDATE_ITEM("UpdateItem"),
    DELETE_ITEM("DeleteItem"),
    QUERY("Query"),
    SCAN("Scan"),
    BATCH_WRITE_ITEM("BatchWriteItem"),
    BATCH_GET_ITEM("BatchGetItem"),
    TRANSACT_WRITE_ITEMS("TransactWriteItems"),
    TRANSACT_GET_ITEMS("TransactGetItems"),
    CREATE_TABLE("CreateTable"),
    DELETE_TABLE("DeleteTable"),
    DESCRIBE_TABLE("DescribeTable"),
    LIST_TABLES("ListTables"),
    UPDATE_TABLE("UpdateTable"),
    EXECUTE_STATEMENT("ExecuteStatement"),
    BATCH_EXECUTE_STATEMENT("BatchExecuteStatement"),
    EXECUTE_TRANSACTION("ExecuteTransaction"),
    OTHER("Other");

    private final String displayName;

    DynamoDbOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The AWS operation name (PascalCase) used as the diagram label. */
    public String displayName() {
        return displayName;
    }
}
