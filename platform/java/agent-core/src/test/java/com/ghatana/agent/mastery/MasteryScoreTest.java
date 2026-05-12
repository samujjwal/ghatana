/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests for MasteryScore record
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("MasteryScore Tests")
class MasteryScoreTest {

    @Test
    @DisplayName("Execution score is product of key dimensions")
    void executionScoreCalculation() {
        MasteryScore score = new MasteryScore(
                0.9,  // correctness
                0.8,  // freshness
                0.7,  // applicability
                0.9,  // safety
                0.6,  // transferability
                0.8,  // evidence strength
                0.9   // regression stability
        );

        // executionScore = correctness * freshness * applicability * safety * regressionStability
        double expected = 0.9 * 0.8 * 0.7 * 0.9 * 0.9;
        assertThat(score.executionScore()).isEqualTo(expected, within(0.001));
    }

    @Test
    @DisplayName("Zero score returns zero execution score")
    void zeroScore() {
        MasteryScore zero = MasteryScore.zero();
        assertThat(zero.executionScore()).isZero();
    }

    @Test
    @DisplayName("Perfect score returns one execution score")
    void perfectScore() {
        MasteryScore perfect = MasteryScore.perfect();
        assertThat(perfect.executionScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Score values must be in [0.0, 1.0]")
    void scoreValidation() {
        assertThatThrownBy(() -> new MasteryScore(1.1, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correctness must be in [0.0, 1.0]");

        assertThatThrownBy(() -> new MasteryScore(-0.1, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correctness must be in [0.0, 1.0]");
    }

    @Test
    @DisplayName("Correctness-only score sets other dimensions to defaults")
    void correctnessOnly() {
        MasteryScore score = MasteryScore.correctnessOnly(0.8);
        assertThat(score.correctness()).isEqualTo(0.8);
        assertThat(score.freshness()).isEqualTo(0.5);
        assertThat(score.applicability()).isEqualTo(0.5);
        assertThat(score.safety()).isEqualTo(1.0);
    }

    private static org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}
