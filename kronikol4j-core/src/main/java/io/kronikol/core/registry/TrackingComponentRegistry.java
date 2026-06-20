package io.kronikol.core.registry;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A registry of all {@link TrackingComponent}s created during a run. Components register themselves
 * in their constructor; the report uses it to flag components that were wired but never invoked.
 * Mirrors the .NET {@code TrackingComponentRegistry}.
 */
public final class TrackingComponentRegistry {

    private static final ConcurrentLinkedQueue<TrackingComponent> COMPONENTS = new ConcurrentLinkedQueue<>();

    private TrackingComponentRegistry() {
    }

    public static void register(TrackingComponent component) {
        if (component != null) {
            COMPONENTS.add(component);
        }
    }

    public static List<TrackingComponent> getRegisteredComponents() {
        return List.copyOf(COMPONENTS);
    }

    public static List<TrackingComponent> getUnusedComponents() {
        return COMPONENTS.stream().filter(c -> !c.wasInvoked()).toList();
    }

    public static void clear() {
        COMPONENTS.clear();
    }
}
