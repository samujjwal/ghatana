package com.ghatana.kernel.adapter.datacloud;

import java.util.Map;

/**
 * Request for reading data from DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud read request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataReadRequest {
    private final String datasetId;
    private final String recordId;
    private final Map<String, String> options;

    public DataReadRequest(String datasetId, String recordId, Map<String, String> options) {
        this.datasetId = datasetId;
        this.recordId = recordId;
        this.options = options != null ? options : Map.of();
    }

    public String getDatasetId() { return datasetId; }
    public String getRecordId() { return recordId; }
    public Map<String, String> getOptions() { return options; }
}
