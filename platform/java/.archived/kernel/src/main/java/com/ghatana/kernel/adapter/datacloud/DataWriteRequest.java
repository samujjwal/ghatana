package com.ghatana.kernel.adapter.datacloud;

import java.util.Map;

/**
 * Request for writing data to DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud write request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataWriteRequest {
    private final String datasetId;
    private final String recordId;
    private final byte[] data;
    private final Map<String, String> metadata;

    public DataWriteRequest(String datasetId, String recordId, byte[] data, Map<String, String> metadata) {
        this.datasetId = datasetId;
        this.recordId = recordId;
        this.data = data;
        this.metadata = metadata != null ? metadata : Map.of();
    }

    public String getDatasetId() { return datasetId; }
    public String getRecordId() { return recordId; }
    public byte[] getData() { return data; }
    public Map<String, String> getMetadata() { return metadata; }
}
