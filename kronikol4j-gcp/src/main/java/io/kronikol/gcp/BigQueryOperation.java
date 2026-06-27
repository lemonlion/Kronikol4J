package io.kronikol.gcp;

/**
 * Classified Google BigQuery operation types. Java port of the .NET {@code BigQueryOperation} enum. Each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used as the diagram label.
 */
public enum BigQueryOperation {

    QUERY("Query"),
    INSERT("Insert"),
    READ("Read"),
    LIST("List"),
    CREATE("Create"),
    DELETE("Delete"),
    UPDATE("Update"),
    CANCEL("Cancel"),
    OTHER("Other");

    private final String displayName;

    BigQueryOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
