package io.kronikol.redis;

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
 * Records a Redis command as a tracked interaction (the reusable core a Lettuce/Jedis wrapper
 * delegates to). Redis category renders as a {@code collections} participant.
 */
public final class RedisTracking {

    private static final URI REDIS_URI = URI.create("redis://cache/");

    private RedisTracking() {
    }

    public static void record(RedisTrackingOptions options, String command, String args, String result) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.REDIS, Method.of(command.toUpperCase(Locale.ROOT)), REDIS_URI,
            args, StatusCode.of("OK"), result);
    }

    /** Configuration for Redis tracking. */
    public record RedisTrackingOptions(String serviceName, String callerName,
                                       Supplier<TestInfo> testInfoFetcher) {
        public static RedisTrackingOptions forCache(String serviceName) {
            return new RedisTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null);
        }
    }
}
