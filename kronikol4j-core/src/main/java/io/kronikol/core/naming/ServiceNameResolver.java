package io.kronikol.core.naming;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Resolves the participant/service name for a tracked outbound call from the configured mappings.
 * Java port of the .NET {@code TestTrackingMessageHandler.ResolveServiceName} chain,
 * extracted as a standalone, reusable component because both the HTTP and cloud adapters share it.
 *
 * <p>Resolution priority (first match wins):
 * <ol>
 *   <li><b>Fixed name</b> ({@code FixedNameForReceivingService}) — overrides everything.</li>
 *   <li><b>Exact client-name mapping</b> — {@code clientNamesToServiceNames[clientName]}.</li>
 *   <li><b>Fuzzy client-name mapping</b> — for generated/Refit client names. Per entry, in insertion
 *       order: a {@code endsWith} match where the character immediately preceding the suffix is
 *       non-alphanumeric (a real boundary), then — only for assembly-qualified names — a {@code contains}
 *       match. Assembly qualification (a {@code ", "} separator, e.g. {@code "Foo.IClient, MyAsm, Version=…"})
 *       is stripped before matching; its presence is what enables the looser {@code contains} fallback,
 *       avoiding false positives like {@code "Client"} matching {@code "MyBetterClient"} on simple names.</li>
 *   <li><b>Port mapping</b> — {@code portsToServiceNames[port]}, else the host and port in .NET's form
 *       ({@code "pricing.example.com:443"}), or {@code "localhost:<port>"} for a loopback host or none
 *       (.NET 5.13.0, #190).</li>
 * </ol>
 *
 * <p>When a non-null client name matches nothing in steps 2-3, {@link Builder#onUnmatchedClientName} is
 * invoked (the seam for the .NET {@code UnmatchedClientNameRegistry.Record}, which is a separate roadmap
 * item); the default is a no-op.
 */
public final class ServiceNameResolver {

    private final String fixedName;
    private final String clientName;
    private final Map<String, String> clientNamesToServiceNames;
    private final Map<Integer, String> portsToServiceNames;
    private final Consumer<String> onUnmatchedClientName;

    private ServiceNameResolver(Builder b) {
        this.fixedName = b.fixedName;
        this.clientName = b.clientName;
        this.clientNamesToServiceNames = b.clientNamesToServiceNames;
        this.portsToServiceNames = b.portsToServiceNames;
        this.onUnmatchedClientName = b.onUnmatchedClientName;
    }

    /** Resolves the service name for a call to the given destination port on this machine. */
    public String resolve(int port) {
        return resolve(null, port);
    }

    /**
     * Resolves the service name for a call to {@code host} on {@code port}. A call nothing maps is named by its host and
     * port ({@code pricing.example.com:443}), or {@code localhost:<port>} when the host is this machine or there is none
     * (.NET #190 R0): before, two remote APIs on one port were one party called localhost. {@code host} may come in any
     * form a client library gives it; it is put in the form .NET's {@code Uri.Host} gives ({@link #hostName}).
     */
    public String resolve(String host, int port) {
        // 1. Fixed name — highest priority.
        if (fixedName != null) {
            return fixedName;
        }

        // 2. Exact client-name mapping.
        if (clientName != null) {
            String exact = clientNamesToServiceNames.get(clientName);
            if (exact != null) {
                return exact;
            }
        }

        // 3. Fuzzy client-name mapping (suffix, then contains for assembly-qualified names).
        if (clientName != null && !clientNamesToServiceNames.isEmpty()) {
            int commaIndex = clientName.indexOf(", ");
            boolean assemblyQualified = commaIndex >= 0;
            String effectiveName = assemblyQualified ? clientName.substring(0, commaIndex) : clientName;

            for (Map.Entry<String, String> e : clientNamesToServiceNames.entrySet()) {
                String key = e.getKey();

                // 3a. endsWith with a non-alphanumeric boundary char.
                if (effectiveName.length() > key.length()
                    && effectiveName.endsWith(key)
                    && !Character.isLetterOrDigit(effectiveName.charAt(effectiveName.length() - key.length() - 1))) {
                    return e.getValue();
                }

                // 3b. contains — only for assembly-qualified names (Refit v9), to avoid false positives.
                if (assemblyQualified
                    && effectiveName.length() > key.length()
                    && effectiveName.contains(key)) {
                    return e.getValue();
                }
            }

            // No fuzzy match for a configured client name — record it for diagnostics.
            onUnmatchedClientName.accept(clientName);
        }

        // 4. Port mapping, by the port alone whatever the host, else the host and port.
        String byPort = portsToServiceNames.get(port);
        if (byPort != null) {
            return byPort;
        }
        String name = hostName(host);
        return (name == null ? "localhost" : name) + ":" + port;
    }

    /**
     * The host as .NET's {@code Uri.Host} gives it, or null for a loopback host or none, which keep
     * {@code localhost}: ASCII letters lower-cased (other letters as written), an IPv6 literal in brackets, compressed,
     * with no zone. A trailing dot is kept, so {@code localhost.} is another host, as in .NET. Never a DNS lookup.
     */
    static String hostName(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (h.indexOf(':') >= 0) {
            int zone = h.indexOf('%');
            String address = zone >= 0 ? h.substring(0, zone) : h;
            String compressed = compressIpv6(address);
            if (compressed == null) {
                return "[" + asciiLower(address) + "]";
            }
            return "::1".equals(compressed) ? null : "[" + compressed + "]";
        }
        String lower = asciiLower(h);
        return "localhost".equals(lower) || isIpv4Loopback(lower) ? null : lower;
    }

    private static String asciiLower(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        }
        return sb.toString();
    }

    /** 127.0.0.0/8, which .NET's {@code Uri.IsLoopback} counts as this machine. */
    private static boolean isIpv4Loopback(String h) {
        String[] parts = h.split("\\.", -1);
        if (parts.length != 4 || !"127".equals(parts[0])) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit) || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    /** An IPv6 literal in RFC 5952's form (lower case, no leading zeros, the longest run of two or more zero groups as ::), or null when it is not one. */
    static String compressIpv6(String address) {
        int[] groups = parseIpv6(address);
        if (groups == null) {
            return null;
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && groups[j] == 0) {
                j++;
            }
            if (j - i > bestLength) {
                bestStart = i;
                bestLength = j - i;
            }
            i = j;
        }
        if (bestLength < 2) {
            bestStart = -1;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLength - 1;
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(Integer.toHexString(groups[i]));
        }
        return sb.toString();
    }

    private static int[] parseIpv6(String address) {
        int doubleColon = address.indexOf("::");
        if (doubleColon >= 0 && address.indexOf("::", doubleColon + 1) >= 0) {
            return null;
        }
        String[] head = doubleColon >= 0 ? split(address.substring(0, doubleColon)) : split(address);
        String[] tail = doubleColon >= 0 ? split(address.substring(doubleColon + 2)) : new String[0];
        if (head == null || tail == null || (doubleColon < 0 && head.length != 8) || head.length + tail.length > (doubleColon >= 0 ? 7 : 8)) {
            return null;
        }
        int[] groups = new int[8];
        for (int i = 0; i < head.length; i++) {
            groups[i] = group(head[i]);
            if (groups[i] < 0) {
                return null;
            }
        }
        for (int i = 0; i < tail.length; i++) {
            groups[8 - tail.length + i] = group(tail[i]);
            if (groups[8 - tail.length + i] < 0) {
                return null;
            }
        }
        return groups;
    }

    private static String[] split(String part) {
        if (part.isEmpty()) {
            return new String[0];
        }
        String[] pieces = part.split(":", -1);
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                return null;
            }
        }
        return pieces;
    }

    private static int group(String piece) {
        if (piece.length() > 4) {
            return -1;
        }
        try {
            return Integer.parseInt(piece, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ServiceNameResolver}. All mappings default to empty; names default to null. */
    public static final class Builder {
        private String fixedName;
        private String clientName;
        private Map<String, String> clientNamesToServiceNames = Map.of();
        private Map<Integer, String> portsToServiceNames = Map.of();
        private Consumer<String> onUnmatchedClientName = name -> { };

        /** {@code FixedNameForReceivingService} — when set, always returned. */
        public Builder fixedName(String v) {
            this.fixedName = v;
            return this;
        }

        /** The client name for this adapter instance (e.g. the typed/Refit client interface name). */
        public Builder clientName(String v) {
            this.clientName = v;
            return this;
        }

        /** {@code ClientNamesToServiceNames}. Copied, preserving iteration order for deterministic matching. */
        public Builder clientNamesToServiceNames(Map<String, String> v) {
            this.clientNamesToServiceNames = v == null ? Map.of() : new LinkedHashMap<>(v);
            return this;
        }

        /** {@code PortsToServiceNames}. */
        public Builder portsToServiceNames(Map<Integer, String> v) {
            this.portsToServiceNames = v == null ? Map.of() : new LinkedHashMap<>(v);
            return this;
        }

        /** Invoked with the client name when it is non-null but matches nothing. Default: no-op. */
        public Builder onUnmatchedClientName(Consumer<String> v) {
            this.onUnmatchedClientName = v == null ? name -> { } : v;
            return this;
        }

        public ServiceNameResolver build() {
            return new ServiceNameResolver(this);
        }
    }
}
