package com.ghatana.finance.kernel.service;

import com.ghatana.finance.service.FinanceTraceContext;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * Finance-specific data service base class.
 *
 * <p>Provides common data access patterns for Finance services without importing
 * kernel implementation classes. Implements KernelLifecycleAware directly.</p>
 *
 * @doc.type class
 * @doc.purpose Finance data service base class — common data access patterns
 * @doc.layer product
 * @doc.pattern Template Method, Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public abstract class FinanceServiceBase implements KernelLifecycleAware {

    protected final DataCloudKernelAdapter dataCloud;
    protected final CrossScopeAuditService auditService;
    protected final Executor executor;
    protected volatile boolean running = false;

    protected FinanceServiceBase(KernelContext context) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
        this.auditService = context.getOptionalDependency(CrossScopeAuditService.class).orElse(null);
        this.executor = ForkJoinPool.commonPool();
    }

    protected FinanceServiceBase(KernelContext context, Executor executor) {
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
                Map<String, String> enrichedMetadata = enrichMutationMetadata(
                    "create",
                    recordId,
                    entityType,
                    metadata
                );
                DataWriteRequest request = new DataWriteRequest(datasetId, recordId, data, enrichedMetadata);
                return dataCloud.writeData(request)
                    .map($ -> record)
                    .then(result -> audit("CREATE", recordId, entityType + " created", enrichedMetadata).map($ -> result));
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
                Map<String, String> enrichedMetadata = enrichMutationMetadata(
                    "update",
                    recordId,
                    entityType,
                    metadata
                );
                DataWriteRequest request = new DataWriteRequest(datasetId, recordId, data, enrichedMetadata);
                return dataCloud.writeData(request)
                    .map($ -> record)
                    .then(result -> audit("UPDATE", recordId, entityType + " updated", enrichedMetadata).map($ -> result));
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

    private Map<String, String> enrichMutationMetadata(
            String action,
            String recordId,
            String entityType,
            Map<String, String> metadata) {
        String normalizedEntityType = normalizeToken(entityType, "record");
        String normalizedAction = normalizeToken(action, "write");
        HashMap<String, Object> enriched = new HashMap<>(metadata == null ? Map.of() : metadata);

        String operation = "finance_" + normalizedEntityType + "_" + normalizedAction;
        String correlationId = Objects.toString(
            enriched.getOrDefault("correlation_id", FinanceTraceContext.newCorrelationId()),
            FinanceTraceContext.newCorrelationId()
        );
        enriched = new HashMap<>(FinanceTraceContext.metadata(correlationId, operation, enriched));

        enriched.putIfAbsent("tenant_id", "default");
        enriched.putIfAbsent("principal_id", inferPrincipalId(enriched));
        enriched.putIfAbsent("idempotency_key", normalizedEntityType + "-" + normalizedAction + "-" + recordId);
        enriched.putIfAbsent(
            "audit_classification",
            normalizedEntityType.toUpperCase(Locale.ROOT) + "_" + normalizedAction.toUpperCase(Locale.ROOT)
        );
        enriched.putIfAbsent("data_owner_scope", inferDataOwnerScope(enriched, normalizedEntityType, recordId));

        return enriched.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Objects.toString(entry.getValue(), "")
            ));
    }

    private String inferPrincipalId(Map<String, Object> metadata) {
        return firstPresent(metadata, "traderId", "accountId", "clientId", "debitAccount", "creditAccount")
            .orElse("system");
    }

    private String inferDataOwnerScope(Map<String, Object> metadata, String entityType, String recordId) {
        return firstPresent(metadata, "accountId", "traderId", "clientId", "debitAccount", "creditAccount")
            .map(value -> entityType + ":" + value)
            .orElse(entityType + ":" + recordId);
    }

    private Optional<String> firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null) {
                String text = Objects.toString(value, "");
                if (!text.isBlank()) {
                    return Optional.of(text);
                }
            }
        }
        return Optional.empty();
    }

    private String normalizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value
            .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
            .replaceAll("[^A-Za-z0-9]+", "-")
            .replaceAll("^-+|-+$", "")
            .toLowerCase(Locale.ROOT);
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
}
