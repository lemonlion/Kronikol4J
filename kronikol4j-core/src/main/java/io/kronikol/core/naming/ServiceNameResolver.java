package io.kronikol.core.naming;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Resolves the participant/service name for a tracked outbound call from the configured mappings.
 * Java port of the .NET {@code TestTrackingMessageHandler.ResolveServiceName} chain (lines 63-105),
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
 *   <li><b>Port mapping</b> — {@code portsToServiceNames[port]}, else {@code "localhost:<port>"}.</li>
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

    /** Resolves the service name for a call to the given destination port. */
    public String resolve(int port) {
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

        // 4. Port mapping, else localhost:port.
        String byPort = portsToServiceNames.get(port);
        return byPort != null ? byPort : "localhost:" + port;
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
