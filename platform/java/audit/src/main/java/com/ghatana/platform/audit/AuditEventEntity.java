/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 */
package com.ghatana.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA persistence entity for audit events.
 *
 * <p>Separate from the domain {@link AuditEvent} to preserve the clean immutable
 * value object without JPA annotations. A mapper handles conversion between the
 * domain model and this entity.</p>
 *
 * @doc.type class
 * @doc.purpose JPA persistence entity for audit event records
 * @doc.layer infrastructure
 * @doc.pattern Entity
 */
@Entity
@Table(
    name = "audit_events",
    indexes = {
        @Index(name = "idx_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_tenant_timestamp", columnList = "tenant_id, timestamp"),
        @Index(name = "idx_audit_tenant_resource", columnList = "tenant_id, resource_type, resource_id"),
        @Index(name = "idx_audit_tenant_principal", columnList = "tenant_id, principal"),
        @Index(name = "idx_audit_tenant_event_type", columnList = "tenant_id, event_type")
    }
)
public class AuditEventEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "principal", length = 256)
    private String principal;

    @Column(name = "resource_type", length = 128)
    private String resourceType;

    @Column(name = "resource_id", length = 512)
    private String resourceId;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /** JSON-serialized representation of {@code Map<String, Object> details}. */
    @Lob
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    /** Hash of the previous audit event in the chain for integrity verification. */
    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    /** Computed hash of this event including all fields for chain integrity. */
    @Column(name = "chain_hash", nullable = false, length = 64)
    private String chainHash;

    /** Required by JPA. */
    protected AuditEventEntity() {}

    public AuditEventEntity(String id, String tenantId, String eventType, String principal,
                            String resourceType, String resourceId, Boolean success,
                            Instant timestamp, String detailsJson, String previousHash, String chainHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.principal = principal;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.success = success;
        this.timestamp = timestamp;
        this.detailsJson = detailsJson;
        this.previousHash = previousHash;
        this.chainHash = chainHash;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getPrincipal() { return principal; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public Boolean getSuccess() { return success; }
    public Instant getTimestamp() { return timestamp; }
    public String getDetailsJson() { return detailsJson; }
    public String getPreviousHash() { return previousHash; }
    public String getChainHash() { return chainHash; }
}
