package com.ghatana.kernel.adapter.datacloud;

/**
 * Information about a DataCloud dataset.
 *
 * @doc.type class
 * @doc.purpose DataCloud dataset information
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DatasetInfo {
    private final String datasetId;
    private final String name;
    private final String description;
    private final long recordCount;
    private final long createdAt;

    public DatasetInfo(String datasetId, String name, String description, long recordCount, long createdAt) {
        this.datasetId = datasetId;
        this.name = name;
        this.description = description;
        this.recordCount = recordCount;
        this.createdAt = createdAt;
    }

    public String getDatasetId() { return datasetId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getRecordCount() { return recordCount; }
    public long getCreatedAt() { return createdAt; }
}
