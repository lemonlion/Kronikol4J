package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestResponseLoggerTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RequestResponseLogger.clear();
        RequestResponseLogger.setMaxContentLength(null);
    }

    private static RequestResponseLog log(String content) {
        return RequestResponseLog.builder()
            .testName("T").testId("t")
            .method(Method.Http.GET)
            .uri(URI.create("http://svc/x"))
            .serviceName("Svc").callerName("Test")
            .type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .content(content)
            .build();
    }

    @Test
    void logsAreAccumulatedInOrderAndCleared() {
        RequestResponseLogger.log(log("a"));
        RequestResponseLogger.log(log("b"));
        assertThat(RequestResponseLogger.getAllLogs()).extracting(RequestResponseLog::content)
            .containsExactly("a", "b");

        RequestResponseLogger.clear();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void nullLogIsIgnored() {
        RequestResponseLogger.log(null);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void contentIsTruncatedToMaxContentLength() {
        RequestResponseLogger.setMaxContentLength(5);
        RequestResponseLogger.log(log("0123456789"));
        assertThat(RequestResponseLogger.getAllLogs().get(0).content()).isEqualTo("01234");
    }

    @Test
    void shortContentIsNotTruncated() {
        RequestResponseLogger.setMaxContentLength(100);
        RequestResponseLogger.log(log("short"));
        assertThat(RequestResponseLogger.getAllLogs().get(0).content()).isEqualTo("short");
    }
}
