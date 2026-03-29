package com.ghatana.kernel.adapter.datacloud;

import java.util.Map;

/**
 * Request for creating a schema in DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud schema creation request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class SchemaCreateRequest {
    private final String datasetId;
    private final Map<String, String> schema;
    private final Map<String, String> options;

    public SchemaCreateRequest(String datasetId, Map<String, String> schema, Map<String, String> options) {
        this.datasetId = datasetId;
        this.schema = schema != null ? schema : Map.of();
        this.options = options != null ? options : Map.of();
    }

    public String getDatasetId() { return datasetId; }
    public Map<String, String> getSchema() { return schema; }
    public Map<String, String> getOptions() { return options; }
}
