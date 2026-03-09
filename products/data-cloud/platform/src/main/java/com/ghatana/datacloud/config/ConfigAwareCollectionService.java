package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.CompiledCollectionConfig;
import com.ghatana.datacloud.config.model.CompiledEventCollectionConfig;
import com.ghatana.datacloud.config.model.CompiledFieldConfig;
import com.ghatana.datacloud.config.model.ConfigKey;
import com.ghatana.platform.core.exception.ConfigurationException;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Configuration-aware collection service that integrates YAML-based configs
 * with runtime operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a unified interface for collection operations that combines: - YAML
 * configuration from ConfigRegistry (schema, indexes, storage, lifecycle) -
 * Runtime metadata from CollectionRepository (permissions, audit, tenant data)
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Acts as an adapter between config-driven architecture and existing
 * CollectionService: - Reads collection definitions from ConfigRegistry -
 * Validates runtime data against compiled config - Emits observability metrics
 * for all operations - Enforces multi-tenancy at config level
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConfigAwareCollectionService service = new ConfigAwareCollectionService(
 *     configRegistry, metricsCollector
 * );
 *
 * // Get collection config with caching
 * CompiledCollectionConfig config = runPromise(() ->
 *     service.getCollectionConfig("tenant-123", "users")
 * );
 *
 * // Validate entity against schema
 * ValidationResult result = runPromise(() ->
 *     service.validateEntity("tenant-123", "users", entityData)
 * );
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses immutable configs from ConfigRegistry.
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations are tenant-scoped. Tenant ID is required for all methods.
 *
 * @see ConfigRegistry
 * @see CompiledCollectionConfig
 * @doc.type class
 * @doc.purpose Configuration-aware adapter for collection operations
 * @doc.layer product
 * @doc.pattern Adapter, Facade
 */
