/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for {@link Validity} — confidence tracking and exponential decay.
 *
 * @doc.type class
 * @doc.purpose Unit tests for Validity value object
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@DisplayName("Validity - confidence tracking and exponential decay")
class ValidityTest {

    // ─────────────────────────────────────────────────────────────
    // Builder defaults
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder defaults")
    class BuilderDefaultsTests {

        @Test
        @DisplayName("Default confidence is 0.0")
        void defaultConfidence_isZero() { // GH-90000
            Validity v = Validity.builder().build(); // GH-90000
            assertThat(v.getConfidence()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("Default decayRate is 0.0 (no decay)")
        void defaultDecayRate_isZero() { // GH-90000
            Validity v = Validity.builder().build(); // GH-90000
            assertThat(v.getDecayRate()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("Default status is ACTIVE")
        void defaultStatus_isActive() { // GH-90000
            Validity v = Validity.builder().build(); // GH-90000
            assertThat(v.getStatus()).isEqualTo(ValidityStatus.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("Default lastVerified is null")
        void defaultLastVerified_isNull() { // GH-90000
            Validity v = Validity.builder().build(); // GH-90000
            assertThat(v.getLastVerified()).isNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────
    // effectiveConfidence — no decay
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("effectiveConfidence - no decay scenarios")
    class NoDecayTests {

        @Test
        @DisplayName("Zero decayRate returns confidence unchanged")
        void zeroDecayRate_returnsConfidenceUnchanged() { // GH-90000
            Instant lastVerified = Instant.now().minus(7, ChronoUnit.DAYS); // GH-90000
            Validity v = Validity.builder().confidence(0.8).decayRate(0.0) // GH-90000
                    .lastVerified(lastVerified).build(); // GH-90000
            assertThat(v.effectiveConfidence(Instant.now())).isEqualTo(0.8); // GH-90000
        }

        @Test
        @DisplayName("Null lastVerified returns confidence unchanged regardless of decayRate")
        void nullLastVerified_returnsConfidenceUnchanged() { // GH-90000
            Validity v = Validity.builder().confidence(0.9).decayRate(0.5) // GH-90000
                    .lastVerified(null).build(); // GH-90000
            assertThat(v.effectiveConfidence(Instant.now())).isEqualTo(0.9); // GH-90000
        }

        @Test
        @DisplayName("Just-verified item: 0 days aged → full confidence retained")
        void justVerified_retainsFullConfidence() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            Validity v = Validity.builder().confidence(1.0).decayRate(0.5) // GH-90000
                    .lastVerified(now).build(); // GH-90000
            // age = 0 ms → effectiveConfidence = 1.0 * e^0 = 1.0
            assertThat(v.effectiveConfidence(now)).isCloseTo(1.0, within(1e-6)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────
    // effectiveConfidence — with decay
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("effectiveConfidence - exponential decay formula")
    class DecayTests {

        @Test
        @DisplayName("After 1 day at decayRate=ln(2)≈0.693: confidence halves")
        void oneDay_halveDecayRate_halvesConfidence() { // GH-90000
            Instant lastVerified = Instant.now().minus(1, ChronoUnit.DAYS); // GH-90000
            double decayRate = Math.log(2); // after 1 day confidence should halve // GH-90000
            Validity v = Validity.builder().confidence(1.0).decayRate(decayRate) // GH-90000
                    .lastVerified(lastVerified).build(); // GH-90000
            assertThat(v.effectiveConfidence(Instant.now())).isCloseTo(0.5, within(1e-3)); // GH-90000
        }

        @Test
        @DisplayName("After 10 days at decayRate=0.1: confidence ≈ 0.3679 (initial=1.0)")
        void tenDays_decayOneTenth_correctFormula() { // GH-90000
            Instant lastVerified = Instant.now().minus(10, ChronoUnit.DAYS); // GH-90000
            Validity v = Validity.builder().confidence(1.0).decayRate(0.1) // GH-90000
                    .lastVerified(lastVerified).build(); // GH-90000
            // 1.0 * e^(-0.1 * 10) = e^(-1) ≈ 0.3679 // GH-90000
            assertThat(v.effectiveConfidence(Instant.now())).isCloseTo(Math.exp(-1), within(1e-3)); // GH-90000
        }

        @Test
        @DisplayName("After 100 days at decayRate=0.5: confidence approaches zero")
        void veryOldItem_confidenceNearZero() { // GH-90000
            Instant lastVerified = Instant.now().minus(100, ChronoUnit.DAYS); // GH-90000
            Validity v = Validity.builder().confidence(1.0).decayRate(0.5) // GH-90000
                    .lastVerified(lastVerified).build(); // GH-90000
            // 1.0 * e^(-50) ≈ 1.93e-22 // GH-90000
            assertThat(v.effectiveConfidence(Instant.now())).isLessThan(0.001); // GH-90000
        }

        @Test
        @DisplayName("Proportional scaling: confidence=0.5 decays at same rate as confidence=1.0")
        void proportionalScaling_conformsToFormula() { // GH-90000
            Instant lastVerified = Instant.now().minus(5, ChronoUnit.DAYS); // GH-90000
            Validity full = Validity.builder().confidence(1.0).decayRate(0.1) // GH-90000
                    .lastVerified(lastVerified).build(); // GH-90000
            Validity half = Validity.builder().confidence(0.5).decayRate(0.1) // GH-90000
                    .lastVerified(lastVerified).build(); // GH-90000
            Instant now = Instant.now(); // GH-90000
            assertThat(half.effectiveConfidence(now)) // GH-90000
                    .isCloseTo(full.effectiveConfidence(now) / 2, within(1e-6)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ValidityStatus enum coverage
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidityStatus enum values")
    class ValidityStatusTests {

        @Test
        @DisplayName("All four ValidityStatus values are present")
        void allStatusValues_present() { // GH-90000
            ValidityStatus[] values = ValidityStatus.values(); // GH-90000
            assertThat(values).containsExactlyInAnyOrder( // GH-90000
                    ValidityStatus.ACTIVE,
                    ValidityStatus.STALE,
                    ValidityStatus.DEPRECATED,
                    ValidityStatus.ARCHIVED);
        }

        @Test
        @DisplayName("STALE status can be set via builder")
        void staleStatus_settableViaBuilder() { // GH-90000
            Validity v = Validity.builder().status(ValidityStatus.STALE).build(); // GH-90000
            assertThat(v.getStatus()).isEqualTo(ValidityStatus.STALE); // GH-90000
        }
    }
}
