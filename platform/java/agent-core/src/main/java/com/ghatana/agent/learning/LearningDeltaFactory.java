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
            @NotNull String tenantId,
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
                tenantId,
                null, // procedureId
                null, // semanticFactId
                null, // negativeKnowledgeId
                contentDigest,
                proposedContent,
                evidenceRefs,
                List.of(), // evaluationRefs
                List.of(), // sourceEpisodeIds
                null, // rollbackRef
                0.0, // confidenceBefore
                0.0, // confidenceAfter
                false, // requiresHumanReview
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
            @NotNull String tenantId,
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
                tenantId,
                procedureId,
                null, // semanticFactId
                null, // negativeKnowledgeId
                contentDigest,
                proposedContent,
                evidenceRefs,
                List.of(), // evaluationRefs
                List.of(), // sourceEpisodeIds
                null, // rollbackRef
                0.0, // confidenceBefore
                0.0, // confidenceAfter
                false, // requiresHumanReview
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
            @NotNull String tenantId,
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
                tenantId,
                null, // procedureId
                semanticFactId,
                null, // negativeKnowledgeId
                contentDigest,
                proposedContent,
                evidenceRefs,
                List.of(), // evaluationRefs
                List.of(), // sourceEpisodeIds
                null, // rollbackRef
                0.0, // confidenceBefore
                0.0, // confidenceAfter
                false, // requiresHumanReview
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
            @NotNull String tenantId,
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
                tenantId,
                null, // procedureId
                null, // semanticFactId
                negativeKnowledgeId,
                contentDigest,
                proposedContent,
                evidenceRefs,
                List.of(), // evaluationRefs
                List.of(), // sourceEpisodeIds
                null, // rollbackRef
                0.0, // confidenceBefore
                0.0, // confidenceAfter
                false, // requiresHumanReview
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
     * Computes a SHA-256 digest of the proposed content for integrity verification.
     *
     * <p>The digest is computed from a canonical string representation of the content,
     * ensuring deterministic hashing regardless of insertion order.
     *
     * @param content content to digest
     * @return SHA-256 hex digest
     */
    @NotNull
    private static String computeDigest(@NotNull Map<String, Object> content) {
        // Create a canonical string representation for deterministic hashing
        String canonicalContent = content.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        
        return sha256Hex(canonicalContent);
    }

    /**
     * Computes SHA-256 hash of a string and returns hex representation.
     *
     * @param input string to hash
     * @return hexadecimal hash string
     */
    @NotNull
    private static String sha256Hex(@NotNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
