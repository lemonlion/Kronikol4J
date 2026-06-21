package io.kronikol.report.flow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip + base64 encoding of diagram/flame payloads (.NET {@code InternalFlowHtmlGenerator.CompressToBase64}).
 * The output is not byte-stable across runtimes (gzip headers/streams differ), so payloads encoded with it
 * — the {@code puml-data} island, {@code data-flame-z} and {@code data-plantuml-z} — are decoded-compared
 * in the parity tests, never byte-compared.
 */
public final class GzipBase64 {

    private GzipBase64() {
    }

    public static String encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
