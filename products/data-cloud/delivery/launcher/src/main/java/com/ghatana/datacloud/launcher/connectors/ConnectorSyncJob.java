package com.ghatana.datacloud.launcher.connectors;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * P0-08: Durable connector sync job record.
 * 
 * <p>This record represents a connector sync job with its state, metadata, and evidence.
 * Jobs are persisted to ensure durability and support retry/cancellation semantics.
 *
 * @doc.type class
 * @doc.purpose Durable connector sync job record
 * @doc.layer product
 * @doc.pattern Job
 */
public class ConnectorSyncJob {
    
    private final String id;
    private final String tenantId;
    private final String connectionId;
    private final String jobType;
    private final ConnectorJobState state;
    private final Map<String, Object> jobConfig;
    private final Map<String, Object> evidence;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String errorMessage;
    private final int retryCount;
    private final String correlationId;
    
    private ConnectorSyncJob(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.connectionId = builder.connectionId;
        this.jobType = builder.jobType;
        this.state = builder.state;
        this.jobConfig = builder.jobConfig;
        this.evidence = builder.evidence;
        this.createdAt = builder.createdAt;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.errorMessage = builder.errorMessage;
        this.retryCount = builder.retryCount;
        this.correlationId = builder.correlationId;
    }
    
    public String id() {
        return id;
    }
    
    public String tenantId() {
        return tenantId;
    }
    
    public String connectionId() {
        return connectionId;
    }
    
    public String jobType() {
        return jobType;
    }
    
    public ConnectorJobState state() {
        return state;
    }
    
    public Map<String, Object> jobConfig() {
        return jobConfig;
    }
    
    public Map<String, Object> evidence() {
        return evidence;
    }
    
    public Instant createdAt() {
        return createdAt;
    }
    
    public Instant startedAt() {
        return startedAt;
    }
    
    public Instant completedAt() {
        return completedAt;
    }
    
    public String errorMessage() {
        return errorMessage;
    }
    
    public int retryCount() {
        return retryCount;
    }
    
    public String correlationId() {
        return correlationId;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String tenantId;
        private String connectionId;
        private String jobType;
        private ConnectorJobState state = ConnectorJobState.PENDING;
        private Map<String, Object> jobConfig;
        private Map<String, Object> evidence;
        private Instant createdAt;
        private Instant startedAt;
        private Instant completedAt;
        private String errorMessage;
        private int retryCount = 0;
        private String correlationId;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }
        
        public Builder jobType(String jobType) {
            this.jobType = jobType;
            return this;
        }
        
        public Builder state(ConnectorJobState state) {
            this.state = state;
            return this;
        }
        
        public Builder jobConfig(Map<String, Object> jobConfig) {
            this.jobConfig = jobConfig;
            return this;
        }
        
        public Builder evidence(Map<String, Object> evidence) {
            this.evidence = evidence;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }
        
        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
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
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public ConnectorSyncJob build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return new ConnectorSyncJob(this);
        }
    }
}
