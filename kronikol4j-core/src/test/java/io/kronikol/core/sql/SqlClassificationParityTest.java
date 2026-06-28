package io.kronikol.core.sql;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cross-runtime byte-parity for the SQL capture logic: the Java {@link UnifiedSqlClassifier} is diffed against
 * the <em>real .NET</em> {@code UnifiedSqlClassifier} output (the {@code sql-classification.txt} fixture was
 * captured by driving the actual .NET classifier in {@code parity-harness/dotnet-capture}). This hardens the
 * capture-side classification/table/URI logic from "unit-proven against the spec" to "byte-proven against
 * .NET's actual output" — the same battery, the same projection ({@code table} / {@code keyword} / DETAILED +
 * SUMMARISED diagram labels), no live database required (pure classification logic).
 */
class SqlClassificationParityTest {

    /** The identical battery the .NET harness drives — drift surfaces as a byte mismatch against the golden. */
    private static final List<String[]> BATTERY = List.of(
        row("SELECT * FROM Orders WHERE id = 1", "TEXT"),
        row("SELECT o.* FROM orders o JOIN customers c ON o.cid = c.id", "TEXT"),
        row("INSERT INTO Orders (id, name) VALUES (1, 'a')", "TEXT"),
        row("INSERT INTO Orders (id) VALUES (1) ON CONFLICT (id) DO UPDATE SET id = 1", "TEXT"),
        row("MERGE INTO Orders USING src ON (Orders.id = src.id) WHEN MATCHED THEN UPDATE SET x = 1", "TEXT"),
        row("UPDATE Orders SET name = 'b' WHERE id = 1", "TEXT"),
        row("DELETE FROM Orders WHERE id = 1", "TEXT"),
        row("CREATE TABLE Orders (id INT)", "TEXT"),
        row("CREATE INDEX ix_orders ON Orders (id)", "TEXT"),
        row("ALTER TABLE Orders ADD COLUMN x INT", "TEXT"),
        row("DROP TABLE Orders", "TEXT"),
        row("TRUNCATE TABLE Orders", "TEXT"),
        row("WITH recent AS (SELECT * FROM Orders) SELECT * FROM recent", "TEXT"),
        row("SELECT * FROM dbo.Orders", "TEXT"),
        row("SELECT * FROM \"Order Items\"", "TEXT"),
        row("SELECT * FROM [Order Items]", "TEXT"),
        row("UPDATE shop.Orders SET x = 1", "TEXT"),
        row("EXEC sp_GetOrders", "TEXT"),
        row("GetOrdersByCustomer", "STORED_PROCEDURE"));

    @Test
    void javaClassifierMatchesDotNetByteForByte() throws IOException {
        String golden = readResource("/parity/sql-classification.txt");
        assertThat(render()).isEqualTo(golden);
    }

    private static String render() {
        StringBuilder sb = new StringBuilder();
        for (String[] r : BATTERY) {
            String sql = r[0];
            SqlCommandType ct = "STORED_PROCEDURE".equals(r[1])
                ? SqlCommandType.STORED_PROCEDURE : SqlCommandType.TEXT;
            UnifiedSqlOperationInfo info = UnifiedSqlClassifier.classify(sql, ct);
            sb.append("in=").append(b64(sql)).append('\n');
            sb.append("ct=").append(r[1]).append('\n');
            sb.append("table=").append(orNull(info.tableName())).append('\n');
            sb.append("keyword=").append(orNull(UnifiedSqlClassifier.getRawKeyword(sql))).append('\n');
            sb.append("detailed=").append(UnifiedSqlClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED))
                .append('\n');
            sb.append("summarised=").append(UnifiedSqlClassifier.getDiagramLabel(info, TrackingVerbosity.SUMMARISED))
                .append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String[] row(String sql, String commandType) {
        return new String[] {sql, commandType};
    }

    private static String orNull(String s) {
        return s == null ? "~null~" : s;
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = SqlClassificationParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
