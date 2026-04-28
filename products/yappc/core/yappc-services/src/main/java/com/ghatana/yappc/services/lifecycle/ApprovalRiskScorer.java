/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Approval Risk Scorer
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Evaluates the risk level of a pending approval request by consulting the platform
 * {@link CompletionService} with a structured prompt, then falls back to a
 * deterministic heuristic if the LLM call fails or returns an unusable response.
 *
 * <p><b>Risk levels and routing behaviour:</b>
 * <ul>
 *   <li>{@code LOW}  — single approver sufficient</li>
 *   <li>{@code MEDIUM} — single approver required; human reviewer notified</li>
 *   <li>{@code HIGH}  — two approvers required; escalation event emitted</li>
 * </ul>
 *
 * <p>The scorer never throws; any internal failure demotes the result to the
 * heuristic-only path and logs a warning so operations can diagnose issues.
 *
 * @doc.type class
 * @doc.purpose AI-assisted risk scoring for human approval routing in lifecycle phase transitions
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle sense
 */
public final class ApprovalRiskScorer {

    private static final Logger log = LoggerFactory.getLogger(ApprovalRiskScorer.class);

    /** LLM temperature — low for stable, deterministic risk assessments. */
    private static final double TEMPERATURE = 0.1;
    private static final int    MAX_TOKENS  = 256;

    /**
     * Canonical risk levels that determine approver routing.
     */
    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    /**
     * Immutable result of a risk scoring evaluation.
     *
     * @param level              assessed risk level
     * @param score              normalised score in [0.0, 1.0] (higher = riskier)
     * @param reasoning          short human-readable justification
     * @param requiredApproverCount minimum number of approvers required based on risk
     */
    public record RiskScore(
            RiskLevel level,
            double score,
            String reasoning,
            int requiredApproverCount) {

        /** Convenience factory for a low-risk result. */
        static RiskScore low(String reasoning) {
            return new RiskScore(RiskLevel.LOW, 0.2, reasoning, 1);
        }

        /** Convenience factory for a medium-risk result. */
        static RiskScore medium(String reasoning) {
            return new RiskScore(RiskLevel.MEDIUM, 0.5, reasoning, 1);
        }

        /** Convenience factory for a high-risk result. */
        static RiskScore high(String reasoning) {
            return new RiskScore(RiskLevel.HIGH, 0.85, reasoning, 2);
        }
    }

    private final CompletionService completionService;

    /**
     * @param completionService platform LLM completion service; must not be null
     */
    public ApprovalRiskScorer(CompletionService completionService) {
        this.completionService = Objects.requireNonNull(completionService, "completionService");
    }

    /**
     * Scores the risk of the given approval request.
     *
     * <p>The method first attempts an LLM-assisted assessment; if the LLM call fails
     * or the response cannot be parsed the deterministic heuristic is used instead.
     *
     * @param request  the pending approval request to evaluate; must not be null
     * @return promise of a {@link RiskScore}; never completes exceptionally
     */
    public Promise<RiskScore> score(ApprovalRequest request) {
        Objects.requireNonNull(request, "request");

        String prompt = buildPrompt(request);
        CompletionRequest completionRequest = CompletionRequest.builder()
                .prompt(prompt)
                .maxTokens(MAX_TOKENS)
                .temperature(TEMPERATURE)
                .build();

        return completionService.complete(completionRequest)
                .map(result -> parseRiskScore(result.getText()))
                .then(
                        score -> Promise.of(score),
                        ex -> {
                            log.warn("[tenant={}][requestId={}] LLM risk scoring failed: {}; using heuristic",
                                    request.tenantId(), request.id(), ex.getMessage());
                            return Promise.of(heuristicScore(request));
                        }
                );
    }

    // ─── Prompt builder ───────────────────────────────────────────────────────

