package com.ghatana.datacloud.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents an external data source connection and synchronization configuration.
 *
 * <p><b>Purpose</b><br>
 * Defines connections to external data systems (databases, APIs, files, streams)
 * and their synchronization policies with Data Cloud collections. DataSources enable
 * data ingestion, CDC, and external system integration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetaDataSource dataSource = MetaDataSource.builder()
 *     .tenantId("tenant-123")
 *     .name("postgres-orders")
 *     .label("PostgreSQL Orders Database")
 *     .type("RELATIONAL")
 *     .connectionConfig(Map.of(
 *         "host", "orders-db.company.com",
 *         "port", "5432",
 *         "database", "orders",
 *         "username", "readonly_user"
 *     ))
 *     .syncConfig(Map.of(
 *         "mode", "cdc",
 *         "targetCollection", "orders",
 *         "syncFrequency", "realtime"
 *     ))
 *     .build();
 *
 * // Save via repository
 * MetaDataSource saved = runPromise(() -> repository.save(dataSource));
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model in hexagonal architecture (domain layer)
 * - Persisted via JPA/Hibernate through core/database
 * - Consumed by DataSourceService for connection management
 * - Drives data ingestion and synchronization workflows
 * - Supports multiple connector types and sync modes
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
 * - Created: User creates data source via API
 * - Connected: Connection validated and established
 * - Syncing: Data synchronization active
 * - Paused: Synchronization temporarily stopped
 * - Archived: Connection decommissioned
 *
 * @see MetaCollection
 * @see com.ghatana.datacloud.application.DataSourceService
 * @doc.type class
 * @doc.purpose External data source connection and synchronization configuration
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@jakarta.persistence.Entity
@Table(
    name = "meta_data_sources",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}),
    indexes = {
        @Index(name = "idx_meta_data_sources_tenant", columnList = "tenant_id"),
        @Index(name = "idx_meta_data_sources_active", columnList = "tenant_id, active"),
        @Index(name = "idx_meta_data_sources_type", columnList = "tenant_id, type"),
        @Index(name = "idx_meta_data_sources_status", columnList = "tenant_id, connection_status"),
        @Index(name = "idx_meta_data_sources_created_at", columnList = "created_at DESC")
    }
)
public class MetaDataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Tenant ID is required")
    @Column(name = DataCloudColumnNames.TENANT_ID, nullable = false, length = 255)
    private String tenantId;

    @NotBlank(message = "Data source name is required")
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Data source type.
     * Values: RELATIONAL, NOSQL, API, FILE, STREAM, MESSAGE_QUEUE, OBJECT_STORAGE
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DataSourceType type;

    /**
     * Connection configuration details.
     * Format varies by type but includes credentials, endpoints, and connection parameters.
     * Stored encrypted for security.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> connectionConfig = new HashMap<>();

    /**
     * Synchronization configuration.
     * Defines how data is synced with target collections.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> syncConfig = new HashMap<>();

    /**
     * Target collection for synchronized data.
     * References MetaCollection name where data will be stored.
     */
    @Column(name = "target_collection", length = 255)
    private String targetCollection;

    /**
     * Connection status.
     * Values: DISCONNECTED, CONNECTING, CONNECTED, ERROR, PAUSED
     */
    @Column(name = "connection_status", nullable = false, length = 50)
    private String connectionStatus = "DISCONNECTED";

    /**
     * Last successful connection timestamp.
     */
    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    /**
     * Last synchronization timestamp.
     */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /**
     * Synchronization statistics.
     * Format: {"recordsSynced": 10000, "errors": 0, "lastSyncDuration": "PT5M"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sync_stats", columnDefinition = "jsonb")
    private Map<String, Object> syncStats;

    /**
     * Error details if connection or sync fails.
     * Format: {"code": "CONNECTION_FAILED", "message": "...", "timestamp": "..."}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "jsonb")
    private Map<String, Object> errorDetails;

    /**
     * Data source owner.
     */
    @Column(name = "owner", length = 255)
    private String owner;

    /**
     * Tags for categorization and discovery.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

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

    /**
     * Data source type enumeration.
     */
    public enum DataSourceType {
        RELATIONAL,
        NOSQL,
        API,
        FILE,
        STREAM,
        MESSAGE_QUEUE,
        OBJECT_STORAGE
    }

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

    public DataSourceType getType() {
        return type;
    }

    public void setType(DataSourceType type) {
        this.type = type;
    }

    public Map<String, Object> getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(Map<String, Object> connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public Map<String, Object> getSyncConfig() {
        return syncConfig;
    }

    public void setSyncConfig(Map<String, Object> syncConfig) {
        this.syncConfig = syncConfig;
    }

    public String getTargetCollection() {
        return targetCollection;
    }

    public void setTargetCollection(String targetCollection) {
        this.targetCollection = targetCollection;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public Instant getLastConnectedAt() {
        return lastConnectedAt;
    }

    public void setLastConnectedAt(Instant lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Map<String, Object> getSyncStats() {
        return syncStats;
    }

    public void setSyncStats(Map<String, Object> syncStats) {
        this.syncStats = syncStats;
    }

    public Map<String, Object> getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(Map<String, Object> errorDetails) {
        this.errorDetails = errorDetails;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
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
        private DataSourceType type;
        private Map<String, Object> connectionConfig = new HashMap<>();
        private Map<String, Object> syncConfig = new HashMap<>();
        private String targetCollection;
        private String connectionStatus = "DISCONNECTED";
        private Instant lastConnectedAt;
        private Instant lastSyncedAt;
        private Map<String, Object> syncStats;
        private Map<String, Object> errorDetails;
        private String owner;
        private List<String> tags = new ArrayList<>();
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

        public Builder type(DataSourceType type) {
            this.type = type;
            return this;
        }

        public Builder connectionConfig(Map<String, Object> connectionConfig) {
            this.connectionConfig = connectionConfig;
            return this;
        }

        public Builder syncConfig(Map<String, Object> syncConfig) {
            this.syncConfig = syncConfig;
            return this;
        }

        public Builder targetCollection(String targetCollection) {
            this.targetCollection = targetCollection;
            return this;
        }

        public Builder connectionStatus(String connectionStatus) {
            this.connectionStatus = connectionStatus;
            return this;
        }

        public Builder lastConnectedAt(Instant lastConnectedAt) {
            this.lastConnectedAt = lastConnectedAt;
            return this;
        }

        public Builder lastSyncedAt(Instant lastSyncedAt) {
            this.lastSyncedAt = lastSyncedAt;
            return this;
        }

        public Builder syncStats(Map<String, Object> syncStats) {
            this.syncStats = syncStats;
            return this;
        }

        public Builder errorDetails(Map<String, Object> errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
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

        public MetaDataSource build() {
            MetaDataSource dataSource = new MetaDataSource();
            dataSource.id = this.id;
            dataSource.tenantId = this.tenantId;
            dataSource.name = this.name;
            dataSource.label = this.label;
            dataSource.description = this.description;
            dataSource.type = this.type;
            dataSource.connectionConfig = this.connectionConfig;
            dataSource.syncConfig = this.syncConfig;
            dataSource.targetCollection = this.targetCollection;
            dataSource.connectionStatus = this.connectionStatus;
            dataSource.lastConnectedAt = this.lastConnectedAt;
            dataSource.lastSyncedAt = this.lastSyncedAt;
            dataSource.syncStats = this.syncStats;
            dataSource.errorDetails = this.errorDetails;
            dataSource.owner = this.owner;
            dataSource.tags = this.tags;
            dataSource.version = this.version;
            dataSource.active = this.active;
            dataSource.createdBy = this.createdBy;
            dataSource.updatedBy = this.updatedBy;
            return dataSource;
        }
    }

    @Override
    public String toString() {
        return "MetaDataSource{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", connectionStatus='" + connectionStatus + '\'' +
                ", targetCollection='" + targetCollection + '\'' +
                ", active=" + active +
                ", version=" + version +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetaDataSource that = (MetaDataSource) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name);
    }
}
