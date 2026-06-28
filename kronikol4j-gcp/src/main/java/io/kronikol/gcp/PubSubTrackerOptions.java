package io.kronikol.gcp;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.function.Supplier;

/**
 * Configuration for the {@link PubSubInteractionRecorder} (and the {@code TrackingPublisher}/
 * {@code TrackingMessageReceiver} wrappers). Java port of the .NET {@code PubSubTrackingOptions}. Built via
 * {@link #builder()}.
 */
public final class PubSubTrackerOptions {

    private final String serviceName;
    private final String callerName;
    private final TrackingVerbosity verbosity;
    private final TrackingVerbosity setupVerbosity;
    private final TrackingVerbosity actionVerbosity;
    private final boolean trackDuringSetup;
    private final boolean trackDuringAction;
    private final Supplier<TestInfo> testInfoFetcher;
    private final IdGenerator ids;

    private PubSubTrackerOptions(Builder b) {
        this.serviceName = b.serviceName;
        this.callerName = b.callerName;
        this.verbosity = b.verbosity;
        this.setupVerbosity = b.setupVerbosity;
        this.actionVerbosity = b.actionVerbosity;
        this.trackDuringSetup = b.trackDuringSetup;
        this.trackDuringAction = b.trackDuringAction;
        this.testInfoFetcher = b.testInfoFetcher;
        this.ids = b.ids;
    }

    public String serviceName() { return serviceName; }
    public String callerName() { return callerName; }
    public TrackingVerbosity verbosity() { return verbosity; }
    public TrackingVerbosity setupVerbosity() { return setupVerbosity; }
    public TrackingVerbosity actionVerbosity() { return actionVerbosity; }
    public boolean trackDuringSetup() { return trackDuringSetup; }
    public boolean trackDuringAction() { return trackDuringAction; }
    public Supplier<TestInfo> testInfoFetcher() { return testInfoFetcher; }
    public IdGenerator ids() { return ids; }

    public static PubSubTrackerOptions forProject(String serviceName) {
        return builder().serviceName(serviceName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceName = "PubSub";
        private String callerName = TrackingDefaults.CALLER_NAME;
        private TrackingVerbosity verbosity = TrackingVerbosity.DETAILED;
        private TrackingVerbosity setupVerbosity;
        private TrackingVerbosity actionVerbosity;
        private boolean trackDuringSetup = true;
        private boolean trackDuringAction = true;
        private Supplier<TestInfo> testInfoFetcher;
        private IdGenerator ids = IdGenerator.random();

        public Builder serviceName(String v) { this.serviceName = v; return this; }
        public Builder callerName(String v) { this.callerName = v; return this; }
        public Builder verbosity(TrackingVerbosity v) {
            this.verbosity = v == null ? TrackingVerbosity.DETAILED : v;
            return this;
        }
        public Builder setupVerbosity(TrackingVerbosity v) { this.setupVerbosity = v; return this; }
        public Builder actionVerbosity(TrackingVerbosity v) { this.actionVerbosity = v; return this; }
        public Builder trackDuringSetup(boolean v) { this.trackDuringSetup = v; return this; }
        public Builder trackDuringAction(boolean v) { this.trackDuringAction = v; return this; }
        public Builder testInfoFetcher(Supplier<TestInfo> v) { this.testInfoFetcher = v; return this; }
        public Builder ids(IdGenerator v) { this.ids = v == null ? IdGenerator.random() : v; return this; }

        public PubSubTrackerOptions build() {
            return new PubSubTrackerOptions(this);
        }
    }
}
