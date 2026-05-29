/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.operations;

import java.time.Instant;
import java.util.Map;

/**
 * Service for emitting structured audit/domain events for every mutation.
 * 
 * P10.2: Emit structured audit/domain events for every mutation.
 * Provides a unified interface for recording all mutation events across the system.
 * 
 * @doc.type interface
 * @doc.purpose Structured audit/domain event emission
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AuditEventService {

    /**
     * Emits an audit event for a mutation.
     *
     * @param event the audit event
     */
    void emitAuditEvent(AuditEvent event);

    /**
     * Emits a domain event.
     *
     * @param event the domain event
     */
    void emitDomainEvent(DomainEvent event);

    /**
     * Emits a policy decision event.
     *
     * @param event the policy decision event
     */
    void emitPolicyDecisionEvent(PolicyDecisionEvent event);

    /**
     * Audit event for mutations.
     *
     * @param eventId unique event ID
     * @param eventType the event type
     * @param tenantId the tenant ID
     * @param userId the user who performed the action
     * @param resourceType the type of resource affected
     * @param resourceId the ID of the resource affected
     * @param action the action performed
     * @param previousState the previous state (if applicable)
     * @param newState the new state (if applicable)
     * @param metadata additional metadata
     * @param timestamp when the event occurred
     */
    record AuditEvent(
            String eventId,
            String eventType,
            String tenantId,
            String userId,
            String resourceType,
            String resourceId,
            String action,
            Map<String, Object> previousState,
            Map<String, Object> newState,
            Map<String, String> metadata,
            Instant timestamp) {

        public AuditEvent(
                String eventType,
                String tenantId,
                String userId,
                String resourceType,
                String resourceId,
                String action,
                Map<String, Object> previousState,
                Map<String, Object> newState) {
            this(java.util.UUID.randomUUID().toString(), eventType, tenantId, userId, resourceType, resourceId, action, previousState, newState, Map.of(), Instant.now());
        }
    }

    /**
     * Domain event for business events.
     *
     * @param eventId unique event ID
     * @param eventType the event type
     * @param tenantId the tenant ID
     * @param aggregateType the aggregate type
     * @param aggregateId the aggregate ID
     * @param eventData the event data
     * @param correlationId the correlation ID (if applicable)
     * @param causationId the causation ID (if applicable)
     * @param timestamp when the event occurred
     */
    record DomainEvent(
            String eventId,
            String eventType,
            String tenantId,
            String aggregateType,
            String aggregateId,
            Map<String, Object> eventData,
            String correlationId,
            String causationId,
            Instant timestamp) {

        public DomainEvent(
                String eventType,
                String tenantId,
                String aggregateType,
                String aggregateId,
                Map<String, Object> eventData) {
            this(java.util.UUID.randomUUID().toString(), eventType, tenantId, aggregateType, aggregateId, eventData, null, null, Instant.now());
        }
    }

    /**
     * Policy decision event.
     *
     * @param eventId unique event ID
     * @param policyId the policy ID
     * @param tenantId the tenant ID
     * @param decision the decision made
     * @param reason the reason for the decision
     * @param context the decision context
     * @param timestamp when the decision was made
     */
    record PolicyDecisionEvent(
            String eventId,
            String policyId,
            String tenantId,
            Decision decision,
            String reason,
            Map<String, Object> context,
            Instant timestamp) {

        public PolicyDecisionEvent(
                String policyId,
                String tenantId,
                Decision decision,
                String reason,
                Map<String, Object> context) {
            this(java.util.UUID.randomUUID().toString(), policyId, tenantId, decision, reason, context, Instant.now());
        }

        public enum Decision {
            APPROVE,
            REJECT,
            ESCALATE,
            DEFER
        }
    }

    /**
     * Common event types for audit events.
     */
    enum AuditEventType {
        COLLECTION_CREATED,
        COLLECTION_UPDATED,
        COLLECTION_DELETED,
        COLLECTION_ARCHIVED,
        DATASET_CREATED,
        DATASET_UPDATED,
        DATASET_DELETED,
        CONNECTOR_CREATED,
        CONNECTOR_UPDATED,
        CONNECTOR_DELETED,
        CONNECTOR_SYNC_STARTED,
        CONNECTOR_SYNC_COMPLETED,
        CONNECTOR_SYNC_FAILED,
        PIPELINE_CREATED,
        PIPELINE_UPDATED,
        PIPELINE_DELETED,
        PIPELINE_STARTED,
        PIPELINE_COMPLETED,
        PIPELINE_FAILED,
        AGENT_REGISTERED,
        AGENT_DEREGISTERED,
        AGENT_EXECUTED,
        POLICY_CREATED,
        POLICY_UPDATED,
        POLICY_DELETED,
        POLICY_EVALUATED,
        RETENTION_POLICY_APPLIED,
        REDACTION_REQUESTED,
        REDACTION_COMPLETED,
        AV_ASSET_INGESTED,
        AV_ASSET_PROCESSED,
        AV_ASSET_DELETED
    }

    /**
     * Common event types for domain events.
     */
    enum DomainEventType {
        DATA_QUALITY_CHANGED,
        DATA_LINEAGE_UPDATED,
        SCHEMA_EVOLVED,
        RETENTION_EXPIRED,
        DATA_PURGED,
        LEARNING_DELTA_CREATED,
        LEARNING_DELTA_PROMOTED,
        LEARNING_DELTA_REJECTED,
        MASTERY_UPDATED,
        POLICY_PROMOTED,
        POLICY_ROLLED_BACK,
        COMPLIANCE_VIOLATION_DETECTED,
        ANOMALY_DETECTED,
        DEAD_LETTER_CREATED,
        DEAD_LETTER_PROCESSED,
        DEAD_LETTER_RECOVERED
    }
}
