package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.util.Map;
import java.util.Objects;

/**
 * Request for reading data from DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud read request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataReadRequest {
    private final BridgeContext context;
    private final String datasetId;
    private final String recordId;
    private final Map<String, String> options;

    public DataReadRequest(BridgeContext context, String datasetId, String recordId, Map<String, String> options) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.recordId = Objects.requireNonNull(recordId, "recordId cannot be null");
        this.options = options != null ? Map.copyOf(options) : Map.of();
    }

    public DataReadRequest(String datasetId, String recordId, Map<String, String> options) {
        this(defaultContext(datasetId), datasetId, recordId, options);
    }

    public BridgeContext getContext() { return context; }
    public String getDatasetId() { return datasetId; }
    public String getRecordId() { return recordId; }
    public Map<String, String> getOptions() { return options; }

    static BridgeContext defaultContext(String datasetId) {
        String tenantId = Objects.requireNonNull(datasetId, "datasetId cannot be null").split("\\.", 2)[0];
        return BridgeContext.builder()
            .tenantId(tenantId)
            .principalId("kernel-data-service")
            .correlationId("data-cloud-request")
            .build();
    }
}
