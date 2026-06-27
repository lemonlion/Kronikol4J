package io.kronikol.core.sql;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/**
 * Ports the behaviour of .NET {@code UnifiedSqlClassifier} — the SQL classifier shared across all database
 * tracking extensions (SQL Server, PostgreSQL, MySQL, SQLite, Oracle, Spanner, ClickHouse). Pure logic, so
 * a unit test is the proof; a golden lands when JDBC wires the classifier into rendered output (Tier 1).
 */
class UnifiedSqlClassifierTest {

    private static UnifiedSqlOperationInfo classify(String sql) {
        return UnifiedSqlClassifier.classify(sql, SqlCommandType.TEXT);
    }

    @Test
    void selectExtractsTableAfterFrom() {
        UnifiedSqlOperationInfo op = classify("SELECT id, name FROM Customers WHERE id = 1");
        assertThat(op.operation()).isEqualTo(UnifiedSqlOperation.SELECT);
        assertThat(op.tableName()).isEqualTo("Customers");
    }

    @Test
    void insertExtractsTableAfterInto() {
        UnifiedSqlOperationInfo op = classify("INSERT INTO Orders (id) VALUES (1)");
        assertThat(op.operation()).isEqualTo(UnifiedSqlOperation.INSERT);
        assertThat(op.tableName()).isEqualTo("Orders");
    }

    @Test
    void updateAndDeleteAndMerge() {
        assertThat(classify("UPDATE Users SET x=1").operation()).isEqualTo(UnifiedSqlOperation.UPDATE);
        assertThat(classify("UPDATE Users SET x=1").tableName()).isEqualTo("Users");
        assertThat(classify("DELETE FROM Logs WHERE x=1").operation()).isEqualTo(UnifiedSqlOperation.DELETE);
        assertThat(classify("DELETE FROM Logs WHERE x=1").tableName()).isEqualTo("Logs");
        assertThat(classify("DELETE Logs WHERE x=1").tableName()).isEqualTo("Logs"); // optional FROM
        assertThat(classify("MERGE INTO Target USING src ON ...").operation()).isEqualTo(UnifiedSqlOperation.MERGE);
        assertThat(classify("MERGE Target USING src").tableName()).isEqualTo("Target");
    }

    @Test
    void upsertVariants() {
        // PostgreSQL / SQLite / Spanner
        assertThat(classify("INSERT INTO t (a) VALUES (1) ON CONFLICT (a) DO UPDATE SET a=1").operation())
            .isEqualTo(UnifiedSqlOperation.UPSERT);
        // MySQL
        assertThat(classify("INSERT INTO t (a) VALUES (1) ON DUPLICATE KEY UPDATE a=1").operation())
            .isEqualTo(UnifiedSqlOperation.UPSERT);
        // SQLite INSERT OR REPLACE / UPDATE => upsert; INSERT OR IGNORE => plain insert
        assertThat(classify("INSERT OR REPLACE INTO t (a) VALUES (1)").operation())
            .isEqualTo(UnifiedSqlOperation.UPSERT);
        assertThat(classify("INSERT OR IGNORE INTO t (a) VALUES (1)").operation())
            .isEqualTo(UnifiedSqlOperation.INSERT);
        assertThat(classify("INSERT OR REPLACE INTO t (a) VALUES (1)").tableName()).isEqualTo("t");
    }

    @Test
    void storedProcedureViaExecCallAndCommandType() {
        assertThat(classify("EXEC dbo.GetUsers @id=1").operation()).isEqualTo(UnifiedSqlOperation.STORED_PROCEDURE);
        assertThat(classify("EXEC dbo.GetUsers @id=1").tableName()).isEqualTo("GetUsers");
        assertThat(classify("CALL update_stats()").operation()).isEqualTo(UnifiedSqlOperation.STORED_PROCEDURE);
        assertThat(classify("CALL update_stats()").tableName()).isEqualTo("update_stats");

        UnifiedSqlOperationInfo proc = UnifiedSqlClassifier.classify("dbo.MyProc", SqlCommandType.STORED_PROCEDURE);
        assertThat(proc.operation()).isEqualTo(UnifiedSqlOperation.STORED_PROCEDURE);
        assertThat(proc.tableName()).isEqualTo("MyProc"); // last identifier part
    }

