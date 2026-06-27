package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestPhaseContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies {@link TrackingDiagramOverride} emits the marker logs + phase changes matching the .NET
 *  {@code DefaultTrackingDiagramOverride}. */
class TrackingDiagramOverrideTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RequestResponseLogger.clear();
        TestPhaseContext.reset();
    }

    @Test
    void startOverrideEmitsBufferedFragmentMarker() {
        TrackingDiagramOverride.startOverride("t1", "@startuml\nA->B\n@enduml");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(1);
        RequestResponseLog log = logs.get(0);
        assertThat(log.overrideStart()).isTrue();
        assertThat(log.overrideEnd()).isFalse();
        assertThat(log.actionStart()).isFalse();
        assertThat(log.plantUml()).isEqualTo("\n@startuml\nA->B\n@enduml\n\n"); // .NET buffered form
        assertThat(log.uri().toString()).isEqualTo("http://override.com");
        assertThat(log.testId()).isEqualTo("t1");
    }

    @Test
    void endOverrideWithoutFragmentHasNullPlantUml() {
        TrackingDiagramOverride.endOverride("t1");
        RequestResponseLog log = RequestResponseLogger.getAllLogs().get(0);
        assertThat(log.overrideEnd()).isTrue();
        assertThat(log.plantUml()).isNull();
    }

    @Test
    void insertPlantUmlEmitsStartFragmentThenEnd() {
        TrackingDiagramOverride.insertPlantUml("t1", "note over A: hi");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).overrideStart()).isTrue();
        assertThat(logs.get(0).plantUml()).isEqualTo("\nnote over A: hi\n\n");
        assertThat(logs.get(1).overrideEnd()).isTrue();
        assertThat(logs.get(1).plantUml()).isNull();
    }

    @Test
    void insertTestDelimiterEmitsBlackHeaderNote() {
        TrackingDiagramOverride.insertTestDelimiter("t1", "#5");
        assertThat(RequestResponseLogger.getAllLogs().get(0).plantUml())
            .isEqualTo("\nhnote across #black:<color:white>Test #5\n\n");
    }

    @Test
    void startActionSetsPhaseAndEmitsActionStartMarker() {
        TrackingDiagramOverride.startAction("t1");

        assertThat(TestPhaseContext.current()).isEqualTo(TestPhase.ACTION);
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).actionStart()).isTrue();
    }

    @Test
    void startSetupSetsPhaseWithoutEmittingAMarker() {
        TestPhaseContext.set(TestPhase.ACTION);
        TrackingDiagramOverride.startSetup("t1");
        assertThat(TestPhaseContext.current()).isEqualTo(TestPhase.SETUP);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void supplierOverloadResolvesTestIdLazily() {
        TrackingDiagramOverride.insertPlantUml(() -> "lazy-id", "X");
        assertThat(RequestResponseLogger.getAllLogs().get(0).testId()).isEqualTo("lazy-id");
    }
}
