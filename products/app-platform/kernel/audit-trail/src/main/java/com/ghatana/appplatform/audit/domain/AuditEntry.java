package com.ghatana.appplatform.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit entry carrying actor/resource context and dual-calendar timestamps.
 *
 * <p>Domain-agnostic audit record for any action that requires a cryptographically
 * verifiable audit trail. Structured {@code actor} and {@code resource} objects
 * provide consistent evidence for compliance queries.
 *
 * @doc.type class
 * @doc.purpose Audit entry with dual-calendar timestamps and structured actor/resource context
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class AuditEntry {

    public enum Outcome { SUCCESS, FAILURE, PARTIAL }

    /** Who performed the action. */
    public record Actor(
        String userId,
        String role,
        String ipAddress,
        String sessionId
    ) {
        public static Actor of(String userId, String role) {
            return new Actor(userId, role, null, null);
        }
    }

    /** What was acted upon. */
    public record Resource(
        String type,
        String id,
        String parentId
    ) {
        public static Resource of(String type, String id) {
            return new Resource(type, id, null);
        }
    }

    private final String id;
    private final String action;
    private final Actor actor;
    private final Resource resource;
    private final Map<String, Object> details;
    private final Outcome outcome;
    private final String tenantId;
    private final String traceId;
    /** BS (Bikram Sambat) date — populated by the calendar-service kernel, else empty string. */
    private final String timestampBs;
    private final Instant timestampGregorian;

    private AuditEntry(Builder b) {
        this.id                 = b.id != null ? b.id : UUID.randomUUID().toString();
        this.action             = Objects.requireNonNull(b.action, "action");
        this.actor              = Objects.requireNonNull(b.actor, "actor");
        this.resource           = Objects.requireNonNull(b.resource, "resource");
        this.details            = b.details != null ? Map.copyOf(b.details) : Map.of();
        this.outcome            = Objects.requireNonNull(b.outcome, "outcome");
        this.tenantId           = Objects.requireNonNull(b.tenantId, "tenantId");
        this.traceId            = b.traceId;
        this.timestampBs        = b.timestampBs != null ? b.timestampBs : "";
        this.timestampGregorian = b.timestampGregorian != null ? b.timestampGregorian : Instant.now();
    }

    public String id()                 { return id; }
    public String action()             { return action; }
    public Actor actor()               { return actor; }
    public Resource resource()         { return resource; }
    public Map<String, Object> details(){ return details; }
    public Outcome outcome()           { return outcome; }
    public String tenantId()           { return tenantId; }
    public String traceId()            { return traceId; }
    /** BS calendar date string — empty when the calendar service is not yet wired. */
    public String timestampBs()        { return timestampBs; }
    public Instant timestampGregorian(){ return timestampGregorian; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String action;
        private Actor actor;
        private Resource resource;
        private Map<String, Object> details;
        private Outcome outcome;
        private String tenantId;
        private String traceId;
        private String timestampBs;
        private Instant timestampGregorian;

        public Builder id(String id)                         { this.id = id; return this; }
        public Builder action(String action)                 { this.action = action; return this; }
        public Builder actor(Actor actor)                    { this.actor = actor; return this; }
        public Builder resource(Resource resource)           { this.resource = resource; return this; }
        public Builder details(Map<String, Object> details)  { this.details = details; return this; }
        public Builder outcome(Outcome outcome)              { this.outcome = outcome; return this; }
        public Builder tenantId(String tenantId)             { this.tenantId = tenantId; return this; }
        public Builder traceId(String traceId)               { this.traceId = traceId; return this; }
        public Builder timestampBs(String bs)                { this.timestampBs = bs; return this; }
        public Builder timestampGregorian(Instant ts)        { this.timestampGregorian = ts; return this; }

        public AuditEntry build() { return new AuditEntry(this); }
    }
}
