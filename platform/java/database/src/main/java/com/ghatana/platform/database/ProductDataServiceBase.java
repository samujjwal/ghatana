package com.ghatana.platform.database;

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
 * Generic product data service base class.
 * <p>
 * Provides common data access patterns for product services without importing
 * kernel implementation classes. Implements KernelLifecycleAware directly.
 * </p>
 * <p>
 * Products can extend this class and provide:
 * - Service name for audit/logging
 * - Dataset initialization logic
 * - Custom metadata enrichment strategy via {@link MetadataEnrichmentStrategy}
 * - Custom owner scope inference via {@link OwnerScopeStrategy}
 * </p>
 *
 * @doc.type class
 * @doc.purpose Product data service base class — common data access patterns
 * @doc.layer platform
 * @doc.pattern Template Method, Service
 * @since 1.0.0
 */
public abstract class ProductDataServiceBase implements KernelLifecycleAware {

    protected final DataCloudKernelAdapter dataCloud;
    protected final CrossScopeAuditService auditService;
    protected final Executor executor;
    protected volatile boolean running = false;
    private final MetadataEnrichmentStrategy metadataStrategy;
    private final OwnerScopeStrategy ownerScopeStrategy;

    protected ProductDataServiceBase(KernelContext context) {
        this(context, ForkJoinPool.commonPool(), new DefaultMetadataEnrichmentStrategy(), new DefaultOwnerScopeStrategy());
    }

    protected ProductDataServiceBase(
            KernelContext context,
            Executor executor,
            MetadataEnrichmentStrategy metadataStrategy,
            OwnerScopeStrategy ownerScopeStrategy) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
        this.auditService = context.getOptionalDependency(CrossScopeAuditService.class).orElse(null);
        this.executor = executor != null ? executor : ForkJoinPool.commonPool();
        this.metadataStrategy = metadataStrategy != null ? metadataStrategy : new DefaultMetadataEnrichmentStrategy();
        this.ownerScopeStrategy = ownerScopeStrategy != null ? ownerScopeStrategy : new DefaultOwnerScopeStrategy();
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
            return Promise.ofException(new IllegalStateException(getName() + " service is not running"));
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
            return Promise.ofException(new IllegalStateException(getName() + " service is not running"));
        }

        DataReadRequest request = new DataReadRequest(datasetId, recordId, Map.of());

        return dataCloud.readData(request)
            .<Optional<T>>map(result -> result == null ? Optional.empty() : Optional.ofNullable(deserialize(result.getData(), type)));
    }

    /**
     * Update a record in the specified dataset.
     */
    protected <T> Promise<T> updateRecord(String datasetId, String recordId, T record,
                                         Map<String, String> metadata, String entityType, int version) {
        if (!running) {
            return Promise.ofException(new IllegalStateException(getName() + " service is not running"));
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
            return Promise.ofException(new IllegalStateException(getName() + " service is not running"));
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
            return Promise.ofException(new IllegalStateException(getName() + " service is not running"));
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
        );
    }

    /**
     * Audit an action using kernel audit service.
     */
    protected Promise<Void> audit(String action, String entityId, String details) {
        return audit(action, entityId, details, Map.of());
    }

    /**
     * Audit an action with additional metadata.
     * Skips audit gracefully if audit service is not configured.
     */
    protected Promise<Void> audit(String action, String entityId, String details,
                                 Map<String, String> metadata) {
        if (!running) {
            return Promise.ofException(new IllegalStateException(getName() + " service is not running"));
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
        );
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

        Map<String, String> metadata = new HashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("service", getName());

        for (int i = 0; i < keyValuePairs.length; i += 2) {
            metadata.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }

        return metadata;
    }

    private Map<String, String> enrichMutationMetadata(
            String action,
            String recordId,
            String entityType,
            Map<String, String> metadata) {
        String normalizedEntityType = normalizeToken(entityType, "record");
        String normalizedAction = normalizeToken(action, "write");
        HashMap<String, String> enriched = new HashMap<>(metadata == null ? Map.of() : metadata);

        String operation = getName().toLowerCase(Locale.ROOT) + "_" + normalizedEntityType + "_" + normalizedAction;
        String correlationId = enriched.getOrDefault("correlationId", generateCorrelationId(operation));
        
        enriched.put("operation", operation);
        enriched.put("correlationId", correlationId);

        // Require explicit tenant and principal context - no defaults for security
        if (!enriched.containsKey("tenantId") || enriched.get("tenantId") == null || enriched.get("tenantId").isBlank()) {
            throw new IllegalArgumentException("tenantId is required in metadata for regulated data operations");
        }
        if (!enriched.containsKey("principalId") || enriched.get("principalId") == null || enriched.get("principalId").isBlank()) {
            throw new IllegalArgumentException("principalId is required in metadata for regulated data operations");
        }

        enriched.putIfAbsent("idempotencyKey", normalizedEntityType + "-" + normalizedAction + "-" + recordId);
        enriched.putIfAbsent("auditClassification", normalizedEntityType.toUpperCase(Locale.ROOT) + "_" + normalizedAction.toUpperCase(Locale.ROOT));
        enriched.putIfAbsent("dataOwnerScope", ownerScopeStrategy.inferOwnerScope(enriched, normalizedEntityType, recordId));

        // Allow custom enrichment strategy to add product-specific metadata
        metadataStrategy.enrich(enriched, action, recordId, entityType);

        return Map.copyOf(enriched);
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

    protected String generateCorrelationId(String operation) {
        return operation + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Template method for dataset initialization. Subclasses override to define their datasets.
     */
    protected abstract Promise<Void> initializeDatasets();

    /**
     * Template method for service stop hook. Override to add custom stop logic.
     */
    protected Promise<Void> onStop() {
        return Promise.complete();
    }

    /**
     * Get the service name.
     */
    public abstract String getName();

    /**
     * Strategy interface for custom metadata enrichment.
     */
    public interface MetadataEnrichmentStrategy {
        void enrich(Map<String, String> metadata, String action, String recordId, String entityType);
    }

    /**
     * Strategy interface for owner scope inference.
     */
    public interface OwnerScopeStrategy {
        String inferOwnerScope(Map<String, String> metadata, String entityType, String recordId);
    }

    /**
     * Default metadata enrichment strategy - can be overridden by products.
     */
    public static class DefaultMetadataEnrichmentStrategy implements MetadataEnrichmentStrategy {
        @Override
        public void enrich(Map<String, String> metadata, String action, String recordId, String entityType) {
            // Default implementation - no additional enrichment
        }
    }

    /**
     * Default owner scope strategy - uses entity type and record ID.
     */
    public static class DefaultOwnerScopeStrategy implements OwnerScopeStrategy {
        @Override
        public String inferOwnerScope(Map<String, String> metadata, String entityType, String recordId) {
            // Try to find owner-specific fields first
            Optional<String> ownerField = firstPresent(metadata, "patientId", "subjectId", "accountId", "traderId", "userId");
            if (ownerField.isPresent()) {
                return entityType + ":" + ownerField.get();
            }
            return entityType + ":" + recordId;
        }

        private Optional<String> firstPresent(Map<String, String> metadata, String... keys) {
            for (String key : keys) {
                String value = metadata.get(key);
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }
}
