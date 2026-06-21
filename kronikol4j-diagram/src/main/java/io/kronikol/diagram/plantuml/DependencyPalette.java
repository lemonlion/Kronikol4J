package io.kronikol.diagram.plantuml;

import java.util.Locale;
import java.util.Map;

/**
 * Maps a dependency category to a participant shape and arrow colour, mirroring the .NET
 * {@code DependencyPalette} <strong>exactly</strong> (category → {@link DependencyType} → shape/colour),
 * including: a {@code null}/empty category resolves to {@link DependencyType#HTTP_API} (the core HTTP
 * handler sets no category), an unknown category resolves to {@link DependencyType#UNKNOWN}, and
 * case-insensitive category lookup. Verified by the golden-file parity tests.
 */
public final class DependencyPalette {

    private DependencyPalette() {
    }

    /** Dependency classifications (mirror of the .NET {@code DependencyType}). */
    public enum DependencyType {
        HTTP_API("entity", "#438DD5"),
        DATABASE("database", "#E74C3C"),
        CACHE("collections", "#F39C12"),
        MESSAGE_QUEUE("queue", "#9B59B6"),
        STORAGE("database", "#2ECC71"),
        UNKNOWN("participant", "#95A5A6");

        private final String shape;
        private final String color;

        DependencyType(String shape, String color) {
            this.shape = shape;
            this.color = color;
        }

        public String shape() {
            return shape;
        }

        public String color() {
            return color;
        }
    }

    // Category (lower-cased for case-insensitive lookup) -> type. Mirrors .NET CategoryToType.
    private static final Map<String, DependencyType> CATEGORY_TO_TYPE = Map.ofEntries(
        Map.entry("cosmosdb", DependencyType.DATABASE),
        Map.entry("sql", DependencyType.DATABASE),
        Map.entry("bigquery", DependencyType.DATABASE),
        Map.entry("redis", DependencyType.CACHE),
        Map.entry("servicebus", DependencyType.MESSAGE_QUEUE),
        Map.entry("blobstorage", DependencyType.STORAGE),
        Map.entry("http", DependencyType.HTTP_API),
        Map.entry("mediatr", DependencyType.HTTP_API),
        Map.entry("messagequeue", DependencyType.MESSAGE_QUEUE),
        Map.entry("mongodb", DependencyType.DATABASE),
        Map.entry("dynamodb", DependencyType.DATABASE),
        Map.entry("elasticsearch", DependencyType.DATABASE),
        Map.entry("spanner", DependencyType.DATABASE),
        Map.entry("bigtable", DependencyType.DATABASE),
        Map.entry("database", DependencyType.DATABASE),
        Map.entry("s3", DependencyType.STORAGE),
        Map.entry("cloudstorage", DependencyType.STORAGE),
        Map.entry("grpc", DependencyType.HTTP_API),
        Map.entry("postgresql", DependencyType.DATABASE),
        Map.entry("sqlserver", DependencyType.DATABASE),
        Map.entry("mysql", DependencyType.DATABASE),
        Map.entry("sqlite", DependencyType.DATABASE),
        Map.entry("oracle", DependencyType.DATABASE),
        Map.entry("clickhouse", DependencyType.DATABASE),
        // Java-native addition (no .NET counterpart): the Cassandra tracker renders as a database.
        Map.entry("cassandra", DependencyType.DATABASE));

    /** Resolves a category to its type. {@code null}/empty → {@code HTTP_API}; unknown → {@code UNKNOWN}. */
    public static DependencyType resolve(String category) {
        if (category == null || category.isEmpty()) {
            return DependencyType.HTTP_API;
        }
        return CATEGORY_TO_TYPE.getOrDefault(category.toLowerCase(Locale.ROOT), DependencyType.UNKNOWN);
    }

    /** The PlantUML participant shape keyword for a category. */
    public static String shapeFor(String category) {
        return resolve(category).shape();
    }

    /** The arrow/participant hex colour for a category (e.g. {@code "#438DD5"}). */
    public static String colorFor(String category) {
        return resolve(category).color();
    }
}
