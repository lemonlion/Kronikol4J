package io.kronikol.jdbc;

import java.util.Locale;

/**
 * Derives the SQL verb (the diagram arrow label) from a statement — e.g.
 * {@code "select * from t"} -> {@code "SELECT"}. Invariant casing (plan §6.5). Unknown -> {@code "SQL"}.
 */
public final class SqlOperationClassifier {

    private SqlOperationClassifier() {
    }

    public static String keyword(String sql) {
        if (sql == null) {
            return "SQL";
        }
        String trimmed = sql.stripLeading();
        int i = 0;
        while (i < trimmed.length() && Character.isLetter(trimmed.charAt(i))) {
            i++;
        }
        String first = trimmed.substring(0, i).toUpperCase(Locale.ROOT);
        return first.isEmpty() ? "SQL" : first;
    }
}
