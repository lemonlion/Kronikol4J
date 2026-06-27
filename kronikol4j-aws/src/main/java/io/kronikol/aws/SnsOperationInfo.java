package io.kronikol.aws;

/**
 * The result of classifying an SNS request: the operation type, the topic name (nullable), and the full
 * topic ARN (nullable). Java port of the .NET {@code SnsOperationInfo} record.
 */
public record SnsOperationInfo(SnsOperation operation, String topicName, String topicArn) {
}
