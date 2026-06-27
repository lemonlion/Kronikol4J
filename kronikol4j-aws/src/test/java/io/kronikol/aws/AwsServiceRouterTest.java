package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies AWS service detection (from the endpoint host) and dispatch to the matching classifier. */
class AwsServiceRouterTest {

    @Test
    void detectsServiceFromHost() {
        assertThat(AwsServiceRouter.detectService(URI.create("https://sqs.us-east-1.amazonaws.com/123/q")))
            .isEqualTo(AwsService.SQS);
        assertThat(AwsServiceRouter.detectService(URI.create("https://sns.eu-west-1.amazonaws.com/")))
            .isEqualTo(AwsService.SNS);
        assertThat(AwsServiceRouter.detectService(URI.create("https://dynamodb.us-east-1.amazonaws.com/")))
            .isEqualTo(AwsService.DYNAMODB);
        assertThat(AwsServiceRouter.detectService(URI.create("https://s3.amazonaws.com/bucket/key")))
            .isEqualTo(AwsService.S3);
        assertThat(AwsServiceRouter.detectService(URI.create("https://mybucket.s3.us-east-1.amazonaws.com/key")))
            .isEqualTo(AwsService.S3);
        assertThat(AwsServiceRouter.detectService(URI.create("https://example.com/"))).isNull();
    }

    @Test
    void serviceCarriesUriSchemeAndCategory() {
        assertThat(AwsService.S3.dependencyCategory()).isEqualTo(DependencyCategories.S3);
        assertThat(AwsService.SQS.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);
        assertThat(AwsService.DYNAMODB.dependencyCategory()).isEqualTo(DependencyCategories.DYNAMO_DB);
    }

    @Test
    void dispatchesToS3Classifier() {
        URI uri = URI.create("https://s3.amazonaws.com/photos/cat.jpg");
        AwsServiceRouter.AwsClassification c = AwsServiceRouter.classify(
            AwsService.S3, "PUT", uri, null, false, null, TrackingVerbosity.DETAILED);
        assertThat(c.label()).isEqualTo("PutObject");
        assertThat(c.resource()).isEqualTo("photos");
    }

    @Test
    void dispatchesToSqsAndSnsAndDynamoClassifiers() {
        URI sqs = URI.create("https://sqs.us-east-1.amazonaws.com/123456789012/orders");
        AwsServiceRouter.AwsClassification q = AwsServiceRouter.classify(
            AwsService.SQS, "POST", sqs, "AmazonSQS.SendMessage", false, null, TrackingVerbosity.DETAILED);
        assertThat(q.label()).isEqualTo("SendMessage");
        assertThat(q.resource()).isEqualTo("orders");

        URI sns = URI.create("https://sns.us-east-1.amazonaws.com/");
        AwsServiceRouter.AwsClassification t = AwsServiceRouter.classify(AwsService.SNS, "POST", sns,
            "AmazonSimpleNotificationService.Publish", false,
            "{\"TopicArn\":\"arn:aws:sns:us-east-1:123456789012:alerts\"}", TrackingVerbosity.DETAILED);
        assertThat(t.label()).isEqualTo("Publish");
        assertThat(t.resource()).isEqualTo("alerts");

        URI ddb = URI.create("https://dynamodb.us-east-1.amazonaws.com/");
        AwsServiceRouter.AwsClassification d = AwsServiceRouter.classify(AwsService.DYNAMODB, "POST", ddb,
            "DynamoDB_20120810.GetItem", false, "{\"TableName\":\"Users\"}", TrackingVerbosity.DETAILED);
        assertThat(d.label()).isEqualTo("GetItem");
        assertThat(d.resource()).isEqualTo("Users");
    }

    @Test
    void nullServiceYieldsNullClassification() {
        assertThat(AwsServiceRouter.classify(null, "GET", URI.create("https://x/"), null, false, null,
            TrackingVerbosity.DETAILED)).isNull();
    }
}
