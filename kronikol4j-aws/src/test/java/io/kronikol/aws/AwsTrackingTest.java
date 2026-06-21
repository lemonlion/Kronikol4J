package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.aws.AwsTracking.AwsTrackingOptions;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class AwsTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void s3RendersAsADatabaseParticipant() {
        AwsTracking.s3(AwsTrackingOptions.forService("FileStore"), "put", "my-bucket", "photo.jpg");
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("PUT");
        assertThat(logs.get(0).content()).isEqualTo("my-bucket/photo.jpg");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("database \"FileStore\" as fileStore")
            .contains("test -[#2ECC71]> fileStore: PUT: /");
    }

    @Test
    void dynamoDbRendersAsADatabaseParticipant() {
        AwsTracking.dynamoDb(AwsTrackingOptions.forService("OrdersTable"), "PutItem", "orders", "{\"id\":1}");
        String uml = PlantUmlCreator.create(RequestResponseLogger.getAllLogs()).get(0).diagrams().get(0);
        assertThat(uml).contains("database \"OrdersTable\" as ordersTable")
            .contains("test -[#E74C3C]> ordersTable: PUTITEM: /");
    }

    @Test
    void sqsAndSnsRenderAsQueueEvents() {
        AwsTracking.sqs(AwsTrackingOptions.forService("OrderQueue"), "orders", "{\"id\":1}");
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(l ->
            assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT));

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("queue \"OrderQueue\" as orderQueue")
            .contains("test -[#9B59B6]> orderQueue: SEND: /");
    }
}
