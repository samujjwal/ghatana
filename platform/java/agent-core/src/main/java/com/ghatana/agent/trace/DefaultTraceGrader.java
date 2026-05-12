/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.trace;

import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of TraceGrader.
 *
 * <p>Grading criteria:
 * <ul>
 *   <li>Completeness: all steps present and valid</li>
 *   <li>Causality: clear cause-effect relationships</li>
 *   <li>Relevance: aligned with learning objectives</li>
 *   <li>Quality: well-structured and readable</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of TraceGrader
 * @doc.layer agent-core
 * @doc.pattern Grader
 */
public final class DefaultTraceGrader implements TraceGrader {

    @Override
    @NotNull
    public TraceGrade grade(@NotNull Trace trace) {
        if (trace.steps().isEmpty()) {
            return TraceGrade.INVALID;
        }

        double completenessScore = computeCompleteness(trace);
        double causalityScore = computeCausality(trace);
        double qualityScore = computeQuality(trace);

        double overallScore = (completenessScore * 0.3) + (causalityScore * 0.4) + (qualityScore * 0.3);

        if (overallScore >= 0.9) {
            return TraceGrade.EXCELLENT;
        } else if (overallScore >= 0.7) {
            return TraceGrade.GOOD;
        } else if (overallScore >= 0.5) {
            return TraceGrade.FAIR;
        } else if (overallScore >= 0.3) {
            return TraceGrade.POOR;
        } else {
            return TraceGrade.INVALID;
        }
    }

    @Override
    public double score(@NotNull TraceGrade grade) {
        return switch (grade) {
            case EXCELLENT -> 1.0;
            case GOOD -> 0.8;
            case FAIR -> 0.6;
            case POOR -> 0.4;
            case INVALID -> 0.0;
        };
    }

    private double computeCompleteness(@NotNull Trace trace) {
        // Check if trace has input, output, and steps
        boolean hasInput = !trace.input().isBlank();
        boolean hasOutput = !trace.output().isBlank();
        boolean hasSteps = !trace.steps().isEmpty();

        if (!hasInput || !hasOutput || !hasSteps) {
            return 0.0;
        }

        // Check if all steps have action and observation
        long completeSteps = trace.steps().stream()
                .filter(step -> !step.action().isBlank() && !step.observation().isBlank())
                .count();

        return (double) completeSteps / trace.steps().size();
    }

    private double computeCausality(@NotNull Trace trace) {
        // Simple heuristic: check if steps have clear progression
        if (trace.steps().size() < 2) {
            return 0.5;
        }

        // Check if observations reference previous actions
        long causalLinks = 0;
        for (int i = 1; i < trace.steps().size(); i++) {
            Trace.TraceStep prev = trace.steps().get(i - 1);
            Trace.TraceStep curr = trace.steps().get(i);
            if (curr.observation().toLowerCase().contains(prev.action().toLowerCase())) {
                causalLinks++;
            }
        }

        return (double) causalLinks / (trace.steps().size() - 1);
    }

    private double computeQuality(@NotNull Trace trace) {
        // Check if metadata is present and steps are well-structured
        boolean hasMetadata = !trace.metadata().isEmpty();
        boolean stepsHaveMetadata = trace.steps().stream()
                .anyMatch(step -> !step.metadata().isEmpty());

        double score = 0.5;
        if (hasMetadata) score += 0.25;
        if (stepsHaveMetadata) score += 0.25;

        return Math.min(1.0, score);
    }
}
