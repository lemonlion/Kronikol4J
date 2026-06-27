package io.kronikol.report.tabular;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.TrackingDiagramOverride;
import io.kronikol.report.model.TabularParameterValue.TabularCell;
import io.kronikol.report.model.TabularParameterValue.TabularColumn;
import io.kronikol.report.model.TabularParameterValue.TabularRow;
import io.kronikol.report.model.TableRowType;
import io.kronikol.report.model.VerificationStatus;
import io.kronikol.report.step.TabularParameterData;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A read-only list of deserialized input rows for a tabular test. Iterating with {@code for-each} emits a
 * per-row diagram delimiter ({@code Row N}). Implements {@link TabularParameterData} so {@link
 * io.kronikol.report.step.StepCollector} renders it as a tabular step parameter. Java port of the .NET
 * {@code TabularInputs<T>}.
 */
public final class TabularInputs<T> extends AbstractList<T> implements TabularParameterData {

    private final List<T> items;
    private final String[] columnNames;
    private final Class<?> elementType;

    public TabularInputs(List<T> items, String[] columnNames, Class<?> elementType) {
        this.items = List.copyOf(items);
        this.columnNames = columnNames.clone();
        this.elementType = elementType;
    }

    @Override
    public T get(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public Iterator<T> iterator() {
        return new RowDelimitingIterator();
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
        Map<String, Function<Object, String>> readers = TabularDeserializer.readers(elementType, columnNames);
        List<TabularRow> rows = new ArrayList<>(items.size());
        for (T item : items) {
            List<TabularCell> cells = new ArrayList<>(readers.size());
            for (Function<Object, String> reader : readers.values()) {
                cells.add(new TabularCell(reader.apply(item), null, VerificationStatus.NOT_APPLICABLE));
            }
            rows.add(new TabularRow(TableRowType.MATCHING, cells));
        }
        return rows;
    }

    /** Iterates the rows, emitting a {@code Row N} diagram delimiter as each row is reached. */
    private final class RowDelimitingIterator implements Iterator<T> {
        private int index = -1;

        @Override
        public boolean hasNext() {
            return index + 1 < items.size();
        }

        @Override
        public T next() {
            index++;
            TestInfo current = TestIdentityScope.current();
            if (current != null) {
                TrackingDiagramOverride.insertPlantUml(current.id(),
                    "hnote across #lightyellow : Row " + (index + 1));
            }
            return items.get(index);
        }
    }
}
