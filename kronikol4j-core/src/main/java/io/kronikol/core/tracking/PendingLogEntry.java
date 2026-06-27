package io.kronikol.core.tracking;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A request/response log entry queued for deferred flushing — used with {@link PendingRequestResponseLogs}
 * when logging must be delayed until test identity is available. Java port of the .NET {@code PendingLogEntry}
 * record. Carries both halves' content (request + response) so {@code flushAll} can emit the pair.
 */
public record PendingLogEntry(
    String serviceName,
    String callerName,
    Method method,
    String requestContent,
    String responseContent,
    URI uri,
    StatusCode statusCode,
    String activityTraceId,
    String activitySpanId,
    String dependencyCategory,
    OffsetDateTime timestamp) {

    public static Builder builder() {
        return new Builder();
    }

    /** Builder defaulting {@code statusCode} to 200 and {@code timestamp} to now (UTC), like .NET. */
    public static final class Builder {
        private String serviceName;
        private String callerName;
        private Method method;
        private String requestContent;
        private String responseContent;
        private URI uri;
        private StatusCode statusCode = StatusCode.of(200);
        private String activityTraceId;
        private String activitySpanId;
        private String dependencyCategory;
        private OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

        public Builder serviceName(String v) { this.serviceName = v; return this; }
        public Builder callerName(String v) { this.callerName = v; return this; }
        public Builder method(Method v) { this.method = v; return this; }
        public Builder requestContent(String v) { this.requestContent = v; return this; }
        public Builder responseContent(String v) { this.responseContent = v; return this; }
        public Builder uri(URI v) { this.uri = v; return this; }
        public Builder statusCode(StatusCode v) { this.statusCode = v; return this; }
        public Builder activityTraceId(String v) { this.activityTraceId = v; return this; }
        public Builder activitySpanId(String v) { this.activitySpanId = v; return this; }
        public Builder dependencyCategory(String v) { this.dependencyCategory = v; return this; }
        public Builder timestamp(OffsetDateTime v) { this.timestamp = v; return this; }

        public PendingLogEntry build() {
            return new PendingLogEntry(serviceName, callerName, method, requestContent, responseContent,
                uri, statusCode, activityTraceId, activitySpanId, dependencyCategory, timestamp);
        }
    }
}
