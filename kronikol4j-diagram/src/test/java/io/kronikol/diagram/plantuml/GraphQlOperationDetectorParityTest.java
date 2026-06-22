package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Cross-runtime parity for {@link GraphQlOperationDetector#tryExtractLabel(String)}: the
 * {@code graphql-labels.txt} fixture pairs each request body with the operation label the <em>real</em>
 * .NET {@code GraphQlOperationDetector} produced (captured by {@code parity-harness/dotnet-capture}). This
 * covers the detector branches the rendered {@code .puml} goldens don't reach on their own — named
 * query/mutation/subscription, the anonymous {@code { … }} shorthand, the {@code operationName} override,
 * leading literal and JSON-escaped whitespace, and the non-GraphQL / nested-key rejections.
 */
class GraphQlOperationDetectorParityTest {

    @Test
    void labelsMatchDotNet() throws IOException {
        String fixture = readFixture("parity/graphql-labels.txt").replace("\r\n", "\n").replace("\r", "\n");
        int asserted = 0;
        for (String line : fixture.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int tab = line.indexOf('\t');
            String input = line.substring(0, tab);
            String expected = line.substring(tab + 1);
            String actual = GraphQlOperationDetector.tryExtractLabel(input);
            assertThat(actual == null ? "null" : actual)
                .as("tryExtractLabel(%s)", input)
                .isEqualTo(expected);
            asserted++;
        }
        assertThat(asserted).isEqualTo(12); // every captured case ran (guards against an empty fixture)
    }

    private static String readFixture(String resource) throws IOException {
        try (InputStream in = GraphQlOperationDetectorParityTest.class.getClassLoader()
            .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
