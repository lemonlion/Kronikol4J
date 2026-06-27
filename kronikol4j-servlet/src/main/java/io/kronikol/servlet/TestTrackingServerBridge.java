package io.kronikol.servlet;

import io.kronikol.core.context.TestInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.UnaryOperator;

/**
 * Reads the test-tracking identity from the current HTTP request's headers, so server-side code running on
 * the system-under-test's request thread can obtain the test name and id that the client-side tracker
 * propagated. The public API analog of the .NET {@code TestTrackingServerBridge.GetCurrentTestInfo} (the
 * extraction the {@link KronikolServletFilter} performs internally, exposed for callers that need the
 * identity without opening a scope).
 *
 * <p>Returns {@code null} when there is no request or the {@code kronikol-current-test-name}/
 * {@code -id} headers are absent <em>or blank</em> (matching the .NET {@code IsNullOrEmpty} check).
 */
public final class TestTrackingServerBridge {

    private TestTrackingServerBridge() {
    }

    /** Reads the current test identity from {@code request}'s headers, or {@code null} if absent/blank. */
    public static TestInfo getCurrentTestInfo(HttpServletRequest request) {
        return request == null ? null : getCurrentTestInfo((UnaryOperator<String>) request::getHeader);
    }

    /**
     * Reads the current test identity via a header-lookup function (source-agnostic — usable outside the
     * servlet API), or {@code null} if the name/id headers are absent or blank.
     */
    public static TestInfo getCurrentTestInfo(UnaryOperator<String> headerLookup) {
        if (headerLookup == null) {
            return null;
        }
        TestInfo info = ServletIdentity.fromHeaders(headerLookup);
        if (info == null || isBlank(info.name()) || isBlank(info.id())) {
            return null; // .NET treats empty name/id as no identity
        }
        return info;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
