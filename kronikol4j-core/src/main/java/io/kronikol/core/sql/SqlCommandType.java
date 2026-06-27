package io.kronikol.core.sql;

/**
 * Whether a SQL command is free text or a stored-procedure name. Java analog of
 * {@code System.Data.CommandType} as consumed by {@link UnifiedSqlClassifier#classify}.
 */
public enum SqlCommandType {
    /** The command is SQL text to be parsed/classified. */
    TEXT,
    /** The command is a stored-procedure name; the last identifier part is the proc name. */
    STORED_PROCEDURE
}
