package io.kronikol.report.step;

import io.kronikol.report.model.TabularParameterValue.TabularColumn;
import io.kronikol.report.model.TabularParameterValue.TabularRow;
import java.util.List;

/**
 * Implemented by tabular step-parameter types so {@link StepCollector} can render them as a
 * {@link io.kronikol.report.model.StepParameter.Kind#TABULAR} parameter (columns + rows) automatically.
 * Java port of the .NET {@code ITabularParameterData} (the conventional Java name drops the {@code I}
 * prefix). The {@code TabularInputs<T>}/{@code TabularOutputs<T>} carriers (the Tier-4 TabularAttributes
 * item) implement this.
 */
public interface TabularParameterData {

    /** The column definitions for the table. */
    List<TabularColumn> getColumns();

    /** The data rows for the table. */
    List<TabularRow> getRows();

    /** Whether this is a linked-output table (rows compared against expectations). Default {@code false}. */
    default boolean isLinkedOutput() {
        return false;
    }
}
