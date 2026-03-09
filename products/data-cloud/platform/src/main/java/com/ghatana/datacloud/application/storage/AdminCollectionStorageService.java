package com.ghatana.datacloud.application.storage;

import com.ghatana.platform.core.exception.BaseException;
import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import com.ghatana.datacloud.entity.storage.StorageBackendType;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.entity.storage.StorageProfile;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application service for managing per-collection storage configurations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides operations to assign storage profiles to individual collections,
 * enabling per-collection customization of backend preferences while
 * maintaining tenant isolation. Supports routing policy configuration, backend
 * selection, and fallover strategies.
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Assign storage profiles to collections (per-tenant) - Retrieve collection
 * storage configurations - Update storage routing policies - Test backend
 * routing for specific collections - Enforce tenant isolation across all
 * operations - Emit metrics for observability
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * AdminCollectionStorageService service = new AdminCollectionStorageService(
 *         connectorRegistry,
 *         metrics);
 *
 * // Assign hot profile to "products" collection
 * Promise<CollectionStorageProfile> mapping = service.assignStorageProfile(
 *         "tenant-1",
 *         "products",
 *         "hot-profile",
 *         "postgres-primary",
 *         List.of("postgres-secondary"));
 *
 * // Get current routing for collection
 * Promise<Optional<CollectionStorageProfile>> routing = service.getCollectionStorage(
 *         "tenant-1",
 *         "products");
 *
 * // Test routing with backends
 * Promise<TestRoutingResponse> test = service.testCollectionRouting(
 *         "tenant-1",
 *         "products");
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * - Application service in hexagonal architecture (application layer) - Uses:
 * Connector registry, metrics collector - Used by:
 * AdminCollectionStorageHttpAdapter - Enforces: Tenant isolation, validation,
 * error handling
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations require tenantId. Collection storage configurations are
 * tenant-scoped. Tenant isolation is enforced at all layers: storage,
 * retrieval, and routing.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Stateless service. All state managed in thread-safe data structures
 * (ConcurrentHashMap). Metrics collection is atomic.
 *
 * @see CollectionStorageProfile
 * @see AdminStorageManagementService
 * @see StorageProfile
 * @doc.type class
 * @doc.purpose Application service for collection storage configuration
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class AdminCollectionStorageService {

    private static final Logger logger = LoggerFactory.getLogger(AdminCollectionStorageService.class);

    private final Map<String, StorageConnector> connectorRegistry;
    private final MetricsCollector metrics;
    private final Map<String, CollectionStorageProfile> collectionStorageCache;

    /**
     * Creates a new collection storage configuration service.
     *
     * @param connectorRegistry connector registry (required)
     * @param metrics           metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public AdminCollectionStorageService(
            Map<String, StorageConnector> connectorRegistry,
            MetricsCollector metrics) {
        this.connectorRegistry = Objects.requireNonNull(
                connectorRegistry,
                "connectorRegistry must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.collectionStorageCache = new ConcurrentHashMap<>();
    }

    // ========== Collection Storage Assignment ==========
    /**
     * Assign a storage profile to a collection.
     *
     * <p>
     * <b>Flow</b><br>
     * GIVEN: tenantId, collectionName, storageProfileId, primaryBackendId,
     * fallbacks<br>
     * WHEN: assignStorageProfile() is called<br>
     * THEN: Create and store mapping, return configuration
     *
     * @param tenantId           tenant context (required)
     * @param collectionName     collection name (required)
     * @param storageProfileId   storage profile ID (required)
     * @param primaryBackendId   primary backend connector ID (required)
     * @param fallbackBackendIds fallback connector IDs (optional)
     * @return Promise of collection storage profile
     */
    public Promise<CollectionStorageProfile> assignStorageProfile(
            String tenantId,
            String collectionName,
            String storageProfileId,
            String primaryBackendId,
            List<String> fallbackBackendIds) {
        // GIVEN: Validate inputs
        if (tenantId == null || tenantId.isBlank()) {
            metrics.incrementCounter("admin.collection.storage.assign.invalid_tenant");
            return Promise.ofException(new BaseException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid tenant: " + tenantId));
        }
        if (collectionName == null || collectionName.isBlank()) {
            metrics.incrementCounter("admin.collection.storage.assign.invalid_collection");
            return Promise.ofException(new BaseException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid collection name: " + collectionName));
        }
        if (primaryBackendId == null || primaryBackendId.isBlank()) {
            metrics.incrementCounter("admin.collection.storage.assign.invalid_backend");
            return Promise.ofException(new BaseException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid primary backend: " + primaryBackendId));
        }

        // Verify backends exist
        if (!connectorRegistry.containsKey(primaryBackendId)) {
            metrics.incrementCounter("admin.collection.storage.assign.backend_not_found");
            return Promise.ofException(new BaseException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Primary backend not found: " + primaryBackendId));
        }

        if (fallbackBackendIds != null) {
            for (String fallbackId : fallbackBackendIds) {
                if (!connectorRegistry.containsKey(fallbackId)) {
                    metrics.incrementCounter("admin.collection.storage.assign.fallback_not_found");
                    return Promise.ofException(new BaseException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Fallback backend not found: " + fallbackId));
                }
            }
        }

        long startMs = System.currentTimeMillis();

        // WHEN: Create and store mapping
        String mappingId = UUID.randomUUID().toString();
        CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .id(mappingId)
                .tenantId(tenantId)
                .collectionName(collectionName)
                .storageProfileId(storageProfileId)
                .primaryBackendId(primaryBackendId)
                .fallbackBackendIds(fallbackBackendIds != null ? fallbackBackendIds : Collections.emptyList())
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Store in cache (tenant-scoped key)
        String cacheKey = buildCacheKey(tenantId, collectionName);
        collectionStorageCache.put(cacheKey, profile);

        long durationMs = System.currentTimeMillis() - startMs;
        metrics.recordTimer("admin.collection.storage.assign", durationMs);
        metrics.incrementCounter(
                "admin.collection.storage.assign.success",
                "tenant", tenantId,
                "collection", collectionName);

        logger.info(
                "Assigned storage profile {} to collection {} for tenant {}",
                storageProfileId, collectionName, tenantId);

        // THEN: Return created mapping
        return Promise.of(profile);
    }

    // ========== Collection Storage Retrieval ==========
    /**
     * Get storage configuration for a collection.
     *
     * @param tenantId       tenant context (required)
     * @param collectionName collection name (required)
     * @return Promise of optional storage configuration
     */
    public Promise<Optional<CollectionStorageProfile>> getCollectionStorage(
            String tenantId,
            String collectionName) {
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.ofException(new BaseException(ErrorCode.INVALID_REQUEST, "Invalid tenant"));
        }
        if (collectionName == null || collectionName.isBlank()) {
            return Promise.ofException(new BaseException(ErrorCode.INVALID_REQUEST, "Invalid collection name"));
        }

        long startMs = System.currentTimeMillis();

        String cacheKey = buildCacheKey(tenantId, collectionName);
        CollectionStorageProfile profile = collectionStorageCache.get(cacheKey);

        long durationMs = System.currentTimeMillis() - startMs;
        metrics.recordTimer("admin.collection.storage.get", durationMs);

        if (profile != null) {
            metrics.incrementCounter(
                    "admin.collection.storage.get.found",
                    "tenant", tenantId,
                    "collection", collectionName);
            return Promise.of(Optional.of(profile));
        } else {
            metrics.incrementCounter(
                    "admin.collection.storage.get.notfound",
                    "tenant", tenantId,
                    "collection", collectionName);
            return Promise.of(Optional.empty());
        }
    }

    // ========== Collection Storage Update ==========
    /**
     * Update storage configuration for a collection.
     *
     * @param tenantId           tenant context (required)
     * @param collectionName     collection name (required)
     * @param primaryBackendId   new primary backend (required)
     * @param fallbackBackendIds new fallback backends (optional)
     * @return Promise of updated configuration
     */
    public Promise<CollectionStorageProfile> updateCollectionStorage(
            String tenantId,
            String collectionName,
            String primaryBackendId,
            List<String> fallbackBackendIds) {
        return getCollectionStorage(tenantId, collectionName)
                .then(existingOpt -> {
                    if (existingOpt.isEmpty()) {
                        metrics.incrementCounter("admin.collection.storage.update.notfound");
                        return Promise.ofException(new BaseException(
                                ErrorCode.RESOURCE_NOT_FOUND,
                                "Collection storage not found"));
                    }

                    // Verify new backends exist
                    if (!connectorRegistry.containsKey(primaryBackendId)) {
                        metrics.incrementCounter("admin.collection.storage.update.backend_not_found");
                        return Promise.ofException(new BaseException(
                                ErrorCode.RESOURCE_NOT_FOUND,
                                "Primary backend not found: " + primaryBackendId));
                    }

                    if (fallbackBackendIds != null) {
                        for (String fallbackId : fallbackBackendIds) {
                            if (!connectorRegistry.containsKey(fallbackId)) {
                                metrics.incrementCounter("admin.collection.storage.update.fallback_not_found");
                                return Promise.ofException(new BaseException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Fallback backend not found: " + fallbackId));
                            }
                        }
                    }

                    CollectionStorageProfile existing = existingOpt.get();
                    CollectionStorageProfile updated = CollectionStorageProfile.builder()
                            .id(existing.getId())
                            .tenantId(existing.getTenantId())
                            .collectionName(existing.getCollectionName())
                            .storageProfileId(existing.getStorageProfileId())
                            .primaryBackendId(primaryBackendId)
                            .fallbackBackendIds(
                                    fallbackBackendIds != null ? fallbackBackendIds : Collections.emptyList())
                            .backendConfig(existing.getBackendConfig())
                            .isActive(existing.getIsActive())
                            .priorityOrder(existing.getPriorityOrder())
                            .createdAt(existing.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();

                    String cacheKey = buildCacheKey(tenantId, collectionName);
                    collectionStorageCache.put(cacheKey, updated);

                    metrics.incrementCounter(
                            "admin.collection.storage.update.success",
                            "tenant", tenantId,
                            "collection", collectionName);

                    logger.info(
                            "Updated storage routing for collection {} in tenant {}",
                            collectionName, tenantId);

                    return Promise.of(updated);
                });
    }

    // ========== Collection Storage Routing Test ==========
    /**
     * Test routing configuration for a collection.
     *
     * @param tenantId       tenant context (required)
     * @param collectionName collection name (required)
     * @return Promise of routing test response
     */
    public Promise<TestCollectionRoutingResponse> testCollectionRouting(
            String tenantId,
            String collectionName) {
        return getCollectionStorage(tenantId, collectionName)
                .then(storageOpt -> {
                    if (storageOpt.isEmpty()) {
                        metrics.incrementCounter("admin.collection.storage.test.notfound");
                        return Promise.ofException(new BaseException(
                                ErrorCode.RESOURCE_NOT_FOUND,
                                "Collection storage not found"));
                    }

                    CollectionStorageProfile storage = storageOpt.get();
                    TestCollectionRoutingResponse response = new TestCollectionRoutingResponse(
                            storage.getCollectionName(),
                            storage.getPrimaryBackendId(),
                            storage.getFallbackBackendIds(),
                            storage.getIsActive(),
                            storage.getAllAvailableBackends());

                    metrics.incrementCounter(
                            "admin.collection.storage.test.success",
                            "tenant", tenantId,
                            "collection", collectionName);

                    return Promise.of(response);
                });
    }

    // ========== List Collection Storage Configurations ==========
    /**
     * List all storage configurations for a tenant.
     *
     * @param tenantId tenant context (required)
     * @return Promise of list of configurations
     */
    public Promise<List<CollectionStorageProfile>> listCollectionStorages(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            metrics.incrementCounter("admin.collection.storage.list.invalid_tenant");
            return Promise.ofException(new BaseException(ErrorCode.INVALID_REQUEST, "Invalid tenant"));
        }

        long startMs = System.currentTimeMillis();

        List<CollectionStorageProfile> tenantStorages = collectionStorageCache.values()
                .stream()
                .filter(storage -> storage.getTenantId().equals(tenantId))
                .collect(Collectors.toList());

        long durationMs = System.currentTimeMillis() - startMs;
        metrics.recordTimer("admin.collection.storage.list", durationMs);
        metrics.incrementCounter(
                "admin.collection.storage.list.success",
                "tenant", tenantId,
                "count", String.valueOf(tenantStorages.size()));

        return Promise.of(tenantStorages);
    }

    // ========== Helper Methods ==========
    /**
     * Build tenant-scoped cache key for collection storage.
     *
     * @param tenantId       tenant ID
     * @param collectionName collection name
     * @return cache key
     */
    private String buildCacheKey(String tenantId, String collectionName) {
        return tenantId + ":" + collectionName;
    }

    // ========== DTOs ==========
    /**
     * Response DTO for collection routing test.
     */
    public static final class TestCollectionRoutingResponse {

        public final String collectionName;
        public final String primaryBackend;
        public final List<String> fallbackBackends;
        public final Boolean isActive;
        public final List<String> allAvailableBackends;

        public TestCollectionRoutingResponse(
                String collectionName,
                String primaryBackend,
                List<String> fallbackBackends,
                Boolean isActive,
                List<String> allAvailableBackends) {
            this.collectionName = collectionName;
            this.primaryBackend = primaryBackend;
            this.fallbackBackends = fallbackBackends;
            this.isActive = isActive;
            this.allAvailableBackends = allAvailableBackends;
        }
    }
}
