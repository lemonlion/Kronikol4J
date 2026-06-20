package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestInfoResolverTest {

    @AfterEach
    void cleanup() {
        TestIdentityScope.clear();
        TestIdentityScope.clearGlobalFallback();
    }

    @Test
    void layer2DelegateWins() {
        var resolved = TestInfoResolver.resolve(() -> new TestInfo("FromDelegate", "d"));
        assertThat(resolved).isEqualTo(new TestInfo("FromDelegate", "d"));
    }

    @Test
    void unknownDelegateIsSkippedAndScopeUsed() {
        try (var scope = TestIdentityScope.begin("FromScope", "s")) {
            var resolved = TestInfoResolver.resolve(() -> TestInfo.UNKNOWN);
            assertThat(resolved).isEqualTo(new TestInfo("FromScope", "s"));
        }
    }

    @Test
    void throwingDelegateIsSkipped() {
        try (var scope = TestIdentityScope.begin("FromScope", "s")) {
            var resolved = TestInfoResolver.resolve(() -> {
                throw new IllegalStateException("no test context on this thread");
            });
            assertThat(resolved).isEqualTo(new TestInfo("FromScope", "s"));
        }
    }

    @Test
    void fallsBackToGlobalWhenNothingElse() {
        TestIdentityScope.setGlobalFallback("Global", "g");
        var resolved = TestInfoResolver.resolve(null);
        assertThat(resolved).isEqualTo(new TestInfo("Global", "g"));
    }

    @Test
    void returnsNullWhenNoLayerCanResolve() {
        assertThat(TestInfoResolver.resolve(null)).isNull();
    }
}
