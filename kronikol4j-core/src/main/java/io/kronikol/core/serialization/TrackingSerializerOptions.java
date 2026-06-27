package io.kronikol.core.serialization;

import java.util.List;
import java.util.Set;

/**
 * Configuration for {@link TrackingSafeSerializer}, controlling serialisation depth, formatting, and the
 * filtering of special values. Java port of the .NET {@code TrackingSerializerOptions}.
 *
 * <p>Two fields are adapted to the Java platform:
 * <ul>
 *   <li><b>mockProxyMarkers</b> replaces .NET's hard-coded {@code "Castle.Proxies"} substring — Java mock
 *       frameworks use different generated-class name markers, so the list is configurable. Defaults cover
 *       Mockito/ByteBuddy/CGLIB/EasyMock subclass proxies. (Mockito inline mocks reuse the real class name
 *       and so are not name-detectable without a runtime dependency on Mockito — a documented limitation.)</li>
 *   <li>.NET's {@code FilterCancellationTokens} has no Java analog (no universal cancellation type); use
 *       {@link #skipTypes()} to drop any platform-specific type.</li>
 * </ul>
 */
public final class TrackingSerializerOptions {

    /** Default markers for mock-framework generated proxy classes (the Java analog of "Castle.Proxies"). */
    public static final List<String> DEFAULT_MOCK_PROXY_MARKERS =
        List.of("$MockitoMock$", "MockitoMock", "ByteBuddy", "$$EnhancerByMockito", "EasyMock", "CGLIB$$");

    private final int maxDepth;
    private final boolean writeIndented;
    private final boolean unwrapFutures;
    private final boolean skipMockProxies;
    private final Set<Class<?>> skipTypes;
    private final List<String> mockProxyMarkers;

    private TrackingSerializerOptions(Builder b) {
        this.maxDepth = b.maxDepth;
        this.writeIndented = b.writeIndented;
        this.unwrapFutures = b.unwrapFutures;
        this.skipMockProxies = b.skipMockProxies;
        this.skipTypes = Set.copyOf(b.skipTypes);
        this.mockProxyMarkers = List.copyOf(b.mockProxyMarkers);
    }

    /** The default options (matching .NET defaults). */
    public static TrackingSerializerOptions defaults() {
        return builder().build();
    }

    public int maxDepth() {
        return maxDepth;
    }

    public boolean writeIndented() {
        return writeIndented;
    }

    public boolean unwrapFutures() {
        return unwrapFutures;
    }

    public boolean skipMockProxies() {
        return skipMockProxies;
    }

    public Set<Class<?>> skipTypes() {
        return skipTypes;
    }

    public List<String> mockProxyMarkers() {
        return mockProxyMarkers;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link TrackingSerializerOptions}. Defaults mirror .NET: maxDepth=10, indented, unwrap. */
    public static final class Builder {
        private int maxDepth = 10;
        private boolean writeIndented = true;
        private boolean unwrapFutures = true;
        private boolean skipMockProxies = true;
        private Set<Class<?>> skipTypes = Set.of();
        private List<String> mockProxyMarkers = DEFAULT_MOCK_PROXY_MARKERS;

        public Builder maxDepth(int v) {
            this.maxDepth = v;
            return this;
        }

        public Builder writeIndented(boolean v) {
            this.writeIndented = v;
            return this;
        }

        public Builder unwrapFutures(boolean v) {
            this.unwrapFutures = v;
            return this;
        }

        public Builder skipMockProxies(boolean v) {
            this.skipMockProxies = v;
            return this;
        }

        public Builder skipTypes(Set<Class<?>> v) {
            this.skipTypes = v == null ? Set.of() : v;
            return this;
        }

        public Builder mockProxyMarkers(List<String> v) {
            this.mockProxyMarkers = v == null ? List.of() : v;
            return this;
        }

        public TrackingSerializerOptions build() {
            return new TrackingSerializerOptions(this);
        }
    }
}
