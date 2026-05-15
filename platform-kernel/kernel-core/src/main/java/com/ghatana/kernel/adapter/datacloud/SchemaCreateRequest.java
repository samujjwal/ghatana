package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.util.Map;
import java.util.Objects;

/**
 * Request for creating a schema in DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud schema creation request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class SchemaCreateRequest {
    private final BridgeContext context;
    private final String datasetId;
    private final Map<String, String> schema;
    private final Map<String, String> options;

    public SchemaCreateRequest(BridgeContext context, String datasetId, Map<String, String> schema, Map<String, String> options) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.schema = schema != null ? Map.copyOf(schema) : Map.of();
        this.options = options != null ? Map.copyOf(options) : Map.of();
    }

    public SchemaCreateRequest(String datasetId, Map<String, String> schema, Map<String, String> options) {
        this(DataReadRequest.defaultContext(datasetId), datasetId, schema, options);
    }

    public BridgeContext getContext() { return context; }
    public String getDatasetId() { return datasetId; }
    public Map<String, String> getSchema() { return schema; }
    public Map<String, String> getOptions() { return options; }
}
