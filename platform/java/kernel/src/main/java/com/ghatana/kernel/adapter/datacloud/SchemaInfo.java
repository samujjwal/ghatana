package com.ghatana.kernel.adapter.datacloud;

import java.util.Map;

/**
 * Information about a DataCloud schema.
 *
 * @doc.type class
 * @doc.purpose DataCloud schema information
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class SchemaInfo {
    private final String datasetId;
    private final Map<String, String> fields;
    private final long createdAt;
    private final long updatedAt;

    public SchemaInfo(String datasetId, Map<String, String> fields, long createdAt, long updatedAt) {
        this.datasetId = datasetId;
        this.fields = fields != null ? fields : Map.of();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getDatasetId() { return datasetId; }
    public Map<String, String> getFields() { return fields; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
}
