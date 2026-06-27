package io.kronikol.mongodb;

/**
 * Classified MongoDB operation types. Java port of the .NET {@code MongoDbOperation} enum. Each constant
 * carries the .NET {@code ToString()} (PascalCase) display name used to build the diagram label.
 */
public enum MongoDbOperation {

    FIND("Find"),
    INSERT("Insert"),
    UPDATE("Update"),
    DELETE("Delete"),
    AGGREGATE("Aggregate"),
    COUNT("Count"),
    FIND_AND_MODIFY("FindAndModify"),
    DISTINCT("Distinct"),
    BULK_WRITE("BulkWrite"),
    CREATE_INDEX("CreateIndex"),
    DROP_INDEX("DropIndex"),
    CREATE_COLLECTION("CreateCollection"),
    DROP_COLLECTION("DropCollection"),
    LIST_COLLECTIONS("ListCollections"),
    LIST_DATABASES("ListDatabases"),
    GET_MORE("GetMore"),
    WATCH("Watch"),
    MAP_REDUCE("MapReduce"),
    COMMIT_TRANSACTION("CommitTransaction"),
    ABORT_TRANSACTION("AbortTransaction"),
    DROP_DATABASE("DropDatabase"),
    RENAME_COLLECTION("RenameCollection"),
    LIST_INDEXES("ListIndexes"),
    SERVER_STATUS("ServerStatus"),
    DB_STATS("DbStats"),
    COLL_STATS("CollStats"),
    OTHER("Other");

    private final String displayName;

    MongoDbOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
