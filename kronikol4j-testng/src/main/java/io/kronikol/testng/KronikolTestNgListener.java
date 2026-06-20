package io.kronikol.testng;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.report.HtmlReportGenerator.GeneratedReport;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import io.kronikol.runtime.ReportFinalizer;
import io.kronikol.runtime.RunResults;
import org.testng.IExecutionListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * TestNG counterpart of the JUnit 5 adapter: scopes each test's identity for the duration of the
 * method (so trackers attribute to it) and records its outcome; finalizes the report once per JVM
 * at {@link #onExecutionFinish()} (plan §5.4). Register via {@code @Listeners}, {@code testng.xml},
 * or ServiceLoader ({@code META-INF/services/org.testng.ITestNGListener}).
 */
public final class KronikolTestNgListener implements ITestListener, IExecutionListener {

    // onTestStart and the outcome callback run on the same thread, so a ThreadLocal handle is safe.
    private static final ThreadLocal<TestIdentityScope.Scope> SCOPE = new ThreadLocal<>();

    @Override
    public void onTestStart(ITestResult result) {
        SCOPE.set(TestIdentityScope.begin(testName(result), testId(result)));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        finish(result, ExecutionStatus.PASSED, null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        finish(result, ExecutionStatus.FAILED, String.valueOf(result.getThrowable()));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        finish(result, ExecutionStatus.SKIPPED, null);
    }

    @Override
    public void onExecutionFinish() {
        try {
            GeneratedReport report = ReportFinalizer.finalizeRunToDefault("Kronikol4J Test Run");
            if (report != null) {
                System.out.println("[Kronikol4J] Report written: " + report.htmlFile().toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("[Kronikol4J] report generation failed: " + e);
        }
    }

    private void finish(ITestResult result, ExecutionStatus status, String error) {
        TestIdentityScope.Scope scope = SCOPE.get();
        if (scope != null) {
            scope.close(); // mandatory clearing (§3.2)
            SCOPE.remove();
        }
        TestPhaseContext.reset();
        RunResults.record(feature(result),
            new Scenario(testName(result), testId(result), status, durationMs(result), error));
    }

    private static String testName(ITestResult result) {
        return result.getMethod().getMethodName();
    }

    private static String testId(ITestResult result) {
        return result.getMethod().getQualifiedName() + "#" + System.identityHashCode(result);
    }

    private static String feature(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName();
    }

    private static long durationMs(ITestResult result) {
        return Math.max(0, result.getEndMillis() - result.getStartMillis());
    }
}
