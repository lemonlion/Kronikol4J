package io.kronikol.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    @Test
    void seededGeneratorIsReproducible() {
        IdGenerator a = IdGenerator.seeded(42);
        IdGenerator b = IdGenerator.seeded(42);
        UUID a1 = a.newId();
        UUID a2 = a.newId();
        // Same seed -> identical sequence (the property the golden-file capture relies on).
        assertThat(b.newId()).isEqualTo(a1);
        assertThat(b.newId()).isEqualTo(a2);
    }

    @Test
    void differentSeedsDiverge() {
        assertThat(IdGenerator.seeded(1).newId()).isNotEqualTo(IdGenerator.seeded(2).newId());
    }

    @Test
    void randomGeneratorProducesDistinctIds() {
        IdGenerator random = IdGenerator.random();
        assertThat(random.newId()).isNotEqualTo(random.newId());
    }
}
