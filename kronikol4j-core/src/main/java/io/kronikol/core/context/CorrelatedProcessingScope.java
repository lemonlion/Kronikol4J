package io.kronikol.core.context;

/**
 * Looks up a correlation key and, if found, opens a {@link TestIdentityScope} for the owning test.
 * Returns {@code null} when the key is unknown (non-test data) — processing then continues
 * unattributed, which is correct. Mirrors the .NET {@code CorrelatedProcessingScope}.
 */
public final class CorrelatedProcessingScope {

    private CorrelatedProcessingScope() {
    }

    /**
     * @return an open scope (close with try-with-resources), or {@code null} if the key is unknown.
     *         Note: try-with-resources tolerates a {@code null} resource.
     */
    public static TestIdentityScope.Scope begin(String correlationKey) {
        TestInfo info = TestCorrelationStore.resolve(correlationKey);
        if (info == null) {
            return null;
        }
        return TestIdentityScope.begin(info.name(), info.id());
    }
}
