package io.kronikol.report.step;

import io.kronikol.core.tracking.AttachmentSink;

/**
 * The report module's {@link AttachmentSink} provider, discovered by {@code Track} via
 * {@link java.util.ServiceLoader} (see {@code META-INF/services/io.kronikol.core.tracking.AttachmentSink}).
 * Routes {@code Track.attachment(...)} to {@link StepCollector#addAttachment(String, String, String)} — the
 * Java analog of the .NET {@code Track.Attachment → StepCollector.AddAttachment} call, across the
 * core→report module boundary. Must keep the public no-arg constructor {@code ServiceLoader} requires.
 */
public final class StepCollectorAttachmentSink implements AttachmentSink {

    @Override
    public void addAttachment(String testId, String filePath, String name) {
        StepCollector.addAttachment(testId, filePath, name);
    }
}
