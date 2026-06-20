package io.kronikol.diagram.plantuml;

import io.kronikol.core.constants.DependencyCategories;
import java.util.Map;

/**
 * Maps a dependency category to a PlantUML participant shape and colour. A static lookup with no
 * hashing (plan §6.5 confirmed the .NET palette is a frozen dictionary — ports cleanly). Unknown
 * categories fall back to a plain participant.
 *
 * <p>NOTE: the exact hex values must be reconciled against the .NET {@code DependencyPalette} during
 * golden-file parity (plan §6.3); the shape mapping below is structurally faithful.
 */
public final class DependencyPalette {

    private DependencyPalette() {
    }

    /** PlantUML participant keywords. */
    public enum Shape {
        PARTICIPANT("participant"),
        DATABASE("database"),
        QUEUE("queue"),
        COLLECTIONS("collections"),
        ENTITY("entity");

        private final String keyword;

        Shape(String keyword) {
            this.keyword = keyword;
        }

        public String keyword() {
            return keyword;
        }
    }

    private static final Map<String, Shape> SHAPES = Map.ofEntries(
        Map.entry(DependencyCategories.SQL, Shape.DATABASE),
        Map.entry(DependencyCategories.DATABASE, Shape.DATABASE),
        Map.entry(DependencyCategories.POSTGRESQL, Shape.DATABASE),
        Map.entry(DependencyCategories.MYSQL, Shape.DATABASE),
        Map.entry(DependencyCategories.COSMOS_DB, Shape.DATABASE),
        Map.entry(DependencyCategories.MONGO_DB, Shape.DATABASE),
        Map.entry(DependencyCategories.BIG_QUERY, Shape.DATABASE),
        Map.entry(DependencyCategories.ELASTICSEARCH, Shape.DATABASE),
        Map.entry(DependencyCategories.REDIS, Shape.COLLECTIONS),
        Map.entry(DependencyCategories.MESSAGE_QUEUE, Shape.QUEUE),
        Map.entry(DependencyCategories.SERVICE_BUS, Shape.QUEUE),
        Map.entry(DependencyCategories.BLOB_STORAGE, Shape.DATABASE),
        Map.entry(DependencyCategories.S3, Shape.DATABASE),
        Map.entry(DependencyCategories.HTTP, Shape.PARTICIPANT),
        Map.entry(DependencyCategories.MEDIATR, Shape.PARTICIPANT),
        Map.entry(DependencyCategories.GRPC, Shape.PARTICIPANT));

    private static final Map<String, String> COLORS = Map.ofEntries(
        Map.entry(DependencyCategories.SQL, "#FADBD8"),
        Map.entry(DependencyCategories.DATABASE, "#FADBD8"),
        Map.entry(DependencyCategories.POSTGRESQL, "#FADBD8"),
        Map.entry(DependencyCategories.MYSQL, "#FADBD8"),
        Map.entry(DependencyCategories.COSMOS_DB, "#FADBD8"),
        Map.entry(DependencyCategories.MONGO_DB, "#FADBD8"),
        Map.entry(DependencyCategories.REDIS, "#FCF3CF"),
        Map.entry(DependencyCategories.MESSAGE_QUEUE, "#D6EAF8"),
        Map.entry(DependencyCategories.SERVICE_BUS, "#D6EAF8"),
        Map.entry(DependencyCategories.HTTP, "#D5F5E3"));

    public static Shape shapeFor(String category) {
        if (category == null) {
            return Shape.PARTICIPANT;
        }
        return SHAPES.getOrDefault(category, Shape.PARTICIPANT);
    }

    /** A PlantUML colour token (e.g. {@code "#D5F5E3"}), or {@code null} for the theme default. */
    public static String colorFor(String category) {
        return category == null ? null : COLORS.get(category);
    }
}