    @Test
    void ddlCreateAlterDropAndIndex() {
        assertThat(classify("CREATE TABLE Foo (id int)").operation()).isEqualTo(UnifiedSqlOperation.CREATE_TABLE);
        assertThat(classify("CREATE TABLE Foo (id int)").tableName()).isEqualTo("Foo");
        assertThat(classify("DROP TABLE Foo").operation()).isEqualTo(UnifiedSqlOperation.DROP_TABLE);
        assertThat(classify("ALTER TABLE Foo ADD COLUMN x int").operation()).isEqualTo(UnifiedSqlOperation.ALTER_TABLE);
        assertThat(classify("CREATE INDEX ix ON Foo (x)").operation()).isEqualTo(UnifiedSqlOperation.CREATE_INDEX);
        assertThat(classify("CREATE UNIQUE INDEX ix ON Foo (x)").operation()).isEqualTo(UnifiedSqlOperation.CREATE_INDEX);
    }

    @Test
    void transactionControl() {
        assertThat(classify("BEGIN TRANSACTION").operation()).isEqualTo(UnifiedSqlOperation.BEGIN_TRANSACTION);
        assertThat(classify("COMMIT").operation()).isEqualTo(UnifiedSqlOperation.COMMIT);
        assertThat(classify("ROLLBACK").operation()).isEqualTo(UnifiedSqlOperation.ROLLBACK);
    }

    @Test
    void clickHouseExtensions() {
        // OPTIMIZE / RENAME / ATTACH / DETACH / TRUNCATE
        assertThat(classify("OPTIMIZE TABLE events").operation()).isEqualTo(UnifiedSqlOperation.OPTIMIZE);
        assertThat(classify("OPTIMIZE TABLE events").tableName()).isEqualTo("events");
        assertThat(classify("RENAME TABLE a TO b").operation()).isEqualTo(UnifiedSqlOperation.RENAME);
        assertThat(classify("RENAME TABLE a TO b").tableName()).isEqualTo("a");
        assertThat(classify("ATTACH TABLE t").operation()).isEqualTo(UnifiedSqlOperation.ATTACH);
        assertThat(classify("DETACH TABLE t").operation()).isEqualTo(UnifiedSqlOperation.DETACH);
        assertThat(classify("TRUNCATE TABLE t").operation()).isEqualTo(UnifiedSqlOperation.TRUNCATE);
        // ClickHouse lightweight mutations: ALTER TABLE ... UPDATE/DELETE behave like DML
        assertThat(classify("ALTER TABLE metrics UPDATE v = 1 WHERE id = 2").operation())
            .isEqualTo(UnifiedSqlOperation.UPDATE);
        assertThat(classify("ALTER TABLE metrics UPDATE v = 1 WHERE id = 2").tableName()).isEqualTo("metrics");
        assertThat(classify("ALTER TABLE metrics DELETE WHERE id = 2").operation())
            .isEqualTo(UnifiedSqlOperation.DELETE);
    }

    @Test
    void prefixStripping() {
        // SET prefix before real DML
        assertThat(classify("SET NOCOUNT ON;\nSELECT * FROM T").operation()).isEqualTo(UnifiedSqlOperation.SELECT);
        assertThat(classify("SET NOCOUNT ON;\nSELECT * FROM T").tableName()).isEqualTo("T");
        // CTE prefix
        assertThat(classify("WITH cte AS (SELECT 1) SELECT * FROM Accounts").operation())
            .isEqualTo(UnifiedSqlOperation.SELECT);
        assertThat(classify("WITH cte AS (SELECT 1) SELECT * FROM Accounts").tableName()).isEqualTo("Accounts");
        // Spanner statement hint
        assertThat(classify("@{PDML_MAX_PARALLELISM=10}UPDATE T SET x=1").operation())
            .isEqualTo(UnifiedSqlOperation.UPDATE);
    }

