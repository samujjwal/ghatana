/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Promotion state machine for learned artifacts.
 */
public final class LearnedArtifactPromotionService {

    private final LearningCandidateRepository candidates;
    private final PromotionEvidenceRepository evidence;
    private final LearnedArtifactRepository artifacts;

    public LearnedArtifactPromotionService(
            @NotNull LearningCandidateRepository candidates,
            @NotNull PromotionEvidenceRepository evidence,
            @NotNull LearnedArtifactRepository artifacts) {
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    }

    public @NotNull Promise<LearningCandidate> submit(@NotNull LearningCandidate candidate) {
        if (candidate.state() != LearningCandidateState.PROPOSED) {
            return Promise.ofException(new IllegalStateException("candidate must start in PROPOSED state"));
        }
        return candidates.save(candidate);
    }

    public @NotNull Promise<LearnedArtifact> promote(
            @NotNull String candidateId,
            @NotNull PromotionEvidence promotionEvidence) {
        return candidates.findById(candidateId)
                .then(found -> {
                    if (found.isEmpty()) {
                        return Promise.ofException(new IllegalStateException("LearningCandidate not found: " + candidateId));
                    }
                    if (!candidateId.equals(promotionEvidence.candidateId())) {
                        return Promise.ofException(new IllegalArgumentException("promotion evidence candidate mismatch"));
                    }
                    LearningCandidate candidate = found.get();
                    LearningCandidate promotedCandidate = new LearningCandidate(
                            candidate.candidateId(),
                            candidate.agentId(),
                            candidate.agentReleaseId(),
                            candidate.traceId(),
                            candidate.target(),
                            LearningCandidateState.PROMOTED,
                            candidate.provenanceRefs(),
                            candidate.proposedArtifact(),
                            candidate.createdAt());
                    LearnedArtifact artifact = new LearnedArtifact(
                            "la-" + UUID.randomUUID(),
                            candidate.agentId(),
                            candidate.agentReleaseId(),
                            candidate.target(),
                            PromotionState.ACTIVE,
                            candidate.proposedArtifact(),
                            candidate.provenanceRefs(),
                            promotionEvidence.evidenceId(),
                            null,
                            Instant.now());
                    return evidence.save(promotionEvidence)
                            .then(saved -> candidates.save(promotedCandidate))
                            .then(saved -> artifacts.save(artifact));
                });
    }

    public @NotNull Promise<LearnedArtifact> rollback(
            @NotNull String artifactId,
            @NotNull String rollbackRef) {
        if (rollbackRef.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("rollbackRef is required"));
        }
        return artifacts.findById(artifactId)
                .then(found -> {
                    if (found.isEmpty()) {
                        return Promise.ofException(new IllegalStateException("LearnedArtifact not found: " + artifactId));
                    }
                    LearnedArtifact existing = found.get();
                    LearnedArtifact rolledBack = new LearnedArtifact(
                            existing.artifactId(),
                            existing.agentId(),
                            existing.agentReleaseId(),
                            existing.target(),
                            PromotionState.ROLLED_BACK,
                            existing.payload(),
                            existing.provenanceRefs(),
                            existing.promotionEvidenceId(),
                            rollbackRef,
                            existing.createdAt());
                    return artifacts.save(rolledBack);
                });
    }
}
