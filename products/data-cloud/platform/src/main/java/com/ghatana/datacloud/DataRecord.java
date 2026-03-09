package com.ghatana.datacloud;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all records in Data-Cloud.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides the common foundation for all data types in the platform. Whether
 * storing entities, events, time-series, or documents, all records share these
 * core attributes while specialized subclasses add type-specific fields.
 *
 * <p>
 * <b>Core Attributes</b><br>
 * <ul>
 * <li><b>id</b> - Unique identifier (UUID)</li>
 * <li><b>tenantId</b> - Multi-tenancy isolation key</li>
 * <li><b>collectionName</b> - Logical grouping (schema)</li>
 * <li><b>recordType</b> - Behavior definition (ENTITY, EVENT, etc.)</li>
 * <li><b>data</b> - Dynamic payload as JSONB</li>
 * <li><b>metadata</b> - Type-specific metadata as JSONB</li>
 * </ul>
 *
 * <p>
 * <b>Inheritance Hierarchy</b><br>
 * <pre>
 * Record (abstract)
 *   ├── EntityRecord   (ENTITY - mutable, versioned)
 *   ├── EventRecord    (EVENT - immutable, ordered)
 *   ├── TimeSeriesRecord (TIMESERIES - timestamped, aggregatable)
 *   ├── GraphRecord    (GRAPH - relationships)
 *   └── DocumentRecord (DOCUMENT - schema-free)
 * </pre>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Records are created via type-specific builders
 * EntityRecord entity = EntityRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("customers")
 *     .data(Map.of("name", "John", "email", "john@example.com"))
 *     .build();
 *
 * EventRecord event = EventRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("order-events")
 *     .streamName("orders")
 *     .data(Map.of("orderId", "ORD-001", "amount", 99.99))
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Storage</b><br>
 * Records are persisted via StoragePlugin implementations. The storage layer
 * uses recordType to determine appropriate operations (CRUD vs append).
 *
 * @see RecordType
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @doc.type class
 * @doc.purpose Base class for all record types
 * @doc.layer core
 * @doc.pattern Template Method, Type Object
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class DataRecord implements DataRecordInterface {

    /**
     * Unique identifier for this record.
     * <p>
     * Generated as UUID v4 if not provided.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    protected UUID id;

    /**
     * Tenant identifier for multi-tenancy isolation.
     * <p>
     * All queries are implicitly scoped to tenant.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 255)
    protected String tenantId;

    /**
     * Collection name this record belongs to.
     * <p>
     * Collections define schema, validation, and storage configuration.
     */
    @Column(name = "collection_name", nullable = false, updatable = false, length = 255)
    protected String collectionName;

    /**
     * Record type defining behavior (ENTITY, EVENT, etc.).
     * <p>
     * Determines which operations are valid for this record.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, updatable = false, length = 50)
    protected RecordType recordType;

    /**
     * Dynamic payload stored as JSONB.
     * <p>
     * Schema is defined by the collection's field definitions.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    @Builder.Default
    protected Map<String, Object> data = new HashMap<>();

    /**
     * Type-specific metadata stored as JSONB.
     * <p>
     * Used for additional attributes that vary by record type.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    protected Map<String, Object> metadata = new HashMap<>();

    /**
     * Timestamp when this record was created.
     */
    @Column(name = "created_at", updatable = false)
    protected Instant createdAt;

    /**
     * User or system that created this record.
     */
    @Column(name = "created_by", updatable = false, length = 255)
    protected String createdBy;

    // ==================== Abstract Methods ====================
    /**
     * Returns the record type this instance represents.
     * <p>
     * Subclasses must return their corresponding RecordType.
     *
     * @return the RecordType for this record
     */
    public abstract RecordType getRecordType();

    // ==================== Lifecycle Callbacks ====================
    /**
     * Pre-persist callback to set defaults.
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (recordType == null) {
            recordType = getRecordType();
        }
        if (data == null) {
            data = new HashMap<>();
        }
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

    // ==================== Utility Methods ====================
    /**
     * Get a typed value from the data payload.
     *
     * @param key the field key
     * @param type the expected type
     * @param <T> the return type
     * @return the value cast to type, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getDataValue(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        throw new ClassCastException(
                "Cannot cast " + value.getClass().getName() + " to " + type.getName()
        );
    }

    /**
     * Set a value in the data payload.
     *
     * @param key the field key
     * @param value the value to set
     * @return this record for chaining
     */
    public DataRecord setDataValue(String key, Object value) {
        if (data == null) {
            data = new HashMap<>();
        }
        data.put(key, value);
        return this;
    }

    /**
     * Get a typed value from metadata.
     *
     * @param key the metadata key
     * @param type the expected type
     * @param <T> the return type
     * @return the value cast to type, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        throw new ClassCastException(
                "Cannot cast " + value.getClass().getName() + " to " + type.getName()
        );
    }

    /**
     * Set a value in metadata.
     *
     * @param key the metadata key
     * @param value the value to set
     * @return this record for chaining
     */
    public DataRecord setMetadataValue(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
        return this;
    }

    /**
     * Check if this record type supports the given operation.
     *
     * @param operation the operation to check
     * @return true if the operation is supported
     */
    public boolean supportsOperation(RecordOperation operation) {
        return switch (operation) {
            case CREATE ->
                true; // All types support create
            case READ ->
                true;   // All types support read
            case UPDATE ->
                getRecordType().isMutable();
            case DELETE ->
                getRecordType().isMutable();
            case APPEND ->
                getRecordType().isAppendOnly();
            case SUBSCRIBE ->
                getRecordType().supportsStreaming();
            case AGGREGATE ->
                getRecordType().isAggregatable();
        };
    }

    /**
     * Operations that can be performed on records.
     */
    public enum RecordOperation {
        CREATE,
        READ,
        UPDATE,
        DELETE,
        APPEND,
        SUBSCRIBE,
        AGGREGATE
    }

    // ==================== Object Methods ====================
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DataRecord record = (DataRecord) o;
        return id != null && id.equals(record.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", collectionName='" + collectionName + '\''
                + ", recordType=" + recordType
                + '}';
    }
}
