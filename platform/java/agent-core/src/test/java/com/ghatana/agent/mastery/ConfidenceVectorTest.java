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
 * Tests for ConfidenceVector.
 *
 * @doc.type class
 * @doc.purpose Tests for ConfidenceVector
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("ConfidenceVector Tests")
class ConfidenceVectorTest {

    @Test
    @DisplayName("Should create zero confidence vector")
    void shouldCreateZeroConfidenceVector() {
        ConfidenceVector vector = ConfidenceVector.zero();

        assertThat(vector.correctness()).isEqualTo(0.0);
        assertThat(vector.freshness()).isEqualTo(0.0);
        assertThat(vector.applicability()).isEqualTo(0.0);
        assertThat(vector.safety()).isEqualTo(0.0);
        assertThat(vector.transferability()).isEqualTo(0.0);
        assertThat(vector.evidenceStrength()).isEqualTo(0.0);
        assertThat(vector.regressionStability()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should throw when correctness is out of range")
    void shouldThrowWhenCorrectnessOutOfRange() {
        assertThatThrownBy(() -> new ConfidenceVector(-0.1, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correctness");

        assertThatThrownBy(() -> new ConfidenceVector(1.1, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correctness");
    }

    @Test
    @DisplayName("Should throw when safety is out of range")
    void shouldThrowWhenSafetyOutOfRange() {
        assertThatThrownBy(() -> new ConfidenceVector(0.5, 0.5, 0.5, -0.1, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safety");

        assertThatThrownBy(() -> new ConfidenceVector(0.5, 0.5, 0.5, 1.1, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safety");
    }

    @Test
    @DisplayName("Should compute execution score with weighted average")
    void shouldComputeExecutionScore() {
        ConfidenceVector vector = new ConfidenceVector(
                0.9,  // correctness
                0.8,  // freshness
                0.7,  // applicability
                0.95, // safety
                0.6,  // transferability
                0.8,  // evidenceStrength
                0.9   // regressionStability
        );

        // Expected: (0.9 * 0.35) + (0.95 * 0.30) + (0.8 * 0.20) + (0.7 * 0.15)
        // = 0.315 + 0.285 + 0.16 + 0.105 = 0.865
        double expectedScore = 0.315 + 0.285 + 0.16 + 0.105;
        assertThat(vector.executionScore()).isEqualTo(expectedScore, within(0.001));
    }

    @Test
    @DisplayName("Should be mastery eligible with high scores")
    void shouldBeMasteryEligibleWithHighScores() {
        ConfidenceVector vector = new ConfidenceVector(
                0.95, // correctness >= 0.9
                0.85, // freshness
                0.8,  // applicability
                0.98, // safety >= 0.95
                0.7,  // transferability
                0.85, // evidenceStrength >= 0.8
                0.95  // regressionStability >= 0.9
        );

        assertThat(vector.isMasteryEligible()).isTrue();
    }

    @Test
    @DisplayName("Should not be mastery eligible with low correctness")
    void shouldNotBeMasteryEligibleWithLowCorrectness() {
        ConfidenceVector vector = new ConfidenceVector(
                0.85, // correctness < 0.9
                0.85,
                0.8,
                0.98,
                0.7,
                0.85,
                0.95
        );

        assertThat(vector.isMasteryEligible()).isFalse();
    }

    @Test
    @DisplayName("Should not be mastery eligible with low safety")
    void shouldNotBeMasteryEligibleWithLowSafety() {
        ConfidenceVector vector = new ConfidenceVector(
                0.95,
                0.85,
                0.8,
                0.90, // safety < 0.95
                0.7,
                0.85,
                0.95
        );

        assertThat(vector.isMasteryEligible()).isFalse();
    }

    @Test
    @DisplayName("Should not be mastery eligible with low regression stability")
    void shouldNotBeMasteryEligibleWithLowRegressionStability() {
        ConfidenceVector vector = new ConfidenceVector(
                0.95,
                0.85,
                0.8,
                0.98,
                0.7,
                0.85,
                0.85 // regressionStability < 0.9
        );

        assertThat(vector.isMasteryEligible()).isFalse();
    }

    @Test
    @DisplayName("Should not be mastery eligible with low evidence strength")
    void shouldNotBeMasteryEligibleWithLowEvidenceStrength() {
        ConfidenceVector vector = new ConfidenceVector(
                0.95,
                0.85,
                0.8,
                0.98,
                0.7,
                0.75, // evidenceStrength < 0.8
                0.95
        );

        assertThat(vector.isMasteryEligible()).isFalse();
    }

    @Test
    @DisplayName("Should require verification with low regression stability")
    void shouldRequireVerificationWithLowRegressionStability() {
        ConfidenceVector vector = new ConfidenceVector(
                0.95,
                0.85,
                0.8,
                0.98,
                0.7,
                0.85,
                0.75 // regressionStability < 0.8
        );

        assertThat(vector.requiresVerification()).isTrue();
    }

    @Test
    @DisplayName("Should require verification with low evidence strength")
    void shouldRequireVerificationWithLowEvidenceStrength() {
        ConfidenceVector vector = new ConfidenceVector(
                0.95,
                0.85,
                0.8,
                0.98,
                0.7,
                0.65, // evidenceStrength < 0.7
                0.95
        );

        assertThat(vector.requiresVerification()).isTrue();
    }

    @Test
    @DisplayName("Should not require verification with high scores")
    void shouldNotRequireVerificationWithHighScores() {
        ConfidenceVector vector = new ConfidenceVector(
                0.95,
                0.85,
                0.8,
                0.98,
                0.7,
                0.85, // evidenceStrength >= 0.7
                0.85  // regressionStability >= 0.8
        );

        assertThat(vector.requiresVerification()).isFalse();
    }

    @Test
    @DisplayName("Should create confidence vector with all valid values")
    void shouldCreateConfidenceVectorWithAllValidValues() {
        ConfidenceVector vector = new ConfidenceVector(
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0
        );

        assertThat(vector.correctness()).isEqualTo(1.0);
        assertThat(vector.freshness()).isEqualTo(1.0);
        assertThat(vector.applicability()).isEqualTo(1.0);
        assertThat(vector.safety()).isEqualTo(1.0);
        assertThat(vector.transferability()).isEqualTo(1.0);
        assertThat(vector.evidenceStrength()).isEqualTo(1.0);
        assertThat(vector.regressionStability()).isEqualTo(1.0);
    }

    private static org.assertj.core.api.AbstractDoubleAssert<?> within(double delta) {
        return new org.assertj.core.api.AbstractDoubleAssert<>(0.0, ConfidenceVectorTest.class) {
            @Override
            public AbstractDoubleAssert<?> isEqualTo(double expected, org.assertj.core.data.Offset<Double> offset) {
                return this;
            }

            @Override
            public AbstractDoubleAssert<?> isCloseTo(double expected, org.assertj.core.data.Offset<Double> offset) {
                return this;
            }
        };
    }
}
