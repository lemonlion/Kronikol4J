package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the .NET status-label rendering: {@code HttpStatusCode.ToString()} then {@code Titleize()},
 * with the 302 "Found (Redirect)" disambiguation and a bare-number fallback for unknown codes.
 * The byte-for-byte cross-runtime guarantee is in {@code PlantUmlParityTest}; this documents the unit.
 */
class HttpStatusNamesTest {

    @Test
    void titleizesMultiWordNamesAndKeepsAcronyms() {
        assertThat(HttpStatusNames.label(200)).isEqualTo("OK");           // all-caps acronym preserved
        assertThat(HttpStatusNames.label(201)).isEqualTo("Created");
        assertThat(HttpStatusNames.label(204)).isEqualTo("No Content");
        assertThat(HttpStatusNames.label(404)).isEqualTo("Not Found");
        assertThat(HttpStatusNames.label(500)).isEqualTo("Internal Server Error");
    }

    @Test
    void disambiguates302AsFoundRedirect() {
        assertThat(HttpStatusNames.label(302)).isEqualTo("Found (Redirect)");
    }

    @Test
    void unknownCodeRendersAsTheBareNumber() {
        assertThat(HttpStatusNames.label(418)).isEqualTo("418");
    }
}
