package io.kronikol.redis;

/** The cache outcome of a Redis operation. Java port of the .NET {@code RedisCacheResult}. */
public enum RedisCacheResult {
    /** The key existed and a value was returned. */
    HIT,
    /** The key did not exist. */
    MISS,
    /** Cache result is not applicable for this operation. */
    NONE
}
