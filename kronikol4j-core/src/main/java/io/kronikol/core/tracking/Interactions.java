package io.kronikol.core.tracking;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestPhaseContext;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Convenience for recording a request/response interaction pair (shared trace/request ids, current
 * phase). Adapters that build a simple pair use this instead of duplicating the two-log dance.
 */
public final class Interactions {

    private Interactions() {
    }

    /** Records a request + response pair for {@code who} (no-op if {@code who} is null). */
    public static void recordPair(TestInfo who, String serviceName, String callerName,
                                  String dependencyCategory, Method method, URI uri,
                                  List<Header> requestHeaders, String requestContent,
                                  StatusCode statusCode, String responseContent,
                                  RequestResponseMetaType metaType) {
        if (who == null) {
            return;
        }
        UUID trace = UUID.randomUUID();
        UUID rr = UUID.randomUUID();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri).headers(requestHeaders)
            .serviceName(serviceName).callerName(callerName)
            .type(RequestResponseType.REQUEST).traceId(trace).requestResponseId(rr)
            .dependencyCategory(dependencyCategory).metaType(metaType).content(requestContent)
            .phase(phase).build());

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri)
            .serviceName(serviceName).callerName(callerName)
            .type(RequestResponseType.RESPONSE).traceId(trace).requestResponseId(rr)
            .statusCode(statusCode).dependencyCategory(dependencyCategory).metaType(metaType)
            .content(responseContent)
            .phase(phase).build());
    }

    /** Simple pair: no request headers, default meta type. */
    public static void recordPair(TestInfo who, String serviceName, String callerName,
                                  String dependencyCategory, Method method, URI uri,
                                  String requestContent, StatusCode statusCode, String responseContent) {
        recordPair(who, serviceName, callerName, dependencyCategory, method, uri, null, requestContent,
            statusCode, responseContent, RequestResponseMetaType.DEFAULT);
    }
}
