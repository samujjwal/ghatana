/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import com.ghatana.data.governance.ConsentManager;
import com.ghatana.data.governance.DataAccessBroker;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * AEP compliance service that orchestrates consent verification and retention
 * checks as a single call for agent data-access decisions.
 *
 * <p>This is the primary entry point callers use in the AEP pipeline to verify
 * that a given data-access operation is compliant with:
 * <ol>
 *   <li>Subject consent (via {@link ConsentManager})</li>
 *   <li>Purpose limitation (via {@link DataAccessBroker})</li>
 *   <li>Retention policies (records are not past their retention deadline)</li>
 * </ol>
 *
 * <p>WS2: Also persists review, approval, and rollback evidence for compliance auditing.
 *
 * @doc.type class
 * @doc.purpose AEP compliance orchestrator for consent, purpose, retention, and governance evidence
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ComplianceService {

    private final DataAccessBroker dataAccessBroker;
    private final RetentionPolicyEnforcer retentionEnforcer;
    private final ComplianceEvidenceStore evidenceStore;

    /**
     * Construct the compliance service.
     *
     * @param dataAccessBroker  broker that checks consent + purpose limitation
     * @param retentionEnforcer checks retention policy compliance
     * @param evidenceStore     persists review/approval/rollback evidence
     */
    public ComplianceService(
            DataAccessBroker dataAccessBroker,
            RetentionPolicyEnforcer retentionEnforcer,
            ComplianceEvidenceStore evidenceStore) {
        this.dataAccessBroker = dataAccessBroker;
        this.retentionEnforcer = retentionEnforcer;
        this.evidenceStore = evidenceStore;
    }

    /**
     * Perform a full compliance check before accessing a data record.
     *
     * @param tenantId  owning tenant
     * @param subjectId data subject (e.g. user ID)
     * @param dataId    logical data asset identifier
     * @param purpose   the declared processing purpose
     * @return completed promise if all checks pass; failed otherwise
     */
    public Promise<Void> checkCompliance(
            String tenantId, String subjectId, String dataId, String purpose) {
        return dataAccessBroker.checkAccess(tenantId, subjectId, dataId, purpose)
            .then(() -> retentionEnforcer.checkRetention(tenantId, dataId));
    }

    // ==================== WS2: Evidence Persistence ====================

    /**
     * Persist review evidence for compliance auditing.
     *
     * <p>WS2: Records when a pattern or agent requires human review,
     * including the reviewer, decision, and rationale.
     *
     * @param tenantId    tenant identifier
     * @param entityId    entity being reviewed (pattern ID, agent ID, etc.)
     * @param entityType  type of entity (PATTERN, AGENT, ACTION)
     * @param reviewerId  ID of the reviewer
     * @param decision    review decision (APPROVED, DENIED, REQUESTED_CHANGES)
     * @param rationale   reviewer's rationale
     * @param metadata    additional metadata
     * @return promise that completes when evidence is persisted
     */
    public Promise<Void> persistReviewEvidence(
            String tenantId,
            String entityId,
            EntityType entityType,
            String reviewerId,
            ReviewDecision decision,
            String rationale,
            Map<String, Object> metadata) {
        ReviewEvidence evidence = new ReviewEvidence(
            java.util.UUID.randomUUID().toString(),
            tenantId,
            entityId,
            entityType,
            reviewerId,
            decision,
            rationale,
            metadata,
            Instant.now()
        );
        return evidenceStore.storeReviewEvidence(evidence);
    }

    /**
     * Persist approval evidence for compliance auditing.
     *
     * <p>WS2: Records when a pattern or agent is approved for activation or execution,
     * including the approver, approval level, and conditions.
     *
     * @param tenantId      tenant identifier
     * @param entityId      entity being approved
     * @param entityType    type of entity
     * @param approverId    ID of the approver
     * @param approvalLevel approval level (TIER_1, TIER_2, TIER_3)
     * @param conditions    any conditions attached to approval
     * @param metadata      additional metadata
     * @return promise that completes when evidence is persisted
     */
    public Promise<Void> persistApprovalEvidence(
            String tenantId,
            String entityId,
            EntityType entityType,
            String approverId,
            ApprovalLevel approvalLevel,
            Map<String, Object> conditions,
            Map<String, Object> metadata) {
        ApprovalEvidence evidence = new ApprovalEvidence(
            java.util.UUID.randomUUID().toString(),
            tenantId,
            entityId,
            entityType,
            approverId,
            approvalLevel,
            conditions,
            metadata,
            Instant.now()
        );
        return evidenceStore.storeApprovalEvidence(evidence);
    }

    /**
     * Persist rollback evidence for compliance auditing.
     *
     * <p>WS2: Records when a pattern or agent execution is rolled back,
     * including the reason, compensation strategy, and affected resources.
     *
     * @param tenantId           tenant identifier
     * @param operationId        operation being rolled back
     * @param entityType         type of entity
     * @param rollbackReason     reason for rollback
     * @param compensationStrategy strategy used for compensation
     * @param affectedResources  resources affected by rollback
     * @param metadata           additional metadata
     * @return promise that completes when evidence is persisted
     */
    public Promise<Void> persistRollbackEvidence(
            String tenantId,
            String operationId,
            EntityType entityType,
            String rollbackReason,
            String compensationStrategy,
            java.util.Set<String> affectedResources,
            Map<String, Object> metadata) {
        RollbackEvidence evidence = new RollbackEvidence(
            java.util.UUID.randomUUID().toString(),
            tenantId,
            operationId,
            entityType,
            rollbackReason,
            compensationStrategy,
            affectedResources,
            metadata,
            Instant.now()
        );
        return evidenceStore.storeRollbackEvidence(evidence);
    }

    /**
     * Retrieve compliance evidence for an entity.
     *
     * @param tenantId   tenant identifier
     * @param entityId   entity ID
     * @param entityType type of entity
     * @return list of compliance evidence records
     */
    public Promise<java.util.List<ComplianceEvidence>> getEvidence(
            String tenantId,
            String entityId,
            EntityType entityType) {
        return evidenceStore.retrieveEvidence(tenantId, entityId, entityType);
    }

    // ==================== Supporting Types ====================

    /**
     * Entity types for compliance evidence.
     */
    public enum EntityType {
        PATTERN,
        AGENT,
        ACTION,
        OPERATION
    }

    /**
     * Review decision types.
     */
    public enum ReviewDecision {
        APPROVED,
        DENIED,
        REQUESTED_CHANGES,
        DEFERRED
    }

    /**
     * Approval levels.
     */
    public enum ApprovalLevel {
        TIER_1,  // Self-approval or automated
        TIER_2,  // Peer review
        TIER_3   // Manager or governance board
    }

    /**
     * Review evidence record.
     */
    public record ReviewEvidence(
            String evidenceId,
            String tenantId,
            String entityId,
            EntityType entityType,
            String reviewerId,
            ReviewDecision decision,
            String rationale,
            Map<String, Object> metadata,
            Instant timestamp
    ) {
        public ReviewEvidence {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Approval evidence record.
     */
    public record ApprovalEvidence(
            String evidenceId,
            String tenantId,
            String entityId,
            EntityType entityType,
            String approverId,
            ApprovalLevel approvalLevel,
            Map<String, Object> conditions,
            Map<String, Object> metadata,
            Instant timestamp
    ) {
        public ApprovalEvidence {
            conditions = Map.copyOf(conditions != null ? conditions : Map.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Rollback evidence record.
     */
    public record RollbackEvidence(
            String evidenceId,
            String tenantId,
            String operationId,
            EntityType entityType,
            String rollbackReason,
            String compensationStrategy,
            java.util.Set<String> affectedResources,
            Map<String, Object> metadata,
            Instant timestamp
    ) {
        public RollbackEvidence {
            affectedResources = java.util.Set.copyOf(affectedResources != null ? affectedResources : java.util.Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Union type for compliance evidence.
     */
    public sealed interface ComplianceEvidence permits ReviewEvidence, ApprovalEvidence, RollbackEvidence {}

    /**
     * Evidence store interface for persistence.
     */
    public interface ComplianceEvidenceStore {
        Promise<Void> storeReviewEvidence(ReviewEvidence evidence);
        Promise<Void> storeApprovalEvidence(ApprovalEvidence evidence);
        Promise<Void> storeRollbackEvidence(RollbackEvidence evidence);
        Promise<java.util.List<ComplianceEvidence>> retrieveEvidence(String tenantId, String entityId, EntityType entityType);
    }
}
