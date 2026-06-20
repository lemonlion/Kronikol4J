package io.kronikol.core.tracking;

import io.kronikol.core.context.TestInfo;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One half (request or response) of a tracked interaction — the lingua franca every extension
 * produces and the report consumes (plan §1 Seam A).
 *
 * <p>Following plan §3.3, the <strong>core fields are final</strong> (set via {@link Builder}) while
 * a small set of <strong>enrichment fields are mutable</strong> (set by extensions/the report
 * pipeline after construction), mirroring the .NET "immutable record + settable properties" shape.
 * A request/response pair shares {@link #traceId()} and {@link #requestResponseId()}.
 */
public final class RequestResponseLog {

    // --- core (final) ---
    private final String testName;
    private final String testId;
    private final Method method;
    private final String content; // nullable
    private final URI uri;
    private final List<Header> headers;
    private final String serviceName;
    private final String callerName;
    private final RequestResponseType type;
    private final UUID traceId;
    private final UUID requestResponseId;
    private final boolean trackingIgnore;
    private final StatusCode statusCode; // nullable
    private final RequestResponseMetaType metaType;
    private final String dependencyCategory; // nullable
    private final String callerDependencyCategory; // nullable

    // --- enrichment (mutable, set after construction) ---
    private boolean noteOnRight;
    private boolean overrideStart;
    private boolean overrideEnd;
    private boolean actionStart;
    private String plantUml; // custom UML fragment override
    private List<String> focusFields;
    private OffsetDateTime timestamp;
    private String activitySpanId;
    private String activityTraceId;
    private TestPhase phase = TestPhase.UNKNOWN;
    private PhaseVariant setupVariant;
    private PhaseVariant actionVariant;

    private RequestResponseLog(Builder b) {
        this.testName = Objects.requireNonNull(b.testName, "testName");
        this.testId = Objects.requireNonNull(b.testId, "testId");
        this.method = Objects.requireNonNull(b.method, "method");
        this.content = b.content;
        this.uri = Objects.requireNonNull(b.uri, "uri");
        this.headers = b.headers == null ? List.of() : List.copyOf(b.headers);
        this.serviceName = Objects.requireNonNull(b.serviceName, "serviceName");
        this.callerName = Objects.requireNonNull(b.callerName, "callerName");
        this.type = Objects.requireNonNull(b.type, "type");
        this.traceId = Objects.requireNonNull(b.traceId, "traceId");
        this.requestResponseId = Objects.requireNonNull(b.requestResponseId, "requestResponseId");
        this.trackingIgnore = b.trackingIgnore;
        this.statusCode = b.statusCode;
        this.metaType = b.metaType == null ? RequestResponseMetaType.DEFAULT : b.metaType;
        this.dependencyCategory = b.dependencyCategory;
        this.callerDependencyCategory = b.callerDependencyCategory;
        // enrichment carried through toBuilder()
        this.noteOnRight = b.noteOnRight;
        this.overrideStart = b.overrideStart;
        this.overrideEnd = b.overrideEnd;
        this.actionStart = b.actionStart;
        this.plantUml = b.plantUml;
        this.focusFields = b.focusFields;
        this.timestamp = b.timestamp;
        this.activitySpanId = b.activitySpanId;
        this.activityTraceId = b.activityTraceId;
        this.phase = b.phase == null ? TestPhase.UNKNOWN : b.phase;
        this.setupVariant = b.setupVariant;
        this.actionVariant = b.actionVariant;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder seeded with this log's values — for producing modified copies. */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.testName = testName;
        b.testId = testId;
        b.method = method;
        b.content = content;
        b.uri = uri;
        b.headers = headers;
        b.serviceName = serviceName;
        b.callerName = callerName;
        b.type = type;
        b.traceId = traceId;
        b.requestResponseId = requestResponseId;
        b.trackingIgnore = trackingIgnore;
        b.statusCode = statusCode;
        b.metaType = metaType;
        b.dependencyCategory = dependencyCategory;
        b.callerDependencyCategory = callerDependencyCategory;
        b.noteOnRight = noteOnRight;
        b.overrideStart = overrideStart;
        b.overrideEnd = overrideEnd;
        b.actionStart = actionStart;
        b.plantUml = plantUml;
        b.focusFields = focusFields;
        b.timestamp = timestamp;
        b.activitySpanId = activitySpanId;
        b.activityTraceId = activityTraceId;
        b.phase = phase;
        b.setupVariant = setupVariant;
        b.actionVariant = actionVariant;
        return b;
    }

    // --- core accessors ---
    public String testName() { return testName; }
    public String testId() { return testId; }
    public TestInfo testInfo() { return new TestInfo(testName, testId); }
    public Method method() { return method; }
    public String content() { return content; }
    public URI uri() { return uri; }
    public List<Header> headers() { return headers; }
    public String serviceName() { return serviceName; }
    public String callerName() { return callerName; }
    public RequestResponseType type() { return type; }
    public UUID traceId() { return traceId; }
    public UUID requestResponseId() { return requestResponseId; }
    public boolean trackingIgnore() { return trackingIgnore; }
    public StatusCode statusCode() { return statusCode; }
    public RequestResponseMetaType metaType() { return metaType; }
    public String dependencyCategory() { return dependencyCategory; }
    public String callerDependencyCategory() { return callerDependencyCategory; }

    // --- enrichment accessors/mutators ---
    public boolean noteOnRight() { return noteOnRight; }
    public RequestResponseLog noteOnRight(boolean v) { this.noteOnRight = v; return this; }
    public boolean overrideStart() { return overrideStart; }
    public RequestResponseLog overrideStart(boolean v) { this.overrideStart = v; return this; }
    public boolean overrideEnd() { return overrideEnd; }
    public RequestResponseLog overrideEnd(boolean v) { this.overrideEnd = v; return this; }
    public boolean actionStart() { return actionStart; }
    public RequestResponseLog actionStart(boolean v) { this.actionStart = v; return this; }
    public String plantUml() { return plantUml; }
    public RequestResponseLog plantUml(String v) { this.plantUml = v; return this; }
    public List<String> focusFields() { return focusFields; }
    public RequestResponseLog focusFields(List<String> v) { this.focusFields = v; return this; }
    public OffsetDateTime timestamp() { return timestamp; }
    public RequestResponseLog timestamp(OffsetDateTime v) { this.timestamp = v; return this; }
    public String activitySpanId() { return activitySpanId; }
    public RequestResponseLog activitySpanId(String v) { this.activitySpanId = v; return this; }
    public String activityTraceId() { return activityTraceId; }
    public RequestResponseLog activityTraceId(String v) { this.activityTraceId = v; return this; }
    public TestPhase phase() { return phase; }
    public RequestResponseLog phase(TestPhase v) { this.phase = v == null ? TestPhase.UNKNOWN : v; return this; }
    public PhaseVariant setupVariant() { return setupVariant; }
    public RequestResponseLog setupVariant(PhaseVariant v) { this.setupVariant = v; return this; }
    public PhaseVariant actionVariant() { return actionVariant; }
    public RequestResponseLog actionVariant(PhaseVariant v) { this.actionVariant = v; return this; }

    /** Mutable builder for the final core fields. Required: testName, testId, method, uri,
     *  serviceName, callerName, type, traceId, requestResponseId. */
    public static final class Builder {
        private String testName;
        private String testId;
        private Method method;
        private String content;
        private URI uri;
        private List<Header> headers;
        private String serviceName;
        private String callerName;
        private RequestResponseType type;
        private UUID traceId;
        private UUID requestResponseId;
        private boolean trackingIgnore;
        private StatusCode statusCode;
        private RequestResponseMetaType metaType = RequestResponseMetaType.DEFAULT;
        private String dependencyCategory;
        private String callerDependencyCategory;
        private boolean noteOnRight;
        private boolean overrideStart;
        private boolean overrideEnd;
        private boolean actionStart;
        private String plantUml;
        private List<String> focusFields;
        private OffsetDateTime timestamp;
        private String activitySpanId;
        private String activityTraceId;
        private TestPhase phase = TestPhase.UNKNOWN;
        private PhaseVariant setupVariant;
        private PhaseVariant actionVariant;

        public Builder testName(String v) { this.testName = v; return this; }
        public Builder testId(String v) { this.testId = v; return this; }
        public Builder testInfo(TestInfo info) { this.testName = info.name(); this.testId = info.id(); return this; }
        public Builder method(Method v) { this.method = v; return this; }
        public Builder content(String v) { this.content = v; return this; }
        public Builder uri(URI v) { this.uri = v; return this; }
        public Builder headers(List<Header> v) { this.headers = v; return this; }
        public Builder serviceName(String v) { this.serviceName = v; return this; }
        public Builder callerName(String v) { this.callerName = v; return this; }
        public Builder type(RequestResponseType v) { this.type = v; return this; }
        public Builder traceId(UUID v) { this.traceId = v; return this; }
        public Builder requestResponseId(UUID v) { this.requestResponseId = v; return this; }
        public Builder trackingIgnore(boolean v) { this.trackingIgnore = v; return this; }
        public Builder statusCode(StatusCode v) { this.statusCode = v; return this; }
        public Builder metaType(RequestResponseMetaType v) { this.metaType = v; return this; }
        public Builder dependencyCategory(String v) { this.dependencyCategory = v; return this; }
        public Builder callerDependencyCategory(String v) { this.callerDependencyCategory = v; return this; }
        public Builder phase(TestPhase v) { this.phase = v; return this; }
        public Builder timestamp(OffsetDateTime v) { this.timestamp = v; return this; }
        public Builder setupVariant(PhaseVariant v) { this.setupVariant = v; return this; }
        public Builder actionVariant(PhaseVariant v) { this.actionVariant = v; return this; }

        public RequestResponseLog build() {
            return new RequestResponseLog(this);
        }
    }
}
