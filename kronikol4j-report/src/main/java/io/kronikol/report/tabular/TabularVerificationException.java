package io.kronikol.report.tabular;

/**
 * Thrown by {@link TabularOutputs#verify()} when actual output rows do not match the expected rows declared
 * via {@code @Outputs}. Java port of the .NET {@code TabularVerificationException}.
 */
public class TabularVerificationException extends RuntimeException {
    public TabularVerificationException(String message) {
        super(message);
    }
}
