/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Idempotency and tenant-isolation tests for learned artifact promotion.
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("LearnedArtifact idempotency and tenant isolation")
class LearnedArtifactIdempotencyTest extends EventloopTestBase {

    private InMemoryLearningCandidateRepository candidates;
    private InMemoryPromotionEvidenceRepository evidence;
    private InMemoryLearnedArtifactRepository artifacts;
    private LearnedArtifactPromotionService service;

    @BeforeEach
    void setUp() {
        candidates = new InMemoryLearningCandidateRepository();
        evidence = new InMemoryPromotionEvidenceRepository();
        artifacts = new InMemoryLearnedArtifactRepository();
        service = new LearnedArtifactPromotionService(candidates, evidence, artifacts);
    }

    @Test
    @DisplayName("duplicate promote is idempotent — same artifact returned on second call")
    void duplicateProduceReturnsExistingArtifact() {
        LearningCandidate candidate = candidate("candidate-1", "agent-1");
        PromotionEvidence promotionEvidence = evidence("evidence-1", "candidate-1");

        runPromise(() -> service.submit(candidate));
        LearnedArtifact first = runPromise(() -> service.promote("candidate-1", promotionEvidence));

        // Second promote call with same candidateId — must return the same artifact (idempotent)
        LearnedArtifact second = runPromise(() -> service.promote("candidate-1", promotionEvidence));

        assertThat(first.artifactId()).isEqualTo(second.artifactId());
        assertThat(second.state()).isEqualTo(PromotionState.ACTIVE);

        // Only one artifact should exist for the candidate
        List<LearnedArtifact> all = runPromise(() -> artifacts.findByCandidateId("candidate-1"));
        assertThat(all).hasSize(1);
    }

    @Test
    @DisplayName("rollback cannot be applied twice — throws IllegalStateException on second rollback")
    void doubleRollbackIsRejected() {
        LearningCandidate candidate = candidate("candidate-2", "agent-2");
        PromotionEvidence promotionEvidence = evidence("evidence-2", "candidate-2");

        runPromise(() -> service.submit(candidate));
        LearnedArtifact active = runPromise(() -> service.promote("candidate-2", promotionEvidence));
        assertThat(active.state()).isEqualTo(PromotionState.ACTIVE);

        // First rollback succeeds
        LearnedArtifact rolledBack = runPromise(() -> service.rollback(active.artifactId(), "rollback-ref-1"));
        assertThat(rolledBack.state()).isEqualTo(PromotionState.ROLLED_BACK);

        // Second rollback must throw
        assertThatThrownBy(() -> runPromise(() -> service.rollback(active.artifactId(), "rollback-ref-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already ROLLED_BACK");
    }

    @Test
    @DisplayName("findByTenantAndCandidateId enforces tenant isolation — tenant A cannot read tenant B artifacts")
    void tenantIsolationOnCandidateIdQuery() {
        LearnedArtifact tenantArtifact = new LearnedArtifact(
                "artifact-tenant-a",
                "agent-1",
                "release-1",
                LearningTarget.PROCEDURAL_SKILL,
                PromotionState.ACTIVE,
                Map.of("key", "value"),
                List.of("episode-1"),
                "evidence-a",
                null,
                java.time.Instant.now(),
                "candidate-abc",
                null,
                "tenant-a",
                List.of(),
                null);

        runPromise(() -> artifacts.save(tenantArtifact));

        // Tenant A can find the artifact
        List<LearnedArtifact> tenantAResults = runPromise(() ->
                artifacts.findByTenantAndCandidateId("tenant-a", "candidate-abc"));
        assertThat(tenantAResults).hasSize(1);
        assertThat(tenantAResults.get(0).artifactId()).isEqualTo("artifact-tenant-a");

        // Tenant B gets no results for the same candidateId
        List<LearnedArtifact> tenantBResults = runPromise(() ->
                artifacts.findByTenantAndCandidateId("tenant-b", "candidate-abc"));
        assertThat(tenantBResults).isEmpty();
    }

    @Test
    @DisplayName("findByTenantContentDigestAndTarget returns matching artifacts only")
    void contentDigestQueryScopedToTenantAndTarget() {
        LearnedArtifact artifact = new LearnedArtifact(
                "artifact-digest-1",
                "agent-1",
                "release-1",
                LearningTarget.RETRIEVAL_POLICY,
                PromotionState.ACTIVE,
                Map.of("policy", "dense"),
                List.of("episode-1"),
                "evidence-1",
                null,
                java.time.Instant.now(),
                "candidate-1",
                null,
                "tenant-x",
                List.of(),
                "sha256-abc123");

        runPromise(() -> artifacts.save(artifact));

        // Correct tenant + digest + target finds artifact
        List<LearnedArtifact> found = runPromise(() ->
                artifacts.findByTenantContentDigestAndTarget("tenant-x", "sha256-abc123", LearningTarget.RETRIEVAL_POLICY));
        assertThat(found).hasSize(1);

        // Wrong target — empty result
        List<LearnedArtifact> wrongTarget = runPromise(() ->
                artifacts.findByTenantContentDigestAndTarget("tenant-x", "sha256-abc123", LearningTarget.PROCEDURAL_SKILL));
        assertThat(wrongTarget).isEmpty();

        // Wrong tenant — empty result
        List<LearnedArtifact> wrongTenant = runPromise(() ->
                artifacts.findByTenantContentDigestAndTarget("tenant-y", "sha256-abc123", LearningTarget.RETRIEVAL_POLICY));
        assertThat(wrongTenant).isEmpty();
    }

    @Test
    @DisplayName("new fields propagate through promote and rollback")
    void newFieldsPropagatedThroughPromotionCycle() {
        LearningCandidate candidate = candidate("candidate-3", "agent-3");
        PromotionEvidence promotionEvidence = evidence("evidence-3", "candidate-3");

        runPromise(() -> service.submit(candidate));
        LearnedArtifact active = runPromise(() -> service.promote("candidate-3", promotionEvidence));

        // candidateId is propagated from candidate
        assertThat(active.candidateId()).isEqualTo("candidate-3");

        // Rollback preserves all new fields
        LearnedArtifact rolledBack = runPromise(() -> service.rollback(active.artifactId(), "rb-ref-1"));
        assertThat(rolledBack.candidateId()).isEqualTo("candidate-3");
        assertThat(rolledBack.state()).isEqualTo(PromotionState.ROLLED_BACK);
    }

    // -- helpers --

    private static LearningCandidate candidate(String candidateId, String agentId) {
        return new LearningCandidate(
                candidateId,
                agentId,
                "release-1",
                "trace-1",
                LearningTarget.PROCEDURAL_SKILL,
                LearningCandidateState.PROPOSED,
                List.of("episode-1"),
                Map.of("procedure", "retry"),
                null);
    }

    private static PromotionEvidence evidence(String evidenceId, String candidateId) {
        return new PromotionEvidence(
                evidenceId,
                candidateId,
                "eval-pack-1",
                List.of("eval-1"),
                Map.of("score", 0.95),
                "reviewer-1",
                null);
    }
}
