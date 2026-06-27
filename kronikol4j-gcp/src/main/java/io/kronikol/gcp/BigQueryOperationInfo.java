package io.kronikol.gcp;

/**
 * The result of classifying a BigQuery request: the operation type, the resource type (e.g. {@code "table"}),
 * the resource name, the project id, and the dataset id (all nullable). Java port of the .NET
 * {@code BigQueryOperationInfo} record.
 */
public record BigQueryOperationInfo(BigQueryOperation operation, String resourceType, String resourceName,
                                    String projectId, String datasetId) {
}
