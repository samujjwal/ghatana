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

    private final TransitionTimingPredictor predictor = new TransitionTimingPredictor(); // GH-90000

    @Test
    @DisplayName("returns ready now when gates already pass")
    void returnsReadyNowWhenTransitionAlreadyReady() { // GH-90000
        TransitionTimingPredictor.Prediction prediction = predictor.predict( // GH-90000
                "intent",
                "shape",
                context("intent", 3, 0.9, 2, 80, true, 1, 2), // GH-90000
                ReadinessReport.ready("intent", "shape", 0.9, "Ready")); // GH-90000

        assertThat(prediction.estimatedHours()).isZero(); // GH-90000
        assertThat(prediction.estimatedReadyIn()).isEqualTo("Ready now");
        assertThat(prediction.confidence()).isEqualTo(0.95); // GH-90000
    }

    @Test
    @DisplayName("predicts additional time when clarity is below threshold")
    void predictsAdditionalTimeForLowClarity() { // GH-90000
        TransitionTimingPredictor.Prediction prediction = predictor.predict( // GH-90000
                "shape",
                "generate",
                context("shape", 4, 0.5, 1, -1, null, 0, 1), // GH-90000
                ReadinessReport.blocked( // GH-90000
                        "shape",
                        "generate",
                        0.5,
                        List.of("Requirements clarity is below the transition threshold."),
                        List.of("Clarify requirements"),
                        "Blocked by low clarity"));

        assertThat(prediction.estimatedHours()).isEqualTo(29); // GH-90000
        assertThat(prediction.estimatedReadyIn()).isEqualTo("~1 day");
        assertThat(prediction.rationale()).contains("blocker");
    }

    @Test
    @DisplayName("adds larger penalty for failing build and low coverage")
    void addsPenaltyForBuildAndCoverageDeficits() { // GH-90000
        TransitionTimingPredictor.Prediction prediction = predictor.predict( // GH-90000
                "run",
                "review",
                context("run", 5, 0.8, 6, 35, false, 1, 2), // GH-90000
                ReadinessReport.blocked( // GH-90000
                        "run",
                        "review",
                        0.8,
                        List.of("Build must be passing.", "Coverage below threshold."), // GH-90000
                        List.of("Fix build", "Add tests"), // GH-90000
                        "Blocked by quality gates"));

        assertThat(prediction.estimatedHours()).isEqualTo(42); // GH-90000
        assertThat(prediction.estimatedReadyIn()).isEqualTo("~2 days");
        assertThat(prediction.confidence()).isEqualTo(0.93); // GH-90000
    }

    @Test
    @DisplayName("reduces confidence as blocker count increases")
    void reducesConfidenceAsBlockersIncrease() { // GH-90000
        ProjectContext context = context("review", 5, 0.85, 8, 82, true, 0, 2); // GH-90000

        TransitionTimingPredictor.Prediction fewerBlockers = predictor.predict( // GH-90000
                "review",
                "deploy",
                context,
                ReadinessReport.blocked( // GH-90000
                        "review",
                        "deploy",
                        0.85,
                        List.of("Decision log missing."),
                        List.of("Record a decision"),
                        "One blocker"));

        TransitionTimingPredictor.Prediction moreBlockers = predictor.predict( // GH-90000
                "review",
                "deploy",
                context,
                ReadinessReport.blocked( // GH-90000
                        "review",
                        "deploy",
                        0.85,
                        List.of("Decision log missing.", "Release checklist incomplete."), // GH-90000
                        List.of("Record a decision", "Finish checklist"), // GH-90000
                        "Two blockers"));

        assertThat(moreBlockers.confidence()).isLessThan(fewerBlockers.confidence()); // GH-90000
        assertThat(moreBlockers.estimatedHours()).isGreaterThan(fewerBlockers.estimatedHours()); // GH-90000
    }

    private static ProjectContext context( // GH-90000
            String currentPhase,
            int requirementCount,
            double clarityScore,
            int commitCount,
            int coveragePercent,
            Boolean buildPassing,
            int decisionCount,
            int activeAgentCount) {
        return new ProjectContext( // GH-90000
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
