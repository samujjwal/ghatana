/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.lifecycle.AgentLifecyclePhase;
import com.ghatana.agent.lifecycle.AgentPhaseTrace;
import com.ghatana.agent.lifecycle.AgentTurnTrace;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Learning promotion substrate")
class LearningPromotionTest extends EventloopTestBase {

    @Test
    void reflection_requiresDeclaredTargetAndTraceProvenance() {
        LearningContract contract = new LearningContract(
                LearningLevel.L2,
                Set.of(LearningTarget.RETRIEVAL_POLICY),
                true,
                true,
                false);
        AgentResult<String> result = AgentResult.success("ok", "ok", java.time.Duration.ZERO)
                .toBuilder()
                .traceId("trace-1")
                .agentReleaseId("release-1")
                .memoryRefs(List.of("episode-1"))
                .build();

        LearningCandidate candidate = new LearningReflectionService().propose(
                contract,
                result,
                "agent-1",
                LearningTarget.RETRIEVAL_POLICY,
                Map.of("route", "dense-first"));

        assertThat(candidate.provenanceRefs()).containsExactly("episode-1");
        assertThat(candidate.proposedArtifact()).containsEntry("route", "dense-first");
        assertThatThrownBy(() -> new LearningReflectionService().propose(
                contract, result, "agent-1", LearningTarget.PROMPT_TEMPLATE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reflectionCanGenerateCandidateFromTurnTrace() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false);
        AgentTurnTrace trace = new AgentTurnTrace(
                "trace-1",
                "turn-1",
                "agent-1",
                java.time.Instant.now(),
                java.time.Instant.now(),
                "SUCCESS",
                List.of(new AgentPhaseTrace(
                        "phase-1",
                        AgentLifecyclePhase.REFLECT,
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        "SUCCESS",
                        null,
                        Map.of())),
                Map.of());

        LearningCandidate candidate = new LearningReflectionService().proposeFromTrace(
                contract,
                trace,
                LearningTarget.PROCEDURAL_SKILL,
                Map.of("procedure", "retry-on-timeout"));

        assertThat(candidate.agentId()).isEqualTo("agent-1");
        assertThat(candidate.provenanceRefs()).containsExactly("trace-1");
        assertThat(candidate.proposedArtifact()).containsKey("phaseTraceRefs");
    }

    @Test
    void promotionRequiresEvidenceAndRollbackCreatesInactiveArtifact() {
        InMemoryLearningCandidateRepository candidates = new InMemoryLearningCandidateRepository();
        InMemoryPromotionEvidenceRepository evidence = new InMemoryPromotionEvidenceRepository();
        InMemoryLearnedArtifactRepository artifacts = new InMemoryLearnedArtifactRepository();
        LearnedArtifactPromotionService service =
                new LearnedArtifactPromotionService(candidates, evidence, artifacts);
        LearningCandidate candidate = new LearningCandidate(
                "candidate-1",
                "agent-1",
                "release-1",
                "trace-1",
                LearningTarget.PROCEDURAL_SKILL,
                LearningCandidateState.PROPOSED,
                List.of("episode-1"),
                Map.of("procedure", "retry"),
                null);
        PromotionEvidence promotionEvidence = new PromotionEvidence(
                "evidence-1",
                "candidate-1",
                "eval-pack-1",
                List.of("eval-1"),
                Map.of("score", 0.95),
                "reviewer-1",
                null);

        runPromise(() -> service.submit(candidate));
        LearnedArtifact active = runPromise(() -> service.promote("candidate-1", promotionEvidence));

        assertThat(active.state()).isEqualTo(PromotionState.ACTIVE);
        assertThat(active.promotionEvidenceId()).isEqualTo("evidence-1");

        LearnedArtifact rolledBack = runPromise(() -> service.rollback(active.artifactId(), "rollback-1"));

        assertThat(rolledBack.state()).isEqualTo(PromotionState.ROLLED_BACK);
        assertThat(rolledBack.rollbackRef()).isEqualTo("rollback-1");
    }

    @Test
    void replayAndMasteryMetricsExposeP2Signals() {
        LearningCandidate candidate = new LearningCandidate(
                "candidate-1",
                "agent-1",
                "release-1",
                "trace-1",
                LearningTarget.ROUTING_POLICY,
                LearningCandidateState.PROPOSED,
                List.of("trace-1"),
                Map.of("routingPolicy", "dense-first", "fallbackCondition", true),
                null);
        LearningReplayResult replay = new LearningReplayService().replay(
                candidate,
                "eval-routing",
                List.of(new LearningReplayCase("case-1", Map.of(), Map.of("routingPolicy", "dense-first"), 1.0)),
                0.8);

        assertThat(replay.passed()).isTrue();

        AgentResult<String> result = AgentResult.success("ok", "ok", java.time.Duration.ZERO).toBuilder()
                .metrics(Map.of("cost", 0.2, "driftScore", 0.1))
                .memoryRefs(List.of("memory-1"))
                .processingTime(java.time.Duration.ofMillis(50))
                .build();
        MasteryMetrics metrics = new MasteryMetricsCalculator().calculate(List.of(result), List.of());

        assertThat(metrics.successRate()).isEqualTo(1.0);
        assertThat(metrics.memoryUtility()).isEqualTo(1.0);
        assertThat(new AgentTypeEvalPackFactory().defaultCases(AgentType.PROBABILISTIC))
                .extracting(LearningReplayCase::caseId)
                .contains("confidence-calibration");
    }
}
