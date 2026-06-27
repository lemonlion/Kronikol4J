package io.kronikol.mongodb;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Configuration for the MongoDB command listener. Java port of the .NET {@code MongoDbTrackingOptions}.
 * Built via {@link #builder()}.
 */
public final class MongoDbTrackingOptions {

    /** Noise commands ignored by default (handshake / heartbeat / cursor cleanup). */
    public static final Set<String> DEFAULT_IGNORED_COMMANDS = Set.of(
        "isMaster", "hello", "saslStart", "saslContinue",
        "ping", "buildInfo", "getLastError", "killCursors", "endSessions");

    private final String serviceName;
    private final String callerName;
    private final TrackingVerbosity verbosity;
    private final TrackingVerbosity setupVerbosity;
    private final TrackingVerbosity actionVerbosity;
    private final boolean trackDuringSetup;
    private final boolean trackDuringAction;
    private final Set<String> ignoredCommands;
    private final boolean trackGetMore;
    private final Set<MongoDbOperation> excludedOperations;
    private final boolean logFilterText;
    private final boolean logResponseContent;
    private final boolean autoCorrelateWrites;
    private final int maxResponseDocuments;
    private final Supplier<TestInfo> testInfoFetcher;
    private final IdGenerator ids;

    private MongoDbTrackingOptions(Builder b) {
        this.serviceName = b.serviceName;
        this.callerName = b.callerName;
        this.verbosity = b.verbosity;
        this.setupVerbosity = b.setupVerbosity;
        this.actionVerbosity = b.actionVerbosity;
        this.trackDuringSetup = b.trackDuringSetup;
        this.trackDuringAction = b.trackDuringAction;
        this.ignoredCommands = Set.copyOf(b.ignoredCommands);
        this.trackGetMore = b.trackGetMore;
        this.excludedOperations = Set.copyOf(b.excludedOperations);
        this.logFilterText = b.logFilterText;
        this.logResponseContent = b.logResponseContent;
        this.autoCorrelateWrites = b.autoCorrelateWrites;
        this.maxResponseDocuments = b.maxResponseDocuments;
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
    public Set<String> ignoredCommands() { return ignoredCommands; }
    public boolean trackGetMore() { return trackGetMore; }
    public Set<MongoDbOperation> excludedOperations() { return excludedOperations; }
    public boolean logFilterText() { return logFilterText; }
    public boolean logResponseContent() { return logResponseContent; }
    public boolean autoCorrelateWrites() { return autoCorrelateWrites; }
    public int maxResponseDocuments() { return maxResponseDocuments; }
    public Supplier<TestInfo> testInfoFetcher() { return testInfoFetcher; }
    public IdGenerator ids() { return ids; }

    public static MongoDbTrackingOptions forDatabase(String serviceName) {
        return builder().serviceName(serviceName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceName = "MongoDB";
        private String callerName = TrackingDefaults.CALLER_NAME;
        private TrackingVerbosity verbosity = TrackingVerbosity.DETAILED;
        private TrackingVerbosity setupVerbosity;
        private TrackingVerbosity actionVerbosity;
        private boolean trackDuringSetup = true;
        private boolean trackDuringAction = true;
        private Set<String> ignoredCommands = DEFAULT_IGNORED_COMMANDS;
        private boolean trackGetMore = false;
        private Set<MongoDbOperation> excludedOperations = Set.of();
        private boolean logFilterText = true;
        private boolean logResponseContent = true;
        private boolean autoCorrelateWrites = true;
        private int maxResponseDocuments = 10;
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
        public Builder ignoredCommands(Set<String> v) {
            this.ignoredCommands = v == null ? Set.of() : v;
            return this;
        }
        public Builder trackGetMore(boolean v) { this.trackGetMore = v; return this; }
        public Builder excludedOperations(Set<MongoDbOperation> v) {
            this.excludedOperations = v == null ? Set.of() : v;
            return this;
        }
        public Builder logFilterText(boolean v) { this.logFilterText = v; return this; }
        public Builder logResponseContent(boolean v) { this.logResponseContent = v; return this; }
        public Builder autoCorrelateWrites(boolean v) { this.autoCorrelateWrites = v; return this; }
        public Builder maxResponseDocuments(int v) { this.maxResponseDocuments = v; return this; }
        public Builder testInfoFetcher(Supplier<TestInfo> v) { this.testInfoFetcher = v; return this; }
        public Builder ids(IdGenerator v) { this.ids = v == null ? IdGenerator.random() : v; return this; }

        public MongoDbTrackingOptions build() {
            return new MongoDbTrackingOptions(this);
        }
    }
}
