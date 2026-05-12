/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating learning deltas.
 *
 * @doc.type class
 * @doc.purpose Factory for creating learning deltas
 * @doc.layer agent-core
 * @doc.pattern Factory
 */
public class LearningDeltaFactory {

    /**
     * Creates a proposed learning delta.
     *
     * @param type type of learning delta
     * @param target learning target
     * @param agentId agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId skill identifier
     * @param proposedContent proposed content
     * @param evidenceRefs evidence references
     * @param proposedBy who proposed the delta
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta propose(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = java.util.UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);

        return new LearningDelta(
                deltaId,
                type,
                target,
                LearningDeltaState.PROPOSED,
                agentId,
                agentReleaseId,
                skillId,
                null, // procedureId
                null, // semanticFactId
                null, // negativeKnowledgeId
                contentDigest,
                proposedContent,
                evidenceRefs,
                proposedBy,
                Instant.now(),
                null, // evaluatedAt
                null, // promotedAt
                null, // rejectedAt
                Map.of(),
                null // rejectionReason
        );
    }

    /**
     * Creates a proposed learning delta with a procedure ID.
     *
     * @param type type of learning delta
     * @param target learning target
     * @param agentId agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId skill identifier
     * @param procedureId procedure identifier
     * @param proposedContent proposed content
     * @param evidenceRefs evidence references
     * @param proposedBy who proposed the delta
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta proposeWithProcedure(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String procedureId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = java.util.UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);

        return new LearningDelta(
                deltaId,
                type,
                target,
                LearningDeltaState.PROPOSED,
                agentId,
                agentReleaseId,
                skillId,
                procedureId,
                null, // semanticFactId
                null, // negativeKnowledgeId
                contentDigest,
                proposedContent,
                evidenceRefs,
                proposedBy,
                Instant.now(),
                null, // evaluatedAt
                null, // promotedAt
                null, // rejectedAt
                Map.of(),
                null // rejectionReason
        );
    }

    /**
     * Creates a proposed learning delta with a semantic fact ID.
     *
     * @param type type of learning delta
     * @param target learning target
     * @param agentId agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId skill identifier
     * @param semanticFactId semantic fact identifier
     * @param proposedContent proposed content
     * @param evidenceRefs evidence references
     * @param proposedBy who proposed the delta
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta proposeWithSemanticFact(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String semanticFactId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = java.util.UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);

        return new LearningDelta(
                deltaId,
                type,
                target,
                LearningDeltaState.PROPOSED,
                agentId,
                agentReleaseId,
                skillId,
                null, // procedureId
                semanticFactId,
                null, // negativeKnowledgeId
                contentDigest,
                proposedContent,
                evidenceRefs,
                proposedBy,
                Instant.now(),
                null, // evaluatedAt
                null, // promotedAt
                null, // rejectedAt
                Map.of(),
                null // rejectionReason
        );
    }

    /**
     * Creates a proposed learning delta with a negative knowledge ID.
     *
     * @param type type of learning delta
     * @param target learning target
     * @param agentId agent identifier
     * @param agentReleaseId agent release identifier
     * @param skillId skill identifier
     * @param negativeKnowledgeId negative knowledge identifier
     * @param proposedContent proposed content
     * @param evidenceRefs evidence references
     * @param proposedBy who proposed the delta
     * @return learning delta in PROPOSED state
     */
    @NotNull
    public static LearningDelta proposeWithNegativeKnowledge(
            @NotNull LearningDeltaType type,
            @NotNull LearningTarget target,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String skillId,
            @NotNull String negativeKnowledgeId,
            @NotNull Map<String, Object> proposedContent,
            @NotNull List<String> evidenceRefs,
            @NotNull String proposedBy
    ) {
        String deltaId = java.util.UUID.randomUUID().toString();
        String contentDigest = computeDigest(proposedContent);

        return new LearningDelta(
                deltaId,
                type,
                target,
                LearningDeltaState.PROPOSED,
                agentId,
                agentReleaseId,
                skillId,
                null, // procedureId
                null, // semanticFactId
                negativeKnowledgeId,
                contentDigest,
                proposedContent,
                evidenceRefs,
                proposedBy,
                Instant.now(),
                null, // evaluatedAt
                null, // promotedAt
                null, // rejectedAt
                Map.of(),
                null // rejectionReason
        );
    }

    /**
     * Computes a digest of the proposed content.
     * This is a simplified implementation - production would use proper hashing.
     *
     * @param content content to digest
     * @return content digest
     */
    @NotNull
    private static String computeDigest(@NotNull Map<String, Object> content) {
        return String.valueOf(java.util.Objects.hash(content.toString()));
    }
}
