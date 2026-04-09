package com.ghatana.yappc.services.lifecycle.assessment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Unit tests for transition timing prediction heuristics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TransitionTimingPredictor Tests")
class TransitionTimingPredictorTest {

    private final TransitionTimingPredictor predictor = new TransitionTimingPredictor();

    @Test
    @DisplayName("returns ready now when gates already pass")
    void returnsReadyNowWhenTransitionAlreadyReady() {
        TransitionTimingPredictor.Prediction prediction = predictor.predict(
                "intent",
                "shape",
                context("intent", 3, 0.9, 2, 80, true, 1, 2),
                ReadinessReport.ready("intent", "shape", 0.9, "Ready"));

        assertThat(prediction.estimatedHours()).isZero();
        assertThat(prediction.estimatedReadyIn()).isEqualTo("Ready now");
        assertThat(prediction.confidence()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("predicts additional time when clarity is below threshold")
    void predictsAdditionalTimeForLowClarity() {
        TransitionTimingPredictor.Prediction prediction = predictor.predict(
                "shape",
                "generate",
                context("shape", 4, 0.5, 1, -1, null, 0, 1),
                ReadinessReport.blocked(
                        "shape",
                        "generate",
                        0.5,
                        List.of("Requirements clarity is below the transition threshold."),
                        List.of("Clarify requirements"),
                        "Blocked by low clarity"));

        assertThat(prediction.estimatedHours()).isEqualTo(29);
        assertThat(prediction.estimatedReadyIn()).isEqualTo("~1 day");
        assertThat(prediction.rationale()).contains("blocker");
    }

    @Test
    @DisplayName("adds larger penalty for failing build and low coverage")
    void addsPenaltyForBuildAndCoverageDeficits() {
        TransitionTimingPredictor.Prediction prediction = predictor.predict(
                "run",
                "review",
                context("run", 5, 0.8, 6, 35, false, 1, 2),
                ReadinessReport.blocked(
                        "run",
                        "review",
                        0.8,
                        List.of("Build must be passing.", "Coverage below threshold."),
                        List.of("Fix build", "Add tests"),
                        "Blocked by quality gates"));

        assertThat(prediction.estimatedHours()).isEqualTo(42);
        assertThat(prediction.estimatedReadyIn()).isEqualTo("~2 days");
        assertThat(prediction.confidence()).isEqualTo(0.93);
    }

    @Test
    @DisplayName("reduces confidence as blocker count increases")
    void reducesConfidenceAsBlockersIncrease() {
        ProjectContext context = context("review", 5, 0.85, 8, 82, true, 0, 2);

        TransitionTimingPredictor.Prediction fewerBlockers = predictor.predict(
                "review",
                "deploy",
                context,
                ReadinessReport.blocked(
                        "review",
                        "deploy",
                        0.85,
                        List.of("Decision log missing."),
                        List.of("Record a decision"),
                        "One blocker"));

        TransitionTimingPredictor.Prediction moreBlockers = predictor.predict(
                "review",
                "deploy",
                context,
                ReadinessReport.blocked(
                        "review",
                        "deploy",
                        0.85,
                        List.of("Decision log missing.", "Release checklist incomplete."),
                        List.of("Record a decision", "Finish checklist"),
                        "Two blockers"));

        assertThat(moreBlockers.confidence()).isLessThan(fewerBlockers.confidence());
        assertThat(moreBlockers.estimatedHours()).isGreaterThan(fewerBlockers.estimatedHours());
    }

    private static ProjectContext context(
            String currentPhase,
            int requirementCount,
            double clarityScore,
            int commitCount,
            int coveragePercent,
            Boolean buildPassing,
            int decisionCount,
            int activeAgentCount) {
        return new ProjectContext(
                "project-1",
                "tenant-1",
                currentPhase,
                requirementCount,
                clarityScore,
                commitCount,
                coveragePercent,
                buildPassing,
                decisionCount,
                activeAgentCount);
    }
}
