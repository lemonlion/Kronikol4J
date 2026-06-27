package io.kronikol.core.sql;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified SQL operation classifier shared across all database tracking extensions (SQL Server,
 * PostgreSQL, MySQL, SQLite, Oracle, Spanner, ClickHouse). Java port of the .NET {@code UnifiedSqlClassifier}.
 *
 * <p>Lives in {@code kronikol4j-core} (zero runtime deps) rather than in any single DB adapter, because
 * JDBC, ClickHouse, Spanner and Bigtable all share it — matching the .NET design where it is "shared across
 * all database tracking extensions". Per-adapter wiring (building a {@code RequestResponseLog} from the
 * classified result) lands with each adapter.
 */
public final class UnifiedSqlClassifier {

    private UnifiedSqlClassifier() {
    }

    // Optional bracket/quote/backtick around an identifier part, and the identifier char class.
    private static final String Q = "[\\[\\]\"`]?";
    private static final String IDENT = "[\\w=+/]+";
    // Optional schema qualifiers + the captured table name: (?:q ident q .)* q (?<table> ident) q
    private static final String TABLE = "(?:" + Q + IDENT + Q + "\\.)*" + Q + "(?<table>" + IDENT + ")" + Q;

    private static final int CI = Pattern.CASE_INSENSITIVE;

    // Strips Spanner statement hints like @{PDML_MAX_PARALLELISM=10}
    private static final Pattern SPANNER_HINT = Pattern.compile("^\\s*@\\{[^}]*\\}\\s*");
    // Strips SET statements before real DML (e.g. SET NOCOUNT ON;\n)
    private static final Pattern SET_PREFIX = Pattern.compile("^\\s*SET\\s[^;]*;\\s*", CI);
    // Strips CTEs: WITH name AS (...) before the real DML
    private static final Pattern CTE_PREFIX =
        Pattern.compile("^\\s*WITH\\s+.+?\\)\\s+(?=SELECT|INSERT|UPDATE|DELETE|MERGE)", CI | Pattern.DOTALL);
    // First keyword after prefix stripping
    private static final Pattern FIRST_KEYWORD = Pattern.compile(
        "^\\s*(?<keyword>SELECT|INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE|CALL|EXPLAIN|COPY|LOAD|BULK|TRUNCATE"
            + "|BEGIN|COMMIT|ROLLBACK|CREATE|ALTER|DROP|OPTIMIZE|RENAME|ATTACH|DETACH)\\b", CI);

    // ClickHouse lightweight mutations: ALTER TABLE [db.]t [ON CLUSTER c] UPDATE|DELETE ...
    private static final Pattern ALTER_MUTATION = Pattern.compile(
        "^\\s*ALTER\\s+TABLE\\s+" + TABLE + "(?:\\s+ON\\s+CLUSTER\\s+\\S+)?\\s+(?<mutation>UPDATE|DELETE)\\b", CI);
    private static final Pattern OPTIMIZE_TABLE = Pattern.compile("^\\s*OPTIMIZE\\s+TABLE\\s+" + TABLE, CI);
    private static final Pattern RENAME_TABLE =
        Pattern.compile("^\\s*RENAME\\s+(?:TABLE|DICTIONARY|DATABASE)\\s+" + TABLE, CI);
    private static final Pattern ATTACH_DETACH = Pattern.compile(
        "^\\s*(?:ATTACH|DETACH)\\s+(?:TABLE|DATABASE|DICTIONARY|VIEW|PERMANENTLY\\s+TABLE)\\s+"
            + "(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?" + TABLE, CI);

    private static final Pattern INSERT_OR_MODIFIER =
        Pattern.compile("^\\s*INSERT\\s+OR\\s+(?<modifier>UPDATE|REPLACE|IGNORE)\\s+(?:INTO\\s+)?", CI);
    private static final Pattern ON_CONFLICT_DO_UPDATE =
        Pattern.compile("\\bON\\s+CONFLICT\\b.*?\\bDO\\s+UPDATE\\b", CI | Pattern.DOTALL);
    private static final Pattern ON_DUPLICATE_KEY_UPDATE =
        Pattern.compile("\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\b", CI);

