package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a logical grouping of collections for data organization and governance.
 *
 * <p><b>Purpose</b><br>
 * Defines a dataset as a collection-level abstraction that groups related collections
 * for business purposes, governance, and data discovery. Datasets provide a semantic
 * layer over physical collections for easier data management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetaDataset dataset = MetaDataset.builder()
 *     .tenantId("tenant-123")
 *     .name("customer-analytics")
 *     .label("Customer Analytics Dataset")
 *     .description("All customer-related data for analytics")
 *     .collectionIds(List.of("customers", "orders", "interactions"))
 *     .tags(List.of("analytics", "customer", "pii"))
 *     .owner("data-team")
 *     .steward("data-steward-123")
 *     .build();
 *
 * // Save via repository
 * MetaDataset saved = runPromise(() -> repository.save(dataset));
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model in hexagonal architecture (domain layer)
 * - Persisted via JPA/Hibernate through core/database
 * - Consumed by DatasetService for metadata operations
 * - Provides logical grouping over physical collections
 * - Supports data governance and discovery
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
 * - Created: User creates dataset via API
 * - Updated: Collection membership or metadata modified
 * - Soft-deleted: active=false (never hard-deleted for audit trail)
 * - Published: Made available for data discovery
 *
 * @see MetaCollection
 * @see com.ghatana.datacloud.application.DatasetService
 * @doc.type class
 * @doc.purpose Logical grouping of collections for data organization
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@jakarta.persistence.Entity
@Table(
    name = "meta_datasets",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}),
    indexes = {
        @Index(name = "idx_meta_datasets_tenant", columnList = "tenant_id"),
        @Index(name = "idx_meta_datasets_active", columnList = "tenant_id, active"),
        @Index(name = "idx_meta_datasets_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_meta_datasets_owner", columnList = "tenant_id, owner")
    }
)
public class MetaDataset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Tenant ID is required")
    @Column(name = DataCloudColumnNames.TENANT_ID, nullable = false, length = 255)
    private String tenantId;

    @NotBlank(message = "Dataset name is required")
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Collection IDs that belong to this dataset.
     * References MetaCollection names/IDs for logical grouping.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> collectionIds = new ArrayList<>();

    /**
     * Tags for categorization and discovery.
     * Business and technical tags for dataset classification.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    /**
     * Dataset owner (team, user, or service).
     * Responsible for dataset lifecycle and access management.
     */
    @Column(name = "owner", length = 255)
    private String owner;

    /**
     * Data steward responsible for data quality and governance.
     * Point of contact for data governance and compliance.
     */
    @Column(name = "steward", length = 255)
    private String steward;

    /**
     * Dataset lifecycle status.
     * Values: DRAFT, PUBLISHED, DEPRECATED, ARCHIVED
     */
    @Column(name = "lifecycle_status", nullable = false, length = 50)
    private String lifecycleStatus = "DRAFT";

    /**
     * Data classification level for security and compliance.
     * Values: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED
     */
    @Column(name = "classification", length = 50)
    private String classification = "INTERNAL";

    /**
     * Usage statistics and metrics.
     * Format: {"queryCount": 1000, "lastAccessed": "2024-01-01T00:00:00Z"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_metrics", columnDefinition = "jsonb")
    private Map<String, Object> usageMetrics;

    /**
     * Data quality metrics for the dataset.
     * Format: {"completeness": 0.95, "accuracy": 0.98, "timeliness": 0.92}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_metrics", columnDefinition = "jsonb")
    private Map<String, Object> qualityMetrics;

    /**
     * Retention and archival policies.
     * Format: {"retentionDays": 2555, "archivalPolicy": "cold_storage_after_1_year"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retention_policy", columnDefinition = "jsonb")
    private Map<String, Object> retentionPolicy;

    /**
     * Access control and permissions.
     * Format: {"read": ["role1", "role2"], "write": ["role3"]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, List<String>> permissions = new HashMap<>();

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = DataCloudColumnNames.CREATED_AT, nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = DataCloudColumnNames.UPDATED_AT, nullable = false)
    private Instant updatedAt;

    @Column(name = DataCloudColumnNames.CREATED_BY, length = 255)
    private String createdBy;

    @Column(name = DataCloudColumnNames.UPDATED_BY, length = 255)
    private String updatedBy;

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

    public List<String> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<String> collectionIds) {
        this.collectionIds = collectionIds;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getSteward() {
        return steward;
    }

    public void setSteward(String steward) {
        this.steward = steward;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public Map<String, Object> getUsageMetrics() {
        return usageMetrics;
    }

    public void setUsageMetrics(Map<String, Object> usageMetrics) {
        this.usageMetrics = usageMetrics;
    }

    public Map<String, Object> getQualityMetrics() {
        return qualityMetrics;
    }

    public void setQualityMetrics(Map<String, Object> qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    public Map<String, Object> getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(Map<String, Object> retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public Map<String, List<String>> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, List<String>> permissions) {
        this.permissions = permissions;
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
        private List<String> collectionIds = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
        private String owner;
        private String steward;
        private String lifecycleStatus = "DRAFT";
        private String classification = "INTERNAL";
        private Map<String, Object> usageMetrics;
        private Map<String, Object> qualityMetrics;
        private Map<String, Object> retentionPolicy;
        private Map<String, List<String>> permissions = new HashMap<>();
        private Integer version = 1;
        private Boolean active = true;
        private String createdBy;
        private String updatedBy;

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

        public Builder collectionIds(List<String> collectionIds) {
            this.collectionIds = collectionIds;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder steward(String steward) {
            this.steward = steward;
            return this;
        }

        public Builder lifecycleStatus(String lifecycleStatus) {
            this.lifecycleStatus = lifecycleStatus;
            return this;
        }

        public Builder classification(String classification) {
            this.classification = classification;
            return this;
        }

        public Builder usageMetrics(Map<String, Object> usageMetrics) {
            this.usageMetrics = usageMetrics;
            return this;
        }

        public Builder qualityMetrics(Map<String, Object> qualityMetrics) {
            this.qualityMetrics = qualityMetrics;
            return this;
        }

        public Builder retentionPolicy(Map<String, Object> retentionPolicy) {
            this.retentionPolicy = retentionPolicy;
            return this;
        }

        public Builder permissions(Map<String, List<String>> permissions) {
            this.permissions = permissions;
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

        public MetaDataset build() {
            MetaDataset dataset = new MetaDataset();
            dataset.id = this.id;
            dataset.tenantId = this.tenantId;
            dataset.name = this.name;
            dataset.label = this.label;
            dataset.description = this.description;
            dataset.collectionIds = this.collectionIds;
            dataset.tags = this.tags;
            dataset.owner = this.owner;
            dataset.steward = this.steward;
            dataset.lifecycleStatus = this.lifecycleStatus;
            dataset.classification = this.classification;
            dataset.usageMetrics = this.usageMetrics;
            dataset.qualityMetrics = this.qualityMetrics;
            dataset.retentionPolicy = this.retentionPolicy;
            dataset.permissions = this.permissions;
            dataset.version = this.version;
            dataset.active = this.active;
            dataset.createdBy = this.createdBy;
            dataset.updatedBy = this.updatedBy;
            return dataset;
        }
    }

    @Override
    public String toString() {
        return "MetaDataset{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", label='" + label + '\'' +
                ", collectionCount=" + (collectionIds != null ? collectionIds.size() : 0) +
                ", active=" + active +
                ", lifecycleStatus='" + lifecycleStatus + '\'' +
                ", version=" + version +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetaDataset that = (MetaDataset) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name);
    }
}
