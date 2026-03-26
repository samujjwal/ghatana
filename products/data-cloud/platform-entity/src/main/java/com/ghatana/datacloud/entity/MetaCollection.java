package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a dynamic collection schema definition with metadata-driven configuration.
 *
 * <p><b>Purpose</b><br>
 * Defines the structure, permissions, and behavior of a dynamic entity collection. Supports
 * multi-application views, RBAC, and flexible schema evolution without code deployment.
 * Collections are the top-level organizational unit for grouping related entities.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetaCollection collection = MetaCollection.builder()
 *     .tenantId("tenant-123")
 *     .name("products")
 *     .label("Products")
 *     .description("Product catalog")
 *     .permission(Map.of(
 *         "read", List.of("ADMIN", "USER"),
 *         "write", List.of("ADMIN"),
 *         "delete", List.of("ADMIN")
 *     ))
 *     .applications(List.of(
 *         Map.of("name", "admin", "visible", true)
 *     ))
 *     .build();
 *
 * // Save via repository
 * MetaCollection saved = runPromise(() -> repository.save(collection));
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model in hexagonal architecture (domain layer)
 * - Persisted via JPA/Hibernate through core/database
 * - Consumed by CollectionService for metadata operations
 * - Cached in Redis via MetadataCacheService
 * - Queried via DynamicQueryBuilder for entity operations
 *
 * <p><b>Thread Safety</b><br>
 * Mutable JPA entity - not thread-safe. Use within transaction boundaries only.
 * Instances should not be shared across threads.
 *
 * <p><b>Multi-Tenancy</b><br>
 * Tenant-scoped via tenantId field. All queries MUST filter by tenant to prevent
 * cross-tenant data access. Enforced at repository layer.
 *
 * <p><b>Lifecycle</b><br>
 * - Created: User creates collection via API
 * - Updated: Schema/permissions modified
 * - Soft-deleted: active=false (never hard-deleted for audit trail)
 * - Archived: Moved to cold storage after retention period
 *
 * @see MetaField
 * @see com.ghatana.datacloud.application.CollectionService
 * @see com.ghatana.datacloud.infrastructure.persistence.CollectionRepository
 * @doc.type class
 * @doc.purpose Dynamic collection schema definition with RBAC and multi-app support
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@jakarta.persistence.Entity
@Table(
    name = "meta_collections",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}),
    indexes = {
        @Index(name = "idx_meta_collections_tenant", columnList = "tenant_id"),
        @Index(name = "idx_meta_collections_active", columnList = "tenant_id, active"),
        @Index(name = "idx_meta_collections_created_at", columnList = "created_at DESC")
    }
)
public class MetaCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Tenant ID is required")
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @NotBlank(message = "Collection name is required")
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * RBAC permissions as JSONB.
     * Format: {"read": ["ADMIN", "USER"], "write": ["ADMIN"], "delete": ["ADMIN"]}
     * Default: {} (empty, denies all access)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, List<String>> permission = new HashMap<>();

    /**
     * Application-specific configurations as JSONB array.
     * Format: [{"name": "admin", "visible": true, "fields": [...]}]
     * Allows different views of the same collection per application.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> applications = new ArrayList<>();

    /**
     * JSON Schema for validation.
     * Optional schema for validating collection-level constraints.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_schema", columnDefinition = "jsonb")
    private Map<String, Object> validationSchema;

    /**
     * Storage profile name for this collection.
     *
     * <p><b>Purpose</b><br>
     * References a storage profile that declares supported backend types,
     * latency class, cost tier, and retention policies. Guides StorageRoutingService
     * in selecting appropriate storage connectors for reads/writes.
     *
     * <p><b>Default</b><br>
     * null or "warm" for backward compatibility (defaults to PostgreSQL)
     *
     * <p><b>Examples</b><br>
     * - "hot" → In-memory + Redis for immediate access
     * - "warm" → PostgreSQL JSONB for standard access
     * - "cold" → Object storage for archive/bulk
     * - "analytics" → Data warehouse for aggregations
     */
    @Column(name = "storage_profile", length = 255)
    private String storageProfile;

    /**
     * Physical storage mappings as JSONB.
     *
     * <p><b>Purpose</b><br>
     * Maps logical storage backends to actual connector configurations.
     * Allows collections to customize which backends are used and how.
     *
     * <p><b>Format</b><br>
     * {
     *   "primary": {
     *     "backendType": "RELATIONAL",
     *     "connectorId": "postgres-prod",
     *     "weight": 100
     *   },
     *   "replica": {
     *     "backendType": "KEY_VALUE",
     *     "connectorId": "redis-cache",
     *     "weight": 50
     *   },
     *   "archive": {
     *     "backendType": "BLOB",
     *     "connectorId": "s3-archive",
     *     "weight": 10
     *   }
     * }
     *
     * <p><b>Default</b><br>
     * null or empty (uses storage profile defaults)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "physical_mapping", columnDefinition = "jsonb")
    private Map<String, Object> physicalMapping;

    /**
     * Schema version for tracking schema evolution.
     *
     * <p><b>Purpose</b><br>
     * Tracks schema changes independently from optimistic locking version.
     * Incremented when fields are added, removed, or modified.
     * Used by SchemaDiffService for change detection and migration planning.
     *
     * <p><b>Version Format</b><br>
     * Semantic versioning: MAJOR.MINOR.PATCH
     * - MAJOR: Breaking changes (field removed, type changed)
     * - MINOR: Non-breaking additions (field added)
     * - PATCH: Metadata changes (label, description)
     *
     * <p><b>Default</b><br>
     * "1.0.0" for new collections
     */
    @Column(name = "schema_version", nullable = false, length = 50)
    private String schemaVersion = "1.0.0";

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MetaField> fields = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, List<String>> getPermission() {
        return permission;
    }

    public void setPermission(Map<String, List<String>> permission) {
        this.permission = permission;
    }

    public List<Map<String, Object>> getApplications() {
        return applications;
    }

    public void setApplications(List<Map<String, Object>> applications) {
        this.applications = applications;
    }

    public Map<String, Object> getValidationSchema() {
        return validationSchema;
    }

    public void setValidationSchema(Map<String, Object> validationSchema) {
        this.validationSchema = validationSchema;
    }

    public String getStorageProfile() {
        return storageProfile;
    }

    public void setStorageProfile(String storageProfile) {
        this.storageProfile = storageProfile;
    }

    public Map<String, Object> getPhysicalMapping() {
        return physicalMapping;
    }

    public void setPhysicalMapping(Map<String, Object> physicalMapping) {
        this.physicalMapping = physicalMapping;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public List<MetaField> getFields() {
        return fields;
    }

    public void setFields(List<MetaField> fields) {
        this.fields = fields;
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String tenantId;
        private String name;
        private String label;
        private String description;
        private Map<String, List<String>> permission = new HashMap<>();
        private List<Map<String, Object>> applications = new ArrayList<>();
        private Map<String, Object> validationSchema;
        private String storageProfile;
        private Map<String, Object> physicalMapping;
        private String schemaVersion = "1.0.0";
        private Integer version = 1;
        private Boolean active = true;
        private String createdBy;
        private String updatedBy;
        private List<MetaField> fields = new ArrayList<>();

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder permission(Map<String, List<String>> permission) {
            this.permission = permission;
            return this;
        }

        public Builder applications(List<Map<String, Object>> applications) {
            this.applications = applications;
            return this;
        }

        public Builder validationSchema(Map<String, Object> validationSchema) {
            this.validationSchema = validationSchema;
            return this;
        }

        public Builder storageProfile(String storageProfile) {
            this.storageProfile = storageProfile;
            return this;
        }

        public Builder physicalMapping(Map<String, Object> physicalMapping) {
            this.physicalMapping = physicalMapping;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public Builder fields(List<MetaField> fields) {
            this.fields = fields;
            return this;
        }

        public MetaCollection build() {
            MetaCollection collection = new MetaCollection();
            collection.id = this.id;
            collection.tenantId = this.tenantId;
            collection.name = this.name;
            collection.label = this.label;
            collection.description = this.description;
            collection.permission = this.permission;
            collection.applications = this.applications;
            collection.validationSchema = this.validationSchema;
            collection.storageProfile = this.storageProfile;
            collection.physicalMapping = this.physicalMapping;
            collection.schemaVersion = this.schemaVersion;
            collection.version = this.version;
            collection.active = this.active;
            collection.createdBy = this.createdBy;
            collection.updatedBy = this.updatedBy;
            collection.fields = this.fields;
            return collection;
        }
    }

    @Override
    public String toString() {
        return "MetaCollection{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", label='" + label + '\'' +
                ", active=" + active +
                ", version=" + version +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetaCollection that = (MetaCollection) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name);
    }
}
