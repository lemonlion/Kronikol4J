package io.kronikol.messaging;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.serialization.TrackingSafeSerializer;
import io.kronikol.core.serialization.TrackingSerializerOptions;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Configuration for the injectable {@link MessageTracker}. Java port of the .NET {@code MessageTrackerOptions}.
 * Built via {@link #builder()}.
 */
public final class MessageTrackerOptions {

    private static final Function<Object, String> DEFAULT_SERIALIZER =
        payload -> TrackingSafeSerializer.serialize(payload,
            TrackingSerializerOptions.builder().writeIndented(false).build());

    private final String serviceName;
    private final String callerName;
    private final TrackingVerbosity verbosity;
    private final TrackingVerbosity setupVerbosity;
    private final TrackingVerbosity actionVerbosity;
    private final boolean trackDuringSetup;
    private final boolean trackDuringAction;
    private final String dependencyCategory;
    private final String callerDependencyCategory;
    private final Supplier<TestInfo> testInfoFetcher;
    private final Function<Object, String> payloadSerializer;
    private final IdGenerator ids;

    private MessageTrackerOptions(Builder b) {
        this.serviceName = b.serviceName;
        this.callerName = b.callerName;
        this.verbosity = b.verbosity;
        this.setupVerbosity = b.setupVerbosity;
        this.actionVerbosity = b.actionVerbosity;
        this.trackDuringSetup = b.trackDuringSetup;
        this.trackDuringAction = b.trackDuringAction;
        this.dependencyCategory = b.dependencyCategory;
        this.callerDependencyCategory = b.callerDependencyCategory;
        this.testInfoFetcher = b.testInfoFetcher;
        this.payloadSerializer = b.payloadSerializer;
        this.ids = b.ids;
    }

    public String serviceName() { return serviceName; }
    public String callerName() { return callerName; }
    public TrackingVerbosity verbosity() { return verbosity; }
    public TrackingVerbosity setupVerbosity() { return setupVerbosity; }
    public TrackingVerbosity actionVerbosity() { return actionVerbosity; }
    public boolean trackDuringSetup() { return trackDuringSetup; }
    public boolean trackDuringAction() { return trackDuringAction; }
    public String dependencyCategory() { return dependencyCategory; }
    public String callerDependencyCategory() { return callerDependencyCategory; }
    public Supplier<TestInfo> testInfoFetcher() { return testInfoFetcher; }
    public Function<Object, String> payloadSerializer() { return payloadSerializer; }
    public IdGenerator ids() { return ids; }

    public static MessageTrackerOptions forService(String serviceName) {
        return builder().serviceName(serviceName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceName = "MessageBus";
        private String callerName = TrackingDefaults.CALLER_NAME;
        private TrackingVerbosity verbosity = TrackingVerbosity.DETAILED;
        private TrackingVerbosity setupVerbosity;
        private TrackingVerbosity actionVerbosity;
        private boolean trackDuringSetup = true;
        private boolean trackDuringAction = true;
        private String dependencyCategory = DependencyCategories.MESSAGE_QUEUE;
        private String callerDependencyCategory;
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
        public Builder trackDuringSetup(boolean v) { this.trackDuringSetup = v; return this; }
        public Builder trackDuringAction(boolean v) { this.trackDuringAction = v; return this; }
        public Builder dependencyCategory(String v) { this.dependencyCategory = v; return this; }
        public Builder callerDependencyCategory(String v) { this.callerDependencyCategory = v; return this; }
        public Builder testInfoFetcher(Supplier<TestInfo> v) { this.testInfoFetcher = v; return this; }
        public Builder payloadSerializer(Function<Object, String> v) {
            this.payloadSerializer = v == null ? DEFAULT_SERIALIZER : v;
            return this;
        }
        public Builder ids(IdGenerator v) { this.ids = v == null ? IdGenerator.random() : v; return this; }

        public MessageTrackerOptions build() {
            return new MessageTrackerOptions(this);
        }
    }
}