    private static final Pattern FROM_TABLE = Pattern.compile("\\bFROM\\s+" + TABLE, CI);
    private static final Pattern INTO_TABLE = Pattern.compile("\\bINTO\\s+" + TABLE, CI);
    private static final Pattern UPDATE_TABLE = Pattern.compile("^\\s*UPDATE\\s+" + TABLE, CI);
    private static final Pattern DELETE_TABLE = Pattern.compile("^\\s*DELETE\\s+(?:FROM\\s+)?" + TABLE, CI);
    private static final Pattern MERGE_TABLE = Pattern.compile("^\\s*MERGE\\s+(?:INTO\\s+)?" + TABLE, CI);
    private static final Pattern EXEC_PROC = Pattern.compile("^\\s*(?:EXEC|EXECUTE)\\s+" + TABLE, CI);
    private static final Pattern CALL_PROC = Pattern.compile("^\\s*CALL\\s+" + TABLE, CI);
    private static final Pattern DDL_TABLE = Pattern.compile(
        "^\\s*(?:CREATE|ALTER|DROP)\\s+(?:TABLE|INDEX)\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?" + TABLE, CI);
    private static final Pattern TRUNCATE_TABLE = Pattern.compile("^\\s*TRUNCATE\\s+TABLE\\s+" + TABLE, CI);
    private static final Pattern INSERT_OR_TABLE = Pattern.compile("^\\s*" + TABLE, CI);

