package io.kronikol.core.naming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the .NET service-name resolution chain in {@code TestTrackingMessageHandler.ResolveServiceName}
 * Priority order:
 * <ol>
 *   <li>{@code FixedNameForReceivingService} (highest);</li>
 *   <li>exact {@code ClientNamesToServiceNames} match on the client name;</li>
 *   <li>suffix ({@code EndsWith} with a non-alphanumeric boundary) then {@code Contains} fallback
 *       (the latter only for assembly-qualified names) against each mapping entry, in insertion order;</li>
 *   <li>{@code PortsToServiceNames} lookup, else the host and port, or {@code localhost:&lt;port&gt;} for this
 *       machine (.NET #190 R0).</li>
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

    // .NET #190 R0: the fallback named every unmapped host localhost:<port>, so two remote APIs on one port were one
    // party and a remote one was called localhost. A loopback address is this machine and keeps that name. The host is
    // put in the form .NET's Uri.Host gives, whatever form the caller's library hands over.

    @Test
    void namesAnUnmappedRemoteHostByItsHostAndPort() {
        ServiceNameResolver r = ServiceNameResolver.builder().build();
        assertThat(r.resolve("pricing.example.com", 443)).isEqualTo("pricing.example.com:443");
    }

    @Test
    void twoUnmappedHostsOnOnePortAreTwoParties() {
        ServiceNameResolver r = ServiceNameResolver.builder().build();
        assertThat(r.resolve("pricing.example.com", 443)).isEqualTo("pricing.example.com:443");
        assertThat(r.resolve("api.openai.example", 443)).isEqualTo("api.openai.example:443");
    }

    @Test
    void aLoopbackAddressKeepsTheLocalhostName() {
        ServiceNameResolver r = ServiceNameResolver.builder().build();
        assertThat(r.resolve("127.0.0.1", 54987)).isEqualTo("localhost:54987");
        assertThat(r.resolve("127.5.6.7", 1)).isEqualTo("localhost:1");
        assertThat(r.resolve("[::1]", 5000)).isEqualTo("localhost:5000");
        assertThat(r.resolve("::1", 5000)).isEqualTo("localhost:5000");
        assertThat(r.resolve("LOCALHOST", 5001)).isEqualTo("localhost:5001");
    }

    @Test
    void namesAHostAsDotNetsUriGivesIt() {
        ServiceNameResolver r = ServiceNameResolver.builder().build();
        // URI.getHost() keeps the case written; .NET lower-cases ASCII.
        assertThat(r.resolve("API.Example.COM", 8080)).isEqualTo("api.example.com:8080");
        // OkHttp's host() gives IPv6 without brackets; URI.getHost() with them, and with the zone. .NET: brackets, no zone,
        // compressed.
        assertThat(r.resolve("fe80::1", 8080)).isEqualTo("[fe80::1]:8080");
        assertThat(r.resolve("[fe80::1%25eth0]", 8080)).isEqualTo("[fe80::1]:8080");
        assertThat(r.resolve("[fe80::1%eth0]", 8080)).isEqualTo("[fe80::1]:8080");
        assertThat(r.resolve("[FE80:0:0:0:0:0:0:1]", 8080)).isEqualTo("[fe80::1]:8080");
        // A trailing dot is not loopback, and is another host.
        assertThat(r.resolve("localhost.", 5000)).isEqualTo("localhost.:5000");
    }

    @Test
    void aRequestWithNoHostKeepsTheLocalhostName() {
        // URI.getHost() is null for a host it cannot parse (an underscore, a non-ASCII name).
        ServiceNameResolver r = ServiceNameResolver.builder().build();
        assertThat(r.resolve(null, 80)).isEqualTo("localhost:80");
        assertThat(r.resolve("", 80)).isEqualTo("localhost:80");
    }

    @Test
    void thePortMapStillNamesARemoteHost() {
        ServiceNameResolver r = ServiceNameResolver.builder()
            .portsToServiceNames(Map.of(5141, "Cow Service"))
            .build();
        assertThat(r.resolve("pricing.internal", 5141)).isEqualTo("Cow Service");
    }

    @Test
    void resolveByPortAloneMeansThisMachine() {
        ServiceNameResolver r = ServiceNameResolver.builder().build();
        assertThat(r.resolve(7777)).isEqualTo("localhost:7777");
    }
}
