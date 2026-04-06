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
     * Creates a schema in Data-Cloud.
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
     * Lists all datasets.
     *
     * @return Promise containing dataset list
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
     * @param transaction the transaction handle
     * @return Promise completing when committed
     */
    Promise<Void> commitTransaction(TransactionHandle transaction);

    /**
     * Rolls back a transaction.
     *
     * @param transaction the transaction handle
     * @return Promise completing when rolled back
     */
    Promise<Void> rollbackTransaction(TransactionHandle transaction);

    // ==================== Streaming Operations ====================

    /**
     * Opens a data stream for reading/writing large datasets.
     *
     * @param request the stream request
     * @return Promise containing data stream
     */
    Promise<DataStream> openStream(DataStreamRequest request);
}
