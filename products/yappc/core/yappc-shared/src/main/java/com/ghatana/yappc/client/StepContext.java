package com.ghatana.yappc.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Context for SDLC step execution.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles step context operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class StepContext {
    
    private final String tenantId;
    private final String userId;
    private final String projectId;
    private final String phase;
    private final Map<String, Object> properties;
    
    private StepContext(Builder builder) {
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.projectId = builder.projectId;
        this.phase = builder.phase;
        this.properties = Map.copyOf(builder.properties);
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public String getPhase() {
        return phase;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public static StepContext forTenant(String tenantId) {
        return builder().tenantId(tenantId).build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String tenantId = "default";
        private String userId = "system";
        private String projectId;
        private String phase;
        private final Map<String, Object> properties = new HashMap<>();
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }
        
        public Builder phase(String phase) {
            this.phase = phase;
            return this;
        }
        
        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }
        
        public StepContext build() {
            return new StepContext(this);
        }
    }
}
