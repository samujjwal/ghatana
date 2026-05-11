package com.ghatana.datacloud.spi;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * P0-06: Outbox entry for entity write operations.
 * 
 * <p>The outbox pattern ensures atomicity of entity writes with downstream operations
 * (event append, audit, indexing, websocket broadcast). The outbox stores the intent
 * to perform these operations, which are processed asynchronously after the transaction commits.
 *
 * @doc.type class
 * @doc.purpose Outbox entry for atomic entity write lifecycle
 * @doc.layer product
 * @doc.pattern Outbox
 */
public class EntityWriteOutbox {
    
    private final String id;
    private final String tenantId;
    private final String collection;
    private final String entityId;
    private final String operationType;
    private final Map<String, Object> entitySnapshot;
    private final Map<String, Object> eventPayload;
    private final String correlationId;
    private final Instant createdAt;
    private final Instant processedAt;
    private final OutboxStatus status;
    private final String errorMessage;
    
    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    private EntityWriteOutbox(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.collection = builder.collection;
        this.entityId = builder.entityId;
        this.operationType = builder.operationType;
        this.entitySnapshot = builder.entitySnapshot;
        this.eventPayload = builder.eventPayload;
        this.correlationId = builder.correlationId;
        this.createdAt = builder.createdAt;
        this.processedAt = builder.processedAt;
        this.status = builder.status;
        this.errorMessage = builder.errorMessage;
    }
    
    public String id() {
        return id;
    }
    
    public String tenantId() {
        return tenantId;
    }
    
    public String collection() {
        return collection;
    }
    
    public String entityId() {
        return entityId;
    }
    
    public String operationType() {
        return operationType;
    }
    
    public Map<String, Object> entitySnapshot() {
        return entitySnapshot;
    }
    
    public Map<String, Object> eventPayload() {
        return eventPayload;
    }
    
    public String correlationId() {
        return correlationId;
    }
    
    public Instant createdAt() {
        return createdAt;
    }
    
    public Instant processedAt() {
        return processedAt;
    }
    
    public OutboxStatus status() {
        return status;
    }
    
    public String errorMessage() {
        return errorMessage;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String tenantId;
        private String collection;
        private String entityId;
        private String operationType;
        private Map<String, Object> entitySnapshot;
        private Map<String, Object> eventPayload;
        private String correlationId;
        private Instant createdAt;
        private Instant processedAt;
        private OutboxStatus status = OutboxStatus.PENDING;
        private String errorMessage;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder collection(String collection) {
            this.collection = collection;
            return this;
        }
        
        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }
        
        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }
        
        public Builder entitySnapshot(Map<String, Object> entitySnapshot) {
            this.entitySnapshot = entitySnapshot;
            return this;
        }
        
        public Builder eventPayload(Map<String, Object> eventPayload) {
            this.eventPayload = eventPayload;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder processedAt(Instant processedAt) {
            this.processedAt = processedAt;
            return this;
        }
        
        public Builder status(OutboxStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public EntityWriteOutbox build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return new EntityWriteOutbox(this);
        }
    }
}
