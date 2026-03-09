package com.ghatana.datacloud.entity.storage;

import java.time.Instant;
import java.util.*;

/**
 * Immutable value object representing a collection's storage profile
 * assignment.
 *
 * <p>
 * <b>Purpose</b><br>
 * Maps a logical collection to a storage profile, defining how that
 * collection's
 * entities are stored and routed across physical backends. Supports
 * per-collection
 * customization of storage preferences while maintaining multi-tenancy
 * isolation.
 *
 * <p>
 * <b>Key Concepts</b><br>
 * - Collection: Logical container of entities (e.g., "products", "orders")
 * - Storage Profile: Named configuration declaring supported backends and
 * latency/cost
 * - Tenant: Data isolation boundary - each tenant has independent collection
 * mappings
 * - Primary Backend: Preferred backend for this collection (e.g., PostgreSQL)
 * - Fallback Backends: Alternative backends if primary fails
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * CollectionStorageProfile mapping = CollectionStorageProfile.builder()
 *         .tenantId("tenant-1")
 *         .collectionName("products")
 *         .storageProfileId("hot-profile")
 *         .primaryBackendId("postgres-primary")
 *         .fallbackBackendIds(List.of("postgres-secondary"))
 *         .build();
 *
 * // Check if backend is available for collection
 * boolean canUsePostgres = mapping.supports("postgres-primary");
 * }</pre>
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * Collection storage profiles are tenant-scoped. The combination of tenantId +
 * collectionName uniquely identifies a mapping within a system.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable - thread-safe. Safe for concurrent access and caching.
 *
 * @see StorageProfile
 * @see StorageBackendType
 * @doc.type class
 * @doc.purpose Maps collections to storage profiles and backends
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class CollectionStorageProfile {

    private final String id;
    private final String tenantId;
    private final String collectionName;
    private final String storageProfileId;
    private final String primaryBackendId;
    private final List<String> fallbackBackendIds;
    private final Map<String, Object> backendConfig;
    private final Boolean isActive;
    private final Integer priorityOrder;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Creates a new collection storage profile mapping.
     *
     * @param id                 mapping ID (unique, required)
     * @param tenantId           tenant context (required)
     * @param collectionName     collection name (required)
     * @param storageProfileId   storage profile ID (required)
     * @param primaryBackendId   primary backend connector ID (required)
     * @param fallbackBackendIds fallback backend connector IDs (optional)
     * @param backendConfig      backend-specific configuration (optional)
     * @param isActive           whether this mapping is active
     * @param priorityOrder      relative priority of this mapping
     * @param createdAt          creation timestamp
     * @param updatedAt          last update timestamp
     */
    public CollectionStorageProfile(
            String id,
            String tenantId,
            String collectionName,
            String storageProfileId,
            String primaryBackendId,
            List<String> fallbackBackendIds,
            Map<String, Object> backendConfig,
            Boolean isActive,
            Integer priorityOrder,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = tenantId;
        this.collectionName = Objects.requireNonNull(collectionName, "collectionName must not be null");
        this.storageProfileId = Objects.requireNonNull(storageProfileId, "storageProfileId must not be null");
        this.primaryBackendId = Objects.requireNonNull(primaryBackendId, "primaryBackendId must not be null");
        this.fallbackBackendIds = fallbackBackendIds != null
                ? Collections.unmodifiableList(new ArrayList<>(fallbackBackendIds))
                : Collections.emptyList();
        this.backendConfig = backendConfig != null
                ? Collections.unmodifiableMap(new HashMap<>(backendConfig))
                : Collections.emptyMap();
        this.isActive = isActive != null ? isActive : true;
        this.priorityOrder = priorityOrder != null ? priorityOrder : 0;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    // ========== Accessors ==========

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getStorageProfileId() {
        return storageProfileId;
    }

    public String getPrimaryBackendId() {
        return primaryBackendId;
    }

    public List<String> getFallbackBackendIds() {
        return fallbackBackendIds;
    }

    public Map<String, Object> getBackendConfig() {
        return backendConfig;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public Integer getPriorityOrder() {
        return priorityOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ========== Queries ==========

    /**
     * Check if a backend is available for this collection.
     *
     * <p>
     * Returns true if:
     * - Backend is primary AND mapping is active
     * - Backend is in fallback list AND mapping is active
     *
     * @param backendId backend connector ID to check
     * @return true if backend is available
     */
    public boolean supportsBackend(String backendId) {
        if (!isActive) {
            return false;
        }
        if (primaryBackendId.equals(backendId)) {
            return true;
        }
        return fallbackBackendIds.contains(backendId);
    }

    /**
     * Get all available backends for this collection.
     *
     * @return list of all backend IDs (primary + fallbacks), empty if inactive
     */
    public List<String> getAllAvailableBackends() {
        if (!isActive) {
            return Collections.emptyList();
        }
        List<String> all = new ArrayList<>();
        all.add(primaryBackendId);
        all.addAll(fallbackBackendIds);
        return Collections.unmodifiableList(all);
    }

    /**
     * Check if mapping is configured for multi-backend failover.
     *
     * @return true if fallback backends are configured
     */
    public boolean hasFailoverSupport() {
        return !fallbackBackendIds.isEmpty();
    }

    // ========== Builder ==========

    /**
     * Create a builder for CollectionStorageProfile.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CollectionStorageProfile.
     */
    public static final class Builder {
        private String id;
        private String tenantId;
        private String collectionName;
        private String storageProfileId;
        private String primaryBackendId;
        private List<String> fallbackBackendIds = Collections.emptyList();
        private Map<String, Object> backendConfig = Collections.emptyMap();
        private Boolean isActive = true;
        private Integer priorityOrder = 0;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder storageProfileId(String storageProfileId) {
            this.storageProfileId = storageProfileId;
            return this;
        }

        public Builder primaryBackendId(String primaryBackendId) {
            this.primaryBackendId = primaryBackendId;
            return this;
        }

        public Builder fallbackBackendIds(List<String> fallbackBackendIds) {
            this.fallbackBackendIds = fallbackBackendIds;
            return this;
        }

        public Builder backendConfig(Map<String, Object> backendConfig) {
            this.backendConfig = backendConfig;
            return this;
        }

        public Builder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder priorityOrder(Integer priorityOrder) {
            this.priorityOrder = priorityOrder;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Build the CollectionStorageProfile.
         *
         * @return new instance
         * @throws NullPointerException if required fields are null
         */
        public CollectionStorageProfile build() {
            // Auto-generate optional fields when not explicitly provided to make
            // builder usage simpler in tests and callers.
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            if (storageProfileId == null && collectionName != null) {
                storageProfileId = collectionName + "-profile";
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            if (updatedAt == null) {
                updatedAt = Instant.now();
            }
            return new CollectionStorageProfile(
                    id,
                    tenantId,
                    collectionName,
                    storageProfileId,
                    primaryBackendId,
                    fallbackBackendIds,
                    backendConfig,
                    isActive,
                    priorityOrder,
                    createdAt,
                    updatedAt);
        }
    }

    // ========== Equality and Hashing ==========

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CollectionStorageProfile that = (CollectionStorageProfile) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(collectionName, that.collectionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, collectionName);
    }

    @Override
    public String toString() {
        return "CollectionStorageProfile{" +
                "id='" + id + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", collectionName='" + collectionName + '\'' +
                ", storageProfileId='" + storageProfileId + '\'' +
                ", primaryBackendId='" + primaryBackendId + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
