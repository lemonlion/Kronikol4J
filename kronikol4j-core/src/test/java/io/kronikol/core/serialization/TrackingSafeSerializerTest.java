package io.kronikol.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Ports the behaviour of .NET {@code TrackingSafeSerializer} — the guard layer that safely turns arbitrary
 * captured values into JSON note content, handling mock proxies, futures (the {@code Task} analog),
 * skip-types, circular references and a max depth. Pure logic → unit-tested; a golden lands when an adapter
 * wires it into rendered output.
 */
class TrackingSafeSerializerTest {

    // --- guards -------------------------------------------------------------------------------------

    @Test
    void nullSerializesToNull() {
        assertThat(TrackingSafeSerializer.serialize(null, null)).isNull();
    }

    @Test
    void mockProxyDetectedByClassNameMarker() {
        TrackingSerializerOptions opts = TrackingSerializerOptions.builder()
            .mockProxyMarkers(List.of("FakeMock"))
            .build();
        Object proxy = new FakeMock();
        assertThat(TrackingSafeSerializer.serialize(proxy, opts)).isEqualTo("\"<mock proxy>\"");
    }

    @Test
    void normalObjectIsNotTreatedAsMockProxyByDefault() {
        assertThat(TrackingSafeSerializer.serialize(new Point(1, 2), null)).doesNotContain("<mock proxy>");
    }

    // --- futures (Task analog) ----------------------------------------------------------------------

    @Test
    void completedFutureUnwrapsToResult() {
        CompletableFuture<Point> f = CompletableFuture.completedFuture(new Point(3, 4));
        String json = TrackingSafeSerializer.serialize(f, null);
        assertThat(json).contains("\"x\"").contains("3").contains("\"y\"").contains("4");
    }

    @Test
    void pendingFutureIsMarked() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        assertThat(TrackingSafeSerializer.serialize(pending, null)).isEqualTo("\"<pending Task>\"");
    }

    @Test
    void exceptionallyCompletedFutureIsTreatedAsPending() {
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));
        assertThat(TrackingSafeSerializer.serialize(failed, null)).isEqualTo("\"<pending Task>\"");
    }

    @Test
    void unwrapCanBeDisabled() {
        TrackingSerializerOptions opts = TrackingSerializerOptions.builder().unwrapFutures(false).build();
        CompletableFuture<String> pending = new CompletableFuture<>();
        assertThat(TrackingSafeSerializer.serialize(pending, opts)).isNotEqualTo("\"<pending Task>\"");
    }

    // --- array filtering ----------------------------------------------------------------------------

    @Test
    void arrayFiltersSkipTypesButKeepsNulls() {
        TrackingSerializerOptions opts = TrackingSerializerOptions.builder()
            .skipTypes(java.util.Set.of(Secret.class))
            .build();
        Object[] arr = {new Point(1, 1), new Secret(), null, new Point(2, 2)};
        String json = TrackingSafeSerializer.serialize(arr, opts);
        assertThat(json).doesNotContain("Secret");
        assertThat(json).contains("null"); // null element kept
        assertThat(json).contains("\"x\"");
    }

    @Test
    void arrayFiltersMockProxies() {
        TrackingSerializerOptions opts = TrackingSerializerOptions.builder()
            .mockProxyMarkers(List.of("FakeMock"))
            .build();
        Object[] arr = {new Point(5, 6), new FakeMock()};
        String json = TrackingSafeSerializer.serialize(arr, opts);
        assertThat(json).contains("5").doesNotContain("FakeMock");
    }

    // --- reflective JSON: maps, lists, records, pojos, primitives -----------------------------------

    @Test
    void serializesMapPreservingOrderAndStrippingNulls() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", null); // stripped
        m.put("c", "x");
        String json = TrackingSafeSerializer.serialize(m, null);
        assertThat(json).contains("\"a\"").contains("\"c\"").doesNotContain("\"b\"");
    }

    @Test
    void serializesNestedListsAndPrimitives() {
        String json = TrackingSafeSerializer.serialize(List.of(1, "two", true), null);
        assertThat(json).contains("1").contains("\"two\"").contains("true");
    }

    @Test
    void serializesRecordViaComponents() {
        String json = TrackingSafeSerializer.serialize(new Point(7, 8), null);
        assertThat(json).contains("\"x\"").contains("7").contains("\"y\"").contains("8");
    }

    // --- circular references + max depth ------------------------------------------------------------

    @Test
    void circularReferenceDoesNotRecurseInfinitely() {
        Node a = new Node("a");
        Node b = new Node("b");
        a.next = b;
        b.next = a; // cycle
        String json = TrackingSafeSerializer.serialize(a, null);
        assertThat(json).contains("\"a\"").contains("\"b\""); // walked once, cycle broken — no StackOverflow
    }

    @Test
    void exceedingMaxDepthFallsBackToToString() {
        // Build nesting deeper than maxDepth; .NET throws on overflow and falls back to the value's string.
        TrackingSerializerOptions opts = TrackingSerializerOptions.builder().maxDepth(2).build();
        List<Object> deep = List.of(List.of(List.of(List.of("too deep"))));
        String json = TrackingSafeSerializer.serialize(deep, opts);
        assertThat(json).isEqualTo("\"" + deep + "\""); // fallback = quoted toString
    }

    // --- fixtures -----------------------------------------------------------------------------------

    record Point(int x, int y) {
    }

    static final class Secret {
    }

    static final class FakeMock {
    }

    static final class Node {
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Node getNext() {
            return next;
        }
    }
}
