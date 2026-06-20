package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.support.SourceExpression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrackSourceTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
    }

    @Test
    void autoCapturesTheAssertionExpressionFromSource() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            Track.that(() -> assertThat(1).isEqualTo(1)); // expression captured from THIS line
        }
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).plantUml())
            .contains("✓")
            .contains("assertThat(1).isEqualTo(1)"); // auto-captured, no description given
    }

    @Test
    void autoCapturedFailureKeepsExpressionAndMessage() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            assertThatThrownBy(() -> Track.that(() -> assertThat(1).isEqualTo(2)))
                .isInstanceOf(AssertionError.class);
        }
        assertThat(RequestResponseLogger.getAllLogs().get(0).plantUml())
            .contains("✗")
            .contains("assertThat(1).isEqualTo(2)");
    }

    @Test
    void extractLambdaBodyStripsTheWrapper() {
        assertThat(SourceExpression.extractLambdaBody("Track.that(() -> assertThat(x).isEqualTo(1));"))
            .isEqualTo("assertThat(x).isEqualTo(1)");
    }
}
