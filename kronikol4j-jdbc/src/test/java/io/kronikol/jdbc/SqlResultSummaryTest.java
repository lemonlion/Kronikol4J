package io.kronikol.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SqlResultSummaryTest {

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
}