    @Test
    void quotedAndSchemaQualifiedIdentifiers() {
        assertThat(classify("SELECT * FROM [dbo].[Customers]").tableName()).isEqualTo("Customers");
        assertThat(classify("SELECT * FROM `mydb`.`orders`").tableName()).isEqualTo("orders");
        assertThat(classify("SELECT * FROM \"public\".\"users\"").tableName()).isEqualTo("users");
    }

    @Test
    void emptyAndUnknownAndNull() {
        assertThat(classify("").operation()).isEqualTo(UnifiedSqlOperation.OTHER);
        assertThat(classify("   ").operation()).isEqualTo(UnifiedSqlOperation.OTHER);
        assertThat(UnifiedSqlClassifier.classify(null, SqlCommandType.TEXT).operation())
            .isEqualTo(UnifiedSqlOperation.OTHER);
        assertThat(classify("VACUUM").operation()).isEqualTo(UnifiedSqlOperation.OTHER);
    }

    @Test
    void getRawKeyword() {
        assertThat(UnifiedSqlClassifier.getRawKeyword("select * from t")).isEqualTo("SELECT");
        assertThat(UnifiedSqlClassifier.getRawKeyword("SET x=1;\nUPDATE t SET a=1")).isEqualTo("UPDATE");
        assertThat(UnifiedSqlClassifier.getRawKeyword("   ")).isNull();
        assertThat(UnifiedSqlClassifier.getRawKeyword("VACUUM")).isNull();
    }

    @Test
    void extractProcName() {
        assertThat(UnifiedSqlClassifier.extractProcName("EXEC dbo.GetUsers @id=1")).isEqualTo("dbo.GetUsers");
        assertThat(UnifiedSqlClassifier.extractProcName("CALL update_stats()")).isEqualTo("update_stats");
        assertThat(UnifiedSqlClassifier.extractProcName("EXECUTE proc")).isEqualTo("proc");
        assertThat(UnifiedSqlClassifier.extractProcName(null)).isEqualTo("?");
    }

    @Test
    void diagramLabelRaw() {
        UnifiedSqlOperationInfo op = classify("SELECT * FROM T");
        assertThat(UnifiedSqlClassifier.getDiagramLabel(op, TrackingVerbosity.RAW)).isEqualTo("SELECT * FROM T");
    }

    @Test
    void diagramLabelDetailed() {
        assertThat(UnifiedSqlClassifier.getDiagramLabel(classify("SELECT * FROM Customers"), TrackingVerbosity.DETAILED))
            .isEqualTo("SELECT FROM Customers");
        assertThat(UnifiedSqlClassifier.getDiagramLabel(classify("INSERT INTO Orders VALUES (1)"), TrackingVerbosity.DETAILED))
            .isEqualTo("INSERT INTO Orders");
        assertThat(UnifiedSqlClassifier.getDiagramLabel(classify("EXEC dbo.GetUsers"), TrackingVerbosity.DETAILED))
            .isEqualTo("EXEC dbo.GetUsers");
        // BeginTransaction falls through to the operation display name in Detailed
        assertThat(UnifiedSqlClassifier.getDiagramLabel(classify("BEGIN TRANSACTION"), TrackingVerbosity.DETAILED))
            .isEqualTo("BeginTransaction");
    }

    @Test
    void diagramLabelSummarised() {
        assertThat(UnifiedSqlClassifier.getDiagramLabel(classify("SELECT * FROM Customers"), TrackingVerbosity.SUMMARISED))
            .isEqualTo("SELECT");
        assertThat(UnifiedSqlClassifier.getDiagramLabel(classify("CREATE TABLE Foo (x int)"), TrackingVerbosity.SUMMARISED))
            .isEqualTo("CREATE TABLE");
        assertThat(UnifiedSqlClassifier.getDiagramLabel(classify("COMMIT"), TrackingVerbosity.SUMMARISED))
            .isEqualTo("COMMIT");
    }
}
