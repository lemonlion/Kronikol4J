package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.naming.ServiceNameResolver;
import io.kronikol.core.tracking.UnmatchedClientNameRegistry.RecordedName;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies the {@link UnmatchedClientNameRegistry} (the .NET diagnostic registry) and its
 *  {@link ServiceNameResolver} wiring. */
class UnmatchedClientNameRegistryTest {

    @BeforeEach
    @AfterEach
    void reset() {
        UnmatchedClientNameRegistry.clear();
    }

    @Test
    void recordsAndCountsOrderedByCountDescending() {
        UnmatchedClientNameRegistry.record("Alpha");
        UnmatchedClientNameRegistry.record("Beta");
        UnmatchedClientNameRegistry.record("Beta");
        UnmatchedClientNameRegistry.record("Beta");
        UnmatchedClientNameRegistry.record("Gamma");
        UnmatchedClientNameRegistry.record("Gamma");

        assertThat(UnmatchedClientNameRegistry.getRecordedNames())
            .containsExactly(
                new RecordedName("Beta", 3),
                new RecordedName("Gamma", 2),
                new RecordedName("Alpha", 1));
    }

    @Test
    void tiesKeepInsertionOrder() {
        UnmatchedClientNameRegistry.record("First");
        UnmatchedClientNameRegistry.record("Second");
        assertThat(UnmatchedClientNameRegistry.getRecordedNames())
            .extracting(RecordedName::clientName)
            .containsExactly("First", "Second"); // both count 1 → stable insertion order
    }

    @Test
    void clearResetsTheRegistry() {
        UnmatchedClientNameRegistry.record("X");
        UnmatchedClientNameRegistry.clear();
        assertThat(UnmatchedClientNameRegistry.getRecordedNames()).isEmpty();
    }

    @Test
    void nullClientNameIsIgnored() {
        UnmatchedClientNameRegistry.record(null);
        assertThat(UnmatchedClientNameRegistry.getRecordedNames()).isEmpty();
    }

    @Test
    void serviceNameResolverFeedsTheRegistryOnUnmatchedClientName() {
        ServiceNameResolver resolver = ServiceNameResolver.builder()
            .clientName("OrdersHttpClient")
            .clientNamesToServiceNames(Map.of("PaymentsHttpClient", "Payments"))
            .onUnmatchedClientName(UnmatchedClientNameRegistry::record)
            .build();

        // No fixed name, no matching client name → falls back to port-based mapping AND records the miss.
        String name = resolver.resolve(8080);
        assertThat(name).isEqualTo("localhost:8080");
        assertThat(UnmatchedClientNameRegistry.getRecordedNames())
            .containsExactly(new RecordedName("OrdersHttpClient", 1));
    }
}
