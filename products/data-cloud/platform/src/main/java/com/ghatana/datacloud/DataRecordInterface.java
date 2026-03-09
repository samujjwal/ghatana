package com.ghatana.datacloud;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Core interface for all records in Data-Cloud.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the contract for all data records without depending on JPA
 * implementation details. This abstraction enables:
 * <ul>
 * <li>Decoupling domain logic from persistence</li>
 * <li>Supporting multiple implementations (in-memory, external systems)</li>
 * <li>Clear API contracts for clients</li>
 * </ul>
 *
 * <p>
 * <b>Core Attributes</b><br>
 * <ul>
 * <li><b>id</b> - Unique identifier (UUID)</li>
 * <li><b>tenantId</b> - Multi-tenancy isolation key</li>
 * <li><b>collectionName</b> - Logical grouping (schema)</li>
 * <li><b>recordType</b> - Behavior definition (ENTITY, EVENT, etc.)</li>
 * <li><b>data</b> - Dynamic payload</li>
 * <li><b>metadata</b> - Type-specific metadata</li>
 * </ul>
 *
 * <p>
 * <b>Record Type Hierarchy</b><br>
 * Implementations include:
 * <ul>
 * <li>EntityRecord - Mutable, versioned entities</li>
 * <li>EventRecord - Immutable, ordered events</li>
 * <li>TimeSeriesRecord - Timestamped measurements</li>
 * <li>GraphRecord - Relationship graphs</li>
 * <li>DocumentRecord - Schema-free documents</li>
 * </ul>
 *
 * @see RecordType
 * @doc.type interface
 * @doc.purpose Contract for all data records
 * @doc.layer core
 * @doc.pattern Domain Model, Strategy
 */
public interface DataRecordInterface {

    /**
     * Get the unique identifier of this record.
     *
     * @return record ID (UUID)
     */
    UUID getId();

    /**
     * Get the tenant identifier for multi-tenancy isolation.
     *
     * @return tenant ID
     */
    String getTenantId();

    /**
     * Get the collection name this record belongs to.
     *
     * @return collection name
     */
    String getCollectionName();

    /**
     * Get the record type defining behavior.
     *
     * @return record type
     */
    RecordType getRecordType();

    /**
     * Get the dynamic data payload.
     *
     * @return data as key-value map
     */
    Map<String, Object> getData();

    /**
     * Get type-specific metadata.
     *
     * @return metadata as key-value map
     */
    Map<String, Object> getMetadata();

    /**
     * Get when this record was created.
     *
     * @return creation timestamp
     */
    Instant getCreatedAt();

    /**
     * Get who/what created this record.
     *
     * @return creator identifier
     */
    String getCreatedBy();

    /**
     * Set the unique identifier.
     *
     * @param id new ID
     */
    void setId(UUID id);

    /**
     * Set the dynamic data payload.
     *
     * @param data new data map
     */
    void setData(Map<String, Object> data);

    /**
     * Set type-specific metadata.
     *
     * @param metadata new metadata map
     */
    void setMetadata(Map<String, Object> metadata);

    /**
     * Set the collection name.
     *
     * @param collectionName new collection name
     */
    void setCollectionName(String collectionName);

    /**
     * Set who/what created this record.
     *
     * @param createdBy creator identifier
     */
    void setCreatedBy(String createdBy);

    /**
     * Set the creation timestamp.
     *
     * @param createdAt creation time
     */
    void setCreatedAt(Instant createdAt);
}

