package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.audit.CrossProductAuditService;
import com.ghatana.kernel.communication.KernelInterProductBus;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Concrete implementation of DataCloudKernelAdapter.
 *
 * <p>Provides real Data-Cloud integration with:
 * <ul>
 *   <li>Promise-based async operations wrapping CompletableFuture</li>
 *   <li>Support for multiple storage backends (Memory, Redis, Postgres, Iceberg, S3)</li>
 *   <li>Transaction support with ACID guarantees</li>
 *   <li>Streaming operations for large datasets</li>
 *   <li>Schema management and validation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Concrete Data-Cloud adapter with multi-tier storage
 * @doc.layer adapter
 * @doc.pattern Adapter, Facade
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class DataCloudKernelAdapterImpl implements DataCloudKernelAdapter {

    private final DataCloudClient dataCloudClient;
    private final Map<String, DataStreamImpl> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, TransactionImpl> activeTransactions = new ConcurrentHashMap<>();
    private final AtomicLong transactionCounter = new AtomicLong(0);
    private final AtomicLong streamCounter = new AtomicLong(0);

    /**
     * Creates a new DataCloud adapter with the given client.
     *
     * @param dataCloudClient the Data-Cloud client for storage operations
     */
    public DataCloudKernelAdapterImpl(DataCloudClient dataCloudClient) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient cannot be null");
    }

    @Override
    public Promise<DataResult> readData(DataReadRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        // Wrap Data-Cloud CompletableFuture with ActiveJ Promise
        CompletableFuture<DataResult> future = dataCloudClient.read(
            request.getDatasetId(),
            request.getRecordId(),
            request.getOptions()
        );

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> writeData(DataWriteRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        CompletableFuture<Void> future = dataCloudClient.write(
            request.getDatasetId(),
            request.getRecordId(),
            request.getData(),
            request.getMetadata()
        );

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> deleteData(DataDeleteRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        CompletableFuture<Void> future = dataCloudClient.delete(
            request.getDatasetId(),
            request.getRecordId()
        );

        return wrapFuture(future);
    }

    @Override
    public Promise<QueryResult> queryData(DataQueryRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        CompletableFuture<List<DataResult>> future = dataCloudClient.query(
            request.getDatasetId(),
            request.getQuery(),
            request.getParameters(),
            request.getLimit(),
            request.getOffset()
        );

        return wrapFuture(future).then(results -> {
            boolean hasMore = results.size() == request.getLimit();
            return Promise.of(new QueryResult(results, results.size(), hasMore));
        });
    }

    @Override
    public Promise<Void> createSchema(SchemaCreateRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        CompletableFuture<Void> future = dataCloudClient.createDataset(
            request.getDatasetId(),
            request.getSchema(),
            request.getOptions()
        );

        return wrapFuture(future);
    }

    @Override
    public Promise<SchemaInfo> getSchema(String datasetId) {
        Objects.requireNonNull(datasetId, "datasetId cannot be null");

        CompletableFuture<SchemaInfo> future = dataCloudClient.getSchema(datasetId);

        return wrapFuture(future);
    }

    @Override
    public Promise<List<DatasetInfo>> listDatasets() {
        CompletableFuture<List<DatasetInfo>> future = dataCloudClient.listDatasets();

        return wrapFuture(future);
    }

    @Override
    public Promise<TransactionHandle> beginTransaction() {
        String txId = "tx-" + transactionCounter.incrementAndGet();

        CompletableFuture<TransactionHandle> future = dataCloudClient.beginTransaction()
            .thenApply(innerTx -> {
                TransactionImpl handle = new TransactionImpl(txId, innerTx);
                activeTransactions.put(txId, handle);
                return handle;
            });

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> commitTransaction(TransactionHandle handle) {
        Objects.requireNonNull(handle, "handle cannot be null");

        TransactionImpl tx = activeTransactions.remove(handle.getId());
        if (tx == null) {
            return Promise.ofException(new IllegalStateException("Transaction not found: " + handle.getId()));
        }

        CompletableFuture<Void> future = dataCloudClient.commitTransaction(tx.getInnerTransaction());

        return wrapFuture(future);
    }

    @Override
    public Promise<Void> rollbackTransaction(TransactionHandle handle) {
        Objects.requireNonNull(handle, "handle cannot be null");

        TransactionImpl tx = activeTransactions.remove(handle.getId());
        if (tx == null) {
            return Promise.ofException(new IllegalStateException("Transaction not found: " + handle.getId()));
        }

        CompletableFuture<Void> future = dataCloudClient.rollbackTransaction(tx.getInnerTransaction());

        return wrapFuture(future);
    }

    @Override
    public Promise<DataStream> openReadStream(DataStreamRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        String streamId = "stream-read-" + streamCounter.incrementAndGet();

        CompletableFuture<DataStreamImpl> future = dataCloudClient.openReadStream(
            request.getDatasetId(),
            request.getOptions()
        ).thenApply(innerStream -> {
            DataStreamImpl stream = new DataStreamImpl(streamId, innerStream, true);
            activeStreams.put(streamId, stream);
            return stream;
        });

        return wrapFuture(future).cast();
    }

    @Override
    public Promise<DataStream> openWriteStream(DataStreamRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        String streamId = "stream-write-" + streamCounter.incrementAndGet();

        CompletableFuture<DataStreamImpl> future = dataCloudClient.openWriteStream(
            request.getDatasetId(),
            request.getOptions()
        ).thenApply(innerStream -> {
            DataStreamImpl stream = new DataStreamImpl(streamId, innerStream, false);
            activeStreams.put(streamId, stream);
            return stream;
        });

        return wrapFuture(future).cast();
    }

    /**
     * Stores audit events with domain-specific retention policies.
     *
     * @param record the audit record to store
     * @return Promise completing when stored
     */
    public Promise<Void> storeAuditEvent(CrossProductAuditService.AuditRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        // Store in audit dataset with appropriate retention
        String auditDataset = "audit." + record.getSourceProduct() + "." + record.getTargetProduct();

        DataWriteRequest request = new DataWriteRequest(
            auditDataset,
            record.getAuditId(),
            serializeAuditRecord(record),
            Map.of(
                "retentionYears", String.valueOf(record.getRetentionPeriod().getYears()),
                "timestamp", record.getTimestamp().toString()
            )
        );

        return writeData(request);
    }

    /**
     * Queries audit events with filtering.
     *
     * @param start start time
     * @param end end time
     * @param sourceProduct optional source filter
     * @param targetProduct optional target filter
     * @return Promise containing matching records
     */
    public Promise<Set<CrossProductAuditService.AuditRecord>> queryAuditEvents(
            Instant start, Instant end,
            String sourceProduct, String targetProduct) {

        // Build query for audit dataset
        StringBuilder query = new StringBuilder("timestamp >= :start AND timestamp <= :end");
        Map<String, Object> params = new HashMap<>();
        params.put("start", start.toEpochMilli());
        params.put("end", end.toEpochMilli());

        if (sourceProduct != null) {
            query.append(" AND sourceProduct = :source");
            params.put("source", sourceProduct);
        }

        if (targetProduct != null) {
            query.append(" AND targetProduct = :target");
            params.put("target", targetProduct);
        }

        // Query across all audit datasets
        return queryAllAuditDatasets(query.toString(), params)
            .map(results -> results.stream()
                .map(this::deserializeAuditRecord)
                .collect(Collectors.toSet()));
    }

    /**
     * Stores shared data for cross-product access.
     *
     * @param record the shared data record
     * @return Promise completing when stored
     */
    public Promise<Void> storeSharedData(KernelInterProductBus.SharedDataRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        String sharedDataset = "shared." + record.getSourceProduct() + "." + record.getTargetProduct();

        DataWriteRequest request = new DataWriteRequest(
            sharedDataset,
            record.getDataId(),
            serializeSharedData(record),
            Map.of(
                "accessPolicy", record.getAccessPolicy(),
                "retentionDays", String.valueOf(record.getRetentionPeriod().toDays()),
                "encryptionRequired", String.valueOf(record.isEncryptionRequired()),
                "auditRequired", String.valueOf(record.isAuditRequired()),
                "createdAt", record.getCreatedAt().toString()
            )
        );

        return writeData(request);
    }

    /**
     * Retrieves shared data from another product.
     *
     * @param dataId the data identifier
     * @param requestingProduct the requesting product
     * @return Promise containing the shared data
     */
    public Promise<KernelInterProductBus.SharedDataRecord> retrieveSharedData(
            String dataId, String requestingProduct) {

        Objects.requireNonNull(dataId, "dataId cannot be null");
        Objects.requireNonNull(requestingProduct, "requestingProduct cannot be null");

        // Query across shared datasets
        return querySharedDatasets(dataId)
            .then(result -> {
                if (result == null) {
                    return Promise.ofException(new IllegalStateException("Shared data not found: " + dataId));
                }
                return Promise.of(deserializeSharedData(result));
            });
    }

    // ==================== Private Methods ====================

    private <T> Promise<T> wrapFuture(CompletableFuture<T> future) {
        return Promise.ofFuture(future);
    }

    private byte[] serializeAuditRecord(CrossProductAuditService.AuditRecord record) {
        // JSON serialization with canonical format for signatures
        return JsonUtils.toJson(record).getBytes(StandardCharsets.UTF_8);
    }

    private CrossProductAuditService.AuditRecord deserializeAuditRecord(DataResult result) {
        return JsonUtils.fromJson(new String(result.getData(), StandardCharsets.UTF_8),
            CrossProductAuditService.AuditRecord.class);
    }

    private byte[] serializeSharedData(KernelInterProductBus.SharedDataRecord record) {
        return JsonUtils.toJson(record).getBytes(StandardCharsets.UTF_8);
    }

    private KernelInterProductBus.SharedDataRecord deserializeSharedData(DataResult result) {
        return JsonUtils.fromJson(new String(result.getData(), StandardCharsets.UTF_8),
            KernelInterProductBus.SharedDataRecord.class);
    }

    private Promise<List<DataResult>> queryAllAuditDatasets(String query, Map<String, Object> params) {
        // Query audit datasets for all products
        List<Promise<List<DataResult>>> promises = List.of(
            queryData(new DataQueryRequest("audit.phr.finance", query, params, 1000, 0)),
            queryData(new DataQueryRequest("audit.finance.phr", query, params, 1000, 0))
        );

        return Promises.all(promises).then(results -> {
            List<DataResult> combined = new ArrayList<>();
            for (QueryResult qr : results) {
                combined.addAll(qr.getResults());
            }
            return Promise.of(combined);
        });
    }

    private Promise<DataResult> querySharedDatasets(String dataId) {
        // Try to find data across all shared datasets
        List<String> datasets = List.of("shared.phr.finance", "shared.finance.phr");

        Promise<DataResult> searchPromise = Promise.of(null);

        for (String dataset : datasets) {
            searchPromise = searchPromise.then(currentResult -> {
                if (currentResult != null) {
                    return Promise.of(currentResult);
                }
                return queryData(new DataQueryRequest(
                    dataset,
                    "dataId = :id",
                    Map.of("id", dataId),
                    1, 0
                )).then(queryResult -> {
                    if (!queryResult.getResults().isEmpty()) {
                        return Promise.of(queryResult.getResults().get(0));
                    }
                    return Promise.of(null);
                }).whenException(e -> {
                    // Continue to next dataset on error
                });
            });
        }

        return searchPromise;
    }

    // ==================== Inner Types ====================

    /**
     * Data-Cloud client interface (to be implemented by data-cloud platform).
     */
    public interface DataCloudClient {
        CompletableFuture<DataResult> read(String datasetId, String recordId, Map<String, String> options);
        CompletableFuture<Void> write(String datasetId, String recordId, byte[] data, Map<String, String> metadata);
        CompletableFuture<Void> delete(String datasetId, String recordId);
        CompletableFuture<List<DataResult>> query(String datasetId, String query, Map<String, Object> params, int limit, int offset);
        CompletableFuture<Void> createDataset(String datasetId, Map<String, String> schema, Map<String, String> options);
        CompletableFuture<SchemaInfo> getSchema(String datasetId);
        CompletableFuture<List<DatasetInfo>> listDatasets();
        CompletableFuture<Object> beginTransaction();
        CompletableFuture<Void> commitTransaction(Object transaction);
        CompletableFuture<Void> rollbackTransaction(Object transaction);
        CompletableFuture<Object> openReadStream(String datasetId, Map<String, String> options);
        CompletableFuture<Object> openWriteStream(String datasetId, Map<String, String> options);
    }

    private static class TransactionImpl implements TransactionHandle {
        private final String id;
        private final Object innerTransaction;
        private final AtomicBoolean active = new AtomicBoolean(true);

        TransactionImpl(String id, Object innerTransaction) {
            this.id = id;
            this.innerTransaction = innerTransaction;
        }

        @Override
        public String getId() { return id; }

        @Override
        public boolean isActive() { return active.get(); }

        Object getInnerTransaction() { return innerTransaction; }
    }

    private class DataStreamImpl implements DataStream {
        private final String streamId;
        private final Object innerStream;
        private final boolean isReadStream;
        private final AtomicBoolean open = new AtomicBoolean(true);

        DataStreamImpl(String streamId, Object innerStream, boolean isReadStream) {
            this.streamId = streamId;
            this.innerStream = innerStream;
            this.isReadStream = isReadStream;
        }

        @Override
        public Promise<byte[]> readChunk() {
            if (!isReadStream) {
                return Promise.ofException(new IllegalStateException("Not a read stream"));
            }
            if (!open.get()) {
                return Promise.ofException(new IllegalStateException("Stream closed"));
            }

            CompletableFuture<byte[]> future = dataCloudClient.readStreamChunk(innerStream);
            return wrapFuture(future);
        }

        @Override
        public Promise<Void> writeChunk(byte[] data) {
            if (isReadStream) {
                return Promise.ofException(new IllegalStateException("Not a write stream"));
            }
            if (!open.get()) {
                return Promise.ofException(new IllegalStateException("Stream closed"));
            }

            CompletableFuture<Void> future = dataCloudClient.writeStreamChunk(innerStream, data);
            return wrapFuture(future);
        }

        @Override
        public Promise<Void> close() {
            if (!open.compareAndSet(true, false)) {
                return Promise.complete();
            }

            activeStreams.remove(streamId);
            CompletableFuture<Void> future = dataCloudClient.closeStream(innerStream);
            return wrapFuture(future);
        }

        @Override
        public boolean isOpen() { return open.get(); }
    }
}
