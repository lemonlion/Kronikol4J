package io.kronikol.redis;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.Locale;

/**
 * Classifies Redis command names into typed operations with cache hit/miss detection. Java port of the .NET
 * {@code RedisOperationClassifier} — the shared classifier a Lettuce/Jedis wrapper feeds.
 */
public final class RedisOperationClassifier {

    private RedisOperationClassifier() {
    }

    /** Classifies a command (with no key/db context). */
    public static RedisOperationInfo classify(String commandName, boolean hasResult) {
        return classify(commandName, hasResult, null, 0);
    }

    /**
     * Classifies a Redis command into a typed operation. Read operations derive hit/miss from
     * {@code hasResult} (whether the command returned a value).
     */
    public static RedisOperationInfo classify(String commandName, boolean hasResult, String key, int db) {
        if (commandName == null || commandName.isEmpty()) {
            return new RedisOperationInfo(RedisOperation.OTHER, RedisCacheResult.NONE, key, db);
        }
        RedisCacheResult hitMiss = hasResult ? RedisCacheResult.HIT : RedisCacheResult.MISS;
        return switch (commandName.toUpperCase(Locale.ROOT)) {
            // String reads (hit/miss)
            case "GET", "GETDEL", "GETSET", "GETEX", "MGET" ->
                new RedisOperationInfo(RedisOperation.GET, hitMiss, key, db);
            // String writes
            case "SET", "SETEX", "SETNX", "PSETEX", "MSET", "MSETNX", "APPEND", "GETRANGE" ->
                new RedisOperationInfo(RedisOperation.SET, RedisCacheResult.NONE, key, db);
            // Increment / decrement
            case "INCR", "INCRBY", "INCRBYFLOAT" ->
                new RedisOperationInfo(RedisOperation.INCREMENT, RedisCacheResult.NONE, key, db);
            case "DECR", "DECRBY" ->
                new RedisOperationInfo(RedisOperation.DECREMENT, RedisCacheResult.NONE, key, db);
            // Key operations
            case "DEL", "UNLINK" ->
                new RedisOperationInfo(RedisOperation.DELETE, RedisCacheResult.NONE, key, db);
            case "EXISTS" ->
                new RedisOperationInfo(RedisOperation.KEY_EXISTS, RedisCacheResult.NONE, key, db);
            case "EXPIRE", "PEXPIRE", "EXPIREAT", "PEXPIREAT", "PERSIST" ->
                new RedisOperationInfo(RedisOperation.EXPIRE, RedisCacheResult.NONE, key, db);
            // Hash reads (hit/miss)
            case "HGET", "HMGET" ->
                new RedisOperationInfo(RedisOperation.HASH_GET, hitMiss, key, db);
            case "HGETALL" ->
                new RedisOperationInfo(RedisOperation.HASH_GET_ALL, RedisCacheResult.NONE, key, db);
            // Hash writes
            case "HSET", "HMSET", "HSETNX" ->
                new RedisOperationInfo(RedisOperation.HASH_SET, RedisCacheResult.NONE, key, db);
            case "HDEL" ->
                new RedisOperationInfo(RedisOperation.HASH_DELETE, RedisCacheResult.NONE, key, db);
            // List operations
            case "LPUSH", "RPUSH", "LPUSHX", "RPUSHX" ->
                new RedisOperationInfo(RedisOperation.LIST_PUSH, RedisCacheResult.NONE, key, db);
            case "LRANGE" ->
                new RedisOperationInfo(RedisOperation.LIST_RANGE, RedisCacheResult.NONE, key, db);
            // Set operations
            case "SADD" ->
                new RedisOperationInfo(RedisOperation.SET_ADD, RedisCacheResult.NONE, key, db);
            case "SMEMBERS" ->
                new RedisOperationInfo(RedisOperation.SET_MEMBERS, RedisCacheResult.NONE, key, db);
            // Pub/Sub
            case "PUBLISH" ->
                new RedisOperationInfo(RedisOperation.PUBLISH, RedisCacheResult.NONE, key, db);
            default ->
                new RedisOperationInfo(RedisOperation.OTHER, RedisCacheResult.NONE, key, db);
        };
    }

    /**
     * Builds the diagram label for the operation. {@code Raw} verbosity returns {@code null} (the raw command
     * is shown instead); otherwise the operation name, suffixed {@code " (Hit)"} / {@code " (Miss)"} for read
     * results.
     */
    public static String getDiagramLabel(RedisOperationInfo op, TrackingVerbosity verbosity) {
        if (verbosity == TrackingVerbosity.RAW) {
            return null;
        }
        String name = op.operation().displayName();
        return switch (op.cacheResult()) {
            case HIT -> name + " (Hit)";
            case MISS -> name + " (Miss)";
            default -> name;
        };
    }
}
