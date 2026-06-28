package io.kronikol.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SqlResultSummaryTest {

    /** An insertion-ordered row (column order preserved, like the captured LinkedHashMap). */
    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void rowCountOnly() {
        assertThat(SqlResultSummary.format(1, List.of("A"), SqlResponseDetail.ROW_COUNT_ONLY)).isEqualTo("1 row");
        assertThat(SqlResultSummary.format(3, List.of("A"), SqlResponseDetail.ROW_COUNT_ONLY)).isEqualTo("3 rows");
    }

    @Test
    void rowCountAndColumns() {
        assertThat(SqlResultSummary.format(2, List.of("Name", "Age"), SqlResponseDetail.ROW_COUNT_AND_COLUMNS))
            .isEqualTo("2 rows [Name, Age]");
        assertThat(SqlResultSummary.format(0, List.of(), SqlResponseDetail.ROW_COUNT_AND_COLUMNS))
            .isEqualTo("0 rows");
    }

    @Test
    void columnsTruncatedPastTwenty() {
        List<String> cols = new ArrayList<>();
        IntStream.rangeClosed(1, 25).forEach(i -> cols.add("C" + i));
        String summary = SqlResultSummary.format(1, cols, SqlResponseDetail.ROW_COUNT_AND_COLUMNS);
        assertThat(summary).startsWith("1 row [C1, C2,").contains("C20 ... (+5 more)]");
    }

    @Test
    void fullRowsRendersCompactJsonKeepingNulls() {
        List<Map<String, Object>> rows = List.of(
            row("id", 1, "name", "Ann", "nick", null),
            row("id", 2, "name", "Bob", "nick", "bobby"));
        String json = SqlResultSummary.formatFullRows(2, List.of("id", "name", "nick"), rows, 10);
        // Compact (no whitespace), numbers unquoted, strings quoted, null cells kept, column order preserved.
        assertThat(json).isEqualTo(
            "[{\"id\":1,\"name\":\"Ann\",\"nick\":null},{\"id\":2,\"name\":\"Bob\",\"nick\":\"bobby\"}]");
    }

    @Test
    void fullRowsAppendsMoreRowsTrailerWhenTruncated() {
        List<Map<String, Object>> rows = List.of(row("id", 1), row("id", 2));
        String json = SqlResultSummary.formatFullRows(5, List.of("id"), rows, 2);
        assertThat(json).isEqualTo("[{\"id\":1},{\"id\":2}]\n... (3 more rows not shown)");
    }

    @Test
    void fullRowsWithZeroMaxRowsDegradesToColumnFormat() {
        // .NET forces the column format when maxResponseRows is 0 (no rows captured).
        String summary = SqlResultSummary.formatFullRows(3, List.of("id", "name"), List.of(), 0);
        assertThat(summary).isEqualTo("3 rows [id, name]");
    }

    @Test
    void fullRowsWithNoCapturedRowsFallsBackToRowLabel() {
        assertThat(SqlResultSummary.formatFullRows(0, List.of("id"), List.of(), 10)).isEqualTo("0 rows");
    }

    @Test
    void fullRowsUsesUnsafeRelaxedEscaping() {
        // < > & + and non-ASCII pass through unescaped; quotes/backslashes/control chars are escaped.
        List<Map<String, Object>> rows = List.of(row("v", "a<b>&c+d\"e\\f\tg€"));
        String json = SqlResultSummary.formatFullRows(1, List.of("v"), rows, 10);
        assertThat(json).isEqualTo("[{\"v\":\"a<b>&c+d\\\"e\\\\f\\tg€\"}]");
    }
}
