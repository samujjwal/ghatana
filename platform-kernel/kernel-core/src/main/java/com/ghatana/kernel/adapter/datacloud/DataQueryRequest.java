package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.util.Map;
import java.util.Objects;

/**
 * Request for querying data from DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud query request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataQueryRequest {
    private final BridgeContext context;
    private final String datasetId;
    private final String query;
    private final Map<String, Object> parameters;
    private final int limit;
    private final int offset;

    public DataQueryRequest(
            BridgeContext context,
            String datasetId,
            String query,
            Map<String, Object> parameters,
            int limit,
            int offset) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.query = Objects.requireNonNull(query, "query cannot be null");
        this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
        this.limit = limit;
        this.offset = offset;
    }

    public DataQueryRequest(String datasetId, String query, Map<String, Object> parameters, int limit, int offset) {
        this(DataReadRequest.defaultContext(datasetId), datasetId, query, parameters, limit, offset);
    }

    public BridgeContext getContext() { return context; }
    public String getDatasetId() { return datasetId; }
    public String getQuery() { return query; }
    public Map<String, Object> getParameters() { return parameters; }
    public int getLimit() { return limit; }
    public int getOffset() { return offset; }
}
