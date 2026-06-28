package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.TrackingHeaders;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
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

    private static UnaryOperator<String> headers(Map<String, String> map) {
        return map::get;
    }

    @Test
    void httpFallbackFetcherPrefersHeaders() {
        Map<String, String> hdrs = new HashMap<>();
        hdrs.put(TrackingHeaders.CURRENT_TEST_NAME, "FromHeader");
        hdrs.put(TrackingHeaders.CURRENT_TEST_ID, "h1");

        var fetcher = TestInfoResolver.createHttpFallbackFetcher(headers(hdrs),
            () -> new TestInfo("FromFallback", "f"));

        assertThat(fetcher.get()).isEqualTo(new TestInfo("FromHeader", "h1"));
    }

    @Test
    void httpFallbackFetcherFallsBackWhenHeadersAbsent() {
        var fetcher = TestInfoResolver.createHttpFallbackFetcher(headers(Map.of()),
            () -> new TestInfo("FromFallback", "f"));

        assertThat(fetcher.get()).isEqualTo(new TestInfo("FromFallback", "f"));
    }

    @Test
    void httpFallbackFetcherFallsBackWhenOnlyOneHeaderPresent() {
        // .NET requires BOTH name and id headers; one alone is treated as absent.
        var fetcher = TestInfoResolver.createHttpFallbackFetcher(
            name -> TrackingHeaders.CURRENT_TEST_NAME.equals(name) ? "OnlyName" : null,
            () -> new TestInfo("FromFallback", "f"));

        assertThat(fetcher.get()).isEqualTo(new TestInfo("FromFallback", "f"));
    }

    @Test
    void httpFallbackFetcherFallsBackWhenLookupNullOrThrows() {
        // No header source at all.
        assertThat(TestInfoResolver.createHttpFallbackFetcher(null, () -> new TestInfo("F", "f")).get())
            .isEqualTo(new TestInfo("F", "f"));
        // Header access blows up — must not propagate; fall through to the delegate (matches .NET).
        UnaryOperator<String> exploding = h -> {
            throw new IllegalStateException("no request context");
        };
        assertThat(TestInfoResolver.createHttpFallbackFetcher(exploding, () -> new TestInfo("F", "f")).get())
            .isEqualTo(new TestInfo("F", "f"));
    }
}
