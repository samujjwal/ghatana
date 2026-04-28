package com.ghatana.yappc.services.lifecycle.assessment;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Predicts the remaining time and confidence for a lifecycle phase gate to become ready
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TransitionTimingPredictor {

    static final double CLARITY_THRESHOLD_SHAPE = 0.7;
    static final int COVERAGE_THRESHOLD_REVIEW = 60;

    public Prediction predict(
            @NotNull String fromPhase,
            @NotNull String toPhase,
            @NotNull ProjectContext context,
            @NotNull ReadinessReport readinessReport) {

        Objects.requireNonNull(fromPhase, "fromPhase must not be null");
        Objects.requireNonNull(toPhase, "toPhase must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(readinessReport, "readinessReport must not be null");

        if (readinessReport.ready()) {
            return new Prediction(
                    0,
                    "Ready now",
                    0.95,
                    "All readiness gates are already satisfied.");
        }

        int estimatedHours = transitionBaseHours(fromPhase, toPhase)
                + (readinessReport.blockers().size() * 6)
                + contextPenalty(fromPhase, toPhase, context);

        double confidence = clamp(
                0.55 + (availableSignals(context) * 0.08) - (readinessReport.blockers().size() * 0.05),
                0.35,
                0.95);

        return new Prediction(
                estimatedHours,
                humanizeHours(estimatedHours),
                confidence,
                buildRationale(fromPhase, toPhase, context, readinessReport));
    }

    static int transitionBaseHours(String fromPhase, String toPhase) {
        return switch ((fromPhase + "→" + toPhase).toLowerCase()) {
            case "intent→shape" -> 16;
            case "shape→generate" -> 18;
            case "generate→run" -> 20;
            case "run→review" -> 12;
            case "review→deploy" -> 10;
            case "deploy→maintain" -> 8;
            default -> 14;
        };
    }

    static String humanizeHours(int estimatedHours) {
        if (estimatedHours <= 0) {
            return "Ready now";
        }
        if (estimatedHours < 24) {
            return "~" + estimatedHours + " hours";
        }
        int roundedDays = Math.max(1, (int) Math.round(estimatedHours / 24.0));
        return "~" + roundedDays + (roundedDays == 1 ? " day" : " days");
    }

    private static int contextPenalty(String fromPhase, String toPhase, ProjectContext context) {
        String transitionKey = (fromPhase + "→" + toPhase).toLowerCase();

        return switch (transitionKey) {
            case "intent→shape", "shape→generate" -> {
                double threshold = CLARITY_THRESHOLD_SHAPE;
                if (context.averageClarityScore() >= threshold) {
                    yield 0;
                }
                yield (int) Math.ceil((threshold - context.averageClarityScore()) * 24);
            }
            case "generate→run" -> context.codeCommitCount() > 0 ? 0 : 8;
            case "run→review" -> {
                int penalty = Boolean.FALSE.equals(context.buildPassing()) ? 6 : 0;
                if (context.testCoveragePercent() >= 0 && context.testCoveragePercent() < COVERAGE_THRESHOLD_REVIEW) {
                    penalty += (int) Math.ceil((COVERAGE_THRESHOLD_REVIEW - context.testCoveragePercent()) / 10.0) * 4;
                }
                yield penalty;
            }
            case "review→deploy" -> context.decisionCount() > 0 ? 0 : 6;
            default -> 0;
        };
    }

    private static int availableSignals(ProjectContext context) {
        int signals = 0;
        if (context.requirementCount() > 0) {
            signals++;
        }
        if (context.averageClarityScore() > 0.0) {
            signals++;
        }
        if (context.codeCommitCount() > 0) {
            signals++;
        }
        if (context.testCoveragePercent() >= 0) {
            signals++;
        }
        if (context.buildPassing() != null) {
            signals++;
        }
        if (context.decisionCount() > 0) {
            signals++;
        }
        return signals;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String buildRationale(
            String fromPhase,
            String toPhase,
            ProjectContext context,
            ReadinessReport readinessReport) {
        if (!readinessReport.blockers().isEmpty()) {
            return "Prediction for " + fromPhase + " to " + toPhase
                    + " is driven by " + readinessReport.blockers().size()
                    + " blocker(s) and current delivery signals.";
        }
        return "Prediction for " + fromPhase + " to " + toPhase
                + " uses current delivery signals for project " + context.projectId() + ".";
    }

    public record Prediction(
            int estimatedHours,
            String estimatedReadyIn,
            double confidence,
            String rationale) {
    }
}
