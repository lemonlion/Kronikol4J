package io.kronikol.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.TrackingHeaders;
import io.kronikol.core.context.TestInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies {@link TestTrackingServerBridge} reads the current test identity from request headers
 *  (the public analog of the .NET {@code TestTrackingServerBridge.GetCurrentTestInfo}). */
class TestTrackingServerBridgeTest {

    @Test
    void readsIdentityFromRequestHeaders() {
        HttpServletRequest request = stubRequest(Map.of(
            TrackingHeaders.CURRENT_TEST_NAME, "MyTest",
            TrackingHeaders.CURRENT_TEST_ID, "id-1"));
        assertThat(TestTrackingServerBridge.getCurrentTestInfo(request))
            .isEqualTo(new TestInfo("MyTest", "id-1"));
    }

    @Test
    void readsIdentityViaHeaderLookup() {
        Map<String, String> headers = Map.of(
            TrackingHeaders.CURRENT_TEST_NAME, "MyTest",
            TrackingHeaders.CURRENT_TEST_ID, "id-1");
        assertThat(TestTrackingServerBridge.getCurrentTestInfo(headers::get))
            .isEqualTo(new TestInfo("MyTest", "id-1"));
    }

    @Test
    void returnsNullWhenHeadersAbsent() {
        assertThat(TestTrackingServerBridge.getCurrentTestInfo(stubRequest(Map.of()))).isNull();
    }

    @Test
    void returnsNullWhenNameOrIdBlank() {
        assertThat(TestTrackingServerBridge.getCurrentTestInfo((java.util.function.UnaryOperator<String>) name ->
            TrackingHeaders.CURRENT_TEST_NAME.equals(name) ? "" : "id-1")).isNull();
        assertThat(TestTrackingServerBridge.getCurrentTestInfo((java.util.function.UnaryOperator<String>) name ->
            TrackingHeaders.CURRENT_TEST_ID.equals(name) ? "" : "MyTest")).isNull();
    }

    @Test
    void returnsNullForNullRequestOrLookup() {
        assertThat(TestTrackingServerBridge.getCurrentTestInfo((HttpServletRequest) null)).isNull();
        assertThat(TestTrackingServerBridge.getCurrentTestInfo((java.util.function.UnaryOperator<String>) null))
            .isNull();
    }

    /** A minimal HttpServletRequest whose getHeader reads from a map (other methods return defaults). */
    private static HttpServletRequest stubRequest(Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            TestTrackingServerBridgeTest.class.getClassLoader(),
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
