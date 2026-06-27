package io.kronikol.jdbc;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.sql.UnifiedSqlOperation;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Configuration for JDBC SQL tracking. Java port of the .NET {@code SqlTrackingOptionsBase} — the option
 * surface that shapes what a SQL interaction records (service name, verbosity, phase filtering, URI scheme,
 * response detail, excluded operations). Built via {@link #builder()}.
 */
public final class SqlTrackingOptions {

    private final String serviceName;
    private final String callerName;
    private final String dependencyCategory;
    private final String uriScheme;
    private final TrackingVerbosity verbosity;
    private final TrackingVerbosity setupVerbosity;
    private final TrackingVerbosity actionVerbosity;
    private final boolean trackDuringSetup;
    private final boolean trackDuringAction;
    private final boolean logSqlText;
    private final boolean logParameters;
    private final boolean logResponseContent;
    private final Set<UnifiedSqlOperation> excludedOperations;
    private final int maxResponseRows;
    private final int maxValueDisplayLength;
    private final SqlResponseDetail responseDetail;
    private final Supplier<TestInfo> testInfoFetcher;
    private final IdGenerator ids;

    private SqlTrackingOptions(Builder b) {
        this.serviceName = b.serviceName;
        this.callerName = b.callerName;
        this.dependencyCategory = b.dependencyCategory;
        this.uriScheme = b.uriScheme;
        this.verbosity = b.verbosity;
        this.setupVerbosity = b.setupVerbosity;
        this.actionVerbosity = b.actionVerbosity;
        this.trackDuringSetup = b.trackDuringSetup;
        this.trackDuringAction = b.trackDuringAction;
        this.logSqlText = b.logSqlText;
        this.logParameters = b.logParameters;
        this.logResponseContent = b.logResponseContent;
        this.excludedOperations = Set.copyOf(b.excludedOperations);
        this.maxResponseRows = b.maxResponseRows;
        this.maxValueDisplayLength = b.maxValueDisplayLength;
        this.responseDetail = b.responseDetail;
        this.testInfoFetcher = b.testInfoFetcher;
        this.ids = b.ids;
    }

    public String serviceName() { return serviceName; }
    public String callerName() { return callerName; }
    public String dependencyCategory() { return dependencyCategory; }
    public String uriScheme() { return uriScheme; }
    public TrackingVerbosity verbosity() { return verbosity; }
    public TrackingVerbosity setupVerbosity() { return setupVerbosity; }
    public TrackingVerbosity actionVerbosity() { return actionVerbosity; }
    public boolean trackDuringSetup() { return trackDuringSetup; }
    public boolean trackDuringAction() { return trackDuringAction; }
    public boolean logSqlText() { return logSqlText; }
    public boolean logParameters() { return logParameters; }
    public boolean logResponseContent() { return logResponseContent; }
    public Set<UnifiedSqlOperation> excludedOperations() { return excludedOperations; }
    public int maxResponseRows() { return maxResponseRows; }
    public int maxValueDisplayLength() { return maxValueDisplayLength; }
    public SqlResponseDetail responseDetail() { return responseDetail; }
    public Supplier<TestInfo> testInfoFetcher() { return testInfoFetcher; }
    public IdGenerator ids() { return ids; }

    public static SqlTrackingOptions forDatabase(String serviceName) {
        return builder().serviceName(serviceName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceName = "Database";
        private String callerName = TrackingDefaults.CALLER_NAME;
        private String dependencyCategory = DependencyCategories.SQL;
        private String uriScheme = "sql";
        private TrackingVerbosity verbosity = TrackingVerbosity.DETAILED;
        private TrackingVerbosity setupVerbosity;
        private TrackingVerbosity actionVerbosity;
        private boolean trackDuringSetup = true;
        private boolean trackDuringAction = true;
        private boolean logSqlText = true;
        private boolean logParameters;
        private boolean logResponseContent = true;
        private Set<UnifiedSqlOperation> excludedOperations = Set.of();
        private int maxResponseRows = 10;
        private int maxValueDisplayLength = 500;
        private SqlResponseDetail responseDetail = SqlResponseDetail.ROW_COUNT_AND_COLUMNS;
        private Supplier<TestInfo> testInfoFetcher;
        private IdGenerator ids = IdGenerator.random();

        public Builder serviceName(String v) { this.serviceName = v; return this; }
        public Builder callerName(String v) { this.callerName = v; return this; }
        public Builder dependencyCategory(String v) { this.dependencyCategory = v; return this; }
        public Builder uriScheme(String v) { this.uriScheme = v; return this; }
        public Builder verbosity(TrackingVerbosity v) {
            this.verbosity = v == null ? TrackingVerbosity.DETAILED : v;
            return this;
        }
        public Builder setupVerbosity(TrackingVerbosity v) { this.setupVerbosity = v; return this; }
        public Builder actionVerbosity(TrackingVerbosity v) { this.actionVerbosity = v; return this; }
        public Builder trackDuringSetup(boolean v) { this.trackDuringSetup = v; return this; }
        public Builder trackDuringAction(boolean v) { this.trackDuringAction = v; return this; }
        public Builder logSqlText(boolean v) { this.logSqlText = v; return this; }
        public Builder logParameters(boolean v) { this.logParameters = v; return this; }
        public Builder logResponseContent(boolean v) { this.logResponseContent = v; return this; }
        public Builder excludedOperations(Set<UnifiedSqlOperation> v) {
            this.excludedOperations = v == null ? Set.of() : v;
            return this;
        }
        public Builder maxResponseRows(int v) { this.maxResponseRows = v; return this; }
        public Builder maxValueDisplayLength(int v) { this.maxValueDisplayLength = v; return this; }
        public Builder responseDetail(SqlResponseDetail v) {
            this.responseDetail = v == null ? SqlResponseDetail.ROW_COUNT_AND_COLUMNS : v;
            return this;
        }
        public Builder testInfoFetcher(Supplier<TestInfo> v) { this.testInfoFetcher = v; return this; }
        public Builder ids(IdGenerator v) { this.ids = v == null ? IdGenerator.random() : v; return this; }

        public SqlTrackingOptions build() {
            return new SqlTrackingOptions(this);
        }
    }
}
