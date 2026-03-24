package com.ghatana.yappc.client;

import java.util.Map;

/**
 * Definition of a task that can be executed.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles task definition operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class TaskDefinition {
    
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final Map<String, Object> configuration;
    
    private TaskDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.category = builder.category;
        this.configuration = Map.copyOf(builder.configuration);
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getCategory() {
        return category;
    }
    
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String id;
        private String name;
        private String description = "";
        private String category = "general";
        private Map<String, Object> configuration = Map.of();
        
        public Builder id(String id) {
            this.id = id;
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
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }
        
        public TaskDefinition build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Task ID is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Task name is required");
            }
            return new TaskDefinition(this);
        }
    }
}
