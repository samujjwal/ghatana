package com.ghatana.agent.registry.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit event model.
 * 
 * @doc.type class
 * @doc.purpose Audit event data
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class AuditEvent {
    
    private final String id;
    private final String tenantId;
    private final String eventType;
    private final String action;
    private final String actor;
    private final String resource;
    private final String outcome;
    private final Map<String, Object> details;
    private final Instant timestamp;
    
    public AuditEvent(String tenantId, String eventType, String action,
                     String actor, String resource, String outcome,
                     Map<String, Object> details) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.action = action;
        this.actor = actor;
        this.resource = resource;
        this.outcome = outcome;
        this.details = details;
        this.timestamp = Instant.now();
    }
    
    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getResource() { return resource; }
    public String getOutcome() { return outcome; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Builder for audit events.
     */
    public static class Builder {
        private String tenantId;
        private String eventType;
        private String action;
        private String actor;
        private String resource;
        private String outcome = "SUCCESS";
        private Map<String, Object> details = Map.of();
        
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder actor(String actor) { this.actor = actor; return this; }
        public Builder resource(String resource) { this.resource = resource; return this; }
        public Builder outcome(String outcome) { this.outcome = outcome; return this; }
        public Builder details(Map<String, Object> details) { this.details = details; return this; }
        
        public AuditEvent build() {
            return new AuditEvent(tenantId, eventType, action, actor, resource, outcome, details);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}
