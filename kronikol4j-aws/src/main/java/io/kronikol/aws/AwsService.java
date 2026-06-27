package io.kronikol.aws;

import io.kronikol.core.constants.DependencyCategories;

/**
 * The AWS services Kronikol4J classifies, with the diagram URI scheme and dependency category each uses
 * (matching the per-service .NET tracking handlers: {@code s3://…} / {@code S3}, {@code sqs:///…} /
 * {@code MessageQueue}, etc.).
 */
public enum AwsService {

    S3("s3", DependencyCategories.S3),
    SQS("sqs", DependencyCategories.MESSAGE_QUEUE),
    SNS("sns", DependencyCategories.MESSAGE_QUEUE),
    DYNAMODB("dynamodb", DependencyCategories.DYNAMO_DB);

    private final String uriScheme;
    private final String dependencyCategory;

    AwsService(String uriScheme, String dependencyCategory) {
        this.uriScheme = uriScheme;
        this.dependencyCategory = dependencyCategory;
    }

    public String uriScheme() {
        return uriScheme;
    }

    public String dependencyCategory() {
        return dependencyCategory;
    }
}
