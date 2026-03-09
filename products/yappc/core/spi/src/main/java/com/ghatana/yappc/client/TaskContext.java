package com.ghatana.yappc.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Context for task execution.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles task context operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class TaskContext {
    
    private final String tenantId;
    private final String userId;
    private final Map<String, Object> properties;
    
    private TaskContext(Builder builder) {
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.properties = Map.copyOf(builder.properties);
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public static TaskContext defaultContext() {
        return builder().build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String tenantId = "default";
        private String userId = "system";
        private final Map<String, Object> properties = new HashMap<>();
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }
        
        public Builder properties(Map<String, Object> properties) {
            this.properties.putAll(properties);
            return this;
        }
        
        public TaskContext build() {
            return new TaskContext(this);
        }
    }
}
