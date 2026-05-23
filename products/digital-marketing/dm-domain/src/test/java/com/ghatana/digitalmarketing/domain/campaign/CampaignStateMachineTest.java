/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.domain.campaign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CampaignStateMachine.
 * 
 * @doc.type class
 * @doc.purpose Validates campaign lifecycle state machine transitions
 * @doc.layer product
 * @doc.pattern StateMachineTest
 */
@DisplayName("dm-001: Campaign Lifecycle State Machine Tests")
class CampaignStateMachineTest {

    private final CampaignStateMachine stateMachine = new CampaignStateMachine();

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        @DisplayName("DRAFT → PENDING_APPROVAL is valid")
        void draftToPendingApproval() {
            assertTrue(stateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.PENDING_APPROVAL));
        }

        @Test
        @DisplayName("PENDING_APPROVAL → APPROVED is valid")
        void pendingApprovalToApproved() {
            assertTrue(stateMachine.canTransition(CampaignStatus.PENDING_APPROVAL, CampaignStatus.APPROVED));
        }

        @Test
        @DisplayName("APPROVED → PENDING_LAUNCH is valid")
        void approvedToPendingLaunch() {
            assertTrue(stateMachine.canTransition(CampaignStatus.APPROVED, CampaignStatus.PENDING_LAUNCH));
        }

        @Test
        @DisplayName("PENDING_LAUNCH → LAUNCH_RUNNING is valid")
        void pendingLaunchToLaunchRunning() {
            assertTrue(stateMachine.canTransition(CampaignStatus.PENDING_LAUNCH, CampaignStatus.LAUNCH_RUNNING));
        }

        @Test
        @DisplayName("PENDING_LAUNCH → EXTERNAL_EXECUTION_BLOCKED is valid")
        void pendingLaunchToExternalExecutionBlocked() {
            assertTrue(stateMachine.canTransition(
                CampaignStatus.PENDING_LAUNCH,
                CampaignStatus.EXTERNAL_EXECUTION_BLOCKED
            ));
        }

        @Test
        @DisplayName("PENDING_LAUNCH → LAUNCH_FAILED is valid")
        void pendingLaunchToLaunchFailed() {
            assertTrue(stateMachine.canTransition(CampaignStatus.PENDING_LAUNCH, CampaignStatus.LAUNCH_FAILED));
        }

        @Test
        @DisplayName("LAUNCH_RUNNING → LAUNCHED is valid")
        void launchRunningToLaunched() {
            assertTrue(stateMachine.canTransition(CampaignStatus.LAUNCH_RUNNING, CampaignStatus.LAUNCHED));
        }

        @Test
        @DisplayName("LAUNCHED → PAUSED is valid")
        void launchedToPaused() {
            assertTrue(stateMachine.canTransition(CampaignStatus.LAUNCHED, CampaignStatus.PAUSED));
        }

        @Test
        @DisplayName("LAUNCHED → COMPLETED is valid")
        void launchedToCompleted() {
            assertTrue(stateMachine.canTransition(CampaignStatus.LAUNCHED, CampaignStatus.COMPLETED));
        }

        @Test
        @DisplayName("PAUSED → COMPLETED is valid")
        void pausedToCompleted() {
            assertTrue(stateMachine.canTransition(CampaignStatus.PAUSED, CampaignStatus.COMPLETED));
        }

        @Test
        @DisplayName("COMPLETED → ARCHIVED is valid")
        void completedToArchived() {
            assertTrue(stateMachine.canTransition(CampaignStatus.COMPLETED, CampaignStatus.ARCHIVED));
        }

        @Test
        @DisplayName("LAUNCH_FAILED → ROLLED_BACK is valid")
        void launchFailedToRolledBack() {
            assertTrue(stateMachine.canTransition(CampaignStatus.LAUNCH_FAILED, CampaignStatus.ROLLED_BACK));
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("Same state transition is invalid")
        void sameStateTransition() {
            assertFalse(stateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.DRAFT));
        }

        @Test
        @DisplayName("DRAFT → APPROVED is invalid (skipping PENDING_APPROVAL)")
        void draftToApproved() {
            assertFalse(stateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.APPROVED));
        }

        @Test
        @DisplayName("PENDING_APPROVAL → PENDING_LAUNCH is invalid (skipping APPROVED)")
        void pendingApprovalToPendingLaunch() {
            assertFalse(stateMachine.canTransition(CampaignStatus.PENDING_APPROVAL, CampaignStatus.PENDING_LAUNCH));
        }

        @Test
        @DisplayName("DRAFT → LAUNCHED is invalid (skipping multiple states)")
        void draftToLaunched() {
            assertFalse(stateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.LAUNCHED));
        }

        @Test
        @DisplayName("ARCHIVED → DRAFT is invalid (no reverse from terminal)")
        void archivedToDraft() {
            assertFalse(stateMachine.canTransition(CampaignStatus.ARCHIVED, CampaignStatus.DRAFT));
        }

        @Test
        @DisplayName("ROLLED_BACK → DRAFT is invalid (no reverse from terminal)")
        void rolledBackToDraft() {
            assertFalse(stateMachine.canTransition(CampaignStatus.ROLLED_BACK, CampaignStatus.DRAFT));
        }

        @Test
        @DisplayName("EXTERNAL_EXECUTION_BLOCKED has no outgoing transitions")
        void externalExecutionBlockedHasNoOutgoing() {
            assertEquals(0, stateMachine.getValidNextStates(CampaignStatus.EXTERNAL_EXECUTION_BLOCKED).size());
        }

        @Test
        @DisplayName("Null from state is invalid")
        void nullFromState() {
            assertFalse(stateMachine.canTransition(null, CampaignStatus.PENDING_APPROVAL));
        }

        @Test
        @DisplayName("Null to state is invalid")
        void nullToState() {
            assertFalse(stateMachine.canTransition(CampaignStatus.DRAFT, null));
        }
    }

    @Nested
    @DisplayName("Terminal states")
    class TerminalStates {

        @Test
        @DisplayName("ARCHIVED is a terminal state")
        void archivedIsTerminal() {
            assertTrue(stateMachine.isTerminal(CampaignStatus.ARCHIVED));
        }

        @Test
        @DisplayName("ROLLED_BACK is a terminal state")
        void rolledBackIsTerminal() {
            assertTrue(stateMachine.isTerminal(CampaignStatus.ROLLED_BACK));
        }

        @Test
        @DisplayName("EXTERNAL_EXECUTION_BLOCKED is a terminal state")
        void externalExecutionBlockedIsTerminal() {
            assertTrue(stateMachine.isTerminal(CampaignStatus.EXTERNAL_EXECUTION_BLOCKED));
        }

        @Test
        @DisplayName("DRAFT is not a terminal state")
        void draftIsNotTerminal() {
            assertFalse(stateMachine.isTerminal(CampaignStatus.DRAFT));
        }

        @Test
        @DisplayName("LAUNCHED is not a terminal state")
        void launchedIsNotTerminal() {
            assertFalse(stateMachine.isTerminal(CampaignStatus.LAUNCHED));
        }
    }

    @Nested
    @DisplayName("Transition execution")
    class TransitionExecution {

        @Test
        @DisplayName("Valid transition executes without exception")
        void validTransitionExecutes() {
            assertDoesNotThrow(() -> stateMachine.transition(CampaignStatus.DRAFT, CampaignStatus.PENDING_APPROVAL));
        }

        @Test
        @DisplayName("Invalid transition throws IllegalStateException")
        void invalidTransitionThrows() {
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(CampaignStatus.DRAFT, CampaignStatus.APPROVED)
            );
            assertTrue(exception.getMessage().contains("Invalid campaign status transition"));
            assertTrue(exception.getMessage().contains("DRAFT"));
            assertTrue(exception.getMessage().contains("APPROVED"));
        }

        @Test
        @DisplayName("Get valid next states returns correct set")
        void getValidNextStates() {
            var nextStates = stateMachine.getValidNextStates(CampaignStatus.LAUNCHED);
            assertEquals(2, nextStates.size());
            assertTrue(nextStates.contains(CampaignStatus.PAUSED));
            assertTrue(nextStates.contains(CampaignStatus.COMPLETED));
        }
    }
}
