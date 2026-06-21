package io.kronikol.report.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.diagnostics.FailureClusterer.FailureCluster;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FailureClusterer} (a port of the .NET {@code FailureClusterer.Cluster}). */
class FailureClustererTest {

    @Test
    void clustersByNormalisedFirstLine_keepsCountGreaterThanOne_orderedBySizeDescending() {
        List<Scenario> scenarios = List.of(
            failed("a", "Timeout after 30s\nat Foo.Bar()"),  // key: "Timeout after 30s"
            failed("b", "Timeout   after 30s"),               // collapses to same key
            failed("c", "  Timeout after 30s  "),             // trims to same key
            failed("d", "Assertion failed: 1 != 2"),          // distinct, single → dropped
            failed("e", "NullReference"),                     // pair with f
            failed("f", "NullReference"),
            passedScenario("g"),                               // not failed → ignored
            failedNoError("h"));                               // null error → ignored

        List<FailureCluster> clusters = FailureClusterer.cluster(scenarios);

        // "Timeout after 30s" (3) before "NullReference" (2); the 1-count "Assertion failed" is dropped.
        assertThat(clusters).extracting(FailureCluster::clusterKey)
            .containsExactly("Timeout after 30s", "NullReference");
        assertThat(clusters.get(0).scenarios()).extracting(Scenario::testId)
            .containsExactly("a", "b", "c"); // first-appearance order within the cluster
        assertThat(clusters.get(1).scenarios()).hasSize(2);
    }

    @Test
    void noFailuresOrAllSingletons_yieldNoClusters() {
        assertThat(FailureClusterer.cluster(List.of(passedScenario("a")))).isEmpty();
        assertThat(FailureClusterer.cluster(List.of(
            failed("a", "unique one"), failed("b", "unique two")))).isEmpty();
    }

    private static Scenario failed(String testId, String error) {
        return Scenario.builder("scenario " + testId, testId, ExecutionStatus.FAILED).error(error).build();
    }

    private static Scenario failedNoError(String testId) {
        return Scenario.builder("scenario " + testId, testId, ExecutionStatus.FAILED).build();
    }

    private static Scenario passedScenario(String testId) {
        return Scenario.builder("scenario " + testId, testId, ExecutionStatus.PASSED).build();
    }
}
