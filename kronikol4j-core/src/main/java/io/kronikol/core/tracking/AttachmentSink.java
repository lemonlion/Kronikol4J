package io.kronikol.core.tracking;

/**
 * SPI seam letting {@link Track#attachment(String, String)} (in zero-dependency {@code kronikol4j-core})
 * forward a file attachment to the report module's {@code StepCollector} without core depending on report.
 * The report module provides an implementation discovered via {@link java.util.ServiceLoader} (a
 * {@code META-INF/services} entry); tests may also install one explicitly with {@link Track#attachmentSink}.
 *
 * <p>The .NET {@code Track.Attachment} calls {@code StepCollector.AddAttachment} directly (one assembly);
 * Java's module split routes the same call through this seam.
 */
@FunctionalInterface
public interface AttachmentSink {

    /** Attaches {@code filePath} (optionally named {@code name}) to the active step/scenario for {@code testId}. */
    void addAttachment(String testId, String filePath, String name);
}
