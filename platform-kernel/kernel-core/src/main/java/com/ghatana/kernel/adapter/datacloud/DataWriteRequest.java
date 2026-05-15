package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Request for writing data to DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud write request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataWriteRequest {
    private final BridgeContext context;
    private final String datasetId;
    private final String recordId;
    private final byte[] data;
    private final Map<String, String> metadata;

    public DataWriteRequest(BridgeContext context, String datasetId, String recordId, byte[] data, Map<String, String> metadata) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.recordId = Objects.requireNonNull(recordId, "recordId cannot be null");
        this.data = data != null ? Arrays.copyOf(data, data.length) : new byte[0];
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public DataWriteRequest(String datasetId, String recordId, byte[] data, Map<String, String> metadata) {
        this(DataReadRequest.defaultContext(datasetId), datasetId, recordId, data, metadata);
    }

    public BridgeContext getContext() { return context; }
    public String getDatasetId() { return datasetId; }
    public String getRecordId() { return recordId; }
    public byte[] getData() { return Arrays.copyOf(data, data.length); }
    public Map<String, String> getMetadata() { return metadata; }
}
