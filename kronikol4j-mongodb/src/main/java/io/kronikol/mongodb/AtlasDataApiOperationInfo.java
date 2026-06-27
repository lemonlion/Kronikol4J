package io.kronikol.mongodb;

/**
 * The result of classifying a MongoDB Atlas Data API operation. Java port of the .NET
 * {@code AtlasDataApiOperationInfo}.
 *
 * @param operation      the classified operation
 * @param dataSource     the Atlas data source / cluster name (or {@code null})
 * @param databaseName   the database name (or {@code null})
 * @param collectionName the collection name (or {@code null})
 * @param filterText     the request {@code filter} document text (or {@code null})
 */
public record AtlasDataApiOperationInfo(AtlasDataApiOperation operation, String dataSource, String databaseName,
                                        String collectionName, String filterText) {

    public AtlasDataApiOperationInfo(AtlasDataApiOperation operation) {
        this(operation, null, null, null, null);
    }
}
