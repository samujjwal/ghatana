/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service — ApprovalRiskScorer Tests
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies ApprovalRiskScorer correctly classifies risk using LLM output and falls back to heuristics
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ApprovalRiskScorer [GH-90000]")
class ApprovalRiskScorerTest extends EventloopTestBase {

    @Mock
    private CompletionService completionService;

    private ApprovalRiskScorer scorer;

    private ApprovalRequest phaseAdvanceRequest;
    private ApprovalRequest deploymentRequest;

    @BeforeEach
    void setUp() { // GH-90000
        scorer = new ApprovalRiskScorer(completionService); // GH-90000

        phaseAdvanceRequest = new ApprovalRequest( // GH-90000
                "req-phase",
                "proj-001",
                "agent-x",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext( // GH-90000
                        "INTENT", "SHAPE", "required", List.of("crit-1 [GH-90000]"), List.of()),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc",
                Instant.now(), // GH-90000
                null, null, null);

        deploymentRequest = new ApprovalRequest( // GH-90000
                "req-deploy",
                "proj-001",
                "agent-x",
                ApprovalRequest.ApprovalType.DEPLOYMENT,
                new ApprovalRequest.ApprovalContext( // GH-90000
                        "VALIDATE", "DEPLOY", "prod deploy", List.of(), List.of()), // GH-90000
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc",
                Instant.now(), // GH-90000
                null, null, null);
    }

    @Test
    @DisplayName("score returns HIGH when LLM responds with RISK: HIGH [GH-90000]")
    void scoreReturnsHighWhenLlmSaysHigh() { // GH-90000
        when(completionService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.of( // GH-90000
                        "RISK: HIGH SCORE: 0.9 REASON: Production deployment with many unmet criteria")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(deploymentRequest)); // GH-90000

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH); // GH-90000
        assertThat(result.score()).isEqualTo(0.9); // GH-90000
        assertThat(result.requiredApproverCount()).isEqualTo(2); // GH-90000
        assertThat(result.reasoning()).contains("Production deployment [GH-90000]");
    }

    @Test
    @DisplayName("score returns LOW when LLM responds with RISK: LOW [GH-90000]")
    void scoreReturnsLowWhenLlmSaysLow() { // GH-90000
        when(completionService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.of( // GH-90000
                        "RISK: LOW SCORE: 0.15 REASON: Routine phase advance with all criteria met")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(phaseAdvanceRequest)); // GH-90000

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.LOW); // GH-90000
        assertThat(result.score()).isEqualTo(0.15); // GH-90000
        assertThat(result.requiredApproverCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("score returns MEDIUM when LLM responds with RISK: MEDIUM [GH-90000]")
    void scoreReturnsMediumWhenLlmSaysMedium() { // GH-90000
        when(completionService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.of( // GH-90000
                        "RISK: MEDIUM SCORE: 0.5 REASON: Some unmet criteria present")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(phaseAdvanceRequest)); // GH-90000

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.MEDIUM); // GH-90000
        assertThat(result.requiredApproverCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("score falls back to heuristic when LLM call fails [GH-90000]")
    void scoreFallsBackToHeuristicOnLlmFailure() { // GH-90000
        when(completionService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("LLM timeout [GH-90000]")));

        // DEPLOYMENT type → heuristic returns HIGH
        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(deploymentRequest)); // GH-90000

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH); // GH-90000
        assertThat(result.requiredApproverCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("score falls back to heuristic when LLM response is empty [GH-90000]")
    void scoreFallsBackToMediumOnEmptyLlmResponse() { // GH-90000
        when(completionService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.of(" [GH-90000]")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(phaseAdvanceRequest)); // GH-90000

        // Empty response → MEDIUM default
        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.MEDIUM); // GH-90000
    }

    // ─── Heuristic tests (direct) ───────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("heuristicScore returns HIGH for DEPLOYMENT type [GH-90000]")
    void heuristicReturnsHighForDeployment() { // GH-90000
        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(deploymentRequest); // GH-90000
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH); // GH-90000
        assertThat(score.requiredApproverCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("heuristicScore returns HIGH for RISK_ACCEPTANCE type [GH-90000]")
    void heuristicReturnsHighForRiskAcceptance() { // GH-90000
        ApprovalRequest riskAcceptance = new ApprovalRequest( // GH-90000
                "req-risk", "proj-001", "agent-x",
                ApprovalRequest.ApprovalType.RISK_ACCEPTANCE,
                new ApprovalRequest.ApprovalContext("VALIDATE", "DEPLOY", "risk", List.of(), List.of()), // GH-90000
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc", Instant.now(), null, null, null); // GH-90000

        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(riskAcceptance); // GH-90000
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH); // GH-90000
    }

    @Test
    @DisplayName("heuristicScore returns MEDIUM when more than 2 unmet criteria [GH-90000]")
    void heuristicReturnsMediumForManyUnmetCriteria() { // GH-90000
        ApprovalRequest manyUnmet = new ApprovalRequest( // GH-90000
                "req-unmet", "proj-001", "agent-x",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext( // GH-90000
                        "INTENT", "SHAPE", "blocked", List.of("c1", "c2", "c3"), List.of()), // GH-90000
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc", Instant.now(), null, null, null); // GH-90000

        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(manyUnmet); // GH-90000
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.MEDIUM); // GH-90000
    }

    @Test
    @DisplayName("heuristicScore returns LOW for routine phase advance with few criteria [GH-90000]")
    void heuristicReturnsLowForRoutineAdvance() { // GH-90000
        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(phaseAdvanceRequest); // GH-90000
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.LOW); // GH-90000
        assertThat(score.requiredApproverCount()).isEqualTo(1); // GH-90000
    }
}
