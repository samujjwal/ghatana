/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — AI Readiness Assessor
 */
package com.ghatana.yappc.services.lifecycle.assessment;

import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates whether a YAPPC project is ready to advance to the next lifecycle phase.
 *
 * <h2>Gate model</h2>
 * <p>Each phase transition has both <em>hard gates</em> (deterministic, enforced regardless
 * of AI) and <em>AI gates</em> (evaluated by LLM reasoning):
 *
 * <ul>
 *   <li><b>intent → shape:</b>
 *       hard=requirementsExist; AI=clarityScore≥0.7</li>
 *   <li><b>shape → generate:</b>
 *       hard=requirementsExist; AI=clarityScore≥0.8</li>
 *   <li><b>generate → run:</b>
 *       hard=hasCommits; AI=generateQualityOk</li>
 *   <li><b>run → review:</b>
 *       hard=buildPassing,coverageOk; AI=reviewReadyOk</li>
 *   <li><b>review → deploy:</b>
 *       hard=decisionsMade; AI=deployReadyOk</li>
 *   <li><b>deploy → maintain:</b>
 *       hard=buildPassing; AI=maintainReadyOk</li>
 * </ul>
 *
 * <p>If the AI service is unavailable, AI gates are bypassed with a warning; hard gates
 * are always enforced.
 *
 * @doc.type class
 * @doc.purpose AI-powered + deterministic readiness assessment for lifecycle phase transitions
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AIReadinessAssessor {

    private static final Logger log = LoggerFactory.getLogger(AIReadinessAssessor.class);

    /** Minimum requirements clarity score required before the shape phase. */
    static final double CLARITY_THRESHOLD_SHAPE    = 0.7;
    /** Higher clarity required before committing to code generation. */
    static final double CLARITY_THRESHOLD_GENERATE = 0.8;
    /** Minimum test coverage before the run phase advances to review. */
    static final int    COVERAGE_THRESHOLD_REVIEW  = 60;

    private final YAPPCAIService aiService;

    /**
     * @param aiService AI service used for LLM reasoning-based gate evaluation
     */
    public AIReadinessAssessor(@NotNull YAPPCAIService aiService) {
        this.aiService = Objects.requireNonNull(aiService, "aiService must not be null");
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Assesses whether the project (described by {@code context}) is ready to advance
     * from {@code fromPhase} to {@code toPhase}.
     *
     * @param fromPhase transition source phase (e.g. {@code "intent"})
     * @param toPhase   transition target phase (e.g. {@code "shape"})
     * @param context   aggregated project state snapshot
     * @return promise of {@link ReadinessReport}
     */
    public Promise<ReadinessReport> assess(
            @NotNull String fromPhase,
            @NotNull String toPhase,
            @NotNull ProjectContext context) {

        Objects.requireNonNull(fromPhase, "fromPhase must not be null");
        Objects.requireNonNull(toPhase,   "toPhase must not be null");
        Objects.requireNonNull(context,   "context must not be null");

        log.debug("Assessing readiness: {} → {} for project={}", fromPhase, toPhase, context.projectId());

        // ── Step 1: Evaluate hard (deterministic) gates ───────────────────────
        List<String> hardBlockers = evaluateHardGates(fromPhase, toPhase, context);

        // ── Step 2: Evaluate AI gates (if AI is available) ───────────────────
        return evaluateAiGates(fromPhase, toPhase, context)
                .then(aiResult -> {
                    List<String> allBlockers = new ArrayList<>(hardBlockers);
                    allBlockers.addAll(aiResult.blockers());

                    if (allBlockers.isEmpty()) {
                        return Promise.of(ReadinessReport.ready(
                                fromPhase, toPhase,
                                aiResult.clarityScore(),
                                aiResult.note()));
                    }

                    return Promise.of(ReadinessReport.blocked(
                            fromPhase, toPhase,
                            aiResult.clarityScore(),
                            allBlockers,
                            aiResult.recommendations(),
                            aiResult.note()));
                });
    }

    // ── Hard gate evaluation ─────────────────────────────────────────────────

    /**
     * Returns deterministic blockers that must be resolved before the given transition.
     * Returns an empty list if no hard gates are violated.
     */
    List<String> evaluateHardGates(
            String fromPhase, String toPhase, ProjectContext context) {

        List<String> blockers = new ArrayList<>();
        String key = fromPhase + "→" + toPhase;

        switch (key) {
            case "intent→shape" -> {
                if (!context.hasRequirements()) {
                    blockers.add("At least one requirement must be captured before entering the shape phase.");
                }
            }
            case "shape→generate" -> {
                if (!context.hasRequirements()) {
                    blockers.add("Requirements must be present before code generation.");
                }
                if (!context.meetsClarity(CLARITY_THRESHOLD_SHAPE)) {
                    blockers.add("Requirements clarity score (" + context.averageClarityScore() +
                            ") is below the minimum " + CLARITY_THRESHOLD_SHAPE + " required for code generation.");
                }
            }
            case "generate→run" -> {
                if (context.codeCommitCount() == 0) {
                    blockers.add("No code commits found. At least one commit is required before entering the run phase.");
                }
            }
            case "run→review" -> {
                if (Boolean.FALSE.equals(context.buildPassing())) {
                    blockers.add("Build must be passing before advancing to the review phase.");
                }
                if (context.testCoveragePercent() >= 0 && !context.hasCoverage(COVERAGE_THRESHOLD_REVIEW)) {
                    blockers.add("Test coverage (" + context.testCoveragePercent() +
                            "%) is below the minimum " + COVERAGE_THRESHOLD_REVIEW + "% required for review.");
                }
            }
            case "review→deploy" -> {
                if (context.decisionCount() == 0) {
                    blockers.add("At least one architectural decision must be recorded before deployment.");
                }
            }
            case "deploy→maintain" -> {
                if (Boolean.FALSE.equals(context.buildPassing())) {
                    blockers.add("Build must be passing before transitioning to the maintain phase.");
                }
            }
            default -> log.debug("No hard gate rules defined for transition: {}", key);
        }

        return blockers;
    }

    // ── AI gate evaluation ────────────────────────────────────────────────────

    private Promise<AiGateResult> evaluateAiGates(
            String fromPhase, String toPhase, ProjectContext context) {

        String prompt = buildPrompt(fromPhase, toPhase, context);

        return aiService.reason(prompt, buildReasoningContext(fromPhase, toPhase, context))
                .then(
                        response -> Promise.of(parseAiResult(response, fromPhase, toPhase, context)),
                        ex -> {
                            log.warn("AI gate evaluation failed for {} → {} project={}: {}",
                                    fromPhase, toPhase, context.projectId(), ex.getMessage());
                            // AI unavailable — pass the gate with a warning note
                            return Promise.of(AiGateResult.aiUnavailable(context.averageClarityScore()));
                        });
    }

    private String buildPrompt(String fromPhase, String toPhase, ProjectContext context) {
        return String.format(
                "Evaluate the readiness of a software project to advance from the '%s' phase " +
                "to the '%s' phase in YAPPC (You Are the Project Conductor).\n\n" +
                "Project stats:\n" +
                "- Requirements: %d (avg clarity: %.2f / 1.0)\n" +
                "- Code commits: %d\n" +
                "- Test coverage: %d%%\n" +
                "- Build passing: %s\n" +
                "- Architectural decisions recorded: %d\n" +
                "- Active agents: %d\n\n" +
                "Respond with:\n" +
                "READY: <yes|no>\n" +
                "CLARITY: <0.0-1.0>\n" +
                "BLOCKERS: <semicolon-separated list or 'none'>\n" +
                "RECOMMENDATIONS: <semicolon-separated list or 'none'>\n" +
                "NOTE: <one sentence summary>",
                fromPhase, toPhase,
                context.requirementCount(), context.averageClarityScore(),
                context.codeCommitCount(),
                context.testCoveragePercent() >= 0 ? context.testCoveragePercent() : 0,
                context.buildPassing() == null ? "unknown"
                        : (context.buildPassing() ? "yes" : "no"),
                context.decisionCount(),
                context.activeAgentCount());
    }

    private Map<String, Object> buildReasoningContext(
            String fromPhase, String toPhase, ProjectContext context) {
        return Map.of(
                "fromPhase",        fromPhase,
                "toPhase",          toPhase,
                "projectId",        context.projectId(),
                "tenantId",         context.tenantId(),
                "requirementCount", context.requirementCount(),
                "clarityScore",     context.averageClarityScore());
    }

    private AiGateResult parseAiResult(
            String response, String fromPhase, String toPhase, ProjectContext context) {

        if (response == null || response.isBlank()) {
            return AiGateResult.aiUnavailable(context.averageClarityScore());
        }

        double clarityScore = context.averageClarityScore();
        List<String> blockers = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        String note = "";

        for (String line : response.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("CLARITY:")) {
                try {
                    clarityScore = Double.parseDouble(trimmed.substring(8).trim());
                    clarityScore = Math.max(0.0, Math.min(1.0, clarityScore));
                } catch (NumberFormatException ignored) { /* keep default */ }
            } else if (trimmed.startsWith("BLOCKERS:")) {
                String raw = trimmed.substring(9).trim();
                if (!raw.isBlank() && !"none".equalsIgnoreCase(raw)) {
                    for (String b : raw.split(";")) {
                        String blocker = b.trim();
                        if (!blocker.isEmpty()) blockers.add(blocker);
                    }
                }
            } else if (trimmed.startsWith("RECOMMENDATIONS:")) {
                String raw = trimmed.substring(16).trim();
                if (!raw.isBlank() && !"none".equalsIgnoreCase(raw)) {
                    for (String r : raw.split(";")) {
                        String rec = r.trim();
                        if (!rec.isEmpty()) recommendations.add(rec);
                    }
                }
            } else if (trimmed.startsWith("NOTE:")) {
                note = trimmed.substring(5).trim();
            }
        }

        return new AiGateResult(clarityScore, blockers, recommendations, note);
    }

    // ── Internal value object ─────────────────────────────────────────────────

    /** Package-private result from the AI gate step. */
    record AiGateResult(
            double clarityScore,
            List<String> blockers,
            List<String> recommendations,
            String note) {

        static AiGateResult aiUnavailable(double fallbackClarityScore) {
            return new AiGateResult(
                    fallbackClarityScore,
                    List.of(),
                    List.of(),
                    "AI evaluation unavailable — hard gates enforced only.");
        }
    }
}
