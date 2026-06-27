package io.kronikol.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies Cosmos DB classification (path + method + headers) matches the .NET classifier. */
class CosmosOperationClassifierTest {

    private static final String BASE = "https://acct.documents.azure.com";

    private static CosmosOperationInfo classify(String method, String path, boolean isQuery, boolean isUpsert) {
        return CosmosOperationClassifier.classify(method, URI.create(BASE + path), isQuery, isUpsert, null);
    }

    private static final String COLL = "/dbs/mydb/colls/mycoll";

    @Test
    void documentWrites() {
        CosmosOperationInfo create = classify("POST", COLL + "/docs", false, false);
        assertThat(create.operation()).isEqualTo(CosmosOperation.CREATE);
        assertThat(create.databaseName()).isEqualTo("mydb");
        assertThat(create.collectionName()).isEqualTo("mycoll");

        assertThat(classify("POST", COLL + "/docs", false, true).operation()).isEqualTo(CosmosOperation.UPSERT);
        assertThat(classify("PUT", COLL + "/docs/doc1", false, false).operation()).isEqualTo(CosmosOperation.REPLACE);
        assertThat(classify("PATCH", COLL + "/docs/doc1", false, false).operation()).isEqualTo(CosmosOperation.PATCH);
        assertThat(classify("DELETE", COLL + "/docs/doc1", false, false).operation()).isEqualTo(CosmosOperation.DELETE);
    }

    @Test
    void documentReads() {
        CosmosOperationInfo read = classify("GET", COLL + "/docs/doc1", false, false);
        assertThat(read.operation()).isEqualTo(CosmosOperation.READ);
        assertThat(read.documentId()).isEqualTo("doc1");

        assertThat(classify("GET", COLL + "/docs", false, false).operation()).isEqualTo(CosmosOperation.LIST);
    }

    @Test
    void queryExtractsQueryText() {
        CosmosOperationInfo info = CosmosOperationClassifier.classify("POST",
            URI.create(BASE + COLL + "/docs"), true, false,
            "{\"query\":\"SELECT * FROM c WHERE c.id = 1\"}");
        assertThat(info.operation()).isEqualTo(CosmosOperation.QUERY);
        assertThat(info.queryText()).isEqualTo("SELECT * FROM c WHERE c.id = 1");
    }

    @Test
    void storedProcAndBatch() {
        assertThat(classify("POST", COLL + "/sprocs/sp1", false, false).operation())
            .isEqualTo(CosmosOperation.EXEC_STORED_PROC);
        assertThat(classify("POST", COLL + "/docs/doc1", false, false).operation())
            .isEqualTo(CosmosOperation.BATCH);
    }

    @Test
    void diagramLabel() {
        CosmosOperationInfo info = classify("POST", COLL + "/docs", false, false);
        assertThat(CosmosOperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED)).isEqualTo("Create");
        assertThat(CosmosOperationClassifier.getDiagramLabel(info, TrackingVerbosity.RAW)).isNull();
    }
}
