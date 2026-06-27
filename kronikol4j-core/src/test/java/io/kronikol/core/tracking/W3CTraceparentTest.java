package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.support.IdGenerator;
import org.junit.jupiter.api.Test;

class W3CTraceparentTest {

    @Test
    void headerHasW3CShape() {
        W3CTraceparent tp = W3CTraceparent.generate(IdGenerator.seeded(7));
        // 00-<32 hex>-<16 hex>-00
        assertThat(tp.header()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-00");
        assertThat(tp.traceId()).hasSize(32);
        assertThat(tp.spanId()).hasSize(16);
    }

    @Test
    void deterministicForAGivenSeed() {
        assertThat(W3CTraceparent.generate(IdGenerator.seeded(7)).header())
            .isEqualTo(W3CTraceparent.generate(IdGenerator.seeded(7)).header());
    }
}
