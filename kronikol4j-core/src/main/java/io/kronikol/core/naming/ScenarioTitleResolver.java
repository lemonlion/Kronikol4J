package io.kronikol.core.naming;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Resolves human-readable scenario and feature titles from test method names and display names.
 * Java port of the .NET {@code ScenarioTitleResolver}. Lives in {@code kronikol4j-core} (the zero-dependency
 * home matching .NET's core {@code Kronikol} namespace) so every test-framework adapter
 * (JUnit/Cucumber/etc.) can call it. Pairs with {@link StringCasing}.
 */
public final class ScenarioTitleResolver {

    private static final int MAX_PARAMETER_LENGTH = 200;

    private static final Pattern LOWER_TO_UPPER = Pattern.compile("(\\p{Ll})(\\p{Lu})");
    private static final Pattern UPPER_SEQUENCE = Pattern.compile("(\\p{Lu}+)(\\p{Lu}\\p{Ll})");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private ScenarioTitleResolver() {
    }

    /**
     * Detects when a BDDfy scenario title has been set to the class name (e.g. via
     * {@code .BDDfy(nameof(ClassName))}) and replaces it with a humanized version of the test method name.
     */
    public static String resolveScenarioTitle(String scenarioTitle, String testClassSimpleName,
            String testMethodName) {
        if (testClassSimpleName == null || testMethodName == null) {
            return scenarioTitle;
        }
        if (!scenarioTitle.equals(testClassSimpleName)) {
            return scenarioTitle;
        }
        return humanizeMethodName(testMethodName);
    }

    /**
     * Extracts the parameter portion from a test display name (e.g. xUnit Theory's
     * {@code Ns.Class.Method(param1: "v1", param2: "v2")}) and appends it as
     * {@code [param1: "v1", param2: "v2"]}. Returns the original title unchanged when there are no parameters.
     */
    public static String appendTestParameters(String resolvedTitle, String testDisplayName) {
        if (testDisplayName == null) {
            return resolvedTitle;
        }
        int parenIndex = testDisplayName.indexOf('(');
        if (parenIndex < 0) {
            return resolvedTitle;
        }
        String paramContent = trimEnd(testDisplayName.substring(parenIndex + 1), ')');
        if (paramContent.isEmpty()) {
            return resolvedTitle;
        }
        if (paramContent.length() > MAX_PARAMETER_LENGTH) {
            paramContent = paramContent.substring(0, MAX_PARAMETER_LENGTH) + "…";
        }
        return resolvedTitle + " [" + paramContent + "]";
    }

    /** Humanizes a test class simple name (e.g. PascalCase) into a Title Case feature name. */
    public static String formatFeatureName(String testClassSimpleName) {
        return StringCasing.titleize(testClassSimpleName);
    }

    /**
     * Parses a test display name (optionally fully-qualified), humanizes the method name, and appends any
     * parameter values in brackets. {@code Ns.Class.MyTestMethod(p: "v")} → {@code My test method [p: "v"]}.
     */
    public static String formatScenarioDisplayName(String testDisplayName) {
        String methodPath;
        String parameters = null;

        int parenIndex = testDisplayName.indexOf('(');
        if (parenIndex >= 0) {
            methodPath = testDisplayName.substring(0, parenIndex);
            String paramContent = trimEnd(testDisplayName.substring(parenIndex + 1), ')');
            if (!paramContent.isEmpty()) {
                parameters = paramContent.length() > MAX_PARAMETER_LENGTH
                    ? paramContent.substring(0, MAX_PARAMETER_LENGTH) + "…"
                    : paramContent;
            }
        } else {
            methodPath = testDisplayName;
        }

        int lastDot = methodPath.lastIndexOf('.');
        String methodName = lastDot >= 0 ? methodPath.substring(lastDot + 1) : methodPath;

        String humanized = humanizeMethodName(methodName);
        return parameters != null ? humanized + " [" + parameters + "]" : humanized;
    }

    /** Split PascalCase, replace underscores, collapse whitespace, sentence-case the result. */
    private static String humanizeMethodName(String methodName) {
        String humanized = splitPascalCase(methodName).replace("_", " ");
        humanized = MULTIPLE_SPACES.matcher(humanized).replaceAll(" ").strip();
        return Character.toUpperCase(humanized.charAt(0)) + humanized.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String splitPascalCase(String input) {
        String result = LOWER_TO_UPPER.matcher(input).replaceAll("$1 $2");
        result = UPPER_SEQUENCE.matcher(result).replaceAll("$1 $2");
        return result;
    }

    private static String trimEnd(String s, char ch) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ch) {
            end--;
        }
        return s.substring(0, end);
    }
}
