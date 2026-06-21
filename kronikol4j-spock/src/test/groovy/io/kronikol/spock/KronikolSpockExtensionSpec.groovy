package io.kronikol.spock

import io.kronikol.core.context.TestInfoResolver
import io.kronikol.core.context.TestPhaseContext
import io.kronikol.core.tracking.RequestResponseLogger
import io.kronikol.core.tracking.TestPhase
import spock.lang.Specification

/**
 * Real end-to-end check of {@link KronikolSpockExtension}: it is auto-registered via ServiceLoader
 * (main resources are on the test classpath), so running this spec exercises the actual Spock
 * interceptor wiring — identity scoping and SETUP/ACTION phase tracking.
 */
class KronikolSpockExtensionSpec extends Specification {

    TestPhase phaseDuringSetup

    def setup() {
        // Captured here so the feature body can assert the SETUP phase was active during setup().
        phaseDuringSetup = TestPhaseContext.current()
    }

    def cleanup() {
        RequestResponseLogger.clear()
    }

    def "the global extension scopes identity and tracks the SETUP then ACTION phases"() {
        expect: "setup() ran in the SETUP phase"
        phaseDuringSetup == TestPhase.SETUP

        and: "the feature body runs in the ACTION phase"
        TestPhaseContext.current() == TestPhase.ACTION

        and: "an identity scope is open and names this iteration"
        def who = TestInfoResolver.resolve(null)
        who != null
        who.name() == "the global extension scopes identity and tracks the SETUP then ACTION phases"
        who.id().endsWith("/" + who.name())
    }
}
