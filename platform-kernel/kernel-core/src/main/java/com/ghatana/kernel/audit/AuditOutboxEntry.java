package com.ghatana.kernel.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Durable audit outbox entry for product mutations.
 * 
 * <p>The audit outbox pattern ensures atomicity of product mutations with audit logging.
 * The outbox stores the intent to audit operations, which are processed asynchronously
 * after the transaction commits. This provides durability and exactly-once audit semantics.</p>
 *
 * @doc.type class
 * @doc.purpose Durable audit outbox entry for product mutation auditing
 * @doc.layer kernel
 * @doc.pattern Outbox
 */
public class AuditOutboxEntry {
    
    private final String id;
    private final String tenantId;
    private final String principalId;
    private final String product;
    private final String resourceType;
    private final String resourceId;
    private final String operationType;
    private final Map<String, Object> mutationData;
    private final Map<String, Object> metadata;
    private final String correlationId;
    private final Instant createdAt;
    private final Instant processedAt;
    private final OutboxStatus status;
    private final String errorMessage;
    private final int retryCount;
    
    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        RETRY_EXCEEDED
    }
    
    public enum OperationType {
        CREATE,
        UPDATE,
        DELETE,
        BULK_CREATE,
        BULK_UPDATE,
        BULK_DELETE
    }
    
    private AuditOutboxEntry(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.principalId = builder.principalId;
        this.product = builder.product;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.operationType = builder.operationType;
        this.mutationData = builder.mutationData;
        this.metadata = builder.metadata;
        this.correlationId = builder.correlationId;
        this.createdAt = builder.createdAt;
        this.processedAt = builder.processedAt;
        this.status = builder.status;
        this.errorMessage = builder.errorMessage;
        this.retryCount = builder.retryCount;
    }
    
    public String id() {
        return id;
    }
    
    public String tenantId() {
        return tenantId;
    }
    
    public String principalId() {
        return principalId;
    }
    
    public String product() {
        return product;
    }
    
    public String resourceType() {
        return resourceType;
    }
    
    public String resourceId() {
        return resourceId;
    }
    
    public String operationType() {
        return operationType;
    }
    
    public Map<String, Object> mutationData() {
        return mutationData;
    }
    
    public Map<String, Object> metadata() {
        return metadata;
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
    
    public int retryCount() {
        return retryCount;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String tenantId;
        private String principalId;
        private String product;
        private String resourceType;
        private String resourceId;
        private String operationType;
        private Map<String, Object> mutationData;
        private Map<String, Object> metadata;
        private String correlationId;
        private Instant createdAt;
        private Instant processedAt;
        private OutboxStatus status = OutboxStatus.PENDING;
        private String errorMessage;
        private int retryCount = 0;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }
        
        public Builder product(String product) {
            this.product = product;
            return this;
        }
        
        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }
        
        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }
        
        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }
        
        public Builder operationType(OperationType operationType) {
            this.operationType = operationType.name();
            return this;
        }
        
        public Builder mutationData(Map<String, Object> mutationData) {
            this.mutationData = mutationData;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
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
        
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }
        
        public AuditOutboxEntry build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (principalId == null || principalId.isBlank()) {
                throw new IllegalArgumentException("principalId is required");
            }
            if (product == null || product.isBlank()) {
                throw new IllegalArgumentException("product is required");
            }
            if (operationType == null || operationType.isBlank()) {
                throw new IllegalArgumentException("operationType is required");
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return new AuditOutboxEntry(this);
        }
    }
}
