/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry.store;

import com.ghatana.aep.pattern.lifecycle.PatternLifecycleState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing pattern lifecycle state in the database.
 * 
 * @doc.type class
 * @doc.purpose Provides durable storage for pattern lifecycle state
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "pattern_lifecycle_states")
public class PatternLifecycleStateEntity {
    
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "pattern_id", nullable = false)
    private String patternId;

    @Column(name = "state", nullable = false)
    private String state;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public PatternLifecycleStateEntity() {
    }

    public PatternLifecycleStateEntity(String tenantId, String patternId, PatternLifecycleState state) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.patternId = patternId;
        this.state = state.name();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPatternId() { return patternId; }
    public void setPatternId(String patternId) { this.patternId = patternId; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public PatternLifecycleState getStateAsEnum() { 
        return PatternLifecycleState.valueOf(state); 
    }

    public void setStateAsEnum(PatternLifecycleState state) { 
        this.state = state.name(); 
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternLifecycleStateEntity that = (PatternLifecycleStateEntity) o;
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
        return "PatternLifecycleStateEntity{" +
               "id='" + id + '\'' +
               ", tenantId='" + tenantId + '\'' +
               ", patternId='" + patternId + '\'' +
               ", state='" + state + '\'' +
               '}';
    }
}
