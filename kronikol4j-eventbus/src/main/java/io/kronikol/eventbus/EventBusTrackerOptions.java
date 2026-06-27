package io.kronikol.eventbus;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.serialization.TrackingSafeSerializer;
import io.kronikol.core.serialization.TrackingSerializerOptions;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Configuration for the {@link EventBusInteractionRecorder}. Java port of the .NET
 * {@code MassTransitTrackingOptions}. Built via {@link #builder()}.
 */
public final class EventBusTrackerOptions {

    private static final Function<Object, String> DEFAULT_SERIALIZER =
        payload -> TrackingSafeSerializer.serialize(payload,
            TrackingSerializerOptions.builder().writeIndented(false).build());

    private final String serviceName;
    private final String callerName;
    private final TrackingVerbosity verbosity;
    private final TrackingVerbosity setupVerbosity;
    private final TrackingVerbosity actionVerbosity;
    private final boolean trackSend;
    private final boolean trackPublish;
    private final boolean trackConsume;
    private final boolean logFaults;
    private final boolean logMessageBody;
    private final boolean trackDuringSetup;
    private final boolean trackDuringAction;
    private final Supplier<TestInfo> testInfoFetcher;
    private final Function<Object, String> payloadSerializer;
    private final IdGenerator ids;

    private EventBusTrackerOptions(Builder b) {
        this.serviceName = b.serviceName;
        this.callerName = b.callerName;
        this.verbosity = b.verbosity;
        this.setupVerbosity = b.setupVerbosity;
        this.actionVerbosity = b.actionVerbosity;
        this.trackSend = b.trackSend;
        this.trackPublish = b.trackPublish;
        this.trackConsume = b.trackConsume;
        this.logFaults = b.logFaults;
        this.logMessageBody = b.logMessageBody;
        this.trackDuringSetup = b.trackDuringSetup;
        this.trackDuringAction = b.trackDuringAction;
        this.testInfoFetcher = b.testInfoFetcher;
        this.payloadSerializer = b.payloadSerializer;
        this.ids = b.ids;
    }

    public String serviceName() { return serviceName; }
    public String callerName() { return callerName; }
    public TrackingVerbosity verbosity() { return verbosity; }
    public TrackingVerbosity setupVerbosity() { return setupVerbosity; }
    public TrackingVerbosity actionVerbosity() { return actionVerbosity; }
    public boolean trackSend() { return trackSend; }
    public boolean trackPublish() { return trackPublish; }
    public boolean trackConsume() { return trackConsume; }
    public boolean logFaults() { return logFaults; }
    public boolean logMessageBody() { return logMessageBody; }
    public boolean trackDuringSetup() { return trackDuringSetup; }
    public boolean trackDuringAction() { return trackDuringAction; }
    public Supplier<TestInfo> testInfoFetcher() { return testInfoFetcher; }
    public Function<Object, String> payloadSerializer() { return payloadSerializer; }
    public IdGenerator ids() { return ids; }

    public static EventBusTrackerOptions forBus(String serviceName) {
        return builder().serviceName(serviceName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceName = "EventBus";
        private String callerName = TrackingDefaults.CALLER_NAME;
        private TrackingVerbosity verbosity = TrackingVerbosity.DETAILED;
        private TrackingVerbosity setupVerbosity;
        private TrackingVerbosity actionVerbosity;
        private boolean trackSend = true;
        private boolean trackPublish = true;
        private boolean trackConsume = true;
        private boolean logFaults = true;
        private boolean logMessageBody = true;
        private boolean trackDuringSetup = true;
        private boolean trackDuringAction = true;
        private Supplier<TestInfo> testInfoFetcher;
        private Function<Object, String> payloadSerializer = DEFAULT_SERIALIZER;
        private IdGenerator ids = IdGenerator.random();

        public Builder serviceName(String v) { this.serviceName = v; return this; }
        public Builder callerName(String v) { this.callerName = v; return this; }
        public Builder verbosity(TrackingVerbosity v) {
            this.verbosity = v == null ? TrackingVerbosity.DETAILED : v;
            return this;
        }
        public Builder setupVerbosity(TrackingVerbosity v) { this.setupVerbosity = v; return this; }
        public Builder actionVerbosity(TrackingVerbosity v) { this.actionVerbosity = v; return this; }
        public Builder trackSend(boolean v) { this.trackSend = v; return this; }
        public Builder trackPublish(boolean v) { this.trackPublish = v; return this; }
        public Builder trackConsume(boolean v) { this.trackConsume = v; return this; }
        public Builder logFaults(boolean v) { this.logFaults = v; return this; }
        public Builder logMessageBody(boolean v) { this.logMessageBody = v; return this; }
        public Builder trackDuringSetup(boolean v) { this.trackDuringSetup = v; return this; }
        public Builder trackDuringAction(boolean v) { this.trackDuringAction = v; return this; }
        public Builder testInfoFetcher(Supplier<TestInfo> v) { this.testInfoFetcher = v; return this; }
        public Builder payloadSerializer(Function<Object, String> v) {
            this.payloadSerializer = v == null ? DEFAULT_SERIALIZER : v;
            return this;
        }
        public Builder ids(IdGenerator v) { this.ids = v == null ? IdGenerator.random() : v; return this; }

        public EventBusTrackerOptions build() {
            return new EventBusTrackerOptions(this);
        }
    }
}
