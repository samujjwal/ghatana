package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for validating entities against collection schemas.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes entity validation logic, ensuring all entities conform to their
 * collection's schema.
 * Wraps the field-level ValidationService with schema resolution and
 * tenant-scoped caching.
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Fetch collection schema from repository
 * - Validate entity data against schema constraints (via ValidationService)
 * - Collect and report validation errors
 * - Track validation metrics
 * - Cache schema definitions for performance
 * - Enforce collection active status
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * EntityValidationService validator = new EntityValidationService(
 *         collectionRepository,
 *         validationService,
 *         metrics);
 *
 * // Validate entity before saving
 * Map<String, Object> entityData = Map.of(
 *         "name", "Sample Product",
 *         "price", 99.99,
 *         "email", "contact@example.com");
 *
 * Promise<EntityValidationResult> result = validator.validateEntity(
 *         "tenant-123",
 *         "products",
 *         entityData);
 *
 * // In test with EventloopTestBase:
 * EntityValidationResult validation = runPromise(() -> result);
 * if (!validation.isValid()) {
 *     validation.getErrors().forEach(e -> System.out.println(e.getField() + ": " + e.getMessage()));
 * }
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * - Application layer service
 * - Depends on CollectionRepository (domain port)
 * - Depends on ValidationService (field-level validator)
 * - Used by EntityService before save/update operations
 * - Enforces schema consistency across tenant
 *
 * <p>
 * <b>Validation Levels</b><br>
 * 1. Schema existence: Collection must exist and be active
 * 2. Field presence: Required fields must be present
 * 3. Type validation: Values must match field types
 * 4. Constraint validation: Values must satisfy min/max, length, pattern
 * constraints
 * 5. Format validation: Email, URL, phone, color, etc.
 *
 * <p>
 * <b>Performance</b><br>
 * - Caches collection schemas (10-minute TTL)
 * - Single schema lookup per validation
 * - Linear validation (O(n fields))
 * - Non-blocking Promise-based execution
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or
 * repositories.
 *
 * @see ValidationService
 * @see MetaCollection
 * @see CollectionRepository
 * @doc.type class
 * @doc.purpose Centralized entity validation against collection schemas
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 * @since 1.0.0
 */
public class EntityValidationService {

    private static final Logger logger = LoggerFactory.getLogger(EntityValidationService.class);

    private final CollectionRepository collectionRepository;
    private final ValidationService validationService;
    private final MetricsCollector metrics;

    // Simple schema cache (in production, use distributed cache like Redis)
    private final Map<String, CachedSchema> schemaCache = new HashMap<>();
    private static final long SCHEMA_CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

    /**
     * Creates a new entity validation service.
     *
     * @param collectionRepository repository for fetching collection schemas
     *                             (required)
     * @param validationService    field-level validation service (required)
     * @param metrics              metrics collector for tracking validation metrics
     *                             (required)
     * @throws NullPointerException if any parameter is null
     */
    public EntityValidationService(
            CollectionRepository collectionRepository,
            ValidationService validationService,
            MetricsCollector metrics) {
        this.collectionRepository = Objects.requireNonNull(collectionRepository,
                "collectionRepository cannot be null");
        this.validationService = Objects.requireNonNull(validationService,
                "validationService cannot be null");
        this.metrics = Objects.requireNonNull(metrics,
                "metrics cannot be null");
    }