public class ConfigAwareCollectionService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigAwareCollectionService.class);

    private final ConfigRegistry configRegistry;
    private final MetricsCollector metrics;

    /**
     * Creates a new configuration-aware collection service.
     *
     * @param configRegistry the config registry (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any argument is null
     */
    public ConfigAwareCollectionService(
            ConfigRegistry configRegistry,
            MetricsCollector metrics) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "ConfigRegistry must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Gets the compiled collection configuration for a tenant and collection
     * name.
     *
     * <p>
     * Returns the immutable, pre-compiled config from the registry cache.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise containing the compiled config, or exception if not found
     */
    public Promise<CompiledCollectionConfig> getCollectionConfig(String tenantId, String collectionName) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        long start = System.nanoTime();
        ConfigKey key = ConfigKey.collection(tenantId, collectionName);

        return configRegistry.getCollection(key)
                .then(configOpt -> {
                    if (configOpt.isEmpty()) {
                        metrics.incrementCounter("config.collection.miss",
                                "tenant", tenantId,
                                "collection", collectionName);
                        return Promise.ofException(new ConfigurationException(
                                String.format("Collection config not found: tenant=%s, name=%s",
                                        tenantId, collectionName)));
                    }

                    long durationNs = System.nanoTime() - start;
                    metrics.recordTimer("config.collection.get.duration", durationNs / 1_000_000);
                    metrics.incrementCounter("config.collection.hit",
                            "tenant", tenantId,
                            "collection", collectionName);

                    return Promise.of(configOpt.get());
                })
                .whenException(ex -> {
                    metrics.incrementCounter("config.collection.error",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to get collection config: tenant={}, collection={}",
                            tenantId, collectionName, ex);
                });
    }

    /**
     * Gets the compiled event collection configuration.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the event collection name (required)
     * @return Promise containing the compiled event config, or exception if not
     * found
     */
    public Promise<CompiledEventCollectionConfig> getEventCollectionConfig(
            String tenantId, String collectionName) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        ConfigKey key = ConfigKey.collection(tenantId, collectionName);
        long start = System.nanoTime();

        return configRegistry.getEventCollection(key)
                .then(configOpt -> {
                    if (configOpt.isEmpty()) {
                        metrics.incrementCounter("config.event-collection.miss",
                                "tenant", tenantId,
                                "collection", collectionName);
                        return Promise.ofException(new ConfigurationException(
                                String.format("Event collection config not found: tenant=%s, name=%s",
                                        tenantId, collectionName)));
                    }

                    long durationNs = System.nanoTime() - start;
                    metrics.recordTimer("config.event-collection.get.duration", durationNs / 1_000_000);
                    metrics.incrementCounter("config.event-collection.hit",
                            "tenant", tenantId,
                            "collection", collectionName);

                    return Promise.of(configOpt.get());
                })
                .whenException(ex -> {
                    metrics.incrementCounter("config.event-collection.error",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to get event collection config: tenant={}, collection={}",
                            tenantId, collectionName, ex);
                });
    }

    /**
     * Validates an entity against the collection's field schema.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityData the entity data as field name to value map (required)
     * @return Promise containing validation result
     */
    public Promise<ValidationResult> validateEntity(
            String tenantId,
            String collectionName,
            Map<String, Object> entityData) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(entityData, "Entity data must not be null");

        return getCollectionConfig(tenantId, collectionName)
                .then(config -> {
                    ValidationResult result = validateEntityAgainstConfig(config, entityData);

                    if (result.isValid()) {
                        metrics.incrementCounter("config.validation.success",
                                "tenant", tenantId,
                                "collection", collectionName);
                    } else {
                        metrics.incrementCounter("config.validation.failure",
                                "tenant", tenantId,
                                "collection", collectionName,
                                "errorCount", String.valueOf(result.errors().size()));
                    }

                    return Promise.of(result);
                });
    }

    /**
     * Gets all collection names for a tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise containing list of collection names
     */
    public Promise<List<String>> getCollectionNames(String tenantId) {
        validateTenantId(tenantId);

        return configRegistry.getCollectionNames(tenantId)
                .whenComplete((names, ex) -> {
                    if (ex == null) {
                        metrics.incrementCounter("config.list.success",
                                "tenant", tenantId,
                                "count", String.valueOf(names.size()));
                    } else {
                        metrics.incrementCounter("config.list.error",
                                "tenant", tenantId,
                                "error", ex.getClass().getSimpleName());
                    }
                });
    }

    /**
     * Gets the field configuration for a specific field in a collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param fieldName the field name (required)
     * @return Promise containing the field config, or exception if not found
     */
    public Promise<CompiledFieldConfig> getFieldConfig(
            String tenantId,
            String collectionName,
            String fieldName) {
        validateTenantId(tenantId);
        Objects.requireNonNull(fieldName, "Field name must not be null");

        return getCollectionConfig(tenantId, collectionName)
                .then(config -> {
                    CompiledFieldConfig field = config.getField(fieldName);
                    if (field == null) {
                        return Promise.ofException(new ConfigurationException(
                                String.format("Field not found: tenant=%s, collection=%s, field=%s",
                                        tenantId, collectionName, fieldName)));
                    }
                    return Promise.of(field);
                });
    }

    /**
     * Checks if a collection is event-sourced.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise containing true if event-sourced
     */
    public Promise<Boolean> isEventSourced(String tenantId, String collectionName) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        ConfigKey key = ConfigKey.collection(tenantId, collectionName);
        return configRegistry.getEventCollection(key)
                .then(opt -> Promise.of(opt.isPresent()));
    }

    /**
     * Registers a listener for configuration reload events.
     *
     * @param listener the reload listener (required)
     */
    public void registerReloadListener(ConfigRegistry.ConfigReloadListener listener) {
        Objects.requireNonNull(listener, "Listener must not be null");
        configRegistry.registerReloadListener(listener);
    }

    /**
     * Triggers a reload of all configurations.
     *
     * @return Promise completing when reload is done
     */
    public Promise<Void> reloadConfigs() {
        long start = System.nanoTime();
        return configRegistry.reloadAll()
                .whenComplete((v, ex) -> {
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    if (ex == null) {
                        metrics.recordTimer("config.reload.duration", durationMs);
                        metrics.incrementCounter("config.reload.success");
                        logger.info("Configuration reload completed in {}ms", durationMs);
                    } else {
                        metrics.incrementCounter("config.reload.error",
                                "error", ex.getClass().getSimpleName());
                        logger.error("Configuration reload failed", ex);
                    }
                });
    }

    /**
     * Gets the current configuration version.
     *
     * @return the version number
     */
    public long getConfigVersion() {
        return configRegistry.getVersion();
    }

    // ========== Private Helpers ==========
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID must not be null or blank");
        }
    }

    private ValidationResult validateEntityAgainstConfig(
            CompiledCollectionConfig config,
            Map<String, Object> entityData) {

        List<ValidationError> errors = new java.util.ArrayList<>();

        // Check required fields
        for (CompiledFieldConfig field : config.fields()) {
            if (field.required() && !entityData.containsKey(field.name())) {
                errors.add(new ValidationError(
                        field.name(),
                        "REQUIRED_FIELD_MISSING",
                        String.format("Required field '%s' is missing", field.name())));
            }
        }

        // Check field types and constraints
        for (Map.Entry<String, Object> entry : entityData.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            CompiledFieldConfig field = config.getField(fieldName);
            if (field == null) {
                // Unknown field - could warn or error based on config
                continue;
            }

            // Type validation
            if (value != null && !isValidType(field, value)) {
                errors.add(new ValidationError(
                        fieldName,
                        "TYPE_MISMATCH",
                        String.format("Field '%s' expected type %s but got %s",
                                fieldName, field.type(), value.getClass().getSimpleName())));
            }

            // Constraint validation (min/max length, etc.)
            if (value instanceof String strValue) {
                if (field.maxLength() != null && strValue.length() > field.maxLength()) {
                    errors.add(new ValidationError(
                            fieldName,
                            "MAX_LENGTH_EXCEEDED",
                            String.format("Field '%s' exceeds max length of %d",
                                    fieldName, field.maxLength())));
                }
                if (field.minLength() != null && strValue.length() < field.minLength()) {
                    errors.add(new ValidationError(
                            fieldName,
                            "MIN_LENGTH_NOT_MET",
                            String.format("Field '%s' is shorter than min length of %d",
                                    fieldName, field.minLength())));
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private boolean isValidType(CompiledFieldConfig field, Object value) {
        return switch (field.type()) {
            case STRING ->
                value instanceof String;
            case INTEGER ->
                value instanceof Integer || value instanceof Long;
            case LONG ->
                value instanceof Long || value instanceof Integer;
            case DOUBLE ->
                value instanceof Float || value instanceof Double || value instanceof Number;
            case BOOLEAN ->
                value instanceof Boolean;
            case TIMESTAMP, DATE, TIME ->
                value instanceof java.time.Instant
                || value instanceof java.time.OffsetDateTime
                || value instanceof java.time.LocalDate
                || value instanceof java.time.LocalTime
                || value instanceof String; // ISO strings accepted
            case UUID ->
                value instanceof java.util.UUID || value instanceof String;
            case JSON, OBJECT ->
                value instanceof Map;
            case ARRAY ->
                value instanceof List;
            case BINARY ->
                value instanceof byte[];
            case DECIMAL ->
                value instanceof java.math.BigDecimal || value instanceof Number;
            case DURATION ->
                value instanceof java.time.Duration || value instanceof String;
            case ENUM ->
                value instanceof String; // Enum values as strings
        };
    }

    /**
     * Validation result containing success status and errors.
     *
     * @param valid true if validation passed
     * @param errors list of validation errors (empty if valid)
     */
    public record ValidationResult(boolean valid, List<ValidationError> errors) {

        

    public boolean isValid() {
        return valid;
    }
}

/**
 * Individual validation error.
 *
 * @param field the field name that failed validation
 * @param code the error code
 * @param message human-readable error message
 */
public record ValidationError(String field, String code, String message) {

}
}
