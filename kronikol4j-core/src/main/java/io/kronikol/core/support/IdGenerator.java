package io.kronikol.core.support;

import java.util.Random;
import java.util.UUID;

/**
 * Source of trace / request-response ids. The determinism seam (plan §6.1): production uses
 * {@link #random()}; golden-file capture and parity tests use {@link #seeded(long)} so the same
 * corpus yields the same ids on every run and across runtimes.
 */
@FunctionalInterface
public interface IdGenerator {

    UUID newId();

    /** Cryptographically-random ids — the production default. */
    static IdGenerator random() {
        return UUID::randomUUID;
    }

    /** Deterministic, reproducible ids from a seed — for tests and golden-file capture. */
    static IdGenerator seeded(long seed) {
        Random random = new Random(seed);
        return () -> new UUID(random.nextLong(), random.nextLong());
    }
}
