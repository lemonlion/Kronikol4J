package io.kronikol.testng;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.report.model.Scenario;
import io.kronikol.runtime.RunResults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testng.ITestNGListener;
import org.testng.TestNG;

/**
 * Drives a real TestNG run (TestNG as a library) from JUnit to verify the listener scopes identity
 * during the test method and records the scenario — no test-framework override needed.
 */
class KronikolTestNgListenerTest {

    static volatile String capturedIdentityName;

    @AfterEach
    void clean() {
        RunResults.clear();
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
        capturedIdentityName = null;
    }

    /** A TestNG test class (org.testng @Test) executed via the embedded TestNG runner below. */
    public static class SampleTestNgTest {
        @org.testng.annotations.Test
        public void tracksWithinTestNg() {
            var current = TestIdentityScope.current();
            capturedIdentityName = current == null ? null : current.name();
        }
    }

    @Test
    void scopesIdentityDuringTestNgRunAndRecordsScenario() {
        TestNG tng = new TestNG();
        tng.setUseDefaultListeners(false);
        tng.setVerbose(0);
        tng.setTestClasses(new Class<?>[] {SampleTestNgTest.class});
        tng.addListener((ITestNGListener) new KronikolTestNgListener());

        tng.run();

        // identity was scoped to the test while it ran...
        assertThat(capturedIdentityName).isEqualTo("tracksWithinTestNg");
        // ...the scenario was recorded...
        assertThat(RunResults.toFeatures())
            .anySatisfy(f -> assertThat(f.scenarios()).extracting(Scenario::name)
                .contains("tracksWithinTestNg"));
        // ...and cleared afterwards (§3.2).
        assertThat(TestIdentityScope.current()).isNull();
    }
}
