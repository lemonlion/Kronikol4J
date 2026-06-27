package io.kronikol.redis;

import io.lettuce.core.api.sync.RedisCommands;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Wraps a Lettuce {@link RedisCommands} so each command issued through it is auto-captured as a tracked
 * request/response pair via {@link RedisInteractionRecorder} — the Java analog of the .NET
 * {@code RedisTrackingDatabase} wrapping {@code IDatabase}.
 *
 * <p>{@code RedisCommands} has hundreds of methods, so it is wrapped with a dynamic {@link Proxy}: a command
 * method's name is the Redis command (e.g. {@code get} → {@code GET}), its first {@code String} argument is
 * the key, and a non-null return means a value was found (driving GET/HGET hit/miss). Connection-management
 * and {@link Object} methods are passed through untracked.
 */
public final class RedisCommandsTracker {

    /** Non-command methods on the sync API that must not be tracked. */
    private static final Set<String> INFRA_METHODS = Set.of(
        "getStatefulConnection", "flushCommands", "setAutoFlushCommands", "isOpen", "reset",
        "dispatch", "setTimeout", "auth", "select", "swapdb", "quit", "ping", "shutdown");

    private RedisCommandsTracker() {
    }

    /** Wraps {@code delegate}; commands are attributed to the test resolved by {@code options}. */
    @SuppressWarnings("unchecked")
    public static <K, V> RedisCommands<K, V> wrap(RedisCommands<K, V> delegate, RedisTrackerOptions options,
                                                  String endpoint) {
        RedisInteractionRecorder recorder = new RedisInteractionRecorder(options, endpoint);
        return (RedisCommands<K, V>) Proxy.newProxyInstance(
            RedisCommands.class.getClassLoader(),
            new Class<?>[] {RedisCommands.class},
            new Handler(delegate, recorder));
    }

    /** Convenience overload defaulting the endpoint to {@code "localhost"}. */
    public static <K, V> RedisCommands<K, V> wrap(RedisCommands<K, V> delegate, RedisTrackerOptions options) {
        return wrap(delegate, options, "localhost");
    }

    private record Handler(RedisCommands<?, ?> delegate, RedisInteractionRecorder recorder)
        implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class || INFRA_METHODS.contains(name)) {
                return invokeDirect(method, args);
            }

            String command = name.toUpperCase(Locale.ROOT);
            String key = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
            Optional<RedisInteractionRecorder.Correlation> corr = recorder.logRequest(command, key, 0, null);
            try {
                Object result = invokeDirect(method, args);
                corr.ifPresent(c -> recorder.logResponse(command, key, 0, result != null, c,
                    result != null ? String.valueOf(result) : null));
                return result;
            } catch (Throwable t) {
                corr.ifPresent(c -> recorder.logResponse(command, key, 0, false, c, null));
                throw t;
            }
        }

        private Object invokeDirect(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
