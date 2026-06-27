package io.kronikol.report.tabular;

import io.kronikol.report.model.TabularParameterValue.TabularCell;
import io.kronikol.report.model.TabularParameterValue.TabularColumn;
import io.kronikol.report.model.TabularParameterValue.TabularRow;
import io.kronikol.report.model.TableRowType;
import io.kronikol.report.model.VerificationStatus;
import io.kronikol.report.step.TabularParameterData;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A read-only list of expected output rows for a tabular test. Call {@link #recordActualResult} per actual,
 * then {@link #verify()} to compare expected vs actual position-by-position (or rely on {@link #close()} to
 * auto-verify). Implements {@link TabularParameterData} (a linked-output table) for step reporting. Java port
 * of the .NET {@code TabularOutputs<T>}.
 */
public final class TabularOutputs<T> extends AbstractList<T> implements TabularParameterData, AutoCloseable {

    private final List<T> expected;
    private final String[] columnNames;
    private final Class<?> elementType;
    private final List<T> actuals = new ArrayList<>();
    private List<TabularRow> verifiedRows;
    private boolean verified;

    public TabularOutputs(List<T> expected, String[] columnNames, Class<?> elementType) {
        this.expected = List.copyOf(expected);
        this.columnNames = columnNames.clone();
        this.elementType = elementType;
    }

    @Override
    public T get(int index) {
        return expected.get(index);
    }

    @Override
    public int size() {
        return expected.size();
    }

    /** Records an actual output value for position-based verification. */
    public void recordActualResult(T actual) {
        actuals.add(actual);
    }

    /** Compares expected vs actual rows position-by-position; throws on mismatch/surplus/missing rows. */
    public void verify() {
        Map<String, Function<Object, String>> readers = TabularDeserializer.readers(elementType, columnNames);
        List<TabularRow> results = new ArrayList<>();
        int maxCount = Math.max(expected.size(), actuals.size());
        for (int i = 0; i < maxCount; i++) {
            if (i < expected.size() && i < actuals.size()) {
                List<TabularCell> cells = new ArrayList<>(readers.size());
                for (Function<Object, String> reader : readers.values()) {
                    String exp = reader.apply(expected.get(i));
                    String act = reader.apply(actuals.get(i));
                    VerificationStatus status = exp.equals(act)
                        ? VerificationStatus.SUCCESS : VerificationStatus.FAILURE;
                    cells.add(new TabularCell(act, exp, status));
                }
                results.add(new TabularRow(TableRowType.MATCHING, cells));
            } else if (i < actuals.size()) {
                List<TabularCell> cells = new ArrayList<>(readers.size());
                for (Function<Object, String> reader : readers.values()) {
                    cells.add(new TabularCell(reader.apply(actuals.get(i)), null, VerificationStatus.FAILURE));
                }
                results.add(new TabularRow(TableRowType.SURPLUS, cells));
            } else {
                List<TabularCell> cells = new ArrayList<>(readers.size());
                for (Function<Object, String> reader : readers.values()) {
                    cells.add(new TabularCell("", reader.apply(expected.get(i)), VerificationStatus.FAILURE));
                }
                results.add(new TabularRow(TableRowType.MISSING, cells));
            }
        }

        verifiedRows = results;
        verified = true;

        List<TabularRow> failures = new ArrayList<>();
        for (TabularRow row : results) {
            boolean cellFailed = row.values().stream().anyMatch(c -> c.status() == VerificationStatus.FAILURE);
            if (row.type() != TableRowType.MATCHING || cellFailed) {
                failures.add(row);
            }
        }
        if (!failures.isEmpty()) {
            throw new TabularVerificationException(buildFailureMessage(failures));
        }
    }

    /** Auto-verifies if actuals were recorded and {@link #verify()} was not already called. */
    @Override
    public void close() {
        if (!verified && !actuals.isEmpty()) {
            verify();
        }
    }

    @Override
    public boolean isLinkedOutput() {
        return true;
    }

    @Override
    public List<TabularColumn> getColumns() {
        List<TabularColumn> columns = new ArrayList<>(columnNames.length);
        for (String name : columnNames) {
            columns.add(new TabularColumn(name, false));
        }
        return columns;
    }

    @Override
    public List<TabularRow> getRows() {
        if (verifiedRows != null) {
            return verifiedRows;
        }
        Map<String, Function<Object, String>> readers = TabularDeserializer.readers(elementType, columnNames);
        List<TabularRow> rows = new ArrayList<>(expected.size());
        for (T item : expected) {
            List<TabularCell> cells = new ArrayList<>(readers.size());
            for (Function<Object, String> reader : readers.values()) {
                cells.add(new TabularCell(reader.apply(item), null, VerificationStatus.NOT_PROVIDED));
            }
            rows.add(new TabularRow(TableRowType.MATCHING, cells));
        }
        return rows;
    }

    private static String buildFailureMessage(List<TabularRow> failures) {
        StringBuilder sb = new StringBuilder("Tabular output verification failed:\n");
        for (TabularRow failure : failures) {
            sb.append("  ").append(failure.type()).append(":\n");
            for (TabularCell cell : failure.values()) {
                if (cell.status() == VerificationStatus.FAILURE) {
                    sb.append("    Expected: ").append(cell.expectation() == null ? "<none>" : cell.expectation())
                        .append(", Actual: ").append(cell.value()).append('\n');
                }
            }
        }
        return sb.toString().stripTrailing();
    }
}
