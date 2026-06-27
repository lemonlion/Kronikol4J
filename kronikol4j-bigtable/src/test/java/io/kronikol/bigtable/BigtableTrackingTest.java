package io.kronikol.bigtable;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.TrackingVerbosity;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the Bigtable classifier + two-phase recorder match the .NET {@code BigtableOperationClassifier}
 *  / {@code BigtableTracker}. */
class BigtableTrackingTest {

    private static final String TABLE = "projects/p/instances/i/tables/users";

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static BigtableTrackerOptions.Builder opts() {
        return BigtableTrackerOptions.builder()
            .serviceName("Bigtable")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1));
    }

    // --- classifier ---

    @Test
    void classifiesMethodNamesIncludingAsyncVariants() {
        assertThat(BigtableOperationClassifier.classify("ReadRows", TABLE, "r1", null).operation())
            .isEqualTo(BigtableOperation.READ_ROWS);
        assertThat(BigtableOperationClassifier.classify("MutateRowAsync", TABLE, "r1", null).operation())
            .isEqualTo(BigtableOperation.MUTATE_ROW);
        assertThat(BigtableOperationClassifier.classify("Unknown", null, null, null).operation())
            .isEqualTo(BigtableOperation.OTHER);
    }

    @Test
    void diagramLabelsAcrossVerbosity() {
        BigtableOperationInfo mutate = BigtableOperationClassifier.classify("MutateRows", TABLE, null, 3);
        assertThat(BigtableOperationClassifier.getDiagramLabel(mutate, TrackingVerbosity.DETAILED))
            .isEqualTo("MutateRows (×3) → users");
        assertThat(BigtableOperationClassifier.getDiagramLabel(mutate, TrackingVerbosity.SUMMARISED))
            .isEqualTo("MutateRow");

        BigtableOperationInfo read = BigtableOperationClassifier.classify("ReadRows", TABLE, "r1", null);
        assertThat(BigtableOperationClassifier.getDiagramLabel(read, TrackingVerbosity.DETAILED))
            .isEqualTo("ReadRows ← users");
        assertThat(BigtableOperationClassifier.getDiagramLabel(read, TrackingVerbosity.RAW))
            .isEqualTo("ReadRows table=" + TABLE + " row=r1");
    }

    // --- recorder ---

    @Test
    void recordsAnEventStyledRequestResponsePair() {
        BigtableInteractionRecorder rec = new BigtableInteractionRecorder(opts().build());
        BigtableOperationInfo op = BigtableOperationClassifier.classify("MutateRow", TABLE, "r1", null);

        Optional<BigtableInteractionRecorder.Correlation> corr = rec.logRequest(op, "{family:cf}");
        assertThat(corr).isPresent();
        rec.logResponse(op, corr.get(), "ok");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.metaType()).isEqualTo(RequestResponseMetaType.EVENT); // event-styled request
        assertThat(req.method().value()).isEqualTo("MutateRow → users");
        assertThat(req.content()).isEqualTo("{family:cf}");
        assertThat(req.uri().toString()).isEqualTo("bigtable:///users");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.BIGTABLE);

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.metaType()).isEqualTo(RequestResponseMetaType.DEFAULT); // response is not event-styled
        assertThat(res.traceId()).isEqualTo(req.traceId());
        assertThat(res.requestResponseId()).isEqualTo(req.requestResponseId());
    }

    @Test
    void excludedOperationsAndPhaseAndIdentityGate() {
        // excluded operation → not tracked
        BigtableInteractionRecorder excluded = new BigtableInteractionRecorder(
            opts().excludedOperations(Set.of(BigtableOperation.READ_ROWS)).build());
        assertThat(excluded.logRequest(
            BigtableOperationClassifier.classify("ReadRows", TABLE, null, null), null)).isEmpty();

        // no identity → not tracked
        BigtableInteractionRecorder noId = new BigtableInteractionRecorder(
            BigtableTrackerOptions.builder().ids(IdGenerator.seeded(1)).build());
        assertThat(noId.logRequest(
            BigtableOperationClassifier.classify("MutateRow", TABLE, null, null), null)).isEmpty();

        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void rendersADatabaseInteraction() {
        BigtableInteractionRecorder rec = new BigtableInteractionRecorder(opts().build());
        BigtableOperationInfo op = BigtableOperationClassifier.classify("ReadRows", TABLE, "r1", null);
        rec.logRequest(op, null);

        String uml = PlantUmlCreator.create(RequestResponseLogger.getAllLogs()).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("database \"Bigtable\" as bigtable")  // Bigtable category → database shape
            .contains("ReadRows");
    }
}
