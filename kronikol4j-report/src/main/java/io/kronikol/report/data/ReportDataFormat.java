package io.kronikol.report.data;

import java.util.Locale;
import java.util.Optional;

/** A machine-readable test-run report-data format and its file extension. */
public enum ReportDataFormat {
    JSON("json"),
    XML("xml"),
    YAML("yaml");

    private final String extension;

    ReportDataFormat(String extension) {
        this.extension = extension;
    }

    /** The file extension (e.g. {@code "yaml"}); the report file is {@code TestRunReport.<ext>}. */
    public String extension() {
        return extension;
    }

    /** Serializes the report data in this format (byte-for-byte aligned with .NET). */
    public String serialize(ReportData data) {
        return switch (this) {
            case JSON -> ReportDataSerializer.toJson(data);
            case XML -> ReportDataSerializer.toXml(data);
            case YAML -> ReportDataSerializer.toYaml(data);
        };
    }

    /** Parses a format from its name or extension (case-insensitive), e.g. {@code "xml"} / {@code "yaml"}. */
    public static Optional<ReportDataFormat> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        for (ReportDataFormat f : values()) {
            if (f.name().toLowerCase(Locale.ROOT).equals(t) || f.extension.equals(t)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
