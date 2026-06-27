package io.kronikol.core.naming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the .NET excluded-hosts check in {@code TestTrackingMessageHandler.SendAsync} (line 137):
 * {@code _excludedHosts.Contains(host, StringComparer.OrdinalIgnoreCase)} — case-insensitive host match
 * used to skip tracking entirely (e.g. ASP.NET Core TestServer's internal {@code override.com}).
 */
class ExcludedHostsTest {

    @Test
    void emptyExcludesNothing() {
        ExcludedHosts hosts = ExcludedHosts.of(List.of());
        assertThat(hosts.excludes("anything.com")).isFalse();
        assertThat(hosts.isEmpty()).isTrue();
    }

    @Test
    void matchesCaseInsensitively() {
        ExcludedHosts hosts = ExcludedHosts.of(List.of("Override.com", "localhost"));
        assertThat(hosts.excludes("override.com")).isTrue();
        assertThat(hosts.excludes("OVERRIDE.COM")).isTrue();
        assertThat(hosts.excludes("LocalHost")).isTrue();
        assertThat(hosts.isEmpty()).isFalse();
    }

    @Test
    void doesNotMatchUnlistedHost() {
        ExcludedHosts hosts = ExcludedHosts.of(List.of("override.com"));
        assertThat(hosts.excludes("real-api.com")).isFalse();
    }

    @Test
    void nullHostIsNotExcluded() {
        ExcludedHosts hosts = ExcludedHosts.of(List.of("override.com"));
        assertThat(hosts.excludes(null)).isFalse();
    }
}
