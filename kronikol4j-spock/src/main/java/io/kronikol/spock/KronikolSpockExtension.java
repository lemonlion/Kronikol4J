package io.kronikol.spock;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import io.kronikol.runtime.RunResults;
import org.spockframework.runtime.extension.IGlobalExtension;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

/**
 * Global Spock extension that scopes feature-iteration identity and tracks phases (plan §3/§7):
 * each iteration opens a {@link TestIdentityScope} and records a {@link Scenario}; setup fixtures run
 * in the {@link TestPhase#SETUP} phase and the feature body ({@code when}/{@code then}/{@code expect})
 * in {@link TestPhase#ACTION} (see {@link SpockBlock}). Registered globally via ServiceLoader
 * ({@code META-INF/services/org.spockframework.runtime.extension.IGlobalExtension}); the user brings
 * Spock. Report finalization is shared with the JUnit Platform listener.
 *
 * <p>Identity and phase live in {@link ThreadLocal}s and are always cleared when the iteration
 * finishes, so they stay correct under Spock's parallel execution (§3.2).
 */
public final class KronikolSpockExtension implements IGlobalExtension {

    @Override
    public void visitSpec(SpecInfo spec) {
        // setup()/given fixtures are the SETUP phase.
        spec.addSetupInterceptor(phase(TestPhase.SETUP));
        for (FeatureInfo feature : spec.getAllFeatures()) {
            feature.addIterationInterceptor(this::interceptIteration);
            // The feature method body (when/then/expect) is the ACTION phase.
            feature.getFeatureMethod().addInterceptor(phase(TestPhase.ACTION));
        }
    }

    private static IMethodInterceptor phase(TestPhase phase) {
        return invocation -> {
            TestPhaseContext.set(phase);
            invocation.proceed();
        };
    }

    private void interceptIteration(IMethodInvocation invocation) throws Throwable {
        IterationInfo iteration = invocation.getIteration();
        String specName = invocation.getSpec().getName();
        String testName = iteration.getDisplayName();
        String testId = specName + "/" + testName;

        TestIdentityScope.Scope scope = TestIdentityScope.begin(testName, testId);
        TestPhaseContext.reset();
        ExecutionStatus status = ExecutionStatus.PASSED;
        String error = null;
        try {
            invocation.proceed();
        } catch (Throwable failure) {
            status = ExecutionStatus.FAILED;
            error = String.valueOf(failure);
            throw failure;
        } finally {
            RunResults.record(specName, new Scenario(testName, testId, status, 0, error));
            scope.close();          // mandatory clearing (§3.2)
            TestPhaseContext.reset();
        }
    }
}
