package io.kronikol.core.registry;

/**
 * A tracking component (interceptor/handler/proxy) registers itself so the report can show which
 * components were wired and whether they actually ran — a diagnostic aid. Mirrors the .NET
 * {@code ITrackingComponent}.
 */
public interface TrackingComponent {

    /** Human-readable identity, e.g. {@code "JdbcTracker (OrderService)"}. */
    String componentName();

    /** Whether this component has processed at least one interaction. */
    boolean wasInvoked();

    /** How many interactions this component has processed. */
    int invocationCount();

    /** Whether this component can resolve identity from inbound HTTP headers (Layer 1). */
    default boolean hasHttpContextAccess() {
        return false;
    }
}
