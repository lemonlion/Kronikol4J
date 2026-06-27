package io.kronikol.jdbc;

/**
 * Controls how much response detail SQL tracking includes in diagram arrows. Java port of the .NET
 * {@code SqlResponseDetail}.
 */
public enum SqlResponseDetail {
    /** Row count only (e.g. {@code "3 rows"}). */
    ROW_COUNT_ONLY,
    /** Row count + column names (e.g. {@code "3 rows [Name, Preference]"}). */
    ROW_COUNT_AND_COLUMNS,
    /** Full row data up to {@code maxResponseRows} (JSON representation). */
    FULL_ROWS
}
