package io.kronikol.report.model;

/** A file attached to a scenario or step (mirrors the .NET {@code FileAttachment}). */
public record FileAttachment(String name, String relativePath, String mediaType) {

    /** An attachment whose media type the producer did not declare. */
    public FileAttachment(String name, String relativePath) {
        this(name, relativePath, null);
    }
}
