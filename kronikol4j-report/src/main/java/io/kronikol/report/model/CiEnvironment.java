package io.kronikol.report.model;

/**
 * The CI environment a test run executed in (.NET {@code CiEnvironment}). Its {@link #toString()}
 * matches the .NET enum name exactly, since the report renders {@code CI ({provider})} from it.
 */
public enum CiEnvironment {
    NONE("None"),
    GITHUB_ACTIONS("GitHubActions"),
    AZURE_DEVOPS("AzureDevOps");

    private final String displayName;

    CiEnvironment(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
