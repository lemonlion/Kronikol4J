package io.kronikol.redis;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import redis.clients.jedis.commands.JedisCommands;

/**
 * Wraps a Jedis {@link JedisCommands} so each command issued through it is auto-captured as a tracked
 * request/response pair via {@link RedisInteractionRecorder} — the Jedis counterpart of
 * {@link RedisCommandsTracker} (which wraps the Lettuce sync API). Both are the Java analog of the .NET
 * {@code RedisTrackingDatabase} wrapping {@code IDatabase}.
 *
 * <p>{@code JedisCommands} exposes the string-keyed command surface ({@code get}/{@code set}/{@code hget}/…),
 * so it is wrapped with a dynamic {@link Proxy}: a command method's name is the Redis command (e.g.
 * {@code get} → {@code GET}), its first {@code String} argument is the key, and a non-null return means a
 * value was found (driving GET/HGET hit/miss). {@link Object} methods and the non-command helpers are passed
 * through untracked. The database number is fixed per Jedis connection; pass it via {@link #wrap} (the Jedis
 * keyed-command interface has no {@code select}, unlike Lettuce's sync API).
 */
public final class JedisCommandsTracker {

    /** Non-command methods on the keyed interface that must not be tracked. */
    private static final Set<String> INFRA_METHODS = Set.of("ping", "echo", "quit");

    private JedisCommandsTracker() {
    }

    /** Wraps {@code delegate} on database {@code db}; commands are attributed to {@code options}' test. */
    public static <T extends JedisCommands> T wrap(T delegate, RedisTrackerOptions options, String endpoint,
                                                   int db) {
        RedisInteractionRecorder recorder = new RedisInteractionRecorder(options, endpoint);
        Class<?>[] interfaces = delegate.getClass().getInterfaces().length > 0
            ? collectJedisInterfaces(delegate)
            : new Class<?>[] {JedisCommands.class};
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
            JedisCommands.class.getClassLoader(), interfaces, new Handler(delegate, recorder, db));
        return proxy;
    }

    /** Convenience overload: endpoint {@code "localhost"}, database {@code 0}. */
    public static <T extends JedisCommands> T wrap(T delegate, RedisTrackerOptions options) {
        return wrap(delegate, options, "localhost", 0);
    }

    /** Convenience overload defaulting the database to {@code 0}. */
    public static <T extends JedisCommands> T wrap(T delegate, RedisTrackerOptions options, String endpoint) {
        return wrap(delegate, options, endpoint, 0);
    }

    /** All interfaces the delegate implements, so the proxy is assignable wherever the delegate was. */
    private static Class<?>[] collectJedisInterfaces(Object delegate) {
        Class<?>[] declared = delegate.getClass().getInterfaces();
        boolean hasJedisCommands = false;
        for (Class<?> c : declared) {
            if (c == JedisCommands.class) {
                hasJedisCommands = true;
                break;
            }
        }
        if (hasJedisCommands) {
            return declared;
        }
        Class<?>[] withJedis = new Class<?>[declared.length + 1];
        System.arraycopy(declared, 0, withJedis, 0, declared.length);
        withJedis[declared.length] = JedisCommands.class;
        return withJedis;
    }

    private static final class Handler implements InvocationHandler {

        private final Object delegate;
        private final RedisInteractionRecorder recorder;
        private final int db;

        Handler(Object delegate, RedisInteractionRecorder recorder, int db) {
            this.delegate = delegate;
            this.recorder = recorder;
            this.db = db;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class || INFRA_METHODS.contains(name)) {
                return invokeDirect(method, args);
            }

            String command = name.toUpperCase(Locale.ROOT);
            String key = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
            Optional<RedisInteractionRecorder.Correlation> corr = recorder.logRequest(command, key, db, null);
            try {
                Object result = invokeDirect(method, args);
                corr.ifPresent(c -> recorder.logResponse(command, key, db, result != null, c,
                    result != null ? String.valueOf(result) : null));
                return result;
            } catch (Throwable t) {
                corr.ifPresent(c -> recorder.logResponse(command, key, db, false, c, null));
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
