package com.ghatana.agent.registry.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Audit query parameters.
 * 
 * @doc.type class
 * @doc.purpose Audit search query
 * @doc.layer product
 * @doc.pattern Query Object
 */
public class AuditQuery {
    
    private final String tenantId;
    private final String eventType;
    private final String action;
    private final String actor;
    private final String resource;
    private final String outcome;
    private final Instant startTime;
    private final Instant endTime;
    private final int limit;
    private final int offset;
    
    private AuditQuery(Builder builder) {
        this.tenantId = builder.tenantId;
        this.eventType = builder.eventType;
        this.action = builder.action;
        this.actor = builder.actor;
        this.resource = builder.resource;
        this.outcome = builder.outcome;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.limit = builder.limit;
        this.offset = builder.offset;
    }
    
    public String getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getResource() { return resource; }
    public String getOutcome() { return outcome; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public int getLimit() { return limit; }
    public int getOffset() { return offset; }
    
    public static class Builder {
        private String tenantId;
        private String eventType;
        private String action;
        private String actor;
        private String resource;
        private String outcome;
        private Instant startTime = Instant.now().minusSeconds(86400);
        private Instant endTime = Instant.now();
        private int limit = 100;
        private int offset = 0;
        
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder actor(String actor) { this.actor = actor; return this; }
        public Builder resource(String resource) { this.resource = resource; return this; }
        public Builder outcome(String outcome) { this.outcome = outcome; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder limit(int limit) { this.limit = limit; return this; }
        public Builder offset(int offset) { this.offset = offset; return this; }
        
        public AuditQuery build() {
            return new AuditQuery(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}
