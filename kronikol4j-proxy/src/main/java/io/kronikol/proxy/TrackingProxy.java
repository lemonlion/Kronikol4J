package io.kronikol.proxy;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.registry.TrackingComponent;
import io.kronikol.core.registry.TrackingComponentRegistry;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps any interface so each method call is recorded as a tracked request/response interaction —
 * the idiomatic Java equivalent of the .NET {@code DispatchProxy} pattern (plan §3.4). When no test
 * identity can be resolved (outside a test), calls pass through untracked.
 */
public final class TrackingProxy {

    private TrackingProxy() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> iface, T target, ProxyOptions options) {
        Handler handler = new Handler(iface, target, options);
        TrackingComponentRegistry.register(handler);
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, handler);
    }

    static final class Handler implements InvocationHandler, TrackingComponent {

        private final Class<?> iface;
        private final Object target;
        private final ProxyOptions options;
        private final AtomicInteger invocations = new AtomicInteger();

        Handler(Class<?> iface, Object target, ProxyOptions options) {
            this.iface = iface;
            this.target = target;
            this.options = options;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeTarget(method, args);
            }

            TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
            if (who == null) {
                return invokeTarget(method, args); // not in a test -> no tracking
            }

            invocations.incrementAndGet();
            UUID trace = UUID.randomUUID();
            UUID rr = UUID.randomUUID();
            String methodName = method.getName();
            URI uri = URI.create("proxy://local/" + iface.getSimpleName() + "/" + methodName);

            log(who, methodName, uri, RequestResponseType.REQUEST, serializeArgs(args), null, trace, rr);
            try {
                Object result = invokeTarget(method, args);
                log(who, methodName, uri, RequestResponseType.RESPONSE,
                    serialize(result), StatusCode.of(200), trace, rr);
                return result;
            } catch (Throwable t) {
                log(who, methodName, uri, RequestResponseType.RESPONSE,
                    String.valueOf(t), StatusCode.of("Error"), trace, rr);
                throw t;
            }
        }

        private void log(TestInfo who, String methodName, URI uri, RequestResponseType type,
                         String content, StatusCode status, UUID trace, UUID rr) {
            RequestResponseLogger.log(RequestResponseLog.builder()
                .testInfo(who)
                .method(Method.of(methodName))
                .uri(uri)
                .serviceName(options.serviceName())
                .callerName(options.callerName())
                .type(type)
                .traceId(trace)
                .requestResponseId(rr)
                .statusCode(status)
                .dependencyCategory(options.dependencyCategory())
                .content(content)
                .phase(TestPhaseContext.current())
                .build());
        }

        private Object invokeTarget(java.lang.reflect.Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private static String serializeArgs(Object[] args) {
            if (args == null || args.length == 0) {
                return null;
            }
            if (args.length == 1) {
                return String.valueOf(args[0]);
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(String.valueOf(args[i]));
            }
            return sb.append(']').toString();
        }

        private static String serialize(Object result) {
            return result == null ? null : String.valueOf(result);
        }

        @Override
        public String componentName() {
            return "TrackingProxy(" + options.serviceName() + ")";
        }

        @Override
        public boolean wasInvoked() {
            return invocations.get() > 0;
        }

        @Override
        public int invocationCount() {
            return invocations.get();
        }
    }
}