    /** Classifies a SQL command into an operation + detected table/proc name. */
    public static UnifiedSqlOperationInfo classify(String commandText, SqlCommandType commandType) {
        if (commandType == SqlCommandType.STORED_PROCEDURE) {
            return new UnifiedSqlOperationInfo(
                UnifiedSqlOperation.STORED_PROCEDURE, extractLastIdentifierPart(commandText), commandText);
        }

        if (isBlank(commandText)) {
            return new UnifiedSqlOperationInfo(UnifiedSqlOperation.OTHER, null, commandText);
        }

        String sql = commandText;
        sql = SPANNER_HINT.matcher(sql).replaceAll("");
        sql = SET_PREFIX.matcher(sql).replaceAll("");
        sql = CTE_PREFIX.matcher(sql).replaceAll("");

        Matcher keywordMatch = FIRST_KEYWORD.matcher(sql);
        if (!keywordMatch.find()) {
            return new UnifiedSqlOperationInfo(UnifiedSqlOperation.OTHER, null, commandText);
        }

        String keyword = keywordMatch.group("keyword").toUpperCase(Locale.ROOT);
        return switch (keyword) {
            case "SELECT" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.SELECT, extractSelectTable(sql), commandText);
            case "INSERT" -> classifyInsert(sql, commandText);
            case "UPDATE" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.UPDATE, extract(UPDATE_TABLE, sql), commandText);
            case "DELETE" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.DELETE, extract(DELETE_TABLE, sql), commandText);
            case "MERGE" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.MERGE, extract(MERGE_TABLE, sql), commandText);
            case "EXEC", "EXECUTE" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.STORED_PROCEDURE, extract(EXEC_PROC, sql), commandText);
            case "CALL" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.STORED_PROCEDURE, extract(CALL_PROC, sql), commandText);
            case "CREATE" -> classifyDdl(sql, commandText, UnifiedSqlOperation.CREATE_TABLE);
            case "ALTER" -> classifyAlter(sql, commandText);
            case "DROP" -> classifyDdl(sql, commandText, UnifiedSqlOperation.DROP_TABLE);
            case "OPTIMIZE" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.OPTIMIZE, extract(OPTIMIZE_TABLE, sql), commandText);
            case "RENAME" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.RENAME, extract(RENAME_TABLE, sql), commandText);
            case "ATTACH" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.ATTACH, extract(ATTACH_DETACH, sql), commandText);
            case "DETACH" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.DETACH, extract(ATTACH_DETACH, sql), commandText);
            case "TRUNCATE" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.TRUNCATE, extract(TRUNCATE_TABLE, sql), commandText);
            case "BEGIN" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.BEGIN_TRANSACTION, null, commandText);
            case "COMMIT" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.COMMIT, null, commandText);
            case "ROLLBACK" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.ROLLBACK, null, commandText);
            default -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.OTHER, null, commandText);
        };
    }

    /** Returns a diagram arrow label for the operation at the given verbosity. */
    public static String getDiagramLabel(UnifiedSqlOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.commandText() != null ? op.commandText() : op.operation().displayName();
            case DETAILED -> switch (op.operation()) {
                case SELECT -> "SELECT FROM " + tableOrPlaceholder(op);
                case INSERT -> "INSERT INTO " + tableOrPlaceholder(op);
                case UPDATE -> "UPDATE " + tableOrPlaceholder(op);
                case DELETE -> "DELETE FROM " + tableOrPlaceholder(op);
                case UPSERT -> "UPSERT " + tableOrPlaceholder(op);
                case MERGE -> "MERGE " + tableOrPlaceholder(op);
                case STORED_PROCEDURE -> "EXEC " + extractProcName(op.commandText());
                case CREATE_TABLE -> "CREATE TABLE " + tableOrPlaceholder(op);
                case ALTER_TABLE -> "ALTER TABLE " + tableOrPlaceholder(op);
                case DROP_TABLE -> "DROP TABLE " + tableOrPlaceholder(op);
                case CREATE_INDEX -> "CREATE INDEX " + tableOrPlaceholder(op);
                case TRUNCATE -> "TRUNCATE " + tableOrPlaceholder(op);
                case OPTIMIZE -> "OPTIMIZE " + tableOrPlaceholder(op);
                case RENAME -> "RENAME " + tableOrPlaceholder(op);
                case ATTACH -> "ATTACH " + tableOrPlaceholder(op);
                case DETACH -> "DETACH " + tableOrPlaceholder(op);
                default -> op.operation().displayName();
            };
            case SUMMARISED -> switch (op.operation()) {
                case SELECT -> "SELECT";
                case INSERT -> "INSERT";
                case UPDATE -> "UPDATE";
                case DELETE -> "DELETE";
                case UPSERT -> "UPSERT";
                case MERGE -> "MERGE";
                case STORED_PROCEDURE -> "EXEC";
                case CREATE_TABLE -> "CREATE TABLE";
                case ALTER_TABLE -> "ALTER TABLE";
                case DROP_TABLE -> "DROP TABLE";
                case CREATE_INDEX -> "CREATE INDEX";
                case TRUNCATE -> "TRUNCATE";
                case OPTIMIZE -> "OPTIMIZE";
                case RENAME -> "RENAME";
                case ATTACH -> "ATTACH";
                case DETACH -> "DETACH";
                case BEGIN_TRANSACTION -> "BEGIN";
                case COMMIT -> "COMMIT";
                case ROLLBACK -> "ROLLBACK";
                default -> op.operation().displayName();
            };
        };
    }

    /** The raw leading SQL keyword (upper-cased), after prefix stripping, or null. */
    public static String getRawKeyword(String commandText) {
        if (isBlank(commandText)) {
            return null;
        }
        String sql = commandText;
        sql = SPANNER_HINT.matcher(sql).replaceAll("");
        sql = SET_PREFIX.matcher(sql).replaceAll("");
        sql = CTE_PREFIX.matcher(sql).replaceAll("");

        Matcher match = FIRST_KEYWORD.matcher(sql);
        return match.find() ? match.group("keyword").toUpperCase(Locale.ROOT) : null;
    }

    /** Extracts the procedure name from an EXEC/EXECUTE/CALL command (else the trimmed text, or "?"). */
    public static String extractProcName(String commandText) {
        if (commandText == null) {
            return "?";
        }
        String trimmed = commandText.stripLeading();
        if (startsWithIgnoreCase(trimmed, "EXEC ")) {
            trimmed = trimmed.substring(5).stripLeading();
        } else if (startsWithIgnoreCase(trimmed, "EXECUTE ")) {
            trimmed = trimmed.substring(8).stripLeading();
        } else if (startsWithIgnoreCase(trimmed, "CALL ")) {
            trimmed = trimmed.substring(5).stripLeading();
        }

        int spaceIdx = trimmed.indexOf(' ');
        int parenIdx = trimmed.indexOf('(');
        int endIdx;
        if (spaceIdx > 0 && parenIdx > 0) {
            endIdx = Math.min(spaceIdx, parenIdx);
        } else if (spaceIdx > 0) {
            endIdx = spaceIdx;
        } else if (parenIdx > 0) {
            endIdx = parenIdx;
        } else {
            endIdx = -1;
        }
        return endIdx > 0 ? trimmed.substring(0, endIdx) : trimmed;
    }

    private static UnifiedSqlOperationInfo classifyInsert(String sql, String originalText) {
        Matcher orMatch = INSERT_OR_MODIFIER.matcher(sql);
        if (orMatch.find()) {
            String modifier = orMatch.group("modifier").toUpperCase(Locale.ROOT);
            String tableName = firstNonNull(extract(INTO_TABLE, sql), extractTableAfterInsertOr(sql));
            return switch (modifier) {
                case "UPDATE", "REPLACE" -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.UPSERT, tableName, originalText);
                default -> new UnifiedSqlOperationInfo(UnifiedSqlOperation.INSERT, tableName, originalText); // IGNORE
            };
        }

        String table = extract(INTO_TABLE, sql);
        if (ON_CONFLICT_DO_UPDATE.matcher(sql).find() || ON_DUPLICATE_KEY_UPDATE.matcher(sql).find()) {
            return new UnifiedSqlOperationInfo(UnifiedSqlOperation.UPSERT, table, originalText);
        }
        return new UnifiedSqlOperationInfo(UnifiedSqlOperation.INSERT, table, originalText);
    }

    private static UnifiedSqlOperationInfo classifyAlter(String sql, String originalText) {
        Matcher mutation = ALTER_MUTATION.matcher(sql);
        if (mutation.find()) {
            String table = stripQuotes(mutation.group("table"));
            return mutation.group("mutation").toUpperCase(Locale.ROOT).equals("DELETE")
                ? new UnifiedSqlOperationInfo(UnifiedSqlOperation.DELETE, table, originalText)
                : new UnifiedSqlOperationInfo(UnifiedSqlOperation.UPDATE, table, originalText);
        }
        return classifyDdl(sql, originalText, UnifiedSqlOperation.ALTER_TABLE);
    }

    private static UnifiedSqlOperationInfo classifyDdl(String sql, String originalText, UnifiedSqlOperation defaultOp) {
        String lead = sql.stripLeading();
        if (startsWithIgnoreCase(lead, "CREATE INDEX") || startsWithIgnoreCase(lead, "CREATE UNIQUE INDEX")) {
            return new UnifiedSqlOperationInfo(UnifiedSqlOperation.CREATE_INDEX, extract(DDL_TABLE, sql), originalText);
        }
        return new UnifiedSqlOperationInfo(defaultOp, extract(DDL_TABLE, sql), originalText);
    }

    private static String extractSelectTable(String sql) {
        Matcher match = FROM_TABLE.matcher(sql);
        if (!match.find()) {
            return null;
        }
        String table = match.group("table");
        return table.startsWith("(") ? null : stripQuotes(table);
    }

    private static String extractTableAfterInsertOr(String sql) {
        String into = extract(INTO_TABLE, sql);
        if (into != null) {
            return into;
        }
        String afterModifier = INSERT_OR_MODIFIER.matcher(sql).replaceAll("");
        Matcher identMatch = INSERT_OR_TABLE.matcher(afterModifier);
        return identMatch.find() ? stripQuotes(identMatch.group("table")) : null;
    }

    /** Runs an anchored table-extraction pattern and returns the stripped table group, or null. */
    private static String extract(Pattern pattern, String sql) {
        Matcher match = pattern.matcher(sql);
        return match.find() ? stripQuotes(match.group("table")) : null;
    }

    private static String extractLastIdentifierPart(String text) {
        if (isBlank(text)) {
            return null;
        }
        String clean = stripQuotes(text.trim());
        int dotIndex = clean.lastIndexOf('.');
        return dotIndex >= 0 ? clean.substring(dotIndex + 1) : clean;
    }

    private static String stripQuotes(String value) {
        return value.replace("[", "").replace("]", "").replace("\"", "").replace("`", "");
    }

    private static String tableOrPlaceholder(UnifiedSqlOperationInfo op) {
        return op.tableName() != null ? op.tableName() : "?";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
