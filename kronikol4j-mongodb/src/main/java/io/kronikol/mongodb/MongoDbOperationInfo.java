package io.kronikol.mongodb;

/**
 * The result of classifying a MongoDB operation: the operation type plus extracted metadata (database,
 * collection, filter text, document count/id, pipeline stages, GridFS flag). Java port of the .NET
 * {@code MongoDbOperationInfo} record. {@code documentCount} is nullable ({@link Integer}).
 */
public record MongoDbOperationInfo(
    MongoDbOperation operation,
    String databaseName,
    String collectionName,
    String filterText,
    Integer documentCount,
    String documentId,
    String pipelineStages,
    boolean isGridFs) {
}
