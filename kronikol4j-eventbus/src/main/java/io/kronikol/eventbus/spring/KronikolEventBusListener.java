package io.kronikol.eventbus.spring;

import io.kronikol.eventbus.EventBusInteractionRecorder;
import io.kronikol.eventbus.EventBusOperationClassifier;
import io.kronikol.eventbus.EventBusOperationInfo;
import java.util.function.Predicate;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

/**
 * Binds the Kronikol {@link EventBusInteractionRecorder} to Spring's in-process application-event bus — the
 * Java analog of the .NET MassTransit {@code TrackingPublishObserver}. Registered as a Spring bean (or via
 * {@code ConfigurableApplicationContext.addApplicationListener}), it observes every event delivered by the
 * application-event multicaster and records it as a {@code Publish} interaction.
 *
 * <p><b>Why Publish (and only Publish).</b> Spring's {@code ApplicationEventPublisher.publishEvent(...)} is a
 * pure broadcast (fan-out) — there is no directed "Send" and no per-consumer "Consume" seam the framework
 * exposes for observation, so the faithful single-binding for the Spring bus is publish-side: each event on
 * the bus maps to one {@code Publish}. MassTransit's Send/Consume(+Fault) paths remain available on the
 * recorder for transports that distinguish them (e.g. Axon) or for manual use.
 *
 * <p><b>Payload unwrapping.</b> Arbitrary objects published via {@code publishEvent(Object)} arrive wrapped in
 * a {@link PayloadApplicationEvent}; this listener unwraps to the payload so the recorded message type is the
 * user's class, not the Spring wrapper. Events that already extend {@link ApplicationEvent} are recorded as
 * themselves.
 *
 * <p><b>Filtering.</b> By default Spring's own framework lifecycle events (anything in the
 * {@code org.springframework.*} package — {@code ContextRefreshedEvent}, etc.) are skipped, mirroring how the
 * MassTransit observer only sees user messages. Supply a custom {@link Predicate} to override.
 *
 * <p><b>Identity.</b> Spring delivers events synchronously on the publishing thread by default, so the ambient
 * test identity resolved by the recorder applies without header propagation. Asynchronous delivery
 * ({@code @Async} listeners / an async multicaster) crosses threads — the documented cross-thread limitation;
 * use the data-keyed correlation store for that case.
 */
public final class KronikolEventBusListener implements ApplicationListener<ApplicationEvent> {

    private final EventBusInteractionRecorder recorder;
    private final Predicate<Object> messageFilter;

    /** Binds the recorder with the default filter (skips {@code org.springframework.*} framework events). */
    public KronikolEventBusListener(EventBusInteractionRecorder recorder) {
        this(recorder, KronikolEventBusListener::isUserMessage);
    }

    /**
     * Binds the recorder with a custom message filter — the (already-unwrapped) payload is tested; return
     * {@code false} to skip recording for that message.
     */
    public KronikolEventBusListener(EventBusInteractionRecorder recorder, Predicate<Object> messageFilter) {
        this.recorder = recorder;
        this.messageFilter = messageFilter == null ? KronikolEventBusListener::isUserMessage : messageFilter;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        Object message = event instanceof PayloadApplicationEvent<?> payload ? payload.getPayload() : event;
        if (!messageFilter.test(message)) {
            return;
        }
        EventBusOperationInfo op = EventBusOperationClassifier.classifyPublish(
            message.getClass().getSimpleName(), null, null, null, null);
        recorder.logPublish(op, message);
    }

    /** The default filter: a non-null message whose class is not a Spring framework type. */
    public static boolean isUserMessage(Object message) {
        return message != null && !message.getClass().getName().startsWith("org.springframework.");
    }
}
