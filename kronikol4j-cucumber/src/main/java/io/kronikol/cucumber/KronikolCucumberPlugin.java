package io.kronikol.cucumber;

import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepStarted;
import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import io.kronikol.runtime.RunResults;
import java.net.URI;

/**
 * Cucumber {@link EventListener} that scopes scenario identity and tracks BDD phases (plan §3/§7):
 * each scenario opens a {@link TestIdentityScope}; each step sets the {@link TestPhaseContext} from
 * its Gherkin keyword ({@link GherkinPhase}). Register via {@code @CucumberOptions(plugin = ...)},
 * {@code cucumber.plugin} property, or ServiceLoader. Report finalization is shared with the JUnit
 * Platform listener.
 *
 * <p>Identity is held in a {@link ThreadLocal} so it is correct under parallel scenario execution,
 * and is always cleared when the scenario finishes (§3.2).
 */
public final class KronikolCucumberPlugin implements EventListener {

    private static final ThreadLocal<TestIdentityScope.Scope> SCOPE = new ThreadLocal<>();

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestStepStarted.class, this::onTestStepStarted);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        TestCase testCase = event.getTestCase();
        SCOPE.set(TestIdentityScope.begin(testCase.getName(), testCase.getId().toString()));
        TestPhaseContext.reset();
    }

    private void onTestStepStarted(TestStepStarted event) {
        if (event.getTestStep() instanceof PickleStepTestStep step) {
            String keyword = step.getStep().getKeyword();
            TestPhaseContext.set(GherkinPhase.forKeyword(keyword, TestPhaseContext.current()));
        }
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        TestCase testCase = event.getTestCase();
        Result result = event.getResult();
        RunResults.record(featureName(testCase.getUri()),
            new Scenario(testCase.getName(), testCase.getId().toString(),
                mapStatus(result.getStatus()), 0,
                result.getError() == null ? null : String.valueOf(result.getError())));

        TestIdentityScope.Scope scope = SCOPE.get();
        if (scope != null) {
            scope.close(); // mandatory clearing (§3.2)
            SCOPE.remove();
        }
        TestPhaseContext.reset();
    }

    private static ExecutionStatus mapStatus(Status status) {
        return switch (status) {
            case PASSED -> ExecutionStatus.PASSED;
            case FAILED, AMBIGUOUS -> ExecutionStatus.FAILED;
            default -> ExecutionStatus.SKIPPED; // SKIPPED, PENDING, UNDEFINED, UNUSED
        };
    }

    static String featureName(URI uri) {
        if (uri == null) {
            return "Cucumber";
        }
        String path = uri.getSchemeSpecificPart();
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }
}
