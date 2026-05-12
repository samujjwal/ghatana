/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MasteryTransition.
 *
 * @doc.type class
 * @doc.purpose Tests for MasteryTransition
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("MasteryTransition Tests")
class MasteryTransitionTest {

    @Test
    @DisplayName("Should create manual transition with generated ID")
    void shouldCreateManualTransitionWithGeneratedId() {
        MasteryTransition transition = MasteryTransition.manual(
                "mastery-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Evaluation passed",
                "user-123"
        );

        assertThat(transition.transitionId()).isNotNull();
        assertThat(transition.transitionId()).isNotEmpty();
        assertThat(transition.masteryId()).isEqualTo("mastery-123");
        assertThat(transition.fromState()).isEqualTo(MasteryState.PRACTICED);
        assertThat(transition.toState()).isEqualTo(MasteryState.COMPETENT);
        assertThat(transition.reason()).isEqualTo("Evaluation passed");
        assertThat(transition.initiatedBy()).isEqualTo("user-123");
        assertThat(transition.transitionedAt()).isNotNull();
        assertThat(transition.evidenceRefs()).isEmpty();
        assertThat(transition.metadata()).isEmpty();
    }

    @Test
    @DisplayName("Should create automatic transition with system initiator")
    void shouldCreateAutomaticTransitionWithSystemInitiator() {
        MasteryTransition transition = MasteryTransition.automatic(
                "mastery-123",
                MasteryState.COMPETENT,
                MasteryState.MASTERED,
                "Regression tests passed"
        );

        assertThat(transition.transitionId()).isNotNull();
        assertThat(transition.masteryId()).isEqualTo("mastery-123");
        assertThat(transition.fromState()).isEqualTo(MasteryState.COMPETENT);
        assertThat(transition.toState()).isEqualTo(MasteryState.MASTERED);
        assertThat(transition.reason()).isEqualTo("Regression tests passed");
        assertThat(transition.initiatedBy()).isEqualTo("system");
        assertThat(transition.transitionedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create transition with custom timestamp")
    void shouldCreateTransitionWithCustomTimestamp() {
        Instant customTime = Instant.parse("2026-01-01T00:00:00Z");

        MasteryTransition transition = new MasteryTransition(
                "transition-123",
                "mastery-123",
                MasteryState.UNKNOWN,
                MasteryState.OBSERVED,
                "First observation",
                "system",
                customTime,
                Map.of("trace-1", "trace-ref-1"),
                Map.of("source", "manual-review")
        );

        assertThat(transition.transitionedAt()).isEqualTo(customTime);
    }

    @Test
    @DisplayName("Should copy evidence refs and metadata")
    void shouldCopyEvidenceRefsAndMetadata() {
        Map<String, String> evidenceRefs = Map.of(
                "eval-1", "eval-ref-1",
                "trace-2", "trace-ref-2"
        );
        Map<String, String> metadata = Map.of(
                "reviewer", "user-123",
                "confidence", "0.95"
        );

        MasteryTransition transition = new MasteryTransition(
                "transition-123",
                "mastery-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Evaluation passed",
                "system",
                Instant.now(),
                evidenceRefs,
                metadata
        );

        assertThat(transition.evidenceRefs()).isEqualTo(evidenceRefs);
        assertThat(transition.metadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("Should create transition for obsolescence")
    void shouldCreateTransitionForObsolescence() {
        MasteryTransition transition = MasteryTransition.automatic(
                "mastery-123",
                MasteryState.MASTERED,
                MasteryState.OBSOLETE,
                "API contract changed"
        );

        assertThat(transition.fromState()).isEqualTo(MasteryState.MASTERED);
        assertThat(transition.toState()).isEqualTo(MasteryState.OBSOLETE);
        assertThat(transition.reason()).contains("API contract");
    }

    @Test
    @DisplayName("Should create transition for retirement")
    void shouldCreateTransitionForRetirement() {
        MasteryTransition transition = MasteryTransition.manual(
                "mastery-123",
                MasteryState.OBSOLETE,
                MasteryState.RETIRED,
                "No longer needed",
                "admin"
        );

        assertThat(transition.fromState()).isEqualTo(MasteryState.OBSOLETE);
        assertThat(transition.toState()).isEqualTo(MasteryState.RETIRED);
        assertThat(transition.initiatedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Should create transition for quarantine")
    void shouldCreateTransitionForQuarantine() {
        MasteryTransition transition = MasteryTransition.automatic(
                "mastery-123",
                MasteryState.COMPETENT,
                MasteryState.QUARANTINED,
                "Safety evaluation failed"
        );

        assertThat(transition.fromState()).isEqualTo(MasteryState.COMPETENT);
        assertThat(transition.toState()).isEqualTo(MasteryState.QUARANTINED);
        assertThat(transition.reason()).contains("Safety");
    }

    @Test
    @DisplayName("Should create transition for maintenance mode")
    void shouldCreateTransitionForMaintenanceMode() {
        MasteryTransition transition = MasteryTransition.automatic(
                "mastery-123",
                MasteryState.MASTERED,
                MasteryState.MAINTENANCE_ONLY,
                "New version available"
        );

        assertThat(transition.fromState()).isEqualTo(MasteryState.MASTERED);
        assertThat(transition.toState()).isEqualTo(MasteryState.MAINTENANCE_ONLY);
        assertThat(transition.reason()).contains("version");
    }
}
