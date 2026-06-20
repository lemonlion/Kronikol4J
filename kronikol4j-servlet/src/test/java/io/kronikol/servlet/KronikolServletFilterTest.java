package io.kronikol.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.TrackingHeaders;
import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KronikolServletFilterTest {

    @AfterEach
    void clear() {
        TestIdentityScope.clear();
    }

    @Test
    void extractsIdentityFromHeaders() {
        Map<String, String> headers = Map.of(
            TrackingHeaders.CURRENT_TEST_NAME, "MyTest",
            TrackingHeaders.CURRENT_TEST_ID, "id-1");
        assertThat(ServletIdentity.fromHeaders(headers::get)).isEqualTo(new TestInfo("MyTest", "id-1"));
        assertThat(ServletIdentity.fromHeaders(n -> null)).isNull();
    }

    @Test
    void filterScopesIdentityDuringRequestAndClearsAfter() throws Exception {
        HttpServletRequest request = stubRequest(Map.of(
            TrackingHeaders.CURRENT_TEST_NAME, "MyTest",
            TrackingHeaders.CURRENT_TEST_ID, "id-1"));

        TestInfo[] during = new TestInfo[1];
        FilterChain chain = (req, res) -> during[0] = TestIdentityScope.current();

        new KronikolServletFilter().doFilter(request, null, chain);

        assertThat(during[0]).isEqualTo(new TestInfo("MyTest", "id-1")); // scoped for handling
        assertThat(TestIdentityScope.current()).isNull();                // cleared after (§3.2)
    }

    @Test
    void filterPassesThroughWhenNoIdentityHeaders() throws Exception {
        HttpServletRequest request = stubRequest(Map.of());
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> {
            chainCalled[0] = true;
            assertThat(TestIdentityScope.current()).isNull();
        };

        new KronikolServletFilter().doFilter(request, null, chain);
        assertThat(chainCalled[0]).isTrue();
    }

    /** A minimal HttpServletRequest whose getHeader reads from a map (other methods return defaults). */
    private static HttpServletRequest stubRequest(Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            KronikolServletFilterTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getHeader") && args != null && args.length == 1) {
                    return headers.get(args[0]);
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) {
                    return false;
                }
                if (rt == int.class) {
                    return 0;
                }
                if (rt == long.class) {
                    return 0L;
                }
                return null;
            });
    }
}
