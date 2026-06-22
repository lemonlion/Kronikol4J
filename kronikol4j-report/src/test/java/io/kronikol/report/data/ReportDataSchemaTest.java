package io.kronikol.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Byte-parity for {@link ReportDataSchema} against the real .NET
 * {@code ReportGenerator.GenerateTestRunReportJsonSchema} (the {@code .schema.json} artifact).
 */
class ReportDataSchemaTest {

    @Test
    void jsonSchema_isByteForByteIdenticalToDotNet() {
        assertThat(ReportDataSchema.jsonSchema()).isEqualTo(readFixture("testrunreport-schema.json"));
    }

    @Test
    void xmlSchema_isByteForByteIdenticalToDotNet() {
        assertThat(ReportDataSchema.xmlSchema()).isEqualTo(readFixture("testrunreport-schema.xsd"));
    }

    private static String readFixture(String name) {
        try (InputStream in = ReportDataSchemaTest.class.getResourceAsStream("/parity/" + name)) {
            assertThat(in).as("fixture /parity/" + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
