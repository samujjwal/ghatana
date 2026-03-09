package com.ghatana.datacloud.entity;

import com.ghatana.datacloud.RecordType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Simple implementation of EntityInterface for basic entity operations.
 * 
 * <p>This class provides a straightforward, mutable implementation of the
 * EntityInterface suitable for use in storage plugins and client libraries.
 * 
 * <p><b>Usage Example</b>
 * <pre>{@code
 * SimpleEntity entity = new SimpleEntity(
 *     UUID.randomUUID(),
 *     "tenant-1",
 *     "users",
 *     Map.of("name", "John", "email", "john@example.com"),
 *     1L,
 *     Instant.now(),
 *     Instant.now()
 * );
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Simple mutable entity implementation
 * @doc.layer core
 * @doc.pattern Value Object
 */
public class SimpleEntity implements EntityInterface {
    
    private UUID id;
    private String tenantId;
    private String collectionName;
    private Map<String, Object> data;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private Integer version;
    private Boolean active;
    
    /**
     * Constructs a SimpleEntity with required fields.
     * 
     * @param id Entity unique identifier
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param data Entity data fields
     * @param version Entity version for optimistic locking
     * @param createdAt Creation timestamp
     * @param updatedAt Last update timestamp
     */
    public SimpleEntity(UUID id, String tenantId, String collectionName, 
                       Map<String, Object> data, long version,
                       Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.collectionName = collectionName;
        this.data = data;
        this.version = (int) version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.active = true;
        this.metadata = Map.of();
    }
    
    /**
     * Constructs a SimpleEntity with all fields including metadata.
     */
    public SimpleEntity(UUID id, String tenantId, String collectionName,
                       Map<String, Object> data, Map<String, Object> metadata,
                       Instant createdAt, Instant updatedAt, Integer version) {
        this.id = id;
        this.tenantId = tenantId;
        this.collectionName = collectionName;
        this.data = data;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.active = true;
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public void setId(UUID id) {
        this.id = id;
    }
    
    @Override
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    @Override
    public String getCollectionName() {
        return collectionName;
    }
    
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }
    
    @Override
    public RecordType getRecordType() {
        return RecordType.ENTITY;
    }
    
    @Override
    public Map<String, Object> getData() {
        return data;
    }
    
    @Override
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        return metadata != null ? metadata : Map.of();
    }
    
    @Override
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    @Override
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String getCreatedBy() {
        return createdBy;
    }
    
    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    @Override
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String getUpdatedBy() {
        return updatedBy;
    }
    
    @Override
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
    
    @Override
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    @Override
    public Boolean getActive() {
        return active != null ? active : true;
    }
    
    @Override
    public void setActive(Boolean active) {
        this.active = active;
    }
    
    @Override
    public String toString() {
        return "SimpleEntity{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", collectionName='" + collectionName + '\'' +
                ", version=" + version +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
