package io.kronikol.http;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.naming.ExcludedHosts;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared configuration for the HTTP client adapters ({@link KronikolOkHttpInterceptor} and
 * {@link TrackingHttpClient}). Wires the cross-cutting capture infrastructure — service-name resolution,
 * excluded hosts, phase filtering, verbosity, the id seam — into whichever client adapter consumes it.
 * Built via {@link #builder()}.
 */
public final class HttpTrackingConfig {

    private final String fixedServiceName;
    private final String clientName;
    private final Map<String, String> clientNamesToServiceNames;
    private final Map<Integer, String> portsToServiceNames;
    private final String callerName;
    private final String dependencyCategory;
    private final Supplier<TestInfo> testInfoFetcher;
    private final ExcludedHosts excludedHosts;
    private final boolean trackDuringSetup;
    private final boolean trackDuringAction;
    private final boolean injectTraceparent;
    private final TrackingVerbosity verbosity;
    private final IdGenerator ids;
    private final List<String> headersToForward;
    private final Supplier<String> currentStepTypeFetcher;
    private final List<String> internalFlowActivitySources;

    private HttpTrackingConfig(Builder b) {
        this.fixedServiceName = b.fixedServiceName;
        this.clientName = b.clientName;
        this.clientNamesToServiceNames = b.clientNamesToServiceNames;
        this.portsToServiceNames = b.portsToServiceNames;
        this.callerName = b.callerName;
        this.dependencyCategory = b.dependencyCategory;
        this.testInfoFetcher = b.testInfoFetcher;
        this.excludedHosts = b.excludedHosts;
        this.trackDuringSetup = b.trackDuringSetup;
        this.trackDuringAction = b.trackDuringAction;
        this.injectTraceparent = b.injectTraceparent;
        this.verbosity = b.verbosity;
        this.ids = b.ids;
        this.headersToForward = b.headersToForward;
        this.currentStepTypeFetcher = b.currentStepTypeFetcher;
        this.internalFlowActivitySources = b.internalFlowActivitySources;
    }

    public String fixedServiceName() { return fixedServiceName; }
    public String clientName() { return clientName; }
    public Map<String, String> clientNamesToServiceNames() { return clientNamesToServiceNames; }
    public Map<Integer, String> portsToServiceNames() { return portsToServiceNames; }
    public String callerName() { return callerName; }
    public String dependencyCategory() { return dependencyCategory; }
    public Supplier<TestInfo> testInfoFetcher() { return testInfoFetcher; }
    public ExcludedHosts excludedHosts() { return excludedHosts; }
    public boolean trackDuringSetup() { return trackDuringSetup; }
    public boolean trackDuringAction() { return trackDuringAction; }
    public boolean injectTraceparent() { return injectTraceparent; }
    public TrackingVerbosity verbosity() { return verbosity; }
    public IdGenerator ids() { return ids; }

    /** HTTP header names to forward from the incoming test/server context to outgoing requests. */
    public List<String> headersToForward() { return headersToForward; }

    /** Returns the current test step type (e.g. "Given"/"When"/"Then"), or {@code null}. Set by adapters. */
    public Supplier<String> currentStepTypeFetcher() { return currentStepTypeFetcher; }

    /** OpenTelemetry activity-source names to capture for InternalFlow diagrams. */
    public List<String> internalFlowActivitySources() { return internalFlowActivitySources; }

    /** A minimal configuration that resolves participant names from {@code portsToServiceNames}/host. */
    public static HttpTrackingConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String fixedServiceName;
        private String clientName;
        private Map<String, String> clientNamesToServiceNames = Map.of();
        private Map<Integer, String> portsToServiceNames = Map.of();
        private String callerName = TrackingDefaults.CALLER_NAME;
        private String dependencyCategory = DependencyCategories.HTTP;
        private Supplier<TestInfo> testInfoFetcher;
        private ExcludedHosts excludedHosts = ExcludedHosts.of(List.of());
        private boolean trackDuringSetup = true;
        private boolean trackDuringAction = true;
        private boolean injectTraceparent = true;
        private TrackingVerbosity verbosity = TrackingVerbosity.DETAILED;
        private IdGenerator ids = IdGenerator.random();
        private List<String> headersToForward = List.of();
        private Supplier<String> currentStepTypeFetcher;
        private List<String> internalFlowActivitySources = List.of();

        /** Sets a fixed participant name for the called service (highest-priority resolution). */
        public Builder fixedServiceName(String v) { this.fixedServiceName = v; return this; }
        public Builder clientName(String v) { this.clientName = v; return this; }
        public Builder clientNamesToServiceNames(Map<String, String> v) {
            this.clientNamesToServiceNames = v == null ? Map.of() : v;
            return this;
        }
        public Builder portsToServiceNames(Map<Integer, String> v) {
            this.portsToServiceNames = v == null ? Map.of() : v;
            return this;
        }
        public Builder callerName(String v) { this.callerName = v; return this; }
        public Builder dependencyCategory(String v) { this.dependencyCategory = v; return this; }
        public Builder testInfoFetcher(Supplier<TestInfo> v) { this.testInfoFetcher = v; return this; }
        public Builder excludedHosts(ExcludedHosts v) {
            this.excludedHosts = v == null ? ExcludedHosts.of(List.of()) : v;
            return this;
        }
        public Builder trackDuringSetup(boolean v) { this.trackDuringSetup = v; return this; }
        public Builder trackDuringAction(boolean v) { this.trackDuringAction = v; return this; }
        public Builder injectTraceparent(boolean v) { this.injectTraceparent = v; return this; }
        public Builder verbosity(TrackingVerbosity v) {
            this.verbosity = v == null ? TrackingVerbosity.DETAILED : v;
            return this;
        }
        public Builder ids(IdGenerator v) { this.ids = v == null ? IdGenerator.random() : v; return this; }
        public Builder headersToForward(List<String> v) {
            this.headersToForward = v == null ? List.of() : List.copyOf(v);
            return this;
        }
        public Builder currentStepTypeFetcher(Supplier<String> v) { this.currentStepTypeFetcher = v; return this; }
        public Builder internalFlowActivitySources(List<String> v) {
            this.internalFlowActivitySources = v == null ? List.of() : List.copyOf(v);
            return this;
        }

        public HttpTrackingConfig build() {
            return new HttpTrackingConfig(this);
        }
    }
}
