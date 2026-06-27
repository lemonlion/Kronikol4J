package io.kronikol.aws;

/**
 * The result of classifying an SQS request: the operation type and the queue name (nullable). Java port of
 * the .NET {@code SqsOperationInfo} record.
 */
public record SqsOperationInfo(SqsOperation operation, String queueName) {
}
