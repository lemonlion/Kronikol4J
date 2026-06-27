package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies SNS classification (target header / Action / topic ARN extraction) matches the .NET classifier. */
class SnsOperationClassifierTest {

    private static final URI ENDPOINT = URI.create("https://sns.us-east-1.amazonaws.com/");

    @Test
    void classifiesFromXAmzTargetHeader() {
        SnsOperationInfo info = SnsOperationClassifier.classify(
            "AmazonSimpleNotificationService.Publish", ENDPOINT,
            "{\"TopicArn\":\"arn:aws:sns:us-east-1:123456789012:orders-topic\"}");
        assertThat(info.operation()).isEqualTo(SnsOperation.PUBLISH);
        assertThat(info.topicName()).isEqualTo("orders-topic");
        assertThat(info.topicArn()).isEqualTo("arn:aws:sns:us-east-1:123456789012:orders-topic");
    }

    @Test
    void classifiesEachKnownOperation() {
        assertThat(op("AmazonSimpleNotificationService.Subscribe")).isEqualTo(SnsOperation.SUBSCRIBE);
        assertThat(op("AmazonSimpleNotificationService.PublishBatch")).isEqualTo(SnsOperation.PUBLISH_BATCH);
        assertThat(op("AmazonSimpleNotificationService.ListSubscriptionsByTopic"))
            .isEqualTo(SnsOperation.LIST_SUBSCRIPTIONS_BY_TOPIC);
        assertThat(op("AmazonSimpleNotificationService.ConfirmSubscription"))
            .isEqualTo(SnsOperation.CONFIRM_SUBSCRIPTION);
        assertThat(op("AmazonSimpleNotificationService.Whatever")).isEqualTo(SnsOperation.OTHER);
    }

    @Test
    void fallsBackToActionQueryAndBody() {
        assertThat(SnsOperationClassifier.classify(null,
            URI.create("https://sns.us-east-1.amazonaws.com/?Action=CreateTopic"), null).operation())
            .isEqualTo(SnsOperation.CREATE_TOPIC);
        assertThat(SnsOperationClassifier.classify(null, ENDPOINT, "Action=ListTopics").operation())
            .isEqualTo(SnsOperation.LIST_TOPICS);
    }

    @Test
    void extractsTopicFromTargetArn() {
        SnsOperationInfo info = SnsOperationClassifier.classify(
            "AmazonSimpleNotificationService.Publish", ENDPOINT,
            "{\"TargetArn\":\"arn:aws:sns:eu-west-1:999999999999:billing\"}");
        assertThat(info.topicName()).isEqualTo("billing");
    }

    @Test
    void noTopicArnYieldsNulls() {
        SnsOperationInfo info = SnsOperationClassifier.classify(
            "AmazonSimpleNotificationService.ListTopics", ENDPOINT, "{}");
        assertThat(info.topicName()).isNull();
        assertThat(info.topicArn()).isNull();
    }

    @Test
    void diagramLabel() {
        SnsOperationInfo info = SnsOperationClassifier.classify(
            "AmazonSimpleNotificationService.Publish", ENDPOINT, null);
        assertThat(SnsOperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED)).isEqualTo("Publish");
        assertThat(SnsOperationClassifier.getDiagramLabel(info, TrackingVerbosity.RAW)).isNull();
    }

    private static SnsOperation op(String target) {
        return SnsOperationClassifier.classify(target, ENDPOINT, null).operation();
    }
}
