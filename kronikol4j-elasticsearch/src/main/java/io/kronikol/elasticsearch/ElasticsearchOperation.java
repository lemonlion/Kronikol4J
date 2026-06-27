package io.kronikol.elasticsearch;

/**
 * Classified Elasticsearch operation types. Java port of the .NET {@code ElasticsearchOperation} enum. Each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used as the diagram-label fallback.
 */
public enum ElasticsearchOperation {

    INDEX_DOCUMENT("IndexDocument"),
    GET_DOCUMENT("GetDocument"),
    DELETE_DOCUMENT("DeleteDocument"),
    UPDATE_DOCUMENT("UpdateDocument"),
    BULK("Bulk"),
    SEARCH("Search"),
    MULTI_SEARCH("MultiSearch"),
    COUNT("Count"),
    SCROLL("Scroll"),
    CREATE_INDEX("CreateIndex"),
    DELETE_INDEX("DeleteIndex"),
    INDEX_EXISTS("IndexExists"),
    PUT_MAPPING("PutMapping"),
    GET_MAPPING("GetMapping"),
    REFRESH("Refresh"),
    REINDEX("Reindex"),
    DELETE_BY_QUERY("DeleteByQuery"),
    UPDATE_BY_QUERY("UpdateByQuery"),
    PUT_INDEX_TEMPLATE("PutIndexTemplate"),
    GET_INDEX_TEMPLATE("GetIndexTemplate"),
    UPDATE_ALIASES("UpdateAliases"),
    CLUSTER_HEALTH("ClusterHealth"),
    CAT_APIS("CatApis"),
    OTHER("Other");

    private final String displayName;

    ElasticsearchOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used in diagram labels. */
    public String displayName() {
        return displayName;
    }
}
