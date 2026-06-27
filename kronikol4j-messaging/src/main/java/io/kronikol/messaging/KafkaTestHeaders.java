package io.kronikol.messaging;

import io.kronikol.core.constants.TrackingHeaders;
import io.kronikol.core.context.TestInfo;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 * Reads and writes the test-identity headers ({@link TrackingHeaders#MESSAGE_TEST_NAME} /
 * {@link TrackingHeaders#MESSAGE_TEST_ID}) on Kafka message headers. This is what enables cross-service,
 * event-driven correlation: a producer stamps the headers from the current test identity, and a downstream
 * consumer reads them to attribute the work to the same test — exactly as the .NET tracking
 * producer/consumer do. Header values match the .NET constants so a Java producer and a .NET consumer (or
 * vice versa) interoperate over the wire.
 */
public final class KafkaTestHeaders {

    private KafkaTestHeaders() {
    }

    /** Stamps the current test identity onto {@code headers}, replacing any existing values. */
    public static void stamp(Headers headers, TestInfo who) {
        headers.remove(TrackingHeaders.MESSAGE_TEST_NAME);
        headers.remove(TrackingHeaders.MESSAGE_TEST_ID);
        headers.add(TrackingHeaders.MESSAGE_TEST_NAME, who.name().getBytes(StandardCharsets.UTF_8));
        headers.add(TrackingHeaders.MESSAGE_TEST_ID, who.id().getBytes(StandardCharsets.UTF_8));
    }

    /** Reads the test identity from {@code headers}, or {@code null} if either header is absent. */
    public static TestInfo read(Headers headers) {
        String name = value(headers, TrackingHeaders.MESSAGE_TEST_NAME);
        String id = value(headers, TrackingHeaders.MESSAGE_TEST_ID);
        return name != null && id != null ? new TestInfo(name, id) : null;
    }

    private static String value(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
