package io.kronikol.report.ci;

import java.util.function.Function;

/**
 * The CI/CD platform where the test run is executing. Java port of the .NET {@code CiEnvironment} +
 * {@code CiEnvironmentDetector} (folded together here — Java has no static class with a separate enum file).
 */
public enum CiEnvironment {

    /** Not running in a known CI environment. */
    NONE,

    /** GitHub Actions. */
    GITHUB_ACTIONS,

    /** Azure DevOps Pipelines. */
    AZURE_DEV_OPS;

    /** Detects the CI environment from the process environment variables. */
    public static CiEnvironment detect() {
        return detect(System::getenv);
    }

    /** As {@link #detect()}, with an injectable environment-variable lookup (for tests). */
    public static CiEnvironment detect(Function<String, String> getEnvVar) {
        if (notBlank(getEnvVar.apply("GITHUB_ACTIONS"))) {
            return GITHUB_ACTIONS;
        }
        if (notBlank(getEnvVar.apply("TF_BUILD"))) {
            return AZURE_DEV_OPS;
        }
        return NONE;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
    }
}
