package io.kronikol.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test using the real extension: proves the test's identity is scoped on the running
 * thread, so any tracker calling {@link TestInfoResolver} attributes its interactions to this test.
 */
@ExtendWith(KronikolExtension.class)
class KronikolExtensionIT {

    @Test
    void identityIsScopedToTheCurrentTest() {
        TestInfo current = TestIdentityScope.current();
        assertThat(current).isNotNull();
        assertThat(current.name()).contains("identityIsScopedToTheCurrentTest");
    }

    @Test
    void aTrackerResolvesThisTestWithNoDelegate() {
        // This is exactly what an HTTP/JDBC tracker does on the test thread (Layer 3, §3.2).
        TestInfo who = TestInfoResolver.resolve(null);
        assertThat(who).isNotNull();
        assertThat(who.name()).contains("aTrackerResolvesThisTest");
    }
}
