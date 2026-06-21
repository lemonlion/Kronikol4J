package io.kronikol.core.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Recovers the source text of a call site by reading the {@code .java} file at the caller's line —
 * the Java analog of .NET's "read source from PDB sequence points" (plan §3.9, Tier 2). Since Java
 * has no {@code CallerArgumentExpression}, this is how assertion tracking can capture a readable
 * expression automatically (no explicit description, no AST plugin), for the wrapper form.
 *
 * <p>Best-effort: requires the source file to be present relative to the working directory and the
 * class to be compiled with line numbers (the default). Returns {@code null} when unavailable.
 */
public final class SourceExpression {

    /** Source roots searched, relative to the working directory. */
    private static final List<String> SOURCE_ROOTS =
        List.of("src/test/java", "src/main/java", "src/test/groovy", "src/main/groovy");

    private SourceExpression() {
    }

    /**
     * The source line of the first stack frame whose class is not in {@code apiClassNames}
     * (i.e. the user's call site), trimmed; or {@code null} if it cannot be read.
     */
    public static String forCallerOutside(Set<String> apiClassNames) {
        return StackWalker.getInstance().walk(frames -> frames
            .filter(f -> !apiClassNames.contains(f.getClassName()))
            .findFirst()
            .map(f -> readLine(f.getClassName(), f.getFileName(), f.getLineNumber()))
            .orElse(null));
    }

    /**
     * The source line of the first stack frame whose class name does not start with any of
     * {@code excludedPrefixes} — for the assertion agent, which runs inside AssertJ and must skip
     * the AssertJ + agent frames to find the user's test line.
     */
    public static String forCallerOutsidePackages(Set<String> excludedPrefixes) {
        return StackWalker.getInstance().walk(frames -> frames
            .filter(f -> excludedPrefixes.stream().noneMatch(p -> f.getClassName().startsWith(p)))
            .findFirst()
            .map(f -> readLine(f.getClassName(), f.getFileName(), f.getLineNumber()))
            .orElse(null));
    }

    /** Reads the given 1-based line from the source file for {@code className}, or {@code null}. */
    public static String readLine(String className, String fileName, int lineNumber) {
        if (fileName == null || lineNumber < 1) {
            return null;
        }
        String topLevel = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
        String packagePath = topLevel.replace('.', '/');
        int lastSlash = packagePath.lastIndexOf('/');
        String dir = lastSlash >= 0 ? packagePath.substring(0, lastSlash) : "";

        for (String root : SOURCE_ROOTS) {
            Path file = Path.of(root, dir, fileName);
            if (Files.isReadable(file)) {
                try {
                    List<String> lines = Files.readAllLines(file);
                    if (lineNumber <= lines.size()) {
                        return lines.get(lineNumber - 1).strip();
                    }
                } catch (IOException ignored) {
                    // fall through to the next root
                }
            }
        }
        return null;
    }

    /**
     * Extracts the lambda body from a wrapper call line, e.g.
     * {@code Track.that(() -> assertThat(x).isEqualTo(1));} → {@code assertThat(x).isEqualTo(1)}.
     * Falls back to the whole line.
     */
    public static String extractLambdaBody(String sourceLine) {
        if (sourceLine == null) {
            return null;
        }
        int arrow = sourceLine.indexOf("->");
        String body = arrow >= 0 ? sourceLine.substring(arrow + 2).strip() : sourceLine;
        // strip a trailing "));" / ");" / "}" that closes the wrapper call
        while (body.endsWith(";") || body.endsWith(")") || body.endsWith("}")) {
            // keep balanced parentheses of the expression itself: only trim a trailing ')' that
            // closes the wrapper, detected by more ')' than '(' .
            if (body.endsWith(";")) {
                body = body.substring(0, body.length() - 1).strip();
            } else if (body.endsWith("}")) {
                body = body.substring(0, body.length() - 1).strip();
            } else { // ')'
                long open = body.chars().filter(c -> c == '(').count();
                long close = body.chars().filter(c -> c == ')').count();
                if (close > open) {
                    body = body.substring(0, body.length() - 1).strip();
                } else {
                    break;
                }
            }
        }
        return body;
    }
}
