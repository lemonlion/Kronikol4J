package io.kronikol.mongodb;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import java.net.URI;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Records a MongoDB operation as a tracked interaction (the reusable core a command-listener wrapper
 * delegates to). MongoDB category renders as a {@code database} participant.
 */
public final class MongoTracking {

    private static final URI MONGO_URI = URI.create("mongodb://database/");

    private MongoTracking() {
    }

    public static void record(MongoTrackingOptions options, String operation, String collection,
                              String document, String resultSummary) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        String request = collection + ": " + (document == null ? "" : document);
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.MONGO_DB, Method.of(operation.toUpperCase(Locale.ROOT)), MONGO_URI,
            request, StatusCode.of("OK"), resultSummary);
    }

    /** Configuration for MongoDB tracking. */
    public record MongoTrackingOptions(String serviceName, String callerName,
                                       Supplier<TestInfo> testInfoFetcher) {
        public static MongoTrackingOptions forDatabase(String serviceName) {
            return new MongoTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null);
        }
    }
}
