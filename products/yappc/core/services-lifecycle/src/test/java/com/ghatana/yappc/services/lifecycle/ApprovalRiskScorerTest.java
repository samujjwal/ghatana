/*
 * Copyright (c) 2026 Ghatana Technologies
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
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalRiskScorer")
class ApprovalRiskScorerTest extends EventloopTestBase {

    @Mock
    private CompletionService completionService;

    private ApprovalRiskScorer scorer;

    private ApprovalRequest phaseAdvanceRequest;
    private ApprovalRequest deploymentRequest;

    @BeforeEach
    void setUp() {
        scorer = new ApprovalRiskScorer(completionService);

        phaseAdvanceRequest = new ApprovalRequest(
                "req-phase",
                "proj-001",
                "agent-x",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext(
                        "INTENT", "SHAPE", "required", List.of("crit-1"), List.of()),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc",
                Instant.now(),
                null, null, null);

        deploymentRequest = new ApprovalRequest(
                "req-deploy",
                "proj-001",
                "agent-x",
                ApprovalRequest.ApprovalType.DEPLOYMENT,
                new ApprovalRequest.ApprovalContext(
                        "VALIDATE", "DEPLOY", "prod deploy", List.of(), List.of()),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc",
                Instant.now(),
                null, null, null);
    }

    @Test
    @DisplayName("score returns HIGH when LLM responds with RISK: HIGH")
    void scoreReturnsHighWhenLlmSaysHigh() {
        when(completionService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.of(
                        "RISK: HIGH SCORE: 0.9 REASON: Production deployment with many unmet criteria")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(deploymentRequest));

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH);
        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.requiredApproverCount()).isEqualTo(2);
        assertThat(result.reasoning()).contains("Production deployment");
    }

    @Test
    @DisplayName("score returns LOW when LLM responds with RISK: LOW")
    void scoreReturnsLowWhenLlmSaysLow() {
        when(completionService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.of(
                        "RISK: LOW SCORE: 0.15 REASON: Routine phase advance with all criteria met")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(phaseAdvanceRequest));

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.LOW);
        assertThat(result.score()).isEqualTo(0.15);
        assertThat(result.requiredApproverCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("score returns MEDIUM when LLM responds with RISK: MEDIUM")
    void scoreReturnsMediumWhenLlmSaysMedium() {
        when(completionService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.of(
                        "RISK: MEDIUM SCORE: 0.5 REASON: Some unmet criteria present")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(phaseAdvanceRequest));

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.MEDIUM);
        assertThat(result.requiredApproverCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("score falls back to heuristic when LLM call fails")
    void scoreFallsBackToHeuristicOnLlmFailure() {
        when(completionService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("LLM timeout")));

        // DEPLOYMENT type → heuristic returns HIGH
        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(deploymentRequest));

        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH);
        assertThat(result.requiredApproverCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("score falls back to heuristic when LLM response is empty")
    void scoreFallsBackToMediumOnEmptyLlmResponse() {
        when(completionService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.of("")));

        ApprovalRiskScorer.RiskScore result = runPromise(() -> scorer.score(phaseAdvanceRequest));

        // Empty response → MEDIUM default
        assertThat(result.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.MEDIUM);
    }

    // ─── Heuristic tests (direct) ─────────────────────────────────────────────

    @Test
    @DisplayName("heuristicScore returns HIGH for DEPLOYMENT type")
    void heuristicReturnsHighForDeployment() {
        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(deploymentRequest);
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH);
        assertThat(score.requiredApproverCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("heuristicScore returns HIGH for RISK_ACCEPTANCE type")
    void heuristicReturnsHighForRiskAcceptance() {
        ApprovalRequest riskAcceptance = new ApprovalRequest(
                "req-risk", "proj-001", "agent-x",
                ApprovalRequest.ApprovalType.RISK_ACCEPTANCE,
                new ApprovalRequest.ApprovalContext("VALIDATE", "DEPLOY", "risk", List.of(), List.of()),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc", Instant.now(), null, null, null);

        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(riskAcceptance);
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.HIGH);
    }

    @Test
    @DisplayName("heuristicScore returns MEDIUM when more than 2 unmet criteria")
    void heuristicReturnsMediumForManyUnmetCriteria() {
        ApprovalRequest manyUnmet = new ApprovalRequest(
                "req-unmet", "proj-001", "agent-x",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext(
                        "INTENT", "SHAPE", "blocked", List.of("c1", "c2", "c3"), List.of()),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-abc", Instant.now(), null, null, null);

        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(manyUnmet);
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("heuristicScore returns LOW for routine phase advance with few criteria")
    void heuristicReturnsLowForRoutineAdvance() {
        ApprovalRiskScorer.RiskScore score = scorer.heuristicScore(phaseAdvanceRequest);
        assertThat(score.level()).isEqualTo(ApprovalRiskScorer.RiskLevel.LOW);
        assertThat(score.requiredApproverCount()).isEqualTo(1);
    }
}
