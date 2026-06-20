package io.kronikol.diagram.plantuml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes PlantUML source for transmission, using PlantUML's scheme: raw DEFLATE followed by a
 * custom base64 alphabet ({@code 0-9 A-Z a-z - _}). Mirrors the .NET {@code PlantUmlTextEncoder}.
 *
 * <p><strong>Parity note (plan §6.4):</strong> raw DEFLATE output is not byte-identical across
 * runtimes, so the encoded string is <em>not</em> pinned against .NET. Correctness is verified by
 * round-trip ({@link #encode} then {@link #decode}); {@link #decode} also lets parity tests decode
 * a .NET-produced string and compare the underlying PlantUML text.
 */
public final class PlantUmlTextEncoder {

    private PlantUmlTextEncoder() {
    }

    public static String encode(String plantUmlText) {
        byte[] deflated = deflate(plantUmlText.getBytes(StandardCharsets.UTF_8));
        return encode64(deflated);
    }

    public static String decode(String encoded) {
        try {
            return new String(inflate(decode64(encoded)), StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Not a valid PlantUML-encoded string", e);
        }
    }

    // --- DEFLATE ---

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true); // nowrap = raw deflate
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 2));
        byte[] buffer = new byte[2048];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] input) throws DataFormatException {
        Inflater inflater = new Inflater(true); // nowrap
        inflater.setInput(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length * 3));
        byte[] buffer = new byte[2048];
        while (!inflater.finished()) {
            int n = inflater.inflate(buffer);
            if (n == 0) {
                // Trailing zero-byte padding from encode64 is ignored once the stream is complete.
                if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()) {
                    break;
                }
            }
            out.write(buffer, 0, n);
        }
        inflater.end();
        return out.toByteArray();
    }

    // --- PlantUML custom base64 ---

    private static String encode64(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length / 3 + 1) * 4);
        for (int i = 0; i < data.length; i += 3) {
            int b1 = data[i] & 0xFF;
            int b2 = (i + 1 < data.length) ? data[i + 1] & 0xFF : 0;
            int b3 = (i + 2 < data.length) ? data[i + 2] & 0xFF : 0;
            append3bytes(sb, b1, b2, b3);
        }
        return sb.toString();
    }

    private static void append3bytes(StringBuilder sb, int b1, int b2, int b3) {
        int c1 = b1 >> 2;
        int c2 = ((b1 & 0x3) << 4) | (b2 >> 4);
        int c3 = ((b2 & 0xF) << 2) | (b3 >> 6);
        int c4 = b3 & 0x3F;
        sb.append(encode6bit(c1 & 0x3F));
        sb.append(encode6bit(c2 & 0x3F));
        sb.append(encode6bit(c3 & 0x3F));
        sb.append(encode6bit(c4 & 0x3F));
    }

    private static char encode6bit(int value) {
        int b = value;
        if (b < 10) {
            return (char) ('0' + b);
        }
        b -= 10;
        if (b < 26) {
            return (char) ('A' + b);
        }
        b -= 26;
        if (b < 26) {
            return (char) ('a' + b);
        }
        b -= 26;
        return b == 0 ? '-' : '_';
    }

    private static byte[] decode64(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() / 4 * 3 + 3);
        for (int i = 0; i < s.length(); i += 4) {
            int c1 = decode6bit(s.charAt(i));
            int c2 = (i + 1 < s.length()) ? decode6bit(s.charAt(i + 1)) : 0;
            int c3 = (i + 2 < s.length()) ? decode6bit(s.charAt(i + 2)) : 0;
            int c4 = (i + 3 < s.length()) ? decode6bit(s.charAt(i + 3)) : 0;
            out.write(((c1 << 2) | (c2 >> 4)) & 0xFF);
            out.write((((c2 & 0xF) << 4) | (c3 >> 2)) & 0xFF);
            out.write((((c3 & 0x3) << 6) | c4) & 0xFF);
        }
        return out.toByteArray();
    }

    private static int decode6bit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 36;
        }
        if (c == '-') {
            return 62;
        }
        if (c == '_') {
            return 63;
        }
        return 0;
    }
}
