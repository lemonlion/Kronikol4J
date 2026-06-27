package io.kronikol.aws;

/**
 * Classified Amazon S3 operation types. Java port of the .NET {@code S3Operation} enum. Each constant carries
 * the AWS operation name (PascalCase), used as the diagram label.
 */
public enum S3Operation {

    PUT_OBJECT("PutObject"),
    GET_OBJECT("GetObject"),
    DELETE_OBJECT("DeleteObject"),
    DELETE_OBJECTS("DeleteObjects"),
    HEAD_OBJECT("HeadObject"),
    COPY_OBJECT("CopyObject"),
    LIST_OBJECTS_V2("ListObjectsV2"),
    LIST_BUCKETS("ListBuckets"),
    CREATE_BUCKET("CreateBucket"),
    DELETE_BUCKET("DeleteBucket"),
    GET_BUCKET_LOCATION("GetBucketLocation"),
    CREATE_MULTIPART_UPLOAD("CreateMultipartUpload"),
    UPLOAD_PART("UploadPart"),
    COMPLETE_MULTIPART_UPLOAD("CompleteMultipartUpload"),
    ABORT_MULTIPART_UPLOAD("AbortMultipartUpload"),
    PUT_OBJECT_TAGGING("PutObjectTagging"),
    GET_OBJECT_TAGGING("GetObjectTagging"),
    DELETE_OBJECT_TAGGING("DeleteObjectTagging"),
    LIST_OBJECT_VERSIONS("ListObjectVersions"),
    OTHER("Other");

    private final String displayName;

    S3Operation(String displayName) {
        this.displayName = displayName;
    }

    /** The AWS operation name (PascalCase) used as the diagram label. */
    public String displayName() {
        return displayName;
    }
}
