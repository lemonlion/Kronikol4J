package io.kronikol.core.constants;

/**
 * Well-known dependency categories. The taxonomy is <em>open</em> — any string is a valid
 * category; unknown categories render with a default shape. These constants exist so the
 * built-in extensions agree on spelling.
 *
 * <p>The string <em>values</em> match the .NET {@code DependencyCategories} exactly (parity);
 * the Java constant <em>names</em> follow Java conventions.
 */
public final class DependencyCategories {

    private DependencyCategories() {
    }

    // Databases
    public static final String SQL = "SQL";
    public static final String COSMOS_DB = "CosmosDB";
    public static final String MONGO_DB = "MongoDB";
    public static final String POSTGRESQL = "PostgreSQL";
    public static final String MYSQL = "MySQL";
    public static final String BIG_QUERY = "BigQuery";
    public static final String ELASTICSEARCH = "Elasticsearch";
    public static final String DATABASE = "Database";

    // Caches
    public static final String REDIS = "Redis";

    // Message queues / events
    public static final String MESSAGE_QUEUE = "MessageQueue";
    public static final String SERVICE_BUS = "ServiceBus";

    // Storage
    public static final String BLOB_STORAGE = "BlobStorage";
    public static final String S3 = "S3";

    // HTTP / RPC / in-process
    public static final String HTTP = "HTTP";
    public static final String MEDIATR = "MediatR";
    public static final String GRPC = "gRPC";
}
