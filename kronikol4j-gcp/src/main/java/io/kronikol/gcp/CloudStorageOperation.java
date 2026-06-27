package io.kronikol.gcp;

/**
 * Classified Google Cloud Storage operation types. Java port of the .NET {@code CloudStorageOperation} enum.
 * Each constant carries the .NET {@code ToString()} (PascalCase) display name used in diagram labels.
 */
public enum CloudStorageOperation {

    UPLOAD("Upload"),
    DOWNLOAD("Download"),
    DELETE("Delete"),
    LIST_OBJECTS("ListObjects"),
    GET_METADATA("GetMetadata"),
    UPDATE_METADATA("UpdateMetadata"),
    COPY("Copy"),
    COMPOSE("Compose"),
    CREATE_BUCKET("CreateBucket"),
    DELETE_BUCKET("DeleteBucket"),
    GET_BUCKET("GetBucket"),
    LIST_BUCKETS("ListBuckets"),
    OTHER("Other");

    private final String displayName;

    CloudStorageOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
