package io.kronikol.elasticsearch;

/**
 * The result of classifying an Elasticsearch request: the operation type, the index name (nullable), and the
 * document id (nullable). Java port of the .NET {@code ElasticsearchOperationInfo} record.
 */
public record ElasticsearchOperationInfo(ElasticsearchOperation operation, String indexName, String documentId) {

    public ElasticsearchOperationInfo(ElasticsearchOperation operation, String indexName) {
        this(operation, indexName, null);
    }
}
