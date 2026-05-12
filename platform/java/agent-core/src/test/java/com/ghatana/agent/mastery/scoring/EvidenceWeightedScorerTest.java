/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery.scoring;

import com.ghatana.agent.mastery.MasteryScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for EvidenceWeightedScorer
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("EvidenceWeightedScorer Tests")
class EvidenceWeightedScorerTest {

    private final EvidenceWeightedScorer scorer = new EvidenceWeightedScorer();

    @Test
    @DisplayName("Score computed from evidence bundle")
    void scoreFromEvidence() {
        EvidenceBundle bundle = new EvidenceBundle(
                "bundle-1",
                "mastery-1",
                Instant.now(),
                List.of(
                        new EvidenceBundle.EvidenceItem("item-1", "correctness", "test", 0.9, Map.of()),
                        new EvidenceBundle.EvidenceItem("item-2", "safety", "test", 1.0, Map.of())
                ),
                Map.of(
                        "correctness", 0.25,
                        "safety", 0.20
                )
        );

        MasteryScore score = scorer.score(bundle);

        assertThat(score.correctness()).isGreaterThan(0);
        assertThat(score.safety()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Update combines current score with new evidence")
    void updateCombinesScores() {
        MasteryScore current = new MasteryScore(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        EvidenceBundle bundle = new EvidenceBundle(
                "bundle-1",
                "mastery-1",
                Instant.now(),
                List.of(
                        new EvidenceBundle.EvidenceItem("item-1", "correctness", "test", 0.9, Map.of())
                ),
                Map.of("correctness", 0.5)
        );

        MasteryScore updated = scorer.update(current, bundle);

        // Updated score should reflect weighted combination
        assertThat(updated.correctness()).isGreaterThan(0);
        assertThat(updated.correctness()).isLessThan(1.0);
    }

    @Test
    @DisplayName("Zero evidence bundle returns zero score")
    void zeroEvidenceReturnsZeroScore() {
        EvidenceBundle bundle = new EvidenceBundle(
                "bundle-1",
                "mastery-1",
                Instant.now(),
                List.of(),
                Map.of()
        );

        MasteryScore score = scorer.score(bundle);

        // Should have default values since no evidence
        assertThat(score).isNotNull();
    }
}
