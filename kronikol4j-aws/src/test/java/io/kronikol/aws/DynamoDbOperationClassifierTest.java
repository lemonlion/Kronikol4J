package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/** Verifies DynamoDB classification (target header, table/batch/statement extraction) matches the .NET classifier. */
class DynamoDbOperationClassifierTest {

    @Test
    void classifiesFromTargetHeaderAndExtractsTable() {
        DynamoDbOperationInfo info = DynamoDbOperationClassifier.classify(
            "DynamoDB_20120810.PutItem", "{\"TableName\":\"Orders\",\"Item\":{}}");
        assertThat(info.operation()).isEqualTo(DynamoDbOperation.PUT_ITEM);
        assertThat(info.tableName()).isEqualTo("Orders");
    }

    @Test
    void classifiesEachKnownOperation() {
        assertThat(op("DynamoDB_20120810.GetItem")).isEqualTo(DynamoDbOperation.GET_ITEM);
        assertThat(op("DynamoDB_20120810.Query")).isEqualTo(DynamoDbOperation.QUERY);
        assertThat(op("DynamoDB_20120810.TransactWriteItems")).isEqualTo(DynamoDbOperation.TRANSACT_WRITE_ITEMS);
        assertThat(op("DynamoDB_20120810.ListTables")).isEqualTo(DynamoDbOperation.LIST_TABLES);
        assertThat(op("DynamoDB_20120810.Mystery")).isEqualTo(DynamoDbOperation.OTHER);
    }

    @Test
    void nullTargetIsOther() {
        assertThat(DynamoDbOperationClassifier.classify(null, "{}").operation()).isEqualTo(DynamoDbOperation.OTHER);
    }

    @Test
    void batchExtractsTableNamesFromRequestItemsKeys() {
        String body = "{\"RequestItems\":{\"Orders\":{\"Keys\":[]},\"Customers\":{\"Keys\":[]}}}";
        DynamoDbOperationInfo info = DynamoDbOperationClassifier.classify("DynamoDB_20120810.BatchGetItem", body);
        assertThat(info.operation()).isEqualTo(DynamoDbOperation.BATCH_GET_ITEM);
        assertThat(info.tableName()).isEqualTo("Orders, Customers");
    }

    @Test
    void executeStatementExtractsPartiqlText() {
        DynamoDbOperationInfo info = DynamoDbOperationClassifier.classify(
            "DynamoDB_20120810.ExecuteStatement", "{\"Statement\":\"SELECT * FROM Orders\"}");
        assertThat(info.operation()).isEqualTo(DynamoDbOperation.EXECUTE_STATEMENT);
        assertThat(info.statementText()).isEqualTo("SELECT * FROM Orders");
    }

    @Test
    void diagramLabel() {
        DynamoDbOperationInfo info = DynamoDbOperationClassifier.classify("DynamoDB_20120810.Scan", "{}");
        assertThat(DynamoDbOperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED)).isEqualTo("Scan");
        assertThat(DynamoDbOperationClassifier.getDiagramLabel(info, TrackingVerbosity.RAW)).isNull();
    }

    private static DynamoDbOperation op(String target) {
        return DynamoDbOperationClassifier.classify(target, "{}").operation();
    }
}
