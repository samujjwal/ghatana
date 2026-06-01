/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.orchestrator.evidence;

import com.ghatana.aep.pattern.lifecycle.PatternLifecycleEvent;
import com.ghatana.aep.registry.DataCloudPatternLifecycleRepository;
import com.ghatana.datacloud.entity.agent.AgentRun;
import com.ghatana.datacloud.entity.agent.ApprovalRequest;
import com.ghatana.datacloud.entity.agent.ApprovalRequest.ApprovalStatus;
import com.ghatana.datacloud.entity.agent.ApprovalRequest.ApprovalType;
import com.ghatana.datacloud.entity.agent.RunTrace;
import com.ghatana.datacloud.entity.policy.PolicyDecision;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WS2: Evidence service for persisting review/approval/rejection/rollback/learning evidence.
 *
 * <p>Coordinates the persistence of governance evidence across multiple entity types:
 * <ul>
 *   <li>Review evidence via ApprovalRequest</li>
 *   <li>Approval/rejection evidence via ApprovalRequest status</li>
 *   <li>Rollback evidence via RunTrace and AgentRun</li>
 *   <li>Learning evidence via PatternLifecycleEvent</li>
 *   <li>Policy decision evidence via PolicyDecision</li>
 * </ul>
 *
 * <p>This service provides a unified interface for evidence persistence while delegating
 * to the appropriate entity repositories for storage.
 *
 * @doc.type class
 * @doc.purpose Unified evidence persistence service for governance evidence
 * @doc.layer product
 * @doc.pattern Service
 */
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final DataCloudPatternLifecycleRepository patternLifecycleRepository;
    private final EvidenceStore evidenceStore;

    public EvidenceService(
            DataCloudPatternLifecycleRepository patternLifecycleRepository,
            EvidenceStore evidenceStore) {
        this.patternLifecycleRepository = patternLifecycleRepository;
        this.evidenceStore = evidenceStore;
    }

    /**
     * Persist approval evidence for a pattern or agent execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @param approverId approver identifier
     * @param approvalType type of approval required
     * @param approved whether approved or rejected
     * @param reason approval/rejection reason
     * @param metadata additional metadata
     * @return promise completing when evidence is persisted
     */
    public Promise<Void> persistApprovalEvidence(
            String tenantId,
            String executionId,
            String approverId,
            ApprovalType approvalType,
            boolean approved,
            String reason,
            Map<String, Object> metadata) {
        log.debug("Persisting approval evidence: tenantId={}, executionId={}, approved={}",
                tenantId, executionId, approved);

        ApprovalRequest approvalRequest = new ApprovalRequest(
                UUID.randomUUID().toString(),
                tenantId,
                executionId,
                approvalType,
                approved ? ApprovalStatus.APPROVED : ApprovalStatus.DENIED,
                approverId,
                reason,
                Instant.now(),
                Instant.now().plus(java.time.Duration.ofHours(24)),
                "HIGH",
                Map.of(),
                metadata
        );

        return evidenceStore.storeApprovalRequest(approvalRequest)
                .then(ignored -> {
                    log.info("Approval evidence persisted: approvalId={}, executionId={}, approved={}",
                            approvalRequest.approvalId(), executionId, approved);
                    return Promise.complete();
                });
    }

    /**
     * Persist rejection evidence for a pattern or agent execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @param rejectorId rejector identifier
     * @param approvalType type of approval that was rejected
     * @param reason rejection reason
     * @param metadata additional metadata
     * @return promise completing when evidence is persisted
     */
    public Promise<Void> persistRejectionEvidence(
            String tenantId,
            String executionId,
            String rejectorId,
            ApprovalType approvalType,
            String reason,
            Map<String, Object> metadata) {
        log.debug("Persisting rejection evidence: tenantId={}, executionId={}", tenantId, executionId);

        return persistApprovalEvidence(tenantId, executionId, rejectorId, approvalType, false, reason, metadata);
    }

    /**
     * Persist rollback evidence for an execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @param compensationStrategy compensation strategy used
     * @param rollbackReason reason for rollback
     * @param metadata additional metadata
     * @return promise completing when evidence is persisted
     */
    public Promise<Void> persistRollbackEvidence(
            String tenantId,
            String executionId,
            String compensationStrategy,
            String rollbackReason,
            Map<String, Object> metadata) {
        log.debug("Persisting rollback evidence: tenantId={}, executionId={}, strategy={}",
                tenantId, executionId, compensationStrategy);

        RunTrace rollbackTrace = new RunTrace(
                UUID.randomUUID().toString(),
                executionId,
                "ROLLBACK",
                RunTrace.TraceLevel.STANDARD,
                Instant.now(),
                Instant.now(),
                "ROLLBACK_INITIATED",
                Map.of(
                        "compensationStrategy", compensationStrategy,
                        "rollbackReason", rollbackReason,
                        "tenantId", tenantId
                ),
                metadata
        );

        return evidenceStore.storeRunTrace(rollbackTrace)
                .then(ignored -> {
                    log.info("Rollback evidence persisted: traceId={}, executionId={}, strategy={}",
                            rollbackTrace.traceId(), executionId, compensationStrategy);
                    return Promise.complete();
                });
    }

    /**
     * Persist learning evidence for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @param learningType type of learning event
     * @param learningData learning data/metrics
     * @param metadata additional metadata
     * @return promise completing when evidence is persisted
     */
    public Promise<Void> persistLearningEvidence(
            String tenantId,
            String patternId,
            String learningType,
            Map<String, Object> learningData,
            Map<String, Object> metadata) {
        log.debug("Persisting learning evidence: tenantId={}, patternId={}, type={}",
                tenantId, patternId, learningType);

        PatternLifecycleEvent learningEvent = new PatternLifecycleEvent(
                UUID.randomUUID().toString(),
                patternId,
                tenantId,
                "LEARNING_" + learningType.toUpperCase(),
                "Pattern learning event: " + learningType,
                Map.of("learningData", learningData),
                metadata,
                Instant.now()
        );

        return patternLifecycleRepository.saveEvent(learningEvent)
                .then(ignored -> {
                    log.info("Learning evidence persisted: eventId={}, patternId={}, type={}",
                            learningEvent.eventId(), patternId, learningType);
                    return Promise.complete();
                });
    }

    /**
     * Persist policy decision evidence.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @param policyId policy identifier
     * @param decision policy decision (ALLOW/DENY)
     * @param reason decision reason
     * @param metadata additional metadata
     * @return promise completing when evidence is persisted
     */
    public Promise<Void> persistPolicyDecisionEvidence(
            String tenantId,
            String executionId,
            String policyId,
            PolicyDecision.Decision decision,
            String reason,
            Map<String, Object> metadata) {
        log.debug("Persisting policy decision evidence: tenantId={}, executionId={}, policyId={}, decision={}",
                tenantId, executionId, policyId, decision);

        PolicyDecision policyDecision = PolicyDecision.builder()
                .tenantId(tenantId)
                .executionId(executionId)
                .policyId(policyId)
                .decision(decision)
                .reason(reason)
                .metadata(metadata)
                .evaluatedAt(Instant.now())
                .build();

        return evidenceStore.storePolicyDecision(policyDecision)
                .then(ignored -> {
                    log.info("Policy decision evidence persisted: decisionId={}, executionId={}, decision={}",
                            policyDecision.decisionId(), executionId, decision);
                    return Promise.complete();
                });
    }

    /**
     * Persist review evidence for a pattern or agent execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @param reviewerId reviewer identifier
     * @param reviewType type of review
     * @param reviewOutcome review outcome
     * @param comments review comments
     * @param metadata additional metadata
     * @return promise completing when evidence is persisted
     */
    public Promise<Void> persistReviewEvidence(
            String tenantId,
            String executionId,
            String reviewerId,
            String reviewType,
            String reviewOutcome,
            String comments,
            Map<String, Object> metadata) {
        log.debug("Persisting review evidence: tenantId={}, executionId={}, reviewerId={}",
                tenantId, executionId, reviewerId);

        RunTrace reviewTrace = new RunTrace(
                UUID.randomUUID().toString(),
                executionId,
                "REVIEW",
                RunTrace.TraceLevel.DETAILED,
                Instant.now(),
                Instant.now(),
                "REVIEW_COMPLETED",
                Map.of(
                        "reviewerId", reviewerId,
                        "reviewType", reviewType,
                        "reviewOutcome", reviewOutcome,
                        "comments", comments,
                        "tenantId", tenantId
                ),
                metadata
        );

        return evidenceStore.storeRunTrace(reviewTrace)
                .then(ignored -> {
                    log.info("Review evidence persisted: traceId={}, executionId={}, reviewerId={}",
                            reviewTrace.traceId(), executionId, reviewerId);
                    return Promise.complete();
                });
    }

    /**
     * Retrieve approval evidence for an execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @return approval evidence
     */
    public Promise<Optional<ApprovalRequest>> getApprovalEvidence(String tenantId, String executionId) {
        return evidenceStore.getApprovalRequest(tenantId, executionId);
    }

    /**
     * Retrieve rollback evidence for an execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @return rollback evidence
     */
    public Promise<Optional<RunTrace>> getRollbackEvidence(String tenantId, String executionId) {
        return evidenceStore.getRunTraceByType(tenantId, executionId, "ROLLBACK");
    }

    /**
     * Retrieve learning evidence for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return learning evidence
     */
    public Promise<java.util.List<PatternLifecycleEvent>> getLearningEvidence(String tenantId, String patternId) {
        return patternLifecycleRepository.getEventsByPattern(tenantId, patternId)
                .then(events -> events.stream()
                        .filter(e -> e.eventType().startsWith("LEARNING_"))
                        .toList());
    }

    /**
     * Retrieve policy decision evidence for an execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @return policy decision evidence
     */
    public Promise<java.util.List<PolicyDecision>> getPolicyDecisionEvidence(String tenantId, String executionId) {
        return evidenceStore.getPolicyDecisions(tenantId, executionId);
    }

    /**
     * Retrieve review evidence for an execution.
     *
     * @param tenantId tenant identifier
     * @param executionId execution identifier
     * @return review evidence
     */
    public Promise<java.util.List<RunTrace>> getReviewEvidence(String tenantId, String executionId) {
        return evidenceStore.getRunTraceByType(tenantId, executionId, "REVIEW")
                .then(opt -> opt.map(java.util.List::of).orElse(java.util.List.of()));
    }

    // ==================== Evidence Store Interface ====================

    /**
     * Evidence store interface for delegating to entity repositories.
     */
    public interface EvidenceStore {
        Promise<Void> storeApprovalRequest(ApprovalRequest approvalRequest);
        Promise<Void> storeRunTrace(RunTrace runTrace);
        Promise<Void> storePolicyDecision(PolicyDecision policyDecision);
        Promise<Optional<ApprovalRequest>> getApprovalRequest(String tenantId, String executionId);
        Promise<Optional<RunTrace>> getRunTraceByType(String tenantId, String executionId, String type);
        Promise<java.util.List<PolicyDecision>> getPolicyDecisions(String tenantId, String executionId);
    }
}
