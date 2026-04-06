package com.ghatana.kernel.adapter.datacloud;

/**
 * Request for deleting data from DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud delete request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataDeleteRequest {
    private final String datasetId;
    private final String recordId;

    public DataDeleteRequest(String datasetId, String recordId) {
        this.datasetId = datasetId;
        this.recordId = recordId;
    }

    public String getDatasetId() { return datasetId; }
    public String getRecordId() { return recordId; }
}
