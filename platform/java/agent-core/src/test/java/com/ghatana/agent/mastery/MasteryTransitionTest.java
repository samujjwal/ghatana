/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Evaluation passed",
                "user-123"
        );

        assertNotNull(transition.transitionId());
        assertFalse(transition.transitionId().isEmpty());
        assertEquals("mastery-123", transition.masteryId());
        assertEquals(MasteryState.PRACTICED, transition.fromState());
        assertEquals(MasteryState.COMPETENT, transition.toState());
        assertEquals("Evaluation passed", transition.reason());
        assertEquals("user-123", transition.initiatedBy());
        assertNotNull(transition.transitionedAt());
        assertTrue(transition.evidenceRefs().isEmpty());
        assertTrue(transition.metadata().isEmpty());
    }

    @Test
    @DisplayName("Should create automatic transition with system initiator")
    void shouldCreateAutomaticTransitionWithSystemInitiator() {
        MasteryTransition transition = MasteryTransition.automatic(
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Auto-promotion"
        );

        assertNotNull(transition.transitionId());
        assertEquals("mastery-123", transition.masteryId());
        assertEquals(MasteryState.PRACTICED, transition.fromState());
        assertEquals(MasteryState.COMPETENT, transition.toState());
        assertEquals("Auto-promotion", transition.reason());
        assertEquals("system", transition.initiatedBy());
        assertNotNull(transition.transitionedAt());
    }

    @Test
    @DisplayName("Should create transition with custom timestamp")
    void shouldCreateTransitionWithCustomTimestamp() {
        Instant customTime = Instant.parse("2026-01-01T00:00:00Z");

        MasteryTransition transition = new MasteryTransition(
                "transition-123",
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Custom transition",
                "user-123",
                customTime,
                Map.of("evidence-1", "ref-1"),
                Map.of("meta-1", "value-1")
        );

        assertEquals(customTime, transition.transitionedAt());
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
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Evaluation passed",
                "system",
                Instant.now(),
                evidenceRefs,
                metadata
        );

        assertEquals(evidenceRefs, transition.evidenceRefs());
        assertEquals(metadata, transition.metadata());
    }

    @Test
    @DisplayName("Should create transition for obsolescence")
    void shouldCreateTransitionForObsolescence() {
        MasteryTransition transition = MasteryTransition.automatic(
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Auto-promotion"
        );

        assertEquals(MasteryState.PRACTICED, transition.fromState());
        assertEquals(MasteryState.COMPETENT, transition.toState());
        assertTrue(transition.reason().contains("Auto-promotion"));
    }

    @Test
    @DisplayName("Should create transition for retirement")
    void shouldCreateTransitionForRetirement() {
        MasteryTransition transition = MasteryTransition.automatic(
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.OBSOLETE,
                MasteryState.RETIRED,
                "No longer needed"
        );

        assertEquals(MasteryState.OBSOLETE, transition.fromState());
        assertEquals(MasteryState.RETIRED, transition.toState());
        assertEquals("system", transition.initiatedBy());
    }

    @Test
    @DisplayName("Should create transition for quarantine")
    void shouldCreateTransitionForQuarantine() {
        MasteryTransition transition = MasteryTransition.automatic(
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.COMPETENT,
                MasteryState.QUARANTINED,
                "Safety evaluation failed"
        );

        assertEquals(MasteryState.COMPETENT, transition.fromState());
        assertEquals(MasteryState.QUARANTINED, transition.toState());
        assertTrue(transition.reason().contains("Safety"));
    }

    @Test
    @DisplayName("Should create transition for maintenance mode")
    void shouldCreateTransitionForMaintenanceMode() {
        MasteryTransition transition = MasteryTransition.automatic(
                "tenant-123",
                "mastery-123",
                "agent-123",
                "release-1.0.0",
                "skill-123",
                MasteryState.MASTERED,
                MasteryState.MAINTENANCE_ONLY,
                "New version available"
        );

        assertEquals(MasteryState.MASTERED, transition.fromState());
        assertEquals(MasteryState.MAINTENANCE_ONLY, transition.toState());
        assertTrue(transition.reason().contains("version"));
    }
}
