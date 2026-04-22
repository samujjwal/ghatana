/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("AutonomyLevel (P0/WP1) [GH-90000]")
class AutonomyLevelTest {

    @Nested
    @DisplayName("canonical values [GH-90000]")
    class CanonicalValues {

        @Test
        @DisplayName("should have exactly 5 canonical tiers [GH-90000]")
        void shouldHaveFiveCanonicalTiers() { // GH-90000
            assertThat(AutonomyLevel.values()).hasSize(5); // GH-90000
            assertThat(AutonomyLevel.values()).containsExactly( // GH-90000
                    AutonomyLevel.ADVISORY,
                    AutonomyLevel.DRAFT,
                    AutonomyLevel.SUPERVISED,
                    AutonomyLevel.BOUNDED_AUTONOMOUS,
                    AutonomyLevel.AUTONOMOUS);
        }

        @Test
        @DisplayName("ADVISORY and DRAFT always require approval [GH-90000]")
        void advisoryAndDraftAlwaysRequireApproval() { // GH-90000
            assertThat(AutonomyLevel.ADVISORY.alwaysRequiresApproval()).isTrue(); // GH-90000
            assertThat(AutonomyLevel.DRAFT.alwaysRequiresApproval()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SUPERVISED and above do not always require approval [GH-90000]")
        void supervisedAndAboveDoNotAlwaysRequire() { // GH-90000
            assertThat(AutonomyLevel.SUPERVISED.alwaysRequiresApproval()).isFalse(); // GH-90000
            assertThat(AutonomyLevel.BOUNDED_AUTONOMOUS.alwaysRequiresApproval()).isFalse(); // GH-90000
            assertThat(AutonomyLevel.AUTONOMOUS.alwaysRequiresApproval()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("canActAutonomously [GH-90000]")
    class CanActAutonomously {

        @Test
        @DisplayName("ADVISORY never acts autonomously regardless of confidence [GH-90000]")
        void advisoryNeverActs() { // GH-90000
            assertThat(AutonomyLevel.ADVISORY.canActAutonomously(1.0, 0.0)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DRAFT never acts autonomously regardless of confidence [GH-90000]")
        void draftNeverActs() { // GH-90000
            assertThat(AutonomyLevel.DRAFT.canActAutonomously(1.0, 0.0)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("SUPERVISED acts when confidence >= threshold [GH-90000]")
        void supervisedActsAboveThreshold() { // GH-90000
            assertThat(AutonomyLevel.SUPERVISED.canActAutonomously(0.9, 0.8)).isTrue(); // GH-90000
            assertThat(AutonomyLevel.SUPERVISED.canActAutonomously(0.7, 0.8)).isFalse(); // GH-90000
            assertThat(AutonomyLevel.SUPERVISED.canActAutonomously(0.8, 0.8)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("BOUNDED_AUTONOMOUS acts when confidence >= threshold [GH-90000]")
        void boundedAutonomousActsAboveThreshold() { // GH-90000
            assertThat(AutonomyLevel.BOUNDED_AUTONOMOUS.canActAutonomously(0.9, 0.8)).isTrue(); // GH-90000
            assertThat(AutonomyLevel.BOUNDED_AUTONOMOUS.canActAutonomously(0.5, 0.8)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("AUTONOMOUS acts when confidence >= threshold [GH-90000]")
        void autonomousActsAboveThreshold() { // GH-90000
            assertThat(AutonomyLevel.AUTONOMOUS.canActAutonomously(0.9, 0.8)).isTrue(); // GH-90000
            assertThat(AutonomyLevel.AUTONOMOUS.canActAutonomously(0.3, 0.8)).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("fromString — legacy alias resolution [GH-90000]")
    class FromString {

        @ParameterizedTest
        @CsvSource({ // GH-90000
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
        @DisplayName("should resolve canonical and legacy values [GH-90000]")
        void shouldResolveKnownValues(String input, String expected) { // GH-90000
            assertThat(AutonomyLevel.fromString(input)) // GH-90000
                    .isEqualTo(AutonomyLevel.valueOf(expected.trim())); // GH-90000
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return null for null or blank input [GH-90000]")
        void shouldReturnNullForBlank(String input) { // GH-90000
            assertThat(AutonomyLevel.fromString(input)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return null for unrecognized value [GH-90000]")
        void shouldReturnNullForUnrecognized() { // GH-90000
            assertThat(AutonomyLevel.fromString("unknown-level [GH-90000]")).isNull();
        }
    }
}
