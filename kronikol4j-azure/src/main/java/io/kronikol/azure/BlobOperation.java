package io.kronikol.azure;

/**
 * Classified Azure Blob Storage operation types. Java port of the .NET {@code BlobOperation} enum. Each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used as the diagram label.
 */
public enum BlobOperation {

    UPLOAD("Upload"),
    DOWNLOAD("Download"),
    DELETE("Delete"),
    GET_PROPERTIES("GetProperties"),
    SET_METADATA("SetMetadata"),
    GET_METADATA("GetMetadata"),
    CREATE_CONTAINER("CreateContainer"),
    DELETE_CONTAINER("DeleteContainer"),
    LIST_BLOBS("ListBlobs"),
    COPY("Copy"),
    PUT_BLOCK("PutBlock"),
    PUT_BLOCK_LIST("PutBlockList"),
    LEASE("Lease"),
    OTHER("Other");

    private final String displayName;

    BlobOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
