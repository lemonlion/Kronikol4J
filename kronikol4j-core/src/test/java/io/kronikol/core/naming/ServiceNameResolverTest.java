package io.kronikol.core.naming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the .NET service-name resolution chain in {@code TestTrackingMessageHandler.ResolveServiceName}
 * (lines 63-105). Priority order:
 * <ol>
 *   <li>{@code FixedNameForReceivingService} (highest);</li>
 *   <li>exact {@code ClientNamesToServiceNames} match on the client name;</li>
 *   <li>suffix ({@code EndsWith} with a non-alphanumeric boundary) then {@code Contains} fallback
 *       (the latter only for assembly-qualified names) against each mapping entry, in insertion order;</li>
 *   <li>{@code PortsToServiceNames} lookup, else the {@code localhost:&lt;port&gt;} fallback.</li>
 * </ol>
 */
class ServiceNameResolverTest {

    @Test
    void fixedNameWinsOverEverything() {
        ServiceNameResolver r = ServiceNameResolver.builder()
            .fixedName("PaymentsApi")
            .clientName("ExactClient")
            .clientNamesToServiceNames(Map.of("ExactClient", "Mapped"))
            .portsToServiceNames(Map.of(8080, "Ported"))
            .build();
        assertThat(r.resolve(8080)).isEqualTo("PaymentsApi");
    }

    @Test
    void exactClientNameMatch() {
        ServiceNameResolver r = ServiceNameResolver.builder()
            .clientName("OrdersClient")
            .clientNamesToServiceNames(Map.of("OrdersClient", "OrdersApi"))
            .build();
        assertThat(r.resolve(80)).isEqualTo("OrdersApi");
    }

    @Test
    void portMappingWhenNoClientMatch() {
        ServiceNameResolver r = ServiceNameResolver.builder()
            .portsToServiceNames(Map.of(5432, "Postgres"))
            .build();
        assertThat(r.resolve(5432)).isEqualTo("Postgres");
    }

    @Test
    void localhostFallbackWhenNothingMatches() {
        ServiceNameResolver r = ServiceNameResolver.builder().build();
        assertThat(r.resolve(9999)).isEqualTo("localhost:9999");
    }

    @Test
    void suffixMatchWithNonAlphanumericBoundary() {
        // "My.Namespace.IOrdersClient" ends with key "IOrdersClient", boundary char '.' is non-alnum.
        ServiceNameResolver r = ServiceNameResolver.builder()
            .clientName("My.Namespace.IOrdersClient")
            .clientNamesToServiceNames(Map.of("IOrdersClient", "OrdersApi"))
            .build();
        assertThat(r.resolve(80)).isEqualTo("OrdersApi");
    }

    @Test
    void suffixMatchRejectedWhenBoundaryCharIsAlphanumeric() {
        // key "OrdersClient" — preceding char in "...IOrdersClient" is 'I' (a letter) → no suffix match,
        // and the name is not assembly-qualified → no Contains fallback → port fallback.
        ServiceNameResolver r = ServiceNameResolver.builder()
            .clientName("My.Namespace.IOrdersClient")
            .clientNamesToServiceNames(Map.of("OrdersClient", "OrdersApi"))
            .build();
        assertThat(r.resolve(80)).isEqualTo("localhost:80");
    }

    @Test
    void containsFallbackOnlyAppliesToAssemblyQualifiedNames() {
        // key "BarClient" appears mid-string, not as a suffix.
        Map<String, String> mapping = Map.of("BarClient", "BarApi");

        // Assembly-qualified → Contains fallback fires.
        ServiceNameResolver qualified = ServiceNameResolver.builder()
            .clientName("Foo.BarClient.Extra, MyAssembly, Version=1.0.0.0")
            .clientNamesToServiceNames(mapping)
            .build();
        assertThat(qualified.resolve(80)).isEqualTo("BarApi");

        // Not assembly-qualified → Contains fallback suppressed → port fallback.
        ServiceNameResolver plain = ServiceNameResolver.builder()
            .clientName("Foo.BarClient.Extra")
            .clientNamesToServiceNames(mapping)
            .build();
        assertThat(plain.resolve(80)).isEqualTo("localhost:80");
    }

    @Test
    void unmatchedClientNameInvokesCallbackThenFallsBackToPort() {
        List<String> unmatched = new ArrayList<>();
        ServiceNameResolver r = ServiceNameResolver.builder()
            .clientName("WeirdGeneratedName")
            .clientNamesToServiceNames(Map.of("SomethingElse", "X"))
            .onUnmatchedClientName(unmatched::add)
            .build();
        assertThat(r.resolve(1234)).isEqualTo("localhost:1234");
        assertThat(unmatched).containsExactly("WeirdGeneratedName");
    }

    @Test
    void noClientNameMeansNoUnmatchedCallback() {
        List<String> unmatched = new ArrayList<>();
        ServiceNameResolver r = ServiceNameResolver.builder()
            .clientNamesToServiceNames(Map.of("SomethingElse", "X"))
            .onUnmatchedClientName(unmatched::add)
            .build();
        assertThat(r.resolve(1234)).isEqualTo("localhost:1234");
        assertThat(unmatched).isEmpty();
    }
}
