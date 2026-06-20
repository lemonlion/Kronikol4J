package io.kronikol.servlet;

import io.kronikol.core.constants.TrackingHeaders;
import io.kronikol.core.context.TestInfo;
import java.util.function.UnaryOperator;

/**
 * Pure extraction of test identity from request headers (kept separate from the servlet API so it is
 * trivially unit-testable). Reads {@link TrackingHeaders#CURRENT_TEST_NAME} and
 * {@link TrackingHeaders#CURRENT_TEST_ID}.
 */
public final class ServletIdentity {

    private ServletIdentity() {
    }

    /**
     * @param headerLookup resolves a header name to its value (or {@code null}).
     * @return the identity carried by the request, or {@code null} if absent.
     */
    public static TestInfo fromHeaders(UnaryOperator<String> headerLookup) {
        String name = headerLookup.apply(TrackingHeaders.CURRENT_TEST_NAME);
        String id = headerLookup.apply(TrackingHeaders.CURRENT_TEST_ID);
        if (name == null || id == null) {
            return null;
        }
        return new TestInfo(name, id);
    }
}
