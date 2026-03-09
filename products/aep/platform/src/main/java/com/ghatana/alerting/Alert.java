package com.ghatana.alerting;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Alert model.
 * 
 * @doc.type class
 * @doc.purpose Alert data model
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class Alert {
    
    private final String id;
    private final String title;
    private final String description;
    private final Severity severity;
    private final String source;
    private final String category;
    private final Map<String, Object> metadata;
    private final Instant createdAt;
    private Status status;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private String resolution;
    
    public Alert(String title, String description, Severity severity, 
                String source, String category, Map<String, Object> metadata) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.source = source;
        this.category = category;
        this.metadata = metadata;
        this.createdAt = Instant.now();
        this.status = Status.ACTIVE;
    }
    
    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Severity getSeverity() { return severity; }
    public String getSource() { return source; }
    public String getCategory() { return category; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolution() { return resolution; }
    
    // Status management
    public void acknowledge() {
        this.status = Status.ACKNOWLEDGED;
        this.acknowledgedAt = Instant.now();
    }
    
    public void resolve(String resolution) {
        this.status = Status.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolution = resolution;
    }
    
    /**
     * Alert severity levels.
     */
    public enum Severity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Alert status.
     */
    public enum Status {
        ACTIVE,
        ACKNOWLEDGED,
        RESOLVED,
        SUPPRESSED
    }
    
    /**
     * Builder for alerts.
     */
    public static class Builder {
        private String title;
        private String description;
        private Severity severity = Severity.INFO;
        private String source = "unknown";
        private String category = "general";
        private Map<String, Object> metadata = Map.of();
        
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder severity(Severity severity) { this.severity = severity; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        
        public Alert build() {
            return new Alert(title, description, severity, source, category, metadata);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}
