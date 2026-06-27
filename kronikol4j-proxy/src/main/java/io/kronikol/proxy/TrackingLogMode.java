package io.kronikol.proxy;

/**
 * Controls when a {@link TrackingProxy} emits its tracked interactions. Java port of the .NET
 * {@code TrackingLogMode}.
 */
public enum TrackingLogMode {

    /** Log each call immediately, resolving the test identity at call time (calls outside a test pass through). */
    IMMEDIATE,

    /**
     * Capture each call into {@link io.kronikol.core.tracking.PendingRequestResponseLogs} to be flushed once
     * the test identity is known (e.g. after an HTTP request completes). Use when the proxy is invoked before
     * identity has been established.
     */
    DEFERRED
}
