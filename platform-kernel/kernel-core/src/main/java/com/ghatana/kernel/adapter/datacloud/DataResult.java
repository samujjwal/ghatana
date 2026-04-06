package com.ghatana.kernel.adapter.datacloud;

import java.util.Map;

/**
 * Result from reading data from DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud read result
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataResult {
    private final String recordId;
    private final byte[] data;
    private final Map<String, String> metadata;
    private final long timestamp;

    public DataResult(String recordId, byte[] data, Map<String, String> metadata, long timestamp) {
        this.recordId = recordId;
        this.data = data;
        this.metadata = metadata != null ? metadata : Map.of();
        this.timestamp = timestamp;
    }

    public String getRecordId() { return recordId; }
    public byte[] getData() { return data; }
    public Map<String, String> getMetadata() { return metadata; }
    public long getTimestamp() { return timestamp; }
}
