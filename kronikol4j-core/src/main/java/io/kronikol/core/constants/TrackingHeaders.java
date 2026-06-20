package io.kronikol.core.constants;

/**
 * Header names used to propagate test identity across process and message boundaries.
 *
 * <p>Values match the .NET constants exactly so that, in principle, a Kronikol4J test client
 * and a .NET-tracked service (or vice versa) could interoperate over the wire.
 */
public final class TrackingHeaders {

    private TrackingHeaders() {
    }

    /** HTTP headers — stamped on outgoing requests, read by the server-side filter (Layer 1). */
    public static final String CURRENT_TEST_NAME = "test-tracking-current-test-name";
    public static final String CURRENT_TEST_ID = "test-tracking-current-test-id";
    public static final String CALLER_NAME = "test-tracking-caller-name";
    public static final String TRACE_ID = "test-tracking-trace-id";

    /** Message headers — stamped by producers, read by consumers (async messaging). */
    public static final String MESSAGE_TEST_NAME = "kronikol-test-name";
    public static final String MESSAGE_TEST_ID = "kronikol-test-id";
}
