package io.kronikol.core.naming;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A case-insensitive set of host names for which tracking is skipped entirely. Java port of the .NET
 * {@code ExcludedHosts} check ({@code TestTrackingMessageHandler.SendAsync}, line 137):
 * {@code _excludedHosts.Contains(host, StringComparer.OrdinalIgnoreCase)}.
 *
 * <p>Matching folds host names to lower case via {@link Locale#ROOT} (the parity rule for invariant
 * casing), which reproduces {@code OrdinalIgnoreCase} for the ASCII host names this is used with.
 */
public final class ExcludedHosts {

    private static final ExcludedHosts EMPTY = new ExcludedHosts(Set.of());

    private final Set<String> lowerCased;

    private ExcludedHosts(Set<String> lowerCased) {
        this.lowerCased = lowerCased;
    }

    /** Builds an {@code ExcludedHosts} from the configured host names (null/empty → excludes nothing). */
    public static ExcludedHosts of(Collection<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return EMPTY;
        }
        Set<String> folded = new HashSet<>();
        for (String h : hosts) {
            if (h != null) {
                folded.add(h.toLowerCase(Locale.ROOT));
            }
        }
        return folded.isEmpty() ? EMPTY : new ExcludedHosts(folded);
    }

    /** Whether the given host is excluded (case-insensitive). A null host is never excluded. */
    public boolean excludes(String host) {
        return host != null && !lowerCased.isEmpty() && lowerCased.contains(host.toLowerCase(Locale.ROOT));
    }

    /** Whether no hosts are configured (the common case — lets callers skip the check cheaply). */
    public boolean isEmpty() {
        return lowerCased.isEmpty();
    }
}
