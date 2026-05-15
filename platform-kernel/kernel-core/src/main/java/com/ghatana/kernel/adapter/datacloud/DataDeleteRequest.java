package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.util.Objects;

/**
 * Request for deleting data from DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud delete request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataDeleteRequest {
    private final BridgeContext context;
    private final String datasetId;
    private final String recordId;

    public DataDeleteRequest(BridgeContext context, String datasetId, String recordId) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.recordId = Objects.requireNonNull(recordId, "recordId cannot be null");
    }

    public DataDeleteRequest(String datasetId, String recordId) {
        this(DataReadRequest.defaultContext(datasetId), datasetId, recordId);
    }

    public BridgeContext getContext() { return context; }
    public String getDatasetId() { return datasetId; }
    public String getRecordId() { return recordId; }
}
