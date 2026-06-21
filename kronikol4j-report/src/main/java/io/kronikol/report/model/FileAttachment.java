package io.kronikol.report.model;

/** A file attached to a scenario or step (mirrors the .NET {@code FileAttachment}). */
public record FileAttachment(String name, String relativePath) {
}
