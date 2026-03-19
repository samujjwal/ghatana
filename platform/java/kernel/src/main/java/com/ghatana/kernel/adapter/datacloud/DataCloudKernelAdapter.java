package com.ghatana.kernel.adapter.datacloud;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter interface for Data-Cloud platform integration.
 *
 * <p>Wraps Data-Cloud CompletableFuture-based SPI with ActiveJ Promise.
 * This is the canonical bridge between kernel SPI and Data-Cloud platform.</p>
 *
 * <p>Usage: {@code dataCloudAdapter.readData(query).then(result -> process(result))}</p>
 *
 * @doc.type interface
 * @doc.purpose Bridge kernel SPI to Data-Cloud platform with Promise wrapping
 * @doc.layer adapter
 * @doc.pattern Adapter
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface DataCloudKernelAdapter {

    // ==================== Data Operations ====================

    /**
     * Reads data from Data-Cloud storage.
     *
     * @param request the read request
     * @return Promise containing the data result
     */
    Promise<DataResult> readData(DataReadRequest request);

    /**
     * Writes data to Data-Cloud storage.
     *
     * @param request the write request
     * @return Promise completing when write is done
     */
    Promise<Void> writeData(DataWriteRequest request);

    /**
     * Deletes data from Data-Cloud storage.
     *
     * @param request the delete request
     * @return Promise completing when delete is done
     */
    Promise<Void> deleteData(DataDeleteRequest request);

    /**
     * Queries data with filtering.
     *
     * @param request the query request
     * @return Promise containing query results
     */
    Promise<QueryResult> queryData(DataQueryRequest request);

    // ==================== Schema Operations ====================

    /**
     * Creates a new dataset/schema.
     *
     * @param request the schema creation request
     * @return Promise completing when schema is created
     */
    Promise<Void> createSchema(SchemaCreateRequest request);

    /**
     * Gets schema information.
     *
     * @param datasetId the dataset identifier
     * @return Promise containing schema info
     */
    Promise<SchemaInfo> getSchema(String datasetId);

    /**
     * Lists all available datasets.
     *
     * @return Promise containing list of dataset info
     */
    Promise<List<DatasetInfo>> listDatasets();

    // ==================== Transaction Operations ====================

    /**
     * Begins a transaction.
     *
     * @return Promise containing transaction handle
     */
    Promise<TransactionHandle> beginTransaction();

    /**
     * Commits a transaction.
     *
     * @param handle the transaction handle
     * @return Promise completing when committed
     */
    Promise<Void> commitTransaction(TransactionHandle handle);

    /**
     * Rolls back a transaction.
     *
     * @param handle the transaction handle
     * @return Promise completing when rolled back
     */
    Promise<Void> rollbackTransaction(TransactionHandle handle);

    // ==================== Streaming Operations ====================

    /**
     * Opens a data stream for reading.
     *
     * @param request the stream request
     * @return Promise containing stream handle
     */
    Promise<DataStream> openReadStream(DataStreamRequest request);

    /**
     * Opens a data stream for writing.
     *
     * @param request the stream request
     * @return Promise containing stream handle
     */
    Promise<DataStream> openWriteStream(DataStreamRequest request);

    // ==================== Request/Result Types ====================

    class DataReadRequest {
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

    class DataWriteRequest {
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

    class DataDeleteRequest {
        private final String datasetId;
        private final String recordId;

        public DataDeleteRequest(String datasetId, String recordId) {
            this.datasetId = datasetId;
            this.recordId = recordId;
        }

        public String getDatasetId() { return datasetId; }
        public String getRecordId() { return recordId; }
    }

    class DataQueryRequest {
        private final String datasetId;
        private final String query;
        private final Map<String, Object> parameters;
        private final int limit;
        private final int offset;

        public DataQueryRequest(String datasetId, String query, Map<String, Object> parameters, int limit, int offset) {
            this.datasetId = datasetId;
            this.query = query;
            this.parameters = parameters != null ? parameters : Map.of();
            this.limit = limit;
            this.offset = offset;
        }

        public String getDatasetId() { return datasetId; }
        public String getQuery() { return query; }
        public Map<String, Object> getParameters() { return parameters; }
        public int getLimit() { return limit; }
        public int getOffset() { return offset; }
    }

    class DataResult {
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

    class QueryResult {
        private final List<DataResult> results;
        private final int totalCount;
        private final boolean hasMore;

        public QueryResult(List<DataResult> results, int totalCount, boolean hasMore) {
            this.results = results != null ? results : List.of();
            this.totalCount = totalCount;
            this.hasMore = hasMore;
        }

        public List<DataResult> getResults() { return results; }
        public int getTotalCount() { return totalCount; }
        public boolean hasMore() { return hasMore; }
    }

    class SchemaCreateRequest {
        private final String datasetId;
        private final Map<String, String> schema;
        private final Map<String, String> options;

        public SchemaCreateRequest(String datasetId, Map<String, String> schema, Map<String, String> options) {
            this.datasetId = datasetId;
            this.schema = schema != null ? schema : Map.of();
            this.options = options != null ? options : Map.of();
        }

        public String getDatasetId() { return datasetId; }
        public Map<String, String> getSchema() { return schema; }
        public Map<String, String> getOptions() { return options; }
    }

    class SchemaInfo {
        private final String datasetId;
        private final Map<String, String> fields;
        private final long createdAt;
        private final long updatedAt;

        public SchemaInfo(String datasetId, Map<String, String> fields, long createdAt, long updatedAt) {
            this.datasetId = datasetId;
            this.fields = fields != null ? fields : Map.of();
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getDatasetId() { return datasetId; }
        public Map<String, String> getFields() { return fields; }
        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }
    }

    class DatasetInfo {
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

    interface TransactionHandle {
        String getId();
        boolean isActive();
    }

    class DataStreamRequest {
        private final String datasetId;
        private final Map<String, String> options;

        public DataStreamRequest(String datasetId, Map<String, String> options) {
            this.datasetId = datasetId;
            this.options = options != null ? options : Map.of();
        }

        public String getDatasetId() { return datasetId; }
        public Map<String, String> getOptions() { return options; }
    }

    interface DataStream {
        Promise<byte[]> readChunk();
        Promise<Void> writeChunk(byte[] data);
        Promise<Void> close();
        boolean isOpen();
    }
}
