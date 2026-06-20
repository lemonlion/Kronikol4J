package io.kronikol.junit5;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import io.kronikol.runtime.RunResults;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * JUnit 5 extension that scopes test identity for the duration of each test and records its outcome.
 * Apply with {@code @ExtendWith(KronikolExtension.class)} (or register globally via
 * {@code junit.jupiter.extensions.autodetection.enabled}).
 *
 * <p>The identity scope (display name + unique id) lets HTTP/JDBC/proxy trackers attribute the
 * interactions they observe on the test thread to this test (Layer 3, plan §3.2). Per §3.2 the scope
 * is <strong>always cleared</strong> in {@code afterEach} via the {@link AutoCloseable} handle — no
 * {@link ThreadLocal} leakage onto the next test on a reused thread.
 */
public final class KronikolExtension implements BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final ExtensionContext.Namespace NS =
        ExtensionContext.Namespace.create("io.kronikol.junit5");
    private static final String SCOPE_KEY = "identity-scope";

    @Override
    public void beforeEach(ExtensionContext context) {
        TestIdentityScope.Scope scope =
            TestIdentityScope.begin(context.getDisplayName(), context.getUniqueId());
        context.getStore(NS).put(SCOPE_KEY, scope);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestIdentityScope.Scope scope =
            context.getStore(NS).remove(SCOPE_KEY, TestIdentityScope.Scope.class);
        if (scope != null) {
            scope.close(); // mandatory clearing (§3.2)
        }
        TestPhaseContext.reset();
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        record(context, ExecutionStatus.PASSED, null);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        record(context, ExecutionStatus.FAILED, String.valueOf(cause));
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        record(context, ExecutionStatus.SKIPPED, String.valueOf(cause));
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        record(context, ExecutionStatus.SKIPPED, reason.orElse(null));
    }

    private static void record(ExtensionContext context, ExecutionStatus status, String error) {
        String feature = context.getTestClass().map(Class::getSimpleName).orElse("Tests");
        RunResults.record(feature,
            new Scenario(context.getDisplayName(), context.getUniqueId(), status, 0, error));
    }
}
