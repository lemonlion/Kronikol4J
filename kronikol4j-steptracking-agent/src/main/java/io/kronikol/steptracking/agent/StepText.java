package io.kronikol.steptracking.agent;

import io.kronikol.report.step.ButStep;
import io.kronikol.report.step.GivenStep;
import io.kronikol.report.step.Step;
import io.kronikol.report.step.ThenStep;
import io.kronikol.report.step.WhenStep;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Resolves the BDD {@code (keyword, text)} for a step-annotated method — the runtime equivalent of the .NET
 * {@code StepWeaver}'s {@code GetKeyword}/{@code GetStepText}/{@code HumanizeMethodName}. Keyword from the
 * annotation type ({@code @GivenStep}→{@code Given}, …, {@code @Step}→none); text from the annotation
 * {@code value()} when set, else the humanised method name with a leading duplicate keyword stripped.
 */
final class StepText {

    private StepText() {
    }

    /** A resolved step descriptor; {@code keyword} is {@code null} for {@code @Step}. */
    record StepInfo(String keyword, String text) {
    }

    /** The step descriptor for {@code method}, or {@code null} when it carries no step annotation. */
    static StepInfo resolve(Method method) {
        GivenStep given = method.getAnnotation(GivenStep.class);
        if (given != null) {
            return build("Given", given.value(), method);
        }
        WhenStep when = method.getAnnotation(WhenStep.class);
        if (when != null) {
            return build("When", when.value(), method);
        }
        ThenStep then = method.getAnnotation(ThenStep.class);
        if (then != null) {
            return build("Then", then.value(), method);
        }
        ButStep but = method.getAnnotation(ButStep.class);
        if (but != null) {
            return build("But", but.value(), method);
        }
        Step step = method.getAnnotation(Step.class);
        if (step != null) {
            return build(null, step.value(), method);
        }
        return null;
    }

    private static StepInfo build(String keyword, String value, Method method) {
        String text = value != null && !value.isEmpty()
            ? value
            : stripLeadingKeyword(humanize(method.getName()), keyword);
        return new StepInfo(keyword, text);
    }

    /** PascalCase/underscore method name → sentence-cased phrase (the .NET {@code HumanizeMethodName}). */
    static String humanize(String methodName) {
        String text = methodName.replace("_", " ");
        text = text.replaceAll("(\\p{Ll})(\\p{Lu})", "$1 $2");          // camelCase → camel Case
        text = text.replaceAll("(\\p{Lu}+)(\\p{Lu}\\p{Ll})", "$1 $2");  // HTTPClient → HTTP Client
        text = text.replaceAll("\\s+", " ").trim();
        if (!text.isEmpty()) {
            text = Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
        }
        return text;
    }

    /** Drops a leading {@code "<keyword> "} (case-insensitive) so {@code WhenTheyGo} → {@code "They go"}. */
    private static String stripLeadingKeyword(String text, String keyword) {
        if (keyword == null) {
            return text;
        }
        String prefix = keyword + " ";
        if (text.regionMatches(true, 0, prefix, 0, prefix.length())) {
            String rest = text.substring(prefix.length());
            return rest.isEmpty() ? rest : Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
        }
        return text;
    }
}
