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

    @Test
    void eventBridgeIsClassifiedAndRecordedAsAnEvent() {
        String body = "{\"Entries\":[{\"EventBusName\":\"orders\",\"DetailType\":\"OrderPlaced\","
            + "\"Source\":\"shop\"}]}";
        AwsTracking.eventBridge(AwsTrackingOptions.forService("Events"), "AWSEvents.PutEvents", body);

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(l -> assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT));
        assertThat(logs).anySatisfy(l -> {
            assertThat(l.method().value()).isEqualTo("PutEvents [OrderPlaced]"); // classifier Detailed label
            assertThat(l.uri().toString()).isEqualTo("eventbridge://orders/");    // bus host-form (matches .NET)
        });
    }

    @Test
    void eventBridgeSummarisedOmitsBodyAndUsesDefaultBus() {
        var options = AwsTrackingOptions.forService("Events")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        AwsTracking.eventBridge(options, "AWSEvents.PutEvents", "{\"Entries\":[{}]}");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).anySatisfy(l -> {
            assertThat(l.method().value()).isEqualTo("PutEvents");       // terse summarised label
            assertThat(l.uri().toString()).isEqualTo("eventbridge://default/"); // no bus → "default"
        });
        assertThat(logs).noneSatisfy(l -> assertThat(l.content()).contains("Entries")); // body dropped
    }

    @Test
    void actionPhaseSuppressionSkipsRecordingWhenTrackDuringActionFalse() {
        var options = AwsTrackingOptions.forService("OrdersTable").withTrackDuringAction(false);
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.ACTION);
        try {
            AwsTracking.dynamoDb(options, "PutItem", "orders", "{\"id\":1}");
            AwsTracking.s3(options, "put", "bucket", "key");
            AwsTracking.sqs(options, "orders", "msg");
            assertThat(RequestResponseLogger.getAllLogs()).isEmpty(); // all suppressed in the Action phase
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void setupPhaseStillTracksWhenOnlyActionSuppressed() {
        var options = AwsTrackingOptions.forService("OrdersTable").withTrackDuringAction(false);
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.SETUP);
        try {
            AwsTracking.dynamoDb(options, "PutItem", "orders", "{\"id\":1}");
            assertThat(RequestResponseLogger.getAllLogs()).hasSize(2); // Setup unaffected
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void perPhaseVerbosityOverrideAppliesInThatPhase() {
        // Base Detailed, but Setup-phase override = Summarised → the Setup-phase record drops the item.
        var options = AwsTrackingOptions.forService("OrdersTable")
            .withSetupVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);

        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.SETUP);
        try {
            AwsTracking.dynamoDb(options, "PutItem", "orders", "{\"id\":1}");
            assertThat(RequestResponseLogger.getAllLogs().get(0).content()).isEqualTo("orders: "); // dropped
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
        RequestResponseLogger.clear();

        // Action phase uses the base Detailed verbosity → item kept.
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.ACTION);
        try {
            AwsTracking.dynamoDb(options, "PutItem", "orders", "{\"id\":1}");
            assertThat(RequestResponseLogger.getAllLogs().get(0).content()).isEqualTo("orders: {\"id\":1}");
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void summarisedVerbosityOmitsDynamoDbItemPayloadButKeepsTable() {
        var options = AwsTrackingOptions.forService("OrdersTable")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        AwsTracking.dynamoDb(options, "PutItem", "orders", "{\"id\":1}");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).content()).isEqualTo("orders: "); // table kept, item payload dropped
    }

    @Test
    void summarisedVerbosityOmitsSqsMessagePayloadButKeepsDestination() {
        var options = AwsTrackingOptions.forService("OrderQueue")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        AwsTracking.sqs(options, "orders", "{\"id\":1}");

        var logs = RequestResponseLogger.getAllLogs();
        // The message payload is dropped, the destination identity kept (on whichever half carries content).
        assertThat(logs).anySatisfy(l -> assertThat(l.content()).isEqualTo("destination: orders\n"));
        assertThat(logs).noneSatisfy(l -> assertThat(l.content()).contains("{\"id\":1}"));
    }
}
