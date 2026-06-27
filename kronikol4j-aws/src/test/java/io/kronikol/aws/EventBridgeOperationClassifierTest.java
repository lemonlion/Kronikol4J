package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/** Verifies the {@link EventBridgeOperationClassifier} matches the .NET classifier — target-header mapping,
 *  PutEvents/rule body extraction, and the diagram labels across verbosity. */
class EventBridgeOperationClassifierTest {

    @Test
    void mapsTargetHeaderToOperations() {
        assertThat(EventBridgeOperationClassifier.classify("AWSEvents.PutTargets", null).operation())
            .isEqualTo(EventBridgeOperation.PUT_TARGETS);
        assertThat(EventBridgeOperationClassifier.classify("AWSEvents.ListRules", null).operation())
            .isEqualTo(EventBridgeOperation.LIST_RULES);
        // case-insensitive (the .NET map is OrdinalIgnoreCase)
        assertThat(EventBridgeOperationClassifier.classify("awsevents.putevents", null).operation())
            .isEqualTo(EventBridgeOperation.PUT_EVENTS);
        assertThat(EventBridgeOperationClassifier.classify("AWSEvents.Nope", null).operation())
            .isEqualTo(EventBridgeOperation.OTHER);
        assertThat(EventBridgeOperationClassifier.classify(null, null).operation())
            .isEqualTo(EventBridgeOperation.OTHER);
    }

    @Test
    void extractsPutEventsMetadataFromBody() {
        String body = "{\"Entries\":["
            + "{\"Source\":\"orders.api\",\"DetailType\":\"OrderPlaced\",\"Detail\":\"{\\\"id\\\":1}\"},"
            + "{\"Source\":\"orders.api\",\"DetailType\":\"OrderPlaced\"}],\"EventBusName\":\"app-bus\"}";
        EventBridgeOperationInfo op = EventBridgeOperationClassifier.classify("AWSEvents.PutEvents", body);

        assertThat(op.operation()).isEqualTo(EventBridgeOperation.PUT_EVENTS);
        assertThat(op.eventBusName()).isEqualTo("app-bus");
        assertThat(op.detailType()).isEqualTo("OrderPlaced");
        assertThat(op.source()).isEqualTo("orders.api");
        assertThat(op.entryCount()).isEqualTo(2);
    }

    @Test
    void extractsRuleMetadataFromBody() {
        EventBridgeOperationInfo op = EventBridgeOperationClassifier.classify("AWSEvents.PutRule",
            "{\"Name\":\"daily-rule\",\"EventBusName\":\"app-bus\",\"ScheduleExpression\":\"rate(1 day)\"}");
        assertThat(op.operation()).isEqualTo(EventBridgeOperation.PUT_RULE);
        assertThat(op.ruleName()).isEqualTo("daily-rule");
        assertThat(op.eventBusName()).isEqualTo("app-bus");
    }

    @Test
    void diagramLabels() {
        EventBridgeOperationInfo putEvents = EventBridgeOperationClassifier.classify("AWSEvents.PutEvents",
            "{\"Entries\":[{\"DetailType\":\"OrderPlaced\",\"Source\":\"s\"},{\"DetailType\":\"OrderPlaced\"}]}");
        assertThat(EventBridgeOperationClassifier.getDiagramLabel(putEvents, TrackingVerbosity.DETAILED))
            .isEqualTo("PutEvents [OrderPlaced] x2");
        assertThat(EventBridgeOperationClassifier.getDiagramLabel(putEvents, TrackingVerbosity.SUMMARISED))
            .isEqualTo("PutEvents");

        EventBridgeOperationInfo putRule = EventBridgeOperationClassifier.classify("AWSEvents.PutRule",
            "{\"Name\":\"daily-rule\"}");
        assertThat(EventBridgeOperationClassifier.getDiagramLabel(putRule, TrackingVerbosity.DETAILED))
            .isEqualTo("PutRule daily-rule");
        assertThat(EventBridgeOperationClassifier.getDiagramLabel(putRule, TrackingVerbosity.SUMMARISED))
            .isEqualTo("ManageRule");

        EventBridgeOperationInfo targets = EventBridgeOperationClassifier.classify("AWSEvents.RemoveTargets", null);
        assertThat(EventBridgeOperationClassifier.getDiagramLabel(targets, TrackingVerbosity.SUMMARISED))
            .isEqualTo("ManageTargets");
    }

    @Test
    void rawLabelIncludesBusAndCount() {
        EventBridgeOperationInfo op = EventBridgeOperationClassifier.classify("AWSEvents.PutEvents",
            "{\"Entries\":[{\"DetailType\":\"OrderPlaced\"}],\"EventBusName\":\"app-bus\"}");
        assertThat(EventBridgeOperationClassifier.getDiagramLabel(op, TrackingVerbosity.RAW))
            .isEqualTo("PutEvents bus=app-bus type=OrderPlaced count=1");
    }
}
