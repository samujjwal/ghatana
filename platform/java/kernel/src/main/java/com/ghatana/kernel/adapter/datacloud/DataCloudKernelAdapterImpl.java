package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.communication.KernelInterScopeBus;
import com.ghatana.kernel.util.JsonUtils;
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

    // ==================== Canonical Scope-Aware Methods ====================

    /**
     * Stores a scope-aware audit record using scope descriptors instead of product ids.
     *
     * <p>Dataset naming is derived from scope descriptors, not hardcoded product names.</p>
     *
     * @param record the scope audit record
     * @return Promise completing when stored
     */
    public Promise<Void> storeScopeAuditRecord(CrossScopeAuditService.ScopeAuditRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        String auditDataset = "audit." + record.getSourceScope().getScopeId()
                + "." + record.getTargetScope().getScopeId();

        DataWriteRequest request = new DataWriteRequest(
            auditDataset,
            record.getAuditId(),
            JsonUtils.toJson(record).getBytes(StandardCharsets.UTF_8),
            Map.of(
                "retentionYears", String.valueOf(record.getRetentionYears()),
                "storageTier", record.getStorageTier() != null ? record.getStorageTier() : "default",
                "timestamp", record.getTimestamp().toString()
            )
        );

        return writeData(request);
    }

    /**
     * Stores scope-aware shared data using scope descriptors instead of product ids.
     *
     * @param record the shared scope record
     * @return Promise completing when stored
     */
    public Promise<Void> storeScopeSharedData(KernelInterScopeBus.SharedScopeRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        String sharedDataset = "shared." + record.getSourceScope().getScopeId()
                + "." + record.getTargetScope().getScopeId();

        DataWriteRequest request = new DataWriteRequest(
            sharedDataset,
            record.getDataId(),
            JsonUtils.toJson(record).getBytes(StandardCharsets.UTF_8),
            Map.of(
                "classification", record.getClassification().toString(),
                "createdAt", record.getCreatedAt().toString()
            )
        );

        return writeData(request);
    }

    // ==================== Private Methods ====================

    private <T> Promise<T> wrapFuture(CompletableFuture<T> future) {
        return Promise.ofFuture(future);
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
        CompletableFuture<byte[]> readStreamChunk(Object stream);
        CompletableFuture<Void> writeStreamChunk(Object stream, byte[] data);
        CompletableFuture<Void> closeStream(Object stream);
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
