package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataDeleteRequest;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DataStream;
import com.ghatana.kernel.adapter.datacloud.DataStreamRequest;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.adapter.datacloud.TransactionHandle;
import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.bridge.AbstractKernelBridge;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.kernel.communication.KernelInterScopeBus;
import com.ghatana.platform.core.client.AsyncClient;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
 * @doc.purpose Concrete Data-Cloud adapter with multi-tier storage and kernel bridge infrastructure
 * @doc.layer adapter
 * @doc.pattern Adapter, Facade
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class DataCloudKernelAdapterImpl extends AbstractKernelBridge implements DataCloudKernelAdapter {

    // Constants for duplicate literals
    private static final String REQUEST_CANNOT_BE_NULL = "request cannot be null";
    private static final String DATACLOUD_CLIENT_CANNOT_BE_NULL = "dataCloudClient cannot be null";
    private static final String DATASET_ID_CANNOT_BE_NULL = "datasetId cannot be null";
    private static final String STREAM_ID_CANNOT_BE_NULL = "streamId cannot be null";
    private static final String TRANSACTION_ID_CANNOT_BE_NULL = "transactionId cannot be null";
    private static final String READ_MODE = "read";
    private static final String WRITE_MODE = "write";
    private static final String MODE = "mode";

    private final DataCloudClient dataCloudClient;
    private final Map<String, DataStreamImpl> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, TransactionImpl> activeTransactions = new ConcurrentHashMap<>();
    private final AtomicLong transactionCounter = new AtomicLong(0);
    private final AtomicLong streamCounter = new AtomicLong(0);

    /**
     * Creates a new DataCloud adapter with the given client and kernel bridge ports.
     * This constructor wires production-grade authorization, audit, and health.
     *
     * @param dataCloudClient the Data-Cloud client for storage operations
     * @param authService     authorization port — must not be {@code null}
     * @param auditEmitter    audit emission port — must not be {@code null}
     * @param healthIndicator health indicator port — must not be {@code null}
     */
    public DataCloudKernelAdapterImpl(
            DataCloudClient dataCloudClient,
            BridgeAuthorizationService authService,
            BridgeAuditEmitter auditEmitter,
            BridgeHealthIndicator healthIndicator) {
        super("data-cloud-kernel-bridge", authService, auditEmitter, healthIndicator);
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, DATACLOUD_CLIENT_CANNOT_BE_NULL);
        markStarted();
    }

    @Override
    public Promise<DataResult> readData(DataReadRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();

        String resource = datasetResource(request.getDatasetId());
        return authorize(request.getContext(), resource, READ_MODE)
            .then(() -> executeWithRetry("readData", request.getContext(), resource, READ_MODE, () ->
                dataCloudClient.read(request.getDatasetId(), request.getRecordId(), request.getOptions())));
    }

    @Override
    public Promise<Void> writeData(DataWriteRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();

        String resource = datasetResource(request.getDatasetId());
        return authorize(request.getContext(), resource, WRITE_MODE)
            .then(() -> executeWithRetry("writeData", request.getContext(), resource, WRITE_MODE, () ->
                dataCloudClient.write(request.getDatasetId(), request.getRecordId(), request.getData(), request.getMetadata())));
    }

    @Override
    public Promise<Void> deleteData(DataDeleteRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();

        String resource = datasetResource(request.getDatasetId());
        return authorize(request.getContext(), resource, "delete")
            .then(() -> executeWithRetry("deleteData", request.getContext(), resource, "delete", () ->
                dataCloudClient.delete(request.getDatasetId(), request.getRecordId())));
    }

    @Override
    public Promise<QueryResult> queryData(DataQueryRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();

        String resource = datasetResource(request.getDatasetId());
        return authorize(request.getContext(), resource, "query")
            .then(() -> executeWithRetry("queryData", request.getContext(), resource, "query", () ->
                dataCloudClient.query(
                    request.getDatasetId(),
                    request.getQuery(),
                    request.getParameters(),
                    request.getLimit(),
                    request.getOffset()
                )))
            .then(results -> {
            boolean hasMore = results.size() == request.getLimit();
            return Promise.of(new QueryResult(results, results.size(), hasMore));
        });
    }

    @Override
    public Promise<Void> createSchema(SchemaCreateRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();

        String resource = datasetResource(request.getDatasetId());
        return authorize(request.getContext(), resource, "schema:create")
            .then(() -> executeWithRetry("createSchema", request.getContext(), resource, "schema:create", () ->
                dataCloudClient.createDataset(request.getDatasetId(), request.getSchema(), request.getOptions())));
    }

    @Override
    public Promise<SchemaInfo> getSchema(BridgeContext context, String datasetId) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(datasetId, "datasetId cannot be null");
        requireStarted();

        String resource = datasetResource(datasetId);
        return authorize(context, resource, "schema:read")
            .then(() -> executeWithRetry("getSchema", context, resource, "schema:read", () ->
                dataCloudClient.getSchema(datasetId)));
    }

    @Override
    public Promise<List<DatasetInfo>> listDatasets(BridgeContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        requireStarted();

        String resource = tenantResource(context);
        return authorize(context, resource, "dataset:list")
            .then(() -> executeWithRetry("listDatasets", context, resource, "dataset:list", dataCloudClient::listDatasets));
    }

    @Override
    public Promise<TransactionHandle> beginTransaction(BridgeContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        requireStarted();
        String txId = "tx-" + transactionCounter.incrementAndGet();

        String resource = tenantResource(context);
        return authorize(context, resource, "transaction:begin")
            .then(() -> executeWithRetry("beginTransaction", context, resource, "transaction:begin", () ->
                dataCloudClient.beginTransaction().thenApply(innerTx -> {
                TransactionImpl handle = new TransactionImpl(txId, innerTx);
                activeTransactions.put(txId, handle);
                return handle;
            })));
    }

    @Override
    public Promise<Void> commitTransaction(BridgeContext context, TransactionHandle handle) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(handle, "handle cannot be null");
        requireStarted();

        TransactionImpl tx = activeTransactions.remove(handle.getId());
        if (tx == null) {
            return Promise.ofException(new IllegalStateException("Transaction not found: " + handle.getId()));
        }

        String resource = tenantResource(context);
        return authorize(context, resource, "transaction:commit")
            .then(() -> executeWithRetry("commitTransaction", context, resource, "transaction:commit", () ->
                dataCloudClient.commitTransaction(tx.getInnerTransaction())));
    }

    @Override
    public Promise<Void> rollbackTransaction(BridgeContext context, TransactionHandle handle) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(handle, "handle cannot be null");
        requireStarted();

        TransactionImpl tx = activeTransactions.remove(handle.getId());
        if (tx == null) {
            return Promise.ofException(new IllegalStateException("Transaction not found: " + handle.getId()));
        }

        String resource = tenantResource(context);
        return authorize(context, resource, "transaction:rollback")
            .then(() -> executeWithRetry("rollbackTransaction", context, resource, "transaction:rollback", () ->
                dataCloudClient.rollbackTransaction(tx.getInnerTransaction())));
    }

    @Override
    public Promise<DataStream> openStream(DataStreamRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();
        String mode = request.getOptions().getOrDefault(MODE, READ_MODE);
        if (WRITE_MODE.equalsIgnoreCase(mode)) {
            return openWriteStream(request);
        }
        return openReadStream(request);
    }

    public Promise<DataStream> openReadStream(DataStreamRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();

        String streamId = "stream-read-" + streamCounter.incrementAndGet();
        String resource = datasetResource(request.getDatasetId());
        return authorize(request.getContext(), resource, "stream:read")
            .then(() -> executeWithRetry("openReadStream", request.getContext(), resource, "stream:read", () ->
                dataCloudClient.openReadStream(request.getDatasetId(), request.getOptions()).thenApply(innerStream -> {
            DataStreamImpl stream = new DataStreamImpl(
                streamId,
                innerStream,
                true,
                request.getContext(),
                resource);
            activeStreams.put(streamId, stream);
            return stream;
        }))).cast();
    }

    public Promise<DataStream> openWriteStream(DataStreamRequest request) {
        Objects.requireNonNull(request, REQUEST_CANNOT_BE_NULL);
        requireStarted();

        String streamId = "stream-write-" + streamCounter.incrementAndGet();
        String resource = datasetResource(request.getDatasetId());
        return authorize(request.getContext(), resource, "stream:write")
            .then(() -> executeWithRetry("openWriteStream", request.getContext(), resource, "stream:write", () ->
                dataCloudClient.openWriteStream(request.getDatasetId(), request.getOptions()).thenApply(innerStream -> {
            DataStreamImpl stream = new DataStreamImpl(
                streamId,
                innerStream,
                false,
                request.getContext(),
                resource);
            activeStreams.put(streamId, stream);
            return stream;
        }))).cast();
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

        byte[] payload;
        try {
            payload = JsonUtils.toJson(record).getBytes(StandardCharsets.UTF_8);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Promise.ofException(e);
        }

        DataWriteRequest request = new DataWriteRequest(
            auditDataset,
            record.getAuditId(),
            payload,
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

        byte[] payload;
        try {
            payload = JsonUtils.toJson(record).getBytes(StandardCharsets.UTF_8);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Promise.ofException(e);
        }

        DataWriteRequest request = new DataWriteRequest(
            sharedDataset,
            record.getDataId(),
            payload,
            Map.of(
                "classification", record.getClassification().toString(),
                "createdAt", record.getCreatedAt().toString()
            )
        );

        return writeData(request);
    }

    // ==================== Private Methods ====================

    private <T> Promise<T> wrapFuture(CompletableFuture<T> future) {
        return wrapAsync(() -> future);
    }

    private Promise<Void> authorize(BridgeContext context, String resource, String action) {
        return checkAuthorized(context, resource, action)
            .then(allowed -> allowed
                ? Promise.complete()
                : Promise.ofException(new SecurityException("Not authorized for " + action + " on " + resource)));
    }

    private static String datasetResource(String datasetId) {
        return "dataset:" + Objects.requireNonNull(datasetId, DATASET_ID_CANNOT_BE_NULL);
    }

    private static String tenantResource(BridgeContext context) {
        return "tenant:" + context.getTenantId();
    }

    // ==================== Inner Types ====================

    /**
     * Data-Cloud client interface (to be implemented by data-cloud platform).
     */
    public interface DataCloudClient extends AsyncClient {
        @Override
        default Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        default Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        default Promise<Boolean> healthCheck() {
            return Promise.of(Boolean.TRUE);
        }

        @Override
        default boolean isRunning() {
            return true;
        }

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
        private final BridgeContext context;
        private final String resource;
        private final AtomicBoolean open = new AtomicBoolean(true);

        DataStreamImpl(
                String streamId,
                Object innerStream,
                boolean isReadStream,
                BridgeContext context,
                String resource) {
            this.streamId = streamId;
            this.innerStream = innerStream;
            this.isReadStream = isReadStream;
            this.context = context;
            this.resource = resource;
        }

        @Override
        public Promise<byte[]> readChunk() {
            requireStarted();
            if (!isReadStream) {
                return Promise.ofException(new IllegalStateException("Not a read stream"));
            }
            if (!open.get()) {
                return Promise.ofException(new IllegalStateException("Stream closed"));
            }

            return authorize(context, resource, "stream:chunk:read")
                .then(() -> executeWithRetry("readStreamChunk", context, resource, "stream:chunk:read", () ->
                    dataCloudClient.readStreamChunk(innerStream)));
        }

        @Override
        public Promise<Void> writeChunk(byte[] data) {
            requireStarted();
            if (isReadStream) {
                return Promise.ofException(new IllegalStateException("Not a write stream"));
            }
            if (!open.get()) {
                return Promise.ofException(new IllegalStateException("Stream closed"));
            }

            return authorize(context, resource, "stream:chunk:write")
                .then(() -> executeWithRetry("writeStreamChunk", context, resource, "stream:chunk:write", () ->
                    dataCloudClient.writeStreamChunk(innerStream, data)));
        }

        @Override
        public Promise<Void> close() {
            requireStarted();
            if (!open.compareAndSet(true, false)) {
                return Promise.complete();
            }

            activeStreams.remove(streamId);
            return authorize(context, resource, "stream:close")
                .then(() -> executeWithRetry("closeStream", context, resource, "stream:close", () ->
                    dataCloudClient.closeStream(innerStream)));
        }

        @Override
        public boolean isOpen() { return open.get(); }
    }
}
