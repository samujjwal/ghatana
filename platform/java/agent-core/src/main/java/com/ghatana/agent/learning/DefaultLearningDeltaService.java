/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link LearningDeltaService}.
 *
 * <p>Implements the full learning delta pipeline:
 * <ol>
 *   <li><b>Contract guard</b>: {@link LearningContract#permits(LearningTarget)} must return
 *       {@code true}; provenance and promotion rules are enforced.</li>
 *   <li><b>Persist as PENDING_EVALUATION</b>: Delta is saved before evaluation begins so it is
 *       durably recorded even if evaluation fails transiently.</li>
 *   <li><b>Evaluate</b>: {@link LearningDeltaEvaluator#evaluate(LearningDelta)} is called and the
 *       result drives the next state transition.</li>
 *   <li><b>State transition</b>:
 *     <ul>
 *       <li>{@link LearningDeltaEvaluator.ReasonCode#APPROVED} → {@link LearningDeltaState#EVALUATED}</li>
 *       <li>{@link LearningDeltaEvaluator.ReasonCode#PENDING_HUMAN_REVIEW} → {@link LearningDeltaState#PENDING_HUMAN_REVIEW}</li>
 *       <li>Any rejection reason → {@link LearningDeltaState#REJECTED}</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>This service does <em>not</em> directly mutate mastery state or memory. Promotion to active
 * knowledge is the responsibility of {@link com.ghatana.agent.promotion.PromotionEngine}.
 *
 * @doc.type class
 * @doc.purpose Default learning delta lifecycle pipeline: propose → evaluate → approve/reject
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class DefaultLearningDeltaService implements LearningDeltaService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLearningDeltaService.class);

    private final LearningDeltaRepository deltaRepository;
    private final LearningDeltaEvaluator evaluator;

    /**
     * Creates a default learning delta service.
     *
     * @param deltaRepository repository for delta persistence
     * @param evaluator       evaluator for delta validation
     */
    public DefaultLearningDeltaService(
            @NotNull LearningDeltaRepository deltaRepository,
            @NotNull LearningDeltaEvaluator evaluator) {
        this.deltaRepository = Objects.requireNonNull(deltaRepository, "deltaRepository must not be null");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    }

    @Override
    @NotNull
    public Promise<LearningDelta> propose(@NotNull LearningDelta delta, @NotNull LearningContract contract) {
        Objects.requireNonNull(delta, "delta must not be null");
        Objects.requireNonNull(contract, "contract must not be null");

        // ── Contract guard ────────────────────────────────────────────────────
        if (!contract.permits(delta.target())) {
            String reason = "LearningContract does not permit target " + delta.target()
                    + " for agent " + delta.agentId() + " (contract level=" + contract.level() + ")";
            log.warn("Delta proposal rejected by contract: {}", reason);
            return Promise.ofException(new IllegalArgumentException(reason));
        }

        if (contract.provenanceRequired() && delta.evidenceRefs().isEmpty()) {
            String reason = "Contract requires provenance but delta " + delta.deltaId()
                    + " has no evidenceRefs";
            log.warn("Delta proposal rejected — missing provenance: {}", reason);
            return Promise.ofException(new IllegalArgumentException(reason));
        }

        if (contract.promotionRequired() && !contract.governanceWorkflow()) {
            // promotionRequired=true means the delta must go through the full evaluation pipeline.
            // This is enforced by the service itself (no auto-skip), so no extra rejection needed here.
            log.debug("promotionRequired=true on contract for delta {}; evaluation pipeline will run", delta.deltaId());
        }

        // ── Transition to PENDING_EVALUATION and persist ─────────────────────
        LearningDelta pendingDelta = withState(delta, LearningDeltaState.PENDING_EVALUATION, null);

        log.info("Persisting delta {} as PENDING_EVALUATION (target={}, tenant={})",
                delta.deltaId(), delta.target(), delta.tenantId());

        return deltaRepository.save(pendingDelta)
                .then(saved -> {
                    log.debug("Delta {} persisted; starting evaluation", saved.deltaId());
                    return runEvaluationPipeline(saved);
                });
    }

    @Override
    @NotNull
    public Promise<LearningDeltaEvaluator.EvaluationResult> evaluate(@NotNull String deltaId) {
        Objects.requireNonNull(deltaId, "deltaId must not be null");

        return deltaRepository.findById(deltaId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Learning delta not found: " + deltaId));
                    }
                    LearningDelta delta = opt.get();
                    return evaluator.evaluate(delta);
                });
    }

    @Override
    @NotNull
    public Promise<LearningDelta> approve(@NotNull String deltaId, @NotNull String approvedBy) {
        Objects.requireNonNull(deltaId, "deltaId must not be null");
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");

        return deltaRepository.findById(deltaId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Learning delta not found: " + deltaId));
                    }
                    LearningDelta delta = opt.get();
                    if (delta.state() != LearningDeltaState.PENDING_HUMAN_REVIEW
                            && delta.state() != LearningDeltaState.EVALUATED) {
                        return Promise.ofException(new IllegalStateException(
                                "Delta " + deltaId + " cannot be approved from state " + delta.state()
                                        + "; expected PENDING_HUMAN_REVIEW or EVALUATED"));
                    }
                    log.info("Approving delta {} (approved by {})", deltaId, approvedBy);

                    LearningDelta approved = new LearningDelta(
                            delta.deltaId(), delta.type(), delta.target(),
                            LearningDeltaState.APPROVED,
                            delta.agentId(), delta.agentReleaseId(), delta.skillId(), delta.tenantId(),
                            delta.procedureId(), delta.semanticFactId(), delta.negativeKnowledgeId(),
                            delta.contentDigest(), delta.proposedContent(), delta.evidenceRefs(),
                            delta.evaluationRefs(), delta.sourceEpisodeIds(), delta.rollbackRef(),
                            delta.confidenceBefore(), delta.confidenceAfter(),
                            false, // requiresHumanReview cleared on approval
                            approvedBy, // proposedBy tracks the approver identity for audit
                            delta.proposedAt(), delta.evaluatedAt(), Instant.now(), null,
                            delta.labels(), null, delta.approvalProofRef(),
                            // Phase 6 FIX: New environment/version fields (null for existing deltas)
                            delta.versionContextDigest(), delta.environmentFingerprintRef(),
                            delta.repositoryConventionRef(), delta.runtimeFingerprintRef()
                    );
                    return deltaRepository.save(approved);
                });
    }

    @Override
    @NotNull
    public Promise<LearningDelta> reject(
            @NotNull String deltaId,
            @NotNull String reason,
            @NotNull String rejectedBy) {
        Objects.requireNonNull(deltaId, "deltaId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(rejectedBy, "rejectedBy must not be null");

        return deltaRepository.findById(deltaId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Learning delta not found: " + deltaId));
                    }
                    LearningDelta delta = opt.get();
                    if (delta.state() == LearningDeltaState.PROMOTED
                            || delta.state() == LearningDeltaState.REJECTED) {
                        return Promise.ofException(new IllegalStateException(
                                "Delta " + deltaId + " is already in terminal state " + delta.state()));
                    }
                    log.info("Rejecting delta {} — reason: {}", deltaId, reason);
                    return deltaRepository.updateStateWithRejection(deltaId, LearningDeltaState.REJECTED, reason);
                });
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> listPending(
            @NotNull String tenantId,
            @Nullable Map<String, String> filters) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        // Determine agentId filter if present
        String agentId = filters != null ? filters.get("agentId") : null;
        Integer limit = parseIntFilter(filters, "limit");
        Integer offset = parseIntFilter(filters, "offset");

        return deltaRepository.findByTenant(tenantId, agentId, limit, offset)
                .map(all -> all.stream()
                        .filter(d -> d.state() == LearningDeltaState.PENDING_HUMAN_REVIEW)
                        .filter(d -> {
                            if (filters == null) return true;
                            String skillId = filters.get("skillId");
                            if (skillId != null && !skillId.equals(d.skillId())) return false;
                            String target = filters.get("target");
                            if (target != null && !target.equals(d.target().name())) return false;
                            return true;
                        })
                        .toList());
    }

    // ── Private pipeline helpers ──────────────────────────────────────────────

    /**
     * Runs the evaluator and transitions the delta to the appropriate next state.
     */
    @NotNull
    private Promise<LearningDelta> runEvaluationPipeline(@NotNull LearningDelta delta) {
        return evaluator.evaluate(delta)
                .then(result -> {
                    log.debug("Evaluation result for delta {}: approved={}, reason={}", 
                            delta.deltaId(), result.approved(), result.reasonCode());

                    LearningDeltaState nextState = resolveNextState(result);

                    if (nextState == LearningDeltaState.REJECTED) {
                        log.info("Delta {} rejected after evaluation: {}", delta.deltaId(), result.reason());
                        return deltaRepository.updateStateWithRejection(
                                delta.deltaId(), LearningDeltaState.REJECTED, result.reason());
                    }

                    // Transition to EVALUATED or PENDING_HUMAN_REVIEW
                    LearningDelta evaluated = new LearningDelta(
                            delta.deltaId(), delta.type(), delta.target(),
                            nextState,
                            delta.agentId(), delta.agentReleaseId(), delta.skillId(), delta.tenantId(),
                            delta.procedureId(), delta.semanticFactId(), delta.negativeKnowledgeId(),
                            delta.contentDigest(), delta.proposedContent(), delta.evidenceRefs(),
                            delta.evaluationRefs(), delta.sourceEpisodeIds(), delta.rollbackRef(),
                            delta.confidenceBefore(), result.confidence(),
                            nextState == LearningDeltaState.PENDING_HUMAN_REVIEW,
                            delta.proposedBy(),
                            delta.proposedAt(), Instant.now(), null, null,
                            delta.labels(), null, delta.approvalProofRef(),
                            // Phase 6 FIX: New environment/version fields (null for existing deltas)
                            delta.versionContextDigest(), delta.environmentFingerprintRef(),
                            delta.repositoryConventionRef(), delta.runtimeFingerprintRef()
                    );

                    log.info("Delta {} transitioned to {} after evaluation", delta.deltaId(), nextState);
                    return deltaRepository.save(evaluated);
                });
    }

    /**
     * Maps an evaluation result to the next {@link LearningDeltaState}.
     */
    @NotNull
    private static LearningDeltaState resolveNextState(@NotNull LearningDeltaEvaluator.EvaluationResult result) {
        return switch (result.reasonCode()) {
            case APPROVED -> LearningDeltaState.EVALUATED;
            case PENDING_HUMAN_REVIEW, HUMAN_REVIEW_REQUIRED -> LearningDeltaState.PENDING_HUMAN_REVIEW;
            default -> LearningDeltaState.REJECTED;
        };
    }

    /**
     * Returns a copy of the delta with the given state (and optional rejection reason).
     */
    @NotNull
    private static LearningDelta withState(
            @NotNull LearningDelta delta,
            @NotNull LearningDeltaState state,
            @Nullable String rejectionReason) {
        return new LearningDelta(
                delta.deltaId(), delta.type(), delta.target(), state,
                delta.agentId(), delta.agentReleaseId(), delta.skillId(), delta.tenantId(),
                delta.procedureId(), delta.semanticFactId(), delta.negativeKnowledgeId(),
                delta.contentDigest(), delta.proposedContent(), delta.evidenceRefs(),
                delta.evaluationRefs(), delta.sourceEpisodeIds(), delta.rollbackRef(),
                delta.confidenceBefore(), delta.confidenceAfter(),
                delta.requiresHumanReview(), delta.proposedBy(),
                delta.proposedAt(), delta.evaluatedAt(), delta.promotedAt(), delta.rejectedAt(),
                delta.labels(), rejectionReason, delta.approvalProofRef(),
                // Phase 6 FIX: New environment/version fields (null for existing deltas)
                delta.versionContextDigest(), delta.environmentFingerprintRef(),
                delta.repositoryConventionRef(), delta.runtimeFingerprintRef()
        );
    }

    @Nullable
    private static Integer parseIntFilter(@Nullable Map<String, String> filters, @NotNull String key) {
        if (filters == null) return null;
        String val = filters.get(key);
        if (val == null) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
