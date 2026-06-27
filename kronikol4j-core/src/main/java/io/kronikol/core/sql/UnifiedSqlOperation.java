package io.kronikol.core.sql;

/**
 * Classified SQL operation types recognised by {@link UnifiedSqlClassifier}. Java port of the .NET
 * {@code UnifiedSqlOperation} enum.
 *
 * <p>Each constant carries the .NET {@code ToString()} (PascalCase) display name, because the .NET diagram
 * label builder falls back to {@code operation.ToString()} for some levels — preserving that exact string
 * keeps the rendered arrow labels byte-identical.
 */
public enum UnifiedSqlOperation {

    SELECT("Select"),
    INSERT("Insert"),
    UPDATE("Update"),
    DELETE("Delete"),
    MERGE("Merge"),
    UPSERT("Upsert"),
    STORED_PROCEDURE("StoredProcedure"),
    CREATE_TABLE("CreateTable"),
    ALTER_TABLE("AlterTable"),
    DROP_TABLE("DropTable"),
    CREATE_INDEX("CreateIndex"),
    TRUNCATE("Truncate"),
    BEGIN_TRANSACTION("BeginTransaction"),
    COMMIT("Commit"),
    ROLLBACK("Rollback"),
    OPTIMIZE("Optimize"),
    RENAME("Rename"),
    ATTACH("Attach"),
    DETACH("Detach"),
    OTHER("Other");

    private final String displayName;

    UnifiedSqlOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form, used as the diagram-label fallback. */
    public String displayName() {
        return displayName;
    }
}
