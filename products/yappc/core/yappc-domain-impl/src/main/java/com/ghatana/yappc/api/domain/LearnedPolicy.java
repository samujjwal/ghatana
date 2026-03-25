package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a learned policy extracted from high-confidence agent executions.
 * Policies capture reusable patterns and procedures learned from successful agent runs.
 *
 * @doc.type class
 * @doc.purpose Domain model for learned agent policies
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public class LearnedPolicy {
    
    private String id;
    private String tenantId;
    private String agentId;
    private String name;
    private String description;
    private String procedure;
    private double confidence;
    private String source;
    private String version;
    private String agentType;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private int usageCount;
    private boolean active;
    
    // Private constructor for builder
    private LearnedPolicy(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.agentId = builder.agentId;
        this.name = builder.name;
        this.description = builder.description;
        this.procedure = builder.procedure;
        this.confidence = builder.confidence;
        this.source = builder.source;
        this.version = builder.version;
        this.agentType = builder.agentType;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        this.usageCount = builder.usageCount;
        this.active = builder.active;
    }
    
    // Default constructor
    public LearnedPolicy() {
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.active = true;
        this.usageCount = 0;
    }
    
    // Builder factory method
    public static Builder builder() {
        return new Builder();
    }
    
    // Builder class
    public static class Builder {
        private String id;
        private String tenantId;
        private String agentId;
        private String name;
        private String description;
        private String procedure;
        private double confidence;
        private String source;
        private String version;
        private String agentType;
        private Map<String, Object> metadata;
        private Instant createdAt;
        private Instant updatedAt;
        private int usageCount;
        private boolean active;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder procedure(String procedure) {
            this.procedure = procedure;
            return this;
        }
        
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }
        
        public Builder source(String source) {
            this.source = source;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public Builder usageCount(int usageCount) {
            this.usageCount = usageCount;
            return this;
        }
        
        public Builder active(boolean active) {
            this.active = active;
            return this;
        }
        
        public LearnedPolicy build() {
            return new LearnedPolicy(this);
        }
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getProcedure() { return procedure; }
    public void setProcedure(String procedure) { this.procedure = procedure; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    /**
     * Increment usage count when this policy is applied.
     */
    public void incrementUsage() {
        this.usageCount++;
        this.updatedAt = Instant.now();
    }
    
    @Override
    public String toString() {
        return "LearnedPolicy{" +
                "id='" + id + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", name='" + name + '\'' +
                ", confidence=" + confidence +
                ", source='" + source + '\'' +
                ", version='" + version + '\'' +
                ", active=" + active +
                '}';
    }
}
