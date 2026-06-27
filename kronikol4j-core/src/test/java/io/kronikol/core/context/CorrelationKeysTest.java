package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the correlation-key formats match the .NET {@code CorrelationKeys} exactly — the keys must be
 * byte-identical across runtimes so write-time auto-population and processing-time resolution agree.
 */
class CorrelationKeysTest {

    @Test
    void existingHelpers() {
        assertThat(CorrelationKeys.cosmos("svc", "doc")).isEqualTo("cosmos:svc:doc");
        assertThat(CorrelationKeys.mongo("svc", "doc")).isEqualTo("mongo:svc:doc");
        assertThat(CorrelationKeys.kafka("topic", "key")).isEqualTo("kafka:topic:key");
        assertThat(CorrelationKeys.serviceBus("queue", "mid")).isEqualTo("servicebus:queue:mid");
        assertThat(CorrelationKeys.custom("hangfire", "svc", "id")).isEqualTo("hangfire:svc:id");
    }

    @Test
    void cosmosWithPartitionKey() {
        assertThat(CorrelationKeys.cosmos("svc", "pk", "doc")).isEqualTo("cosmos:svc:pk:doc");
    }

    @Test
    void eventHubs() {
        assertThat(CorrelationKeys.eventHubs("hub", "evt")).isEqualTo("eventhubs:hub:evt");
    }

    @Test
    void pubSub() {
        assertThat(CorrelationKeys.pubSub("topic", "mid")).isEqualTo("pubsub:topic:mid");
    }

    @Test
    void sqs() {
        assertThat(CorrelationKeys.sqs("queue", "mid")).isEqualTo("sqs:queue:mid");
    }

    @Test
    void sns() {
        assertThat(CorrelationKeys.sns("topic", "mid")).isEqualTo("sns:topic:mid");
    }

    @Test
    void storageQueue() {
        assertThat(CorrelationKeys.storageQueue("queue", "mid")).isEqualTo("storagequeue:queue:mid");
    }
}
