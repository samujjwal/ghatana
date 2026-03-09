package com.ghatana.agent.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Validity} value object and confidence decay.
 *
 * @doc.type class
 * @doc.purpose Validity effective confidence and decay tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("Validity")
class ValidityTest {

    @Nested
    @DisplayName("effectiveConfidence")
    class EffectiveConfidence {

        @Test
        @DisplayName("should return base confidence when just verified")
        void shouldReturnBaseWhenJustVerified() {
            Instant now = Instant.now();
            Validity validity = Validity.builder()
                    .confidence(0.95)
                    .lastVerified(now)
                    .decayRate(0.01)
                    .build();

            double effective = validity.effectiveConfidence(now);

            assertThat(effective).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("should decay confidence over time")
        void shouldDecayOverTime() {
            Instant past = Instant.now().minus(30, ChronoUnit.DAYS);
            Validity validity = Validity.builder()
                    .confidence(0.95)
                    .lastVerified(past)
                    .decayRate(0.01)
                    .build();

            double effective = validity.effectiveConfidence(Instant.now());

            assertThat(effective).isLessThan(0.95);
            assertThat(effective).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("should not go below zero")
        void shouldNotGoBelowZero() {
            Instant veryOld = Instant.now().minus(365 * 10, ChronoUnit.DAYS);
            Validity validity = Validity.builder()
                    .confidence(0.5)
                    .lastVerified(veryOld)
                    .decayRate(0.1)
                    .build();

            double effective = validity.effectiveConfidence(Instant.now());

            assertThat(effective).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("should not decay when rate is zero")
        void shouldNotDecayWhenRateIsZero() {
            Instant past = Instant.now().minus(365, ChronoUnit.DAYS);
            Validity validity = Validity.builder()
                    .confidence(0.8)
                    .lastVerified(past)
                    .decayRate(0.0)
                    .build();

            double effective = validity.effectiveConfidence(Instant.now());

            assertThat(effective).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.001));
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            Instant now = Instant.now();
            Validity validity = Validity.builder()
                    .confidence(0.9)
                    .lastVerified(now)
                    .decayRate(0.05)
                    .status(ValidityStatus.ACTIVE)
                    .build();

            assertThat(validity.getConfidence()).isEqualTo(0.9);
            assertThat(validity.getLastVerified()).isEqualTo(now);
            assertThat(validity.getDecayRate()).isEqualTo(0.05);
            assertThat(validity.getStatus()).isEqualTo(ValidityStatus.ACTIVE);
        }
    }
}
