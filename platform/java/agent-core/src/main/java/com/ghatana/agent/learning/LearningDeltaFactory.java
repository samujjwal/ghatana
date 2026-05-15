/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for creating learning deltas with enforced required-field validation per target type.
 *
 * @doc.type class
 * @doc.purpose Factory for creating learning deltas with target-specific enforcement
 * @doc.layer agent-core
 * @doc.pattern Factory
 */
public final class LearningDeltaFactory {

    private LearningDeltaFactory() {}

    // -------------------------------------------------------------------------
    // Generic factory (no target-specific enforcement)
    // -------------------------------------------------------------------------

    /**
     * Creates a proposed learning delta.
     *
     * @param type           type of learning delta
     * @param target         learning target
     * @param agentId        agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId        skill identifier
     * @param tenantId       tenant identifier
     * @param proposedContent proposed content
     * @param evidenceRefs   evidence references
     * @param proposedBy     who proposed the delta
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta propose(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);

        return new LearningDelta(
                deltaId, type, target, LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                null, null, null,
                contentDigest, proposedContent, evidenceRefs,
                List.of(), List.of(), null,
                0.0, 0.0, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    // -------------------------------------------------------------------------
    // Target-specific explicit factories (Phase 3.2)
    // -------------------------------------------------------------------------

    /**
     * Proposes a procedural-skill delta.
     *
     * <p>Enforces:
     * <ul>
     *   <li>{@code procedureId} – required</li>
     *   <li>{@code rollbackRef} – required (safety gate)</li>
     *   <li>{@code evidenceRefs} – must be non-empty</li>
     *   <li>{@code confidenceBefore}/{@code confidenceAfter} – set from caller</li>
     *   <li>Content digest is computed deterministically</li>
     * </ul>
     *
     * @param agentId          agent identifier
     * @param agentReleaseId   agent release identifier
     * @param skillId          skill identifier
     * @param tenantId         tenant identifier
     * @param procedureId      persisted procedure artifact identifier
     * @param rollbackRef      rollback artifact reference
     * @param proposedContent  proposed content
     * @param evidenceRefs     source evidence references (must be non-empty)
     * @param sourceEpisodeIds source episode identifiers
     * @param confidenceBefore confidence before this change
     * @param confidenceAfter  confidence after this change
     * @param proposedBy       proposing principal
     * @return learning delta in PROPOSED state
     * @throws IllegalArgumentException if procedureId, rollbackRef, or evidenceRefs are missing
     */
    @NotNull
    public static LearningDelta proposeProceduralSkill(
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull String procedureId,
            @NotNull String rollbackRef,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull List<String> sourceEpisodeIds,
            double confidenceBefore,
            double confidenceAfter,
            @NotNull String proposedBy
    ) {
        if (procedureId.isBlank()) {
            throw new IllegalArgumentException("procedureId must not be blank for PROCEDURAL_SKILL delta");
        }
        if (rollbackRef.isBlank()) {
            throw new IllegalArgumentException("rollbackRef must not be blank for PROCEDURAL_SKILL delta");
        }
        if (evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs must not be empty for PROCEDURAL_SKILL delta");
        }
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, LearningDeltaType.PROCEDURAL_SKILL, LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                procedureId, null, null,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.of(), List.copyOf(sourceEpisodeIds), rollbackRef,
                confidenceBefore, confidenceAfter, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    /**
     * Proposes a semantic-fact delta.
     *
     * <p>Enforces {@code semanticFactId} is present.
     *
     * @param agentId        agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId        skill identifier
     * @param tenantId       tenant identifier
     * @param semanticFactId persisted semantic-fact identifier
     * @param proposedContent proposed content
     * @param evidenceRefs   evidence references (must be non-empty)
     * @param proposedBy     proposing principal
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta proposeSemanticFact(
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull String semanticFactId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        if (semanticFactId.isBlank()) {
            throw new IllegalArgumentException("semanticFactId must not be blank for SEMANTIC_FACT delta");
        }
        if (evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs must not be empty for SEMANTIC_FACT delta");
        }
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, LearningDeltaType.SEMANTIC_FACT, LearningTarget.SEMANTIC_FACT,
                LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                null, semanticFactId, null,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.of(), List.of(), null,
                0.0, 0.0, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    /**
     * Proposes a negative-knowledge delta.
     *
     * <p>Enforces {@code negativeKnowledgeId} is present.
     *
     * @param agentId             agent identifier
     * @param agentReleaseId      agent release identifier
     * @param skillId             skill identifier
     * @param tenantId            tenant identifier
     * @param negativeKnowledgeId persisted negative-knowledge identifier
     * @param proposedContent     proposed content
     * @param evidenceRefs        evidence references (must be non-empty)
     * @param proposedBy          proposing principal
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta proposeNegativeKnowledge(
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull String negativeKnowledgeId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        if (negativeKnowledgeId.isBlank()) {
            throw new IllegalArgumentException("negativeKnowledgeId must not be blank for NEGATIVE_KNOWLEDGE delta");
        }
        if (evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs must not be empty for NEGATIVE_KNOWLEDGE delta");
        }
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, LearningDeltaType.NEGATIVE_KNOWLEDGE, LearningTarget.NEGATIVE_KNOWLEDGE,
                LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                null, null, negativeKnowledgeId,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.of(), List.of(), null,
                0.0, 0.0, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    /**
     * Proposes a retrieval-policy delta.
     *
     * @param agentId        agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId        skill identifier
     * @param tenantId       tenant identifier
     * @param proposedContent proposed policy content
     * @param evidenceRefs   evidence references (must be non-empty)
     * @param evaluationRefs evaluation references (must be non-empty for policy changes)
     * @param proposedBy     proposing principal
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta proposeRetrievalPolicy(
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull List<String> evaluationRefs,
            @NotNull String proposedBy
    ) {
        if (evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs must not be empty for RETRIEVAL_POLICY delta");
        }
        if (evaluationRefs.isEmpty()) {
            throw new IllegalArgumentException("evaluationRefs must not be empty for RETRIEVAL_POLICY delta");
        }
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, LearningDeltaType.RETRIEVAL_POLICY, LearningTarget.RETRIEVAL_POLICY,
                LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                null, null, null,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.copyOf(evaluationRefs), List.of(), null,
                0.0, 0.0, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    /**
     * Creates a delta that is immediately placed in PENDING_HUMAN_REVIEW state.
     *
     * <p>Used when confidence is below auto-approval threshold but the delta has
     * enough evidence to warrant human consideration rather than outright rejection.
     *
     * @param type           learning delta type
     * @param target         learning target
     * @param agentId        agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId        skill identifier
     * @param tenantId       tenant identifier
     * @param proposedContent proposed content
     * @param evidenceRefs   evidence references
     * @param confidenceAfter current confidence estimate
     * @param proposedBy     proposing principal
     * @return learning delta in PENDING_HUMAN_REVIEW state
     */
    @NotNull
    public static LearningDelta pendingHumanReview(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            double confidenceAfter,
            @NotNull String proposedBy
    ) {
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, type, target, LearningDeltaState.PENDING_HUMAN_REVIEW,
                agentId, agentReleaseId, skillId, tenantId,
                null, null, null,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.of(), List.of(), null,
                0.0, confidenceAfter, true, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    // -------------------------------------------------------------------------
    // Legacy convenience factories preserved for backward compatibility
    // -------------------------------------------------------------------------

    /**
     * Creates a proposed learning delta with a procedure ID (legacy compatibility).
     * Prefer {@link #proposeProceduralSkill} which enforces all required fields.
     */
    @NotNull
    public static LearningDelta proposeWithProcedure(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull String procedureId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, type, target, LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                procedureId, null, null,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.of(), List.of(), null,
                0.0, 0.0, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    /**
     * Creates a proposed learning delta with a semantic fact ID (legacy compatibility).
     * Prefer {@link #proposeSemanticFact} which enforces all required fields.
     */
    @NotNull
    public static LearningDelta proposeWithSemanticFact(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull String semanticFactId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, type, target, LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                null, semanticFactId, null,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.of(), List.of(), null,
                0.0, 0.0, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    /**
     * Creates a proposed learning delta with a negative knowledge ID (legacy compatibility).
     * Prefer {@link #proposeNegativeKnowledge} which enforces all required fields.
     */
    @NotNull
    public static LearningDelta proposeWithNegativeKnowledge(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull String negativeKnowledgeId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);
        return new LearningDelta(
                deltaId, type, target, LearningDeltaState.PROPOSED,
                agentId, agentReleaseId, skillId, tenantId,
                null, null, negativeKnowledgeId,
                contentDigest, proposedContent, List.copyOf(evidenceRefs),
                List.of(), List.of(), null,
                0.0, 0.0, false, proposedBy,
                Instant.now(), null, null, null,
                Map.of(), null, null,
                // Phase 6 FIX: New environment/version fields (null for new deltas)
                null, null, null, null
        );
    }

    // -------------------------------------------------------------------------
    // Digest
    // -------------------------------------------------------------------------

    /**
     * Computes a stable SHA-256 digest of the proposed content.
     *
     * @param content content to digest
     * @return hex-encoded SHA-256 digest
     */
    @NotNull
    public static String computeDigest(@NotNull Map<String, Object> content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Sort by key so the digest is stable regardless of insertion order
            String normalized = new java.util.TreeMap<>(content).toString();
            byte[] hash = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec; this branch is unreachable in practice
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

