package io.kronikol.report.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScenarioStableIdTest {

    @Test
    void matchesDotNetSha256First16Lowercase() {
        // Pinned against the values .NET emits in the report-data fixtures (s2 carries an outlineId).
        assertThat(ScenarioStableId.compute("Checkout", "Checkout succeeds", null))
            .isEqualTo("79b75f96aa4fced3");
        assertThat(ScenarioStableId.compute("Checkout", "Checkout rejects empty cart", "outline-1"))
            .isEqualTo("478c03688cd2bdb0");
    }

    @Test
    void outlineIdParticipatesInTheHash() {
        String without = ScenarioStableId.compute("F", "S", null);
        String with = ScenarioStableId.compute("F", "S", "outline-1");
        assertThat(with).hasSize(16).isNotEqualTo(without);
    }
}
