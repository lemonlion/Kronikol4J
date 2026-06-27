package io.kronikol.azure;

/**
 * The result of classifying a Cosmos DB request: the operation type, the database + collection names
 * (nullable), the document id (nullable), and the query text for queries (nullable). Java port of the .NET
 * {@code CosmosOperationInfo} record.
 */
public record CosmosOperationInfo(CosmosOperation operation, String databaseName, String collectionName,
                                  String documentId, String queryText) {
}
