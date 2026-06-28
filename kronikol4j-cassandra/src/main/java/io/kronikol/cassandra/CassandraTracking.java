package io.kronikol.cassandra;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Records an Apache Cassandra CQL operation as a tracked interaction (the reusable core a
 * {@code RequestTracker}/session wrapper delegates to). Cassandra category renders as a
 * {@code database} participant — the same direct-log pattern as the Mongo/JDBC trackers.
 */
public final class CassandraTracking {

    private static final URI CASSANDRA_URI = URI.create("cassandra://database/");

    private CassandraTracking() {
    }

    /**
     * @param operation the CQL verb (e.g. {@code SELECT}, {@code INSERT}, {@code UPDATE}, {@code DELETE})
     * @param table     the keyspace.table (or table) the statement targets
     * @param statement the CQL text (or a redacted form)
     * @param resultSummary a short human description of the outcome (e.g. {@code "1 row"})
     */
    public static void record(CassandraTrackingOptions options, String operation, String table,
                              String statement, String resultSummary) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        // Summarised omits the CQL statement payload — only the table identity is kept.
        String payload = options.verbosity().includesPayload() && statement != null ? statement : "";
        String request = table + ": " + payload;
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.CASSANDRA, Method.of(operation.toUpperCase(Locale.ROOT)), CASSANDRA_URI,
            request, StatusCode.of("OK"), resultSummary);
    }

    /** Configuration for Cassandra tracking. */
    public record CassandraTrackingOptions(String serviceName, String callerName,
                                           Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity) {

        public CassandraTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Three-arg shape (default verbosity) — the back-compatible constructor. */
        public CassandraTrackingOptions(String serviceName, String callerName,
                                        Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT);
        }

        public static CassandraTrackingOptions forKeyspace(String serviceName) {
            return new CassandraTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT);
        }

        /** A copy with the given verbosity (Summarised omits the CQL statement). */
        public CassandraTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new CassandraTrackingOptions(serviceName, callerName, testInfoFetcher, value);
        }
    }
}