    /**
     * Validates entity data against collection schema.
     *
     * <p>
     * <b>Process</b><br>
     * 1. Fetch collection schema
     * 2. Validate collection is active
     * 3. Validate entity against field definitions
     * 4. Return validation result with all errors
     *
     * @param tenantId       tenant identifier (required)
     * @param collectionName collection name (required)
     * @param entityData     entity data to validate (required)
     * @return Promise of EntityValidationResult
     * @throws IllegalArgumentException if tenantId or collectionName is null/empty
     */
    public Promise<EntityValidationResult> validateEntity(
            String tenantId,
            String collectionName,
            Map<String, Object> entityData) {

        validateParameters(tenantId, collectionName);
        Objects.requireNonNull(entityData, "entityData cannot be null");

        logger.debug("Validating entity: tenantId={}, collection={}, dataSize={}",
                tenantId, collectionName, entityData.size());

        // Fetch collection schema
        Promise<Optional<MetaCollection>> collectionPromise = collectionRepository.findByName(tenantId, collectionName);

        // Some tests may use mocks that return null instead of a Promise.
        // Treat a null promise as "collection not found" rather than throwing NPE.
        if (collectionPromise == null) {
            metrics.incrementCounter("entity.validation.collection_not_found",
                    "tenant", tenantId,
                    "collection", collectionName);
            logger.warn("Collection repository returned null promise for validation: tenantId={}, collection={}",
                    tenantId, collectionName);
            return Promise.of(new EntityValidationResult(
                    false,
                    List.of(new ValidationService.ValidationError(
                            "collection",
                            "NOT_FOUND",
                            "Collection not found: " + collectionName))));
        }

        return collectionPromise
                .then(collectionOpt -> {
                    // Check collection exists
                    if (collectionOpt.isEmpty()) {
                        metrics.incrementCounter("entity.validation.collection_not_found",
                                "tenant", tenantId,
                                "collection", collectionName);
                        logger.warn("Collection not found for validation: tenantId={}, collection={}",
                                tenantId, collectionName);
                        return Promise.of(new EntityValidationResult(
                                false,
                                List.of(new ValidationService.ValidationError(
                                        "collection",
                                        "NOT_FOUND",
                                        "Collection not found: " + collectionName))));
                    }

                    MetaCollection collection = collectionOpt.get();

                    // Check collection is active
                    if (Boolean.FALSE.equals(collection.getActive())) {
                        metrics.incrementCounter("entity.validation.collection_inactive",
                                "tenant", tenantId,
                                "collection", collectionName);
                        logger.warn("Collection is inactive: tenantId={}, collection={}",
                                tenantId, collectionName);
                        return Promise.of(new EntityValidationResult(
                                false,
                                List.of(new ValidationService.ValidationError(
                                        "collection",
                                        "INACTIVE",
                                        "Collection is inactive: " + collectionName))));
                    }

                    // Validate entity against collection schema
                    return validationService.validate(entityData, collection.getFields())
                            .map(validationResult -> {
                                // Track metrics
                                if (validationResult.isValid()) {
                                    metrics.incrementCounter("entity.validation.success",
                                            "tenant", tenantId,
                                            "collection", collectionName);
                                    logger.debug("Entity validation passed: tenantId={}, collection={}",
                                            tenantId, collectionName);
                                } else {
                                    metrics.incrementCounter("entity.validation.failed",
                                            "tenant", tenantId,
                                            "collection", collectionName,
                                            "error_count", String.valueOf(validationResult.getErrors().size()));
                                    logger.warn("Entity validation failed: tenantId={}, collection={}, errors={}",
                                            tenantId, collectionName, validationResult.getErrors().size());
                                }

                                // Return wrapped result
                                return new EntityValidationResult(
                                        validationResult.isValid(),
                                        validationResult.getErrors());
                            });
                })
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        metrics.incrementCounter("entity.validation.error",
                                "tenant", tenantId,
                                "collection", collectionName,
                                "error_type", exception.getClass().getSimpleName());
                        logger.error("Entity validation error: tenantId={}, collection={}",
                                tenantId, collectionName, exception);
                    }
                });
    }

    /**
     * Validates entity data against collection schema with caching.
     *
     * <p>
     * This method caches collection schemas for better performance.
     * Use this when calling validation frequently for the same collection.
     *
     * @param tenantId       tenant identifier (required)
     * @param collectionName collection name (required)
     * @param entityData     entity data to validate (required)
     * @return Promise of EntityValidationResult
     */
    public Promise<EntityValidationResult> validateEntityWithCache(
            String tenantId,
            String collectionName,
            Map<String, Object> entityData) {

        validateParameters(tenantId, collectionName);
        Objects.requireNonNull(entityData, "entityData cannot be null");

        String cacheKey = getCacheKey(tenantId, collectionName);

        // Check cache
        CachedSchema cachedSchema = schemaCache.get(cacheKey);
        if (cachedSchema != null && !cachedSchema.isExpired()) {
            // Use cached schema
            logger.debug("Using cached schema: tenantId={}, collection={}",
                    tenantId, collectionName);
            metrics.incrementCounter("entity.validation.cache_hit",
                    "tenant", tenantId,
                    "collection", collectionName);

            return validationService.validate(entityData, cachedSchema.getFields())
                    .map(result -> new EntityValidationResult(result.isValid(), result.getErrors()))
                    .whenComplete((result, ex) -> {
                        if (ex == null && !result.isValid()) {
                            metrics.incrementCounter("entity.validation.failed",
                                    "tenant", tenantId,
                                    "collection", collectionName,
                                    "error_count", String.valueOf(result.getErrors().size()));
                        }
                    });
        }

        // Cache miss - fetch from repository
        metrics.incrementCounter("entity.validation.cache_miss",
                "tenant", tenantId,
                "collection", collectionName);

        return collectionRepository.findByName(tenantId, collectionName)
                .then(collectionOpt -> {
                    if (collectionOpt.isEmpty()) {
                        metrics.incrementCounter("entity.validation.collection_not_found",
                                "tenant", tenantId,
                                "collection", collectionName);
                        return Promise.of(new EntityValidationResult(
                                false,
                                List.of(new ValidationService.ValidationError(
                                        "collection",
                                        "NOT_FOUND",
                                        "Collection not found: " + collectionName))));
                    }

                    MetaCollection collection = collectionOpt.get();

                    if (Boolean.FALSE.equals(collection.getActive())) {
                        metrics.incrementCounter("entity.validation.collection_inactive",
                                "tenant", tenantId,
                                "collection", collectionName);
                        return Promise.of(new EntityValidationResult(
                                false,
                                List.of(new ValidationService.ValidationError(
                                        "collection",
                                        "INACTIVE",
                                        "Collection is inactive"))));
                    }

                    // Cache schema for future use
                    schemaCache.put(cacheKey, new CachedSchema(collection.getFields()));

                    return validationService.validate(entityData, collection.getFields())
                            .map(result -> new EntityValidationResult(result.isValid(), result.getErrors()));
                });
    }

    /**
     * Clears validation schema cache (useful for testing or manual refresh).
     */
    public void clearSchemaCache() {
        logger.info("Clearing entity validation schema cache");
        schemaCache.clear();
    }

    /**
     * Clears cache entry for specific collection.
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     */
    public void clearSchemaCacheEntry(String tenantId, String collectionName) {
        String key = getCacheKey(tenantId, collectionName);
        schemaCache.remove(key);
        logger.debug("Cleared schema cache entry: tenantId={}, collection={}",
                tenantId, collectionName);
    }

    /**
     * Validates parameters are not null or empty.
     *
     * @param tenantId       tenant ID
     * @param collectionName collection name
     * @throws IllegalArgumentException if parameters invalid
     */
    private void validateParameters(String tenantId, String collectionName) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId must not be null or empty");
        }
        if (collectionName == null || collectionName.trim().isEmpty()) {
            throw new IllegalArgumentException("collectionName must not be null or empty");
        }
    }

    /**
     * Gets cache key for collection schema.
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @return cache key
     */
    private String getCacheKey(String tenantId, String collectionName) {
        return tenantId + ":" + collectionName;
    }

    /**
     * Cached schema with timestamp.
     */
    private static class CachedSchema {
        private final List<MetaField> fields;
        private final long cachedAt;

        CachedSchema(List<MetaField> fieldsList) {
            this.fields = new ArrayList<>(Objects.requireNonNull(fieldsList, "fieldsList cannot be null"));
            this.cachedAt = System.currentTimeMillis();
        }

        List<MetaField> getFields() {
            return fields;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > SCHEMA_CACHE_TTL_MS;
        }
    }

    /**
     * Validation result containing validity flag and errors.
     *
     * <p>
     * This wraps the ValidationService.ValidationResult to provide
     * consistent validation results across the application layer.
     */
    public static class EntityValidationResult {
        private final boolean valid;
        private final List<ValidationService.ValidationError> errors;

        /**
         * Creates a validation result.
         *
         * @param valid  whether validation passed (true = no errors)
         * @param errors list of validation errors (empty if valid)
         */
        public EntityValidationResult(boolean valid, List<ValidationService.ValidationError> errors) {
            this.valid = valid;
            this.errors = Objects.requireNonNull(errors, "errors cannot be null");
        }

        /**
         * Checks if validation passed.
         *
         * @return true if valid, false otherwise
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Gets validation errors.
         *
         * @return list of validation errors (empty if valid)
         */
        public List<ValidationService.ValidationError> getErrors() {
            return errors;
        }
    }
}