    private String buildPrompt(ApprovalRequest request) {
        ApprovalRequest.ApprovalContext ctx = request.context();
        List<String> unmetCriteria = ctx != null ? ctx.unmetCriteria() : List.of();
        List<String> missingArtifacts = ctx != null ? ctx.missingArtifacts() : List.of();
        String blockReason = ctx != null ? ctx.blockReason() : "not specified";
        String fromPhase   = ctx != null ? ctx.fromPhase() : "unknown";
        String toPhase     = ctx != null ? ctx.toPhase()   : "unknown";

        return """
                You are a software development lifecycle risk assessor.
                Evaluate the risk of the following human approval request and respond with
                exactly one line in this format:
                RISK: <LOW|MEDIUM|HIGH> SCORE: <0.0-1.0> REASON: <brief justification>

                Approval Type  : %s
                Phase Transition: %s → %s
                Block Reason   : %s
                Unmet Criteria : %s
                Missing Artifacts: %s
                Requesting Agent : %s
                """.formatted(
                request.approvalType().name(),
                fromPhase, toPhase,
                blockReason,
                unmetCriteria.isEmpty() ? "none" : String.join(", ", unmetCriteria),
                missingArtifacts.isEmpty() ? "none" : String.join(", ", missingArtifacts),
                request.requestingAgentId()
        );
    }

    // ─── Response parser ──────────────────────────────────────────────────────

    private RiskScore parseRiskScore(String text) {
        if (text == null || text.isBlank()) {
            return RiskScore.medium("LLM returned empty response; defaulting to MEDIUM");
        }

        String upper = text.toUpperCase();
        RiskLevel level;
        if (upper.contains("RISK: HIGH")) {
            level = RiskLevel.HIGH;
        } else if (upper.contains("RISK: LOW")) {
            level = RiskLevel.LOW;
        } else {
            level = RiskLevel.MEDIUM;
        }

        double score = extractScore(upper, level);
        String reasoning = extractReasoning(text);

        int requiredApprovers = level == RiskLevel.HIGH ? 2 : 1;
        return new RiskScore(level, score, reasoning, requiredApprovers);
    }

    private double extractScore(String upper, RiskLevel defaultLevel) {
        int scoreIdx = upper.indexOf("SCORE:");
        if (scoreIdx < 0) {
            return switch (defaultLevel) {
                case LOW    -> 0.2;
                case MEDIUM -> 0.5;
                case HIGH   -> 0.85;
            };
        }
        try {
            String after = upper.substring(scoreIdx + 6).trim();
            String token = after.split("\\s+")[0];
            return Double.parseDouble(token);
        } catch (NumberFormatException ex) {
            return 0.5;
        }
    }

    private String extractReasoning(String text) {
        int idx = text.toUpperCase().indexOf("REASON:");
        if (idx < 0) {
            return text.trim();
        }
        return text.substring(idx + 7).trim();
    }

    // ─── Deterministic heuristic (fallback) ───────────────────────────────────

    /**
     * Heuristic risk scoring when the LLM is unavailable.
     *
     * <ul>
     *   <li>DEPLOYMENT or RISK_ACCEPTANCE → HIGH</li>
     *   <li>More than 2 unmet criteria → MEDIUM</li>
     *   <li>Otherwise → LOW</li>
     * </ul>
     */
    RiskScore heuristicScore(ApprovalRequest request) {
        if (request.approvalType() == ApprovalRequest.ApprovalType.DEPLOYMENT
                || request.approvalType() == ApprovalRequest.ApprovalType.RISK_ACCEPTANCE) {
            return RiskScore.high("High-risk approval type: " + request.approvalType().name());
        }

        ApprovalRequest.ApprovalContext ctx = request.context();
        if (ctx != null && ctx.unmetCriteria() != null && ctx.unmetCriteria().size() > 2) {
            return RiskScore.medium("Multiple unmet criteria: " + ctx.unmetCriteria().size());
        }

        return RiskScore.low("Standard phase transition with minimal risk signals");
    }
}
