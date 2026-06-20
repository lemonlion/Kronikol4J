package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlantUmlTextEncoderTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "a",
        "ab",
        "abc",
        "abcd",
        "@startuml\nAlice -> Bob: hello\n@enduml",
        "unicode: éèê 你好 ✓ ✗"
    })
    void roundTripsArbitraryText(String text) {
        String encoded = PlantUmlTextEncoder.encode(text);
        assertThat(PlantUmlTextEncoder.decode(encoded)).isEqualTo(text);
    }

    @Test
    void encodedOutputUsesOnlyTheCustomAlphabet() {
        String encoded = PlantUmlTextEncoder.encode("@startuml\nAlice -> Bob\n@enduml");
        assertThat(encoded).matches("[0-9A-Za-z_-]*");
    }

    @Test
    void roundTripsALargeDiagram() {
        StringBuilder big = new StringBuilder("@startuml\n");
        for (int i = 0; i < 2000; i++) {
            big.append("Client -> Service").append(i).append(": call ").append(i).append('\n');
        }
        big.append("@enduml");
        String text = big.toString();
        assertThat(PlantUmlTextEncoder.decode(PlantUmlTextEncoder.encode(text))).isEqualTo(text);
    }
}
