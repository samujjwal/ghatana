/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PipelineLifecycle state machine.
 * 
 * P7.1: Verify pipeline lifecycle states and transitions.
 * 
 * @doc.type test
 * @doc.purpose Verify pipeline lifecycle state machine
 * @doc.layer product
 */
@DisplayName("PipelineLifecycle Tests")
class PipelineLifecycleTest {

    @Test
    @DisplayName("DRAFT can transition to VALIDATED and ARCHIVED")
    void draftValidTransitions() {
        assertTrue(PipelineLifecycle.DRAFT.canTransitionTo(PipelineLifecycle.VALIDATED));
        assertTrue(PipelineLifecycle.DRAFT.canTransitionTo(PipelineLifecycle.ARCHIVED));
        assertFalse(PipelineLifecycle.DRAFT.canTransitionTo(PipelineLifecycle.ACTIVE));
        assertFalse(PipelineLifecycle.DRAFT.canTransitionTo(PipelineLifecycle.PAUSED));
    }

    @Test
    @DisplayName("VALIDATED can transition to ACTIVE and ARCHIVED")
    void validatedValidTransitions() {
        assertTrue(PipelineLifecycle.VALIDATED.canTransitionTo(PipelineLifecycle.ACTIVE));
        assertTrue(PipelineLifecycle.VALIDATED.canTransitionTo(PipelineLifecycle.ARCHIVED));
        assertFalse(PipelineLifecycle.VALIDATED.canTransitionTo(PipelineLifecycle.DRAFT));
    }

    @Test
    @DisplayName("ACTIVE can transition to PAUSED, COMPLETED, FAILED, and ARCHIVED")
    void activeValidTransitions() {
        assertTrue(PipelineLifecycle.ACTIVE.canTransitionTo(PipelineLifecycle.PAUSED));
        assertTrue(PipelineLifecycle.ACTIVE.canTransitionTo(PipelineLifecycle.COMPLETED));
        assertTrue(PipelineLifecycle.ACTIVE.canTransitionTo(PipelineLifecycle.FAILED));
        assertTrue(PipelineLifecycle.ACTIVE.canTransitionTo(PipelineLifecycle.ARCHIVED));
        assertFalse(PipelineLifecycle.ACTIVE.canTransitionTo(PipelineLifecycle.DRAFT));
    }

    @Test
    @DisplayName("PAUSED can transition to ACTIVE and ARCHIVED")
    void pausedValidTransitions() {
        assertTrue(PipelineLifecycle.PAUSED.canTransitionTo(PipelineLifecycle.ACTIVE));
        assertTrue(PipelineLifecycle.PAUSED.canTransitionTo(PipelineLifecycle.ARCHIVED));
        assertFalse(PipelineLifecycle.PAUSED.canTransitionTo(PipelineLifecycle.COMPLETED));
    }

    @Test
    @DisplayName("FAILED can transition to DRAFT and ARCHIVED")
    void failedValidTransitions() {
        assertTrue(PipelineLifecycle.FAILED.canTransitionTo(PipelineLifecycle.DRAFT));
        assertTrue(PipelineLifecycle.FAILED.canTransitionTo(PipelineLifecycle.ARCHIVED));
        assertFalse(PipelineLifecycle.FAILED.canTransitionTo(PipelineLifecycle.ACTIVE));
    }

    @Test
    @DisplayName("COMPLETED can only transition to ARCHIVED")
    void completedValidTransitions() {
        assertTrue(PipelineLifecycle.COMPLETED.canTransitionTo(PipelineLifecycle.ARCHIVED));
        assertFalse(PipelineLifecycle.COMPLETED.canTransitionTo(PipelineLifecycle.DRAFT));
    }

    @Test
    @DisplayName("ARCHIVED is terminal with no transitions")
    void archivedIsTerminal() {
        assertTrue(PipelineLifecycle.ARCHIVED.isTerminal());
        assertFalse(PipelineLifecycle.ARCHIVED.canTransitionTo(PipelineLifecycle.DRAFT));
    }

    @Test
    @DisplayName("Only ACTIVE state can execute")
    void onlyActiveCanExecute() {
        assertTrue(PipelineLifecycle.ACTIVE.canExecute());
        assertFalse(PipelineLifecycle.DRAFT.canExecute());
        assertFalse(PipelineLifecycle.VALIDATED.canExecute());
        assertFalse(PipelineLifecycle.PAUSED.canExecute());
        assertFalse(PipelineLifecycle.COMPLETED.canExecute());
        assertFalse(PipelineLifecycle.FAILED.canExecute());
        assertFalse(PipelineLifecycle.ARCHIVED.canExecute());
    }

    @Test
    @DisplayName("Only DRAFT and FAILED can be modified")
    void onlyDraftAndFailedCanModify() {
        assertTrue(PipelineLifecycle.DRAFT.canModify());
        assertTrue(PipelineLifecycle.FAILED.canModify());
        assertFalse(PipelineLifecycle.VALIDATED.canModify());
        assertFalse(PipelineLifecycle.ACTIVE.canModify());
        assertFalse(PipelineLifecycle.PAUSED.canModify());
        assertFalse(PipelineLifecycle.COMPLETED.canModify());
        assertFalse(PipelineLifecycle.ARCHIVED.canModify());
    }

    @Test
    @DisplayName("transitionTo succeeds for valid transitions")
    void transitionToSucceedsForValid() {
        assertEquals(PipelineLifecycle.VALIDATED, PipelineLifecycle.DRAFT.transitionTo(PipelineLifecycle.VALIDATED));
        assertEquals(PipelineLifecycle.ACTIVE, PipelineLifecycle.VALIDATED.transitionTo(PipelineLifecycle.ACTIVE));
    }

    @Test
    @DisplayName("transitionTo throws for invalid transitions")
    void transitionToThrowsForInvalid() {
        assertThrows(IllegalStateException.class, () -> 
            PipelineLifecycle.DRAFT.transitionTo(PipelineLifecycle.ACTIVE));
        assertThrows(IllegalStateException.class, () -> 
            PipelineLifecycle.ACTIVE.transitionTo(PipelineLifecycle.DRAFT));
    }

    @Test
    @DisplayName("canDelete returns true for appropriate states")
    void canDeleteReturnsCorrectly() {
        assertTrue(PipelineLifecycle.DRAFT.canDelete());
        assertTrue(PipelineLifecycle.FAILED.canDelete());
        assertTrue(PipelineLifecycle.COMPLETED.canDelete());
        assertTrue(PipelineLifecycle.ARCHIVED.canDelete());
        assertFalse(PipelineLifecycle.VALIDATED.canDelete());
        assertFalse(PipelineLifecycle.ACTIVE.canDelete());
        assertFalse(PipelineLifecycle.PAUSED.canDelete());
    }
}
