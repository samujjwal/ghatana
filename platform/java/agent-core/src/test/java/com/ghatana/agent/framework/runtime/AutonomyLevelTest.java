/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for P0: AutonomyLevel five-tier canonical vocabulary,
 * legacy alias mapping, and approval semantics.
 */
@DisplayName("AutonomyLevel (P0/WP1)")
class AutonomyLevelTest {

    @Nested
    @DisplayName("canonical values")
    class CanonicalValues {

        @Test
        @DisplayName("should have exactly 5 canonical tiers")
        void shouldHaveFiveCanonicalTiers() {
            assertThat(AutonomyLevel.values()).hasSize(5);
            assertThat(AutonomyLevel.values()).containsExactly(
                    AutonomyLevel.ADVISORY,
                    AutonomyLevel.DRAFT,
                    AutonomyLevel.SUPERVISED,
                    AutonomyLevel.BOUNDED_AUTONOMOUS,
                    AutonomyLevel.AUTONOMOUS);
        }

        @Test
        @DisplayName("ADVISORY and DRAFT always require approval")
        void advisoryAndDraftAlwaysRequireApproval() {
            assertThat(AutonomyLevel.ADVISORY.alwaysRequiresApproval()).isTrue();
            assertThat(AutonomyLevel.DRAFT.alwaysRequiresApproval()).isTrue();
        }

        @Test
        @DisplayName("SUPERVISED and above do not always require approval")
        void supervisedAndAboveDoNotAlwaysRequire() {
            assertThat(AutonomyLevel.SUPERVISED.alwaysRequiresApproval()).isFalse();
            assertThat(AutonomyLevel.BOUNDED_AUTONOMOUS.alwaysRequiresApproval()).isFalse();
            assertThat(AutonomyLevel.AUTONOMOUS.alwaysRequiresApproval()).isFalse();
        }
    }

    @Nested
    @DisplayName("canActAutonomously")
    class CanActAutonomously {

        @Test
        @DisplayName("ADVISORY never acts autonomously regardless of confidence")
        void advisoryNeverActs() {
            assertThat(AutonomyLevel.ADVISORY.canActAutonomously(1.0, 0.0)).isFalse();
        }

        @Test
        @DisplayName("DRAFT never acts autonomously regardless of confidence")
        void draftNeverActs() {
            assertThat(AutonomyLevel.DRAFT.canActAutonomously(1.0, 0.0)).isFalse();
        }

        @Test
        @DisplayName("SUPERVISED acts when confidence >= threshold")
        void supervisedActsAboveThreshold() {
            assertThat(AutonomyLevel.SUPERVISED.canActAutonomously(0.9, 0.8)).isTrue();
            assertThat(AutonomyLevel.SUPERVISED.canActAutonomously(0.7, 0.8)).isFalse();
            assertThat(AutonomyLevel.SUPERVISED.canActAutonomously(0.8, 0.8)).isTrue();
        }

        @Test
        @DisplayName("BOUNDED_AUTONOMOUS acts when confidence >= threshold")
        void boundedAutonomousActsAboveThreshold() {
            assertThat(AutonomyLevel.BOUNDED_AUTONOMOUS.canActAutonomously(0.9, 0.8)).isTrue();
            assertThat(AutonomyLevel.BOUNDED_AUTONOMOUS.canActAutonomously(0.5, 0.8)).isFalse();
        }

        @Test
        @DisplayName("AUTONOMOUS acts when confidence >= threshold")
        void autonomousActsAboveThreshold() {
            assertThat(AutonomyLevel.AUTONOMOUS.canActAutonomously(0.9, 0.8)).isTrue();
            assertThat(AutonomyLevel.AUTONOMOUS.canActAutonomously(0.3, 0.8)).isFalse();
        }
    }

    @Nested
    @DisplayName("fromString — legacy alias resolution")
    class FromString {

        @ParameterizedTest
        @CsvSource({
                "advisory,       ADVISORY",
                "ADVISORY,       ADVISORY",
                "draft,          DRAFT",
                "DRAFT,          DRAFT",
                "supervised,     SUPERVISED",
                "SUPERVISED,     SUPERVISED",
                "semi-autonomous,SUPERVISED",
                "semi_autonomous,SUPERVISED",
                "bounded-autonomous,BOUNDED_AUTONOMOUS",
                "bounded_autonomous,BOUNDED_AUTONOMOUS",
                "BOUNDED_AUTONOMOUS,BOUNDED_AUTONOMOUS",
                "autonomous,     AUTONOMOUS",
                "AUTONOMOUS,     AUTONOMOUS",
                "manual,         DRAFT",
                "MANUAL,         DRAFT",
                "assisted,       DRAFT"
        })
        @DisplayName("should resolve canonical and legacy values")
        void shouldResolveKnownValues(String input, String expected) {
            assertThat(AutonomyLevel.fromString(input))
                    .isEqualTo(AutonomyLevel.valueOf(expected.trim()));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return null for null or blank input")
        void shouldReturnNullForBlank(String input) {
            assertThat(AutonomyLevel.fromString(input)).isNull();
        }

        @Test
        @DisplayName("should return null for unrecognized value")
        void shouldReturnNullForUnrecognized() {
            assertThat(AutonomyLevel.fromString("unknown-level")).isNull();
        }
    }
}
