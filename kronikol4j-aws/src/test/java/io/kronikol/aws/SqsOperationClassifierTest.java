package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies SQS classification (target header / Action / queue extraction) matches the .NET classifier. */
class SqsOperationClassifierTest {

    @Test
    void classifiesFromXAmzTargetHeader() {
        SqsOperationInfo info = SqsOperationClassifier.classify(
            "AmazonSQS.SendMessage",
            URI.create("https://sqs.us-east-1.amazonaws.com/123456789012/orders"),
            null);
        assertThat(info.operation()).isEqualTo(SqsOperation.SEND_MESSAGE);
        assertThat(info.queueName()).isEqualTo("orders");
    }

    @Test
    void classifiesEachKnownOperation() {
        assertThat(op("AmazonSQS.ReceiveMessage")).isEqualTo(SqsOperation.RECEIVE_MESSAGE);
        assertThat(op("AmazonSQS.DeleteMessageBatch")).isEqualTo(SqsOperation.DELETE_MESSAGE_BATCH);
        assertThat(op("AmazonSQS.CreateQueue")).isEqualTo(SqsOperation.CREATE_QUEUE);
        assertThat(op("AmazonSQS.ChangeMessageVisibilityBatch"))
            .isEqualTo(SqsOperation.CHANGE_MESSAGE_VISIBILITY_BATCH);
        assertThat(op("AmazonSQS.ListQueues")).isEqualTo(SqsOperation.LIST_QUEUES);
        assertThat(op("AmazonSQS.SomethingNew")).isEqualTo(SqsOperation.OTHER);
    }

    @Test
    void fallsBackToActionQueryParameter() {
        SqsOperationInfo info = SqsOperationClassifier.classify(
            null,
            URI.create("https://sqs.us-east-1.amazonaws.com/123456789012/orders?Action=PurgeQueue&Version=2012-11-05"),
            null);
        assertThat(info.operation()).isEqualTo(SqsOperation.PURGE_QUEUE);
        assertThat(info.queueName()).isEqualTo("orders");
    }

    @Test
    void fallsBackToActionInFormBody() {
        SqsOperationInfo info = SqsOperationClassifier.classify(null,
            URI.create("https://sqs.us-east-1.amazonaws.com/"), "Action=GetQueueUrl&QueueName=orders");
        assertThat(info.operation()).isEqualTo(SqsOperation.GET_QUEUE_URL);
    }

    @Test
    void extractsQueueNameFromBodyUrlAndName() {
        SqsOperationInfo byUrl = SqsOperationClassifier.classify("AmazonSQS.SendMessage",
            URI.create("https://sqs.us-east-1.amazonaws.com/"),
            "{\"QueueUrl\":\"https://sqs.us-east-1.amazonaws.com/123456789012/billing\"}");
        assertThat(byUrl.queueName()).isEqualTo("billing");

        SqsOperationInfo byName = SqsOperationClassifier.classify("AmazonSQS.CreateQueue",
            URI.create("https://sqs.us-east-1.amazonaws.com/"), "{\"QueueName\":\"new-queue\"}");
        assertThat(byName.queueName()).isEqualTo("new-queue");
    }

    @Test
    void diagramLabel() {
        SqsOperationInfo info = SqsOperationClassifier.classify("AmazonSQS.SendMessage",
            URI.create("https://sqs.us-east-1.amazonaws.com/123456789012/orders"), null);
        assertThat(SqsOperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED)).isEqualTo("SendMessage");
        assertThat(SqsOperationClassifier.getDiagramLabel(info, TrackingVerbosity.RAW)).isNull();
    }

    private static SqsOperation op(String target) {
        return SqsOperationClassifier.classify(target,
            URI.create("https://sqs.us-east-1.amazonaws.com/123456789012/q"), null).operation();
    }
}
