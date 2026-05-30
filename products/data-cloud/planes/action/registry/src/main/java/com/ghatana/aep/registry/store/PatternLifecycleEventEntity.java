/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry.store;

import com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing pattern lifecycle event in the database.
 * 
 * @doc.type class
 * @doc.purpose Provides durable storage for pattern lifecycle events
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "pattern_lifecycle_events")
public class PatternLifecycleEventEntity {
    
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "pattern_id", nullable = false)
    private String patternId;

    @Column(name = "from_state", nullable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "policy_decision")
    private String policyDecision;

    @Column(name = "confidence")
    private Double confidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public PatternLifecycleEventEntity() {
    }

    public PatternLifecycleEventEntity(
            String eventId,
            String tenantId,
            String patternId,
            String fromState,
            String toState,
            PatternLifecycleEventType eventType,
            String actor,
            Instant occurredAt) {
        this.id = eventId != null && !eventId.isBlank() ? eventId : UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.patternId = patternId;
        this.fromState = fromState;
        this.toState = toState;
        this.eventType = eventType.name();
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPatternId() { return patternId; }
    public void setPatternId(String patternId) { this.patternId = patternId; }

    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }

    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public PatternLifecycleEventType getEventTypeAsEnum() { 
        return PatternLifecycleEventType.valueOf(eventType); 
    }

    public void setEventTypeAsEnum(PatternLifecycleEventType eventType) { 
        this.eventType = eventType.name(); 
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getPolicyDecision() { return policyDecision; }
    public void setPolicyDecision(String policyDecision) { this.policyDecision = policyDecision; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternLifecycleEventEntity that = (PatternLifecycleEventEntity) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(patternId, that.patternId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, patternId);
    }

    @Override
    public String toString() {
        return "PatternLifecycleEventEntity{" +
               "id='" + id + '\'' +
               ", tenantId='" + tenantId + '\'' +
               ", patternId='" + patternId + '\'' +
               ", eventType='" + eventType + '\'' +
               ", actor='" + actor + '\'' +
               ", occurredAt=" + occurredAt +
               '}';
    }
}
