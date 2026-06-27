package io.kronikol.redis;

/**
 * The result of classifying a Redis operation: the operation type, the cache outcome (hit/miss/none), the
 * key, and the database number. Java port of the .NET {@code RedisOperationInfo} record.
 */
public record RedisOperationInfo(RedisOperation operation, RedisCacheResult cacheResult, String key,
                                 int databaseNumber) {
}
