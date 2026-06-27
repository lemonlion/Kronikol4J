package io.kronikol.report.step;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.tracking.Track;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.core.tracking.TrackingDiagramOverride;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.InlineParameterValue;
import io.kronikol.report.model.ScenarioStep;
import io.kronikol.report.model.StepParameter;
import io.kronikol.report.model.VerificationStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Collects BDD-style steps (Given/When/Then) per test, keyed by test id. Java port of the .NET
 * {@code StepCollector}: nested sub-steps, keyword sequencing (Given/And/And, ButWhen→But), parameter
 * capture, assertion sub-steps, file attachments, top-level step-delimiter notes and the
 * When/Then→Action phase transition. Thread-safe.
 *
 * <p>Lives in {@code kronikol4j-report} because it produces the report's {@link ScenarioStep} model; it
 * resolves identity, emits delimiter notes and flips the phase through the core seams ({@link
 * TestIdentityScope}/{@link Track#testIdResolver()}, {@link TrackingDiagramOverride}, {@link
 * TestPhaseContext}). The build-time step weaver (which injects {@link #startStep}/{@link #completeStep}
 * calls from the {@code @GivenStep}/… annotations) is the Tier-5 build-tooling follow-up; consumers can also
 * call these methods directly.
 */
public final class StepCollector {

    private static final Map<String, TestStepState> STATES = new ConcurrentHashMap<>();
    private static volatile StepTrackingOptions options = StepTrackingOptions.defaults();

    private StepCollector() {
    }

    /** The current step-tracking options. */
    public static StepTrackingOptions options() {
        return options;
    }

    /** Sets the step-tracking options. */
    public static void options(StepTrackingOptions value) {
        options = value == null ? StepTrackingOptions.defaults() : value;
    }

    /** Starts a step, resolving the test id from the ambient context. */
    public static void startStep(String keyword, String text, String[] paramNames, Object[] paramValues) {
        startStep(resolveTestId(), keyword, text, paramNames, paramValues);
    }

    /** Starts a step for {@code testId}; if a step is already active, this becomes a sub-step of it. */
    public static void startStep(String testId, String keyword, String text, String[] paramNames,
                                 Object[] paramValues) {
        if (testId == null) {
            return;
        }
        TestStepState state = STATES.computeIfAbsent(testId, k -> new TestStepState());
        CollectedStep step = new CollectedStep();
        step.text = text;
        step.startNanos = System.nanoTime();
        step.parameters = buildParameters(paramNames, paramValues);

        boolean isTopLevel;
        synchronized (state) {
            step.effectiveKeyword = resolveKeyword(state, keyword);
            isTopLevel = state.stepStack.isEmpty();
            state.stepStack.push(step);
        }

        if (isTopLevel && options.showStepDelimiters()) {
            String label = options.prependKeyword() && step.effectiveKeyword != null
                ? step.effectiveKeyword + " " + text
                : text;
            TrackingDiagramOverride.insertPlantUml(testId,
                "hnote across <<stepDelimiter>> #black:<color:white>" + label);
        }

        if (options.whenTriggersAction() && keyword != null) {
            switch (keyword) {
                case "Given", "But" -> TestPhaseContext.set(TestPhase.SETUP);
                case "When", "Then", "ButWhen" -> TestPhaseContext.set(TestPhase.ACTION);
                default -> { }
            }
        }
    }

    /** Completes the active step, resolving the test id from the ambient context. */
    public static void completeStep(boolean passed, String errorMessage) {
        completeStep(resolveTestId(), passed, errorMessage);
    }

    /** Completes the active step for {@code testId} (recorded as a sub-step of its parent, if any). */
    public static void completeStep(String testId, boolean passed, String errorMessage) {
        if (testId == null) {
            return;
        }
        TestStepState state = STATES.get(testId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.stepStack.isEmpty()) {
                return;
            }
            CollectedStep completed = state.stepStack.pop();
            completed.endNanos = System.nanoTime();
            completed.passed = passed;
            completed.errorMessage = errorMessage;
            record(state, completed);
        }
    }

    /** Bypasses the active step (its body did not run) for {@code testId}, recording it as Bypassed. */
    public static void bypassStep(String reason) {
        bypassStep(resolveTestId(), reason);
    }

    /** Bypasses the active step for {@code testId}. */
    public static void bypassStep(String testId, String reason) {
        if (testId == null) {
            return;
        }
        TestStepState state = STATES.get(testId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.stepStack.isEmpty()) {
                return;
            }
            CollectedStep bypassed = state.stepStack.pop();
            bypassed.endNanos = System.nanoTime();
            bypassed.bypassed = true;
            bypassed.bypassReason = reason;
            record(state, bypassed);
        }
    }

    /** Adds an assertion as a sub-step of the active step (when enabled). */
    public static void addAssertionSubStep(String testId, String expression, boolean passed) {
        if (testId == null || !options.includeTrackedAssertionsInStepList()) {
            return;
        }
        TestStepState state = STATES.get(testId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.stepStack.isEmpty()) {
                return;
            }
            CollectedStep sub = new CollectedStep();
            sub.text = expression;
            sub.startNanos = System.nanoTime();
            sub.endNanos = sub.startNanos;
            sub.passed = passed;
            state.stepStack.peek().subSteps.add(sub);
        }
    }

    /** Adds a file attachment to the active step, or to the scenario when no step is active. */
    public static void addAttachment(String testId, String filePath, String name) {
        if (testId == null) {
            return;
        }
        String resolvedName = name != null ? name : extractFileName(filePath);
        FileAttachment attachment = new FileAttachment(resolvedName, filePath);
        TestStepState state = STATES.computeIfAbsent(testId, k -> new TestStepState());
        synchronized (state) {
            if (!state.stepStack.isEmpty()) {
                state.stepStack.peek().attachments.add(attachment);
            } else {
                state.scenarioAttachments.add(attachment);
            }
        }
    }

    /** Scenario-level attachments (added when no step was active), or empty. */
    public static List<FileAttachment> getScenarioAttachments(String testId) {
        if (testId == null) {
            return List.of();
        }
        TestStepState state = STATES.get(testId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return List.copyOf(state.scenarioAttachments);
        }
    }

    /** Whether a step is currently in progress for {@code testId}. */
    public static boolean hasActiveStep(String testId) {
        if (testId == null) {
            return false;
        }
        TestStepState state = STATES.get(testId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return !state.stepStack.isEmpty();
        }
    }

    /** All completed top-level steps for {@code testId} as {@link ScenarioStep}s, or empty. */
    public static List<ScenarioStep> getSteps(String testId) {
        if (testId == null) {
            return List.of();
        }
        TestStepState state = STATES.get(testId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            List<ScenarioStep> out = new ArrayList<>(state.completedSteps.size());
            for (CollectedStep s : state.completedSteps) {
                out.add(toScenarioStep(s));
            }
            return out;
        }
    }

    /** Clears all collected steps + attachments for {@code testId}. */
    public static void clearSteps(String testId) {
        if (testId != null) {
            STATES.remove(testId);
        }
    }

    private static void record(TestStepState state, CollectedStep step) {
        if (!state.stepStack.isEmpty()) {
            state.stepStack.peek().subSteps.add(step); // nested → sub-step of parent
        } else {
            state.completedSteps.add(step); // top-level
        }
    }

    private static String resolveTestId() {
        Supplier<String> hook = Track.testIdResolver();
        if (hook != null) {
            try {
                String resolved = hook.get();
                if (resolved != null) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
                // resolver threw — fall through to the scope
            }
        }
        TestInfo current = TestIdentityScope.current();
        return current == null ? null : current.id();
    }

    /** Given/And sequencing at the top level; ButWhen→But; sub-steps keep their keyword. */
    private static String resolveKeyword(TestStepState state, String keyword) {
        if (keyword == null) {
            return null;
        }
        String displayKeyword = keyword;
        String categoryKeyword = keyword;
        if (keyword.equals("ButWhen")) {
            displayKeyword = "But";
            categoryKeyword = "But";
        }
        if (!state.stepStack.isEmpty()) {
            return displayKeyword; // sub-steps keep their original keyword
        }
        if (categoryKeyword.equals(state.lastKeywordCategory)) {
            return "And";
        }
        state.lastKeywordCategory = categoryKeyword;
        return displayKeyword;
    }

    private static List<StepParameter> buildParameters(String[] paramNames, Object[] paramValues) {
        if (paramNames == null || paramValues == null || paramNames.length == 0) {
            return List.of();
        }
        int count = Math.min(paramNames.length, paramValues.length);
        List<StepParameter> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Inline-only for now; ITabularParameterData handling lands with that Tier-4 item.
            String value = paramValues[i] == null ? "null" : String.valueOf(paramValues[i]);
            result.add(StepParameter.inline(paramNames[i],
                new InlineParameterValue(value, null, VerificationStatus.NOT_APPLICABLE)));
        }
        return result;
    }

    private static ScenarioStep toScenarioStep(CollectedStep step) {
        Long durationMs = step.endNanos > 0 ? (step.endNanos - step.startNanos) / 1_000_000L : null;
        ExecutionStatus status = step.bypassed ? ExecutionStatus.INCONCLUSIVE
            : step.passed ? ExecutionStatus.PASSED : ExecutionStatus.FAILED;

        List<ScenarioStep> subSteps = new ArrayList<>(step.subSteps.size());
        for (CollectedStep sub : step.subSteps) {
            subSteps.add(toScenarioStep(sub));
        }
        // The Java ScenarioStep model has no dedicated bypass-reason / error-message field; surface them as
        // comments.
        List<String> comments = new ArrayList<>();
        if (step.bypassReason != null) {
            comments.add("Bypassed: " + step.bypassReason);
        }
        if (step.errorMessage != null) {
            comments.add(step.errorMessage);
        }

        return ScenarioStep.builder(step.effectiveKeyword, step.text, status)
            .durationMs(durationMs)
            .subSteps(subSteps)
            .attachments(step.attachments)
            .parameters(step.parameters)
            .comments(comments)
            .build();
    }

    private static String extractFileName(String filePath) {
        int sep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return sep >= 0 ? filePath.substring(sep + 1) : filePath;
    }

    private static final class TestStepState {
        final Deque<CollectedStep> stepStack = new ArrayDeque<>();
        final List<CollectedStep> completedSteps = new ArrayList<>();
        final List<FileAttachment> scenarioAttachments = new ArrayList<>();
        String lastKeywordCategory;
    }

    private static final class CollectedStep {
        String effectiveKeyword;
        String text = "";
        long startNanos;
        long endNanos;
        boolean passed;
        boolean bypassed;
        String bypassReason;
        String errorMessage;
        List<StepParameter> parameters = List.of();
        final List<CollectedStep> subSteps = new ArrayList<>();
        final List<FileAttachment> attachments = new ArrayList<>();
    }
}
