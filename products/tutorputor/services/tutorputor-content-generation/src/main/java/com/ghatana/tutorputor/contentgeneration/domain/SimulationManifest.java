package com.ghatana.tutorputor.explorer.model;

import java.util.Map;

public class SimulationManifest {
    private final String id;
    private final String title;
    private final String description;
    private final String domain;
    private final Map<String, Object> configuration;
    
    public SimulationManifest(String id, String title, String description, String domain, Map<String, Object> configuration) {
        this.id = id; this.title = title; this.description = description; 
        this.domain = domain; this.configuration = configuration;
    }
    
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDomain() { return domain; }
    public Map<String, Object> getConfiguration() { return configuration; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id, title, description, domain;
        private Map<String, Object> configuration;
        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder configuration(Map<String, Object> configuration) { this.configuration = configuration; return this; }
        public SimulationManifest build() { return new SimulationManifest(id, title, description, domain, configuration); }
    }
}
