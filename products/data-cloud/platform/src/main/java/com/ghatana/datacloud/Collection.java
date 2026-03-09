package com.ghatana.datacloud;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Collection defines schema and behavior configuration for a group of records.
 *
 * <p>
 * <b>Purpose</b><br>
 * Collections are the schema definition layer of Data-Cloud. Each collection:
 * <ul>
 * <li>Defines what type of records it holds (ENTITY, EVENT, TIMESERIES,
 * etc.)</li>
 * <li>Specifies field definitions and validation rules</li>
 * <li>Configures storage profile (hot/warm/cold tiering)</li>
 * <li>Sets retention policies (for append-only types)</li>
 * <li>Manages permissions (RBAC)</li>
 * </ul>
 *
 * <p>
 * <b>Record Type Determines Behavior</b><br>
 * <pre>
 * Collection(recordType=ENTITY)    → CRUD operations, soft delete, versioning
 * Collection(recordType=EVENT)     → Append-only, streaming, offset tracking
 * Collection(recordType=TIMESERIES)→ Time-based queries, aggregations
 * </pre>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Entity collection
 * Collection customers = Collection.builder()
 *     .tenantId("tenant-123")
 *     .name("customers")
 *     .label("Customer Records")
 *     .recordType(RecordType.ENTITY)
 *     .fields(List.of(
 *         FieldDefinition.string("name").required(),
 *         FieldDefinition.string("email").required().unique(),
 *         FieldDefinition.string("status").defaultValue("active")
 *     ))
 *     .storageProfile("default")
 *     .build();
 *
 * // Event collection
 * Collection orderEvents = Collection.builder()
 *     .tenantId("tenant-123")
 *     .name("order-events")
 *     .label("Order Event Stream")
 *     .recordType(RecordType.EVENT)
 *     .eventConfig(EventConfig.builder()
 *         .partitionCount(8)
 *         .partitionKey("orderId")
 *         .build())
 *     .retentionPolicy(RetentionPolicy.builder()
 *         .retentionDays(90)
 *         .build())
 *     .storageProfile("tiered-hot-warm-cold")
 *     .build();
 * }</pre>
 *
 * @see RecordType
 * @see FieldDefinition
 * @see EventConfig
 * @see RetentionPolicy
 * @doc.type class
 * @doc.purpose Schema definition for record collections
 * @doc.layer core
 * @doc.pattern Registry, Schema-on-Read
 */
@Entity
@Table(name = "collections",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_collections_tenant_name",
                columnNames = {"tenant_id", "name"}
        ),
        indexes = {
            @Index(name = "idx_collections_tenant", columnList = "tenant_id"),
            @Index(name = "idx_collections_type", columnList = "tenant_id, record_type"),
            @Index(name = "idx_collections_active", columnList = "tenant_id, active")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Collection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tenant this collection belongs to.
     */
    @NotBlank
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    /**
     * Unique name within tenant (used in API and storage).
     */
    @NotBlank
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Human-readable label.
     */
    @Column(name = "label", length = 255)
    private String label;

    /**
     * Description of the collection's purpose.
     */
    @Column(name = "description", length = 2000)
    private String description;

    // ==================== Record Type & Behavior ====================
    /**
     * Type of records this collection holds.
     * <p>
     * Determines what operations are valid and how storage behaves.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 50)
    @Builder.Default
    private RecordType recordType = RecordType.ENTITY;

    // ==================== Schema Definition ====================
    /**
     * Field definitions for this collection.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", columnDefinition = "jsonb")
    @Builder.Default
    private List<FieldDefinition> fields = new ArrayList<>();

    /**
     * JSON Schema for validation (optional, for strict validation).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_schema", columnDefinition = "jsonb")
    private Map<String, Object> validationSchema;

    // ==================== Storage Configuration ====================
    /**
     * Storage profile name (references StorageProfile configuration).
     */
    @Column(name = "storage_profile", length = 255)
    @Builder.Default
    private String storageProfile = "default";

    /**
     * Custom storage configuration overrides.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "storage_config", columnDefinition = "jsonb")
    private Map<String, Object> storageConfig;

    // ==================== Event-Specific Configuration ====================
    /**
     * Event-specific configuration (only for EVENT record type).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_config", columnDefinition = "jsonb")
    private EventConfig eventConfig;

    // ==================== Retention Policy ====================
    /**
     * Retention policy (primarily for EVENT and TIMESERIES types).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retention_policy", columnDefinition = "jsonb")
    private RetentionPolicy retentionPolicy;

    // ==================== Permissions ====================
    /**
     * RBAC permissions.
     * <p>
     * Map of role → list of actions (read, write, delete, admin)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, List<String>> permissions = new HashMap<>();

    // ==================== Versioning ====================
    /**
     * Schema version (semantic versioning).
     */
    @Column(name = "schema_version", length = 50)
    @Builder.Default
    private String schemaVersion = "1.0.0";

    /**
     * Record version for optimistic locking.
     */
    @Version
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    // ==================== Status ====================
    /**
     * Whether this collection is active.
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    // ==================== Audit ====================
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    // ==================== Lifecycle Callbacks ====================
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (fields == null) {
            fields = new ArrayList<>();
        }
        if (permissions == null) {
            permissions = new HashMap<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ==================== Utility Methods ====================
    /**
     * Check if this collection supports a given operation.
     *
     * @param operation the operation to check
     * @return true if supported
     */
    public boolean supportsOperation(DataRecord.RecordOperation operation) {
        return recordType != null && switch (operation) {
            case CREATE ->
                true;
            case READ ->
                true;
            case UPDATE ->
                recordType.isMutable();
            case DELETE ->
                recordType.isMutable();
            case APPEND ->
                recordType.isAppendOnly();
            case SUBSCRIBE ->
                recordType.supportsStreaming();
            case AGGREGATE ->
                recordType.isAggregatable();
        };
    }

    /**
     * Get field definition by name.
     *
     * @param fieldName field name
     * @return field definition or empty
     */
    public Optional<FieldDefinition> getField(String fieldName) {
        if (fields == null) {
            return Optional.empty();
        }
        return fields.stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst();
    }

    /**
     * Add a field definition.
     *
     * @param field field definition
     * @return this collection for chaining
     */
    public Collection addField(FieldDefinition field) {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        fields.add(field);
        return this;
    }

    /**
     * Get number of partitions (for EVENT type).
     *
     * @return partition count or 1 if not configured
     */
    public int getPartitionCount() {
        if (eventConfig != null && eventConfig.getPartitionCount() != null) {
            return eventConfig.getPartitionCount();
        }
        return 1;
    }

    @Override
    public String toString() {
        return "Collection{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", name='" + name + '\''
                + ", recordType=" + recordType
                + ", active=" + active
                + '}';
    }
}
