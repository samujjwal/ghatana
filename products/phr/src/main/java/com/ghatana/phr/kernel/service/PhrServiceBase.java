package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataDeleteRequest;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.kernel.util.TypedDataSerializer;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * PHR-specific data service base class.
 *
 * <p>Provides common data access patterns for PHR services without importing
 * kernel implementation classes. Implements KernelLifecycleAware directly.</p>
 *
 * @doc.type class
 * @doc.purpose PHR data service base class — common data access patterns
 * @doc.layer product
 * @doc.pattern Template Method, Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public abstract class PhrServiceBase implements KernelLifecycleAware {

    protected final DataCloudKernelAdapter dataCloud;
    protected final CrossScopeAuditService auditService;
    protected final Executor executor;
    protected volatile boolean running = false;

    protected PhrServiceBase(KernelContext context) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
        this.auditService = context.getOptionalDependency(CrossScopeAuditService.class).orElse(null);
        this.executor = ForkJoinPool.commonPool();
    }

    protected PhrServiceBase(KernelContext context, Executor executor) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
        this.auditService = context.getOptionalDependency(CrossScopeAuditService.class).orElse(null);
        this.executor = executor != null ? executor : ForkJoinPool.commonPool();
    }

    @Override
    public Promise<Void> start() {
        running = true;
        return initializeDatasets();
    }

    @Override
    public Promise<Void> stop() {
        running = false;
        return onStop();
    }

    @Override
    public boolean isHealthy() {
        return running;
    }

    /**
     * Create a record in the specified dataset.
     */
    protected <T> Promise<T> createRecord(String datasetId, String recordId, T record,
                                         Map<String, String> metadata, String entityType, int version) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return Promise.ofBlocking(executor, () -> serialize(record, entityType, version))
            .then(data -> {
                DataWriteRequest request = new DataWriteRequest(datasetId, recordId, data, metadata);
                return dataCloud.writeData(request)
                    .map($ -> record)
                    .then(result -> audit("CREATE", recordId, entityType + " created", metadata).map($ -> result));
            });
    }

    /**
     * Read a record from the specified dataset.
     */
    protected <T> Promise<Optional<T>> readRecord(String datasetId, String recordId, Class<T> type) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        DataReadRequest request = new DataReadRequest(datasetId, recordId, Map.of());

        return dataCloud.readData(request)
            .<Optional<T>>map(result -> result == null ? Optional.empty() : Optional.ofNullable(deserialize(result.getData(), type)))
            .whenException(e -> Promise.of(Optional.empty()));
    }

    /**
     * Update a record in the specified dataset.
     */
    protected <T> Promise<T> updateRecord(String datasetId, String recordId, T record,
                                         Map<String, String> metadata, String entityType, int version) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return Promise.ofBlocking(executor, () -> serialize(record, entityType, version))
            .then(data -> {
                DataWriteRequest request = new DataWriteRequest(datasetId, recordId, data, metadata);
                return dataCloud.writeData(request)
                    .map($ -> record)
                    .then(result -> audit("UPDATE", recordId, entityType + " updated", metadata).map($ -> result));
            });
    }

    /**
     * Delete a record from the specified dataset.
     */
    protected Promise<Void> deleteRecord(String datasetId, String recordId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return dataCloud.deleteData(new DataDeleteRequest(datasetId, recordId));
    }

    /**
     * Query records from the specified dataset.
     */
    protected <T> Promise<List<T>> queryRecords(String datasetId, String query,
                                                Map<String, Object> parameters,
                                                int limit, int offset, Class<T> type) {
        if (!running) {
            return Promise.of(List.of());
        }

        DataQueryRequest request = new DataQueryRequest(datasetId, query, parameters, limit, offset);

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserialize(r.getData(), type))
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * Query records with custom result mapping.
     */
    protected <T, R> Promise<List<R>> queryRecordsWithMapping(String datasetId, String query,
                                                              Map<String, Object> parameters,
                                                              int limit, int offset,
                                                              Class<T> type,
                                                              Function<T, R> mapper) {
        return queryRecords(datasetId, query, parameters, limit, offset, type)
            .map(records -> records.stream().map(mapper).toList());
    }

    /**
     * Create a schema in DataCloud.
     */
    protected Promise<Void> createSchema(String datasetId, Map<String, String> schema,
                                        Map<String, String> options) {
        return dataCloud.createSchema(
            new SchemaCreateRequest(datasetId, schema, options)
        ).whenException(e -> {
            // Schema may already exist - log but don't fail
        });
    }

    /**
     * Audit an action using kernel audit service.
     */
    protected Promise<Void> audit(String action, String entityId, String details) {
        return audit(action, entityId, details, Map.of());
    }

    /**
     * Audit an action with additional metadata.
     */
    protected Promise<Void> audit(String action, String entityId, String details,
                                 Map<String, String> metadata) {
        if (!running) {
            return Promise.complete();
        }

        if (auditService == null) {
            return Promise.complete();
        }

        return auditService.recordAuditEvent(
            action,
            getName(),
            entityId,
            details,
            metadata
        ).whenException(e -> {
            // Audit failure should not fail the operation
        });
    }

    /**
     * Serialize an object to bytes.
     */
    protected <T> byte[] serialize(T object, String typeName, int version) {
        return TypedDataSerializer.toBytes(object, typeName, version);
    }

    /**
     * Deserialize bytes to an object.
     */
    protected <T> T deserialize(byte[] data, Class<T> type) {
        if (data == null) {
            return null;
        }
        return TypedDataSerializer.fromBytes(data, type);
    }

    /**
     * Generate a unique ID with the specified prefix.
     */
    protected String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Generate a unique ID without prefix.
     */
    protected String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Ensures the service is running; throws exception if not.
     *
     * @throws IllegalStateException if service is not running
     */
    protected void ensureRunning() {
        if (!running) {
            throw new IllegalStateException(getName() + " service is not running");
        }
    }

    /**
     * Validate a required field.
     */
    protected void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value instanceof String && ((String) value).isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    /**
     * Create audit metadata with timestamp.
     */
    protected Map<String, String> createAuditMetadata(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even");
        }

        Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("service", getName());

        for (int i = 0; i < keyValuePairs.length; i += 2) {
            metadata.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }

        return metadata;
    }

    /**
     * Template method for dataset initialization. Subclasses override to define their datasets.
     *
     * @return Promise that completes when initialization is done
     */
    protected abstract Promise<Void> initializeDatasets();

    /**
     * Template method for service stop hook. Override to add custom stop logic.
     *
     * @return Promise that completes when stop is done
     */
    protected Promise<Void> onStop() {
        return Promise.complete();
    }

    /**
     * Get the service name.
     *
     * @return the service name for audit trail and logging
     */
    public abstract String getName();
}
