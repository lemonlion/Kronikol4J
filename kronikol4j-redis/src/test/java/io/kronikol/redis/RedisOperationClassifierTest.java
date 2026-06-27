package io.kronikol.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Redis command→operation table, hit/miss detection and diagram labels match the .NET
 * {@code RedisOperationClassifier}. Pure logic, so a unit test is the proof.
 */
class RedisOperationClassifierTest {

    private static RedisOperation op(String cmd) {
        return RedisOperationClassifier.classify(cmd, true).operation();
    }

    @Test
    void stringReadsClassifyAsGet() {
        for (String cmd : new String[] {"GET", "GETDEL", "GETSET", "GETEX", "MGET", "get"}) {
            assertThat(op(cmd)).as(cmd).isEqualTo(RedisOperation.GET);
        }
    }

    @Test
    void stringWritesClassifyAsSet() {
        for (String cmd : new String[] {"SET", "SETEX", "SETNX", "PSETEX", "MSET", "MSETNX", "APPEND", "GETRANGE"}) {
            assertThat(op(cmd)).as(cmd).isEqualTo(RedisOperation.SET);
        }
    }

    @Test
    void incrementDecrementKeyAndHashAndListAndSetAndPubSub() {
        assertThat(op("INCR")).isEqualTo(RedisOperation.INCREMENT);
        assertThat(op("INCRBYFLOAT")).isEqualTo(RedisOperation.INCREMENT);
        assertThat(op("DECRBY")).isEqualTo(RedisOperation.DECREMENT);
        assertThat(op("DEL")).isEqualTo(RedisOperation.DELETE);
        assertThat(op("UNLINK")).isEqualTo(RedisOperation.DELETE);
        assertThat(op("EXISTS")).isEqualTo(RedisOperation.KEY_EXISTS);
        assertThat(op("PEXPIREAT")).isEqualTo(RedisOperation.EXPIRE);
        assertThat(op("HGET")).isEqualTo(RedisOperation.HASH_GET);
        assertThat(op("HGETALL")).isEqualTo(RedisOperation.HASH_GET_ALL);
        assertThat(op("HSETNX")).isEqualTo(RedisOperation.HASH_SET);
        assertThat(op("HDEL")).isEqualTo(RedisOperation.HASH_DELETE);
        assertThat(op("RPUSHX")).isEqualTo(RedisOperation.LIST_PUSH);
        assertThat(op("LRANGE")).isEqualTo(RedisOperation.LIST_RANGE);
        assertThat(op("SADD")).isEqualTo(RedisOperation.SET_ADD);
        assertThat(op("SMEMBERS")).isEqualTo(RedisOperation.SET_MEMBERS);
        assertThat(op("PUBLISH")).isEqualTo(RedisOperation.PUBLISH);
        assertThat(op("WEIRDCMD")).isEqualTo(RedisOperation.OTHER);
    }

    @Test
    void hitMissDetectionForReads() {
        assertThat(RedisOperationClassifier.classify("GET", true).cacheResult()).isEqualTo(RedisCacheResult.HIT);
        assertThat(RedisOperationClassifier.classify("GET", false).cacheResult()).isEqualTo(RedisCacheResult.MISS);
        assertThat(RedisOperationClassifier.classify("HGET", false).cacheResult()).isEqualTo(RedisCacheResult.MISS);
        // writes never carry hit/miss
        assertThat(RedisOperationClassifier.classify("SET", true).cacheResult()).isEqualTo(RedisCacheResult.NONE);
        assertThat(RedisOperationClassifier.classify("HGETALL", true).cacheResult()).isEqualTo(RedisCacheResult.NONE);
    }

    @Test
    void carriesKeyAndDatabaseNumber() {
        RedisOperationInfo info = RedisOperationClassifier.classify("GET", true, "user:1", 3);
        assertThat(info.key()).isEqualTo("user:1");
        assertThat(info.databaseNumber()).isEqualTo(3);
    }

    @Test
    void nullOrEmptyCommandIsOther() {
        assertThat(RedisOperationClassifier.classify(null, true).operation()).isEqualTo(RedisOperation.OTHER);
        assertThat(RedisOperationClassifier.classify("", true).operation()).isEqualTo(RedisOperation.OTHER);
    }

    @Test
    void diagramLabels() {
        RedisOperationInfo hit = RedisOperationClassifier.classify("GET", true);
        RedisOperationInfo miss = RedisOperationClassifier.classify("HGET", false);
        RedisOperationInfo set = RedisOperationClassifier.classify("SET", true);

        assertThat(RedisOperationClassifier.getDiagramLabel(hit, TrackingVerbosity.DETAILED)).isEqualTo("Get (Hit)");
        assertThat(RedisOperationClassifier.getDiagramLabel(miss, TrackingVerbosity.DETAILED)).isEqualTo("HashGet (Miss)");
        assertThat(RedisOperationClassifier.getDiagramLabel(set, TrackingVerbosity.SUMMARISED)).isEqualTo("Set");
        assertThat(RedisOperationClassifier.getDiagramLabel(hit, TrackingVerbosity.RAW)).isNull();
    }
}
