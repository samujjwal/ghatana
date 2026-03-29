package com.ghatana.kernel.adapter.datacloud;

import java.util.Map;

/**
 * Request for opening a data stream.
 *
 * @doc.type class
 * @doc.purpose DataCloud stream request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataStreamRequest {
    private final String datasetId;
    private final Map<String, String> options;

    public DataStreamRequest(String datasetId, Map<String, String> options) {
        this.datasetId = datasetId;
        this.options = options != null ? options : Map.of();
    }

    public String getDatasetId() { return datasetId; }
    public Map<String, String> getOptions() { return options; }
}
