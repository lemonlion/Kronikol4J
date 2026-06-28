package io.kronikol.eventbus.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.eventbus.EventBusInteractionRecorder;
import io.kronikol.eventbus.EventBusTrackerOptions;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Verifies the Spring {@code ApplicationEvent} binding records each published event as a {@code Publish},
 * unwraps {@code PayloadApplicationEvent}, skips Spring framework events, and honours a custom filter — driven
 * through a real {@link GenericApplicationContext} (not a mocked publisher).
 */
class KronikolEventBusListenerTest {

    /** A user message published via {@code publishEvent(Object)} (wrapped in a PayloadApplicationEvent). */
    record OrderPlaced(String id) {
    }

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static EventBusInteractionRecorder recorder() {
        return new EventBusInteractionRecorder(EventBusTrackerOptions.builder()
            .serviceName("OrderBus").callerName("Test")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build());
    }

    @Test
    void publishedPayloadIsRecordedAsAPublishPair() {
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            ctx.refresh(); // ContextRefreshedEvent fires here — must be skipped by the default filter
            ctx.addApplicationListener(new KronikolEventBusListener(recorder()));

            ctx.publishEvent(new OrderPlaced("o-1"));

            List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
            assertThat(logs).hasSize(2); // request + response, only the user event (refresh skipped)
            assertThat(logs).allSatisfy(l ->
                assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT));

            RequestResponseLog request = logs.stream()
                .filter(l -> l.type() == RequestResponseType.REQUEST).findFirst().orElseThrow();
            assertThat(request.method().value()).isEqualTo("Publish OrderPlaced"); // payload class, not wrapper
            assertThat(request.uri().toString()).isEqualTo("masstransit:///unknown"); // no in-process address
            assertThat(request.content()).contains("o-1"); // serialized payload body
        }
    }

    @Test
    void springFrameworkEventsAreSkippedByDefault() {
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            ctx.addApplicationListener(new KronikolEventBusListener(recorder()));
            ctx.refresh(); // ContextRefreshedEvent
            ctx.start();   // ContextStartedEvent
            ctx.stop();    // ContextStoppedEvent

            assertThat(RequestResponseLogger.getAllLogs()).isEmpty(); // all org.springframework.* → skipped
        }
    }

    @Test
    void customFilterCanSuppressSelectedMessages() {
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            ctx.refresh();
            ctx.addApplicationListener(new KronikolEventBusListener(recorder(),
                msg -> msg instanceof OrderPlaced placed && placed.id().startsWith("keep")));

            ctx.publishEvent(new OrderPlaced("drop-1"));
            ctx.publishEvent(new OrderPlaced("keep-1"));

            List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
            assertThat(logs).hasSize(2); // only the kept message produced a pair
            assertThat(logs).allSatisfy(l -> assertThat(l.content() == null || l.content().contains("keep-1"))
                .isTrue());
        }
    }

    @Test
    void isUserMessageRejectsSpringTypesAndNull() {
        assertThat(KronikolEventBusListener.isUserMessage(new OrderPlaced("x"))).isTrue();
        assertThat(KronikolEventBusListener.isUserMessage(null)).isFalse();
        assertThat(KronikolEventBusListener.isUserMessage(
            new org.springframework.context.event.ContextRefreshedEvent(new GenericApplicationContext())))
            .isFalse();
    }
}
