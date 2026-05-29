/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.connector.googleads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Validates connector readiness state machine for Google Ads
 * @doc.layer product
 * @doc.pattern StateMachineTest
 */
@DisplayName("dm-005: Connector Readiness State Machine Tests")
class GoogleAdsConnectorReadinessStateTest {

    @Test
    @DisplayName("enum exposes expected readiness states")
    void shouldExposeAllReadinessStates() {
        GoogleAdsConnectorReadinessState[] values = GoogleAdsConnectorReadinessState.values();

        assertEquals(7, values.length);
        assertTrue(EnumSet.allOf(GoogleAdsConnectorReadinessState.class)
                .contains(GoogleAdsConnectorReadinessState.READY));
        assertTrue(EnumSet.allOf(GoogleAdsConnectorReadinessState.class)
                .contains(GoogleAdsConnectorReadinessState.NOT_READY));
        assertTrue(EnumSet.allOf(GoogleAdsConnectorReadinessState.class)
                .contains(GoogleAdsConnectorReadinessState.AUTH_FAILED));
        assertTrue(EnumSet.allOf(GoogleAdsConnectorReadinessState.class)
                .contains(GoogleAdsConnectorReadinessState.RATE_LIMITED));
        assertTrue(EnumSet.allOf(GoogleAdsConnectorReadinessState.class)
                .contains(GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED));
        assertTrue(EnumSet.allOf(GoogleAdsConnectorReadinessState.class)
                .contains(GoogleAdsConnectorReadinessState.PUBLISH_FAILED));
        assertTrue(EnumSet.allOf(GoogleAdsConnectorReadinessState.class)
                .contains(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED));
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        @Test
        @DisplayName("NOT_READY can transition to READY")
        void notReadyToReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("READY can transition to RATE_LIMITED")
        void readyToRateLimited() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.RATE_LIMITED));
        }

        @Test
        @DisplayName("READY can transition to AUTH_FAILED")
        void readyToAuthFailed() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.AUTH_FAILED));
        }

        @Test
        @DisplayName("READY can transition to REMOTE_VALIDATION_FAILED")
        void readyToRemoteValidationFailed() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED));
        }

        @Test
        @DisplayName("READY can transition to PUBLISH_FAILED")
        void readyToPublishFailed() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.PUBLISH_FAILED));
        }

        @Test
        @DisplayName("READY can transition to ENVIRONMENT_BLOCKED")
        void readyToEnvironmentBlocked() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED));
        }

        @Test
        @DisplayName("AUTH_FAILED can transition to READY with re-authentication")
        void authFailedCanTransitionToReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.AUTH_FAILED, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("RATE_LIMITED can transition to READY when rate limit expires")
        void rateLimitedCanTransitionToReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.RATE_LIMITED, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("REMOTE_VALIDATION_FAILED can transition to READY when validation passes")
        void remoteValidationFailedCanTransitionToReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("PUBLISH_FAILED can transition to READY when retry succeeds")
        void publishFailedCanTransitionToReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.PUBLISH_FAILED, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("ENVIRONMENT_BLOCKED can transition to READY when unblocked")
        void environmentBlockedCanTransitionToReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.canTransition(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("NOT_READY cannot transition to failure states directly")
        void notReadyCannotTransitionToFailureStates() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.AUTH_FAILED));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.RATE_LIMITED));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.PUBLISH_FAILED));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED));
        }

        @Test
        @DisplayName("Failure states cannot transition to other failure states")
        void failureStatesCannotTransitionToOtherFailureStates() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.AUTH_FAILED, GoogleAdsConnectorReadinessState.RATE_LIMITED));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.RATE_LIMITED, GoogleAdsConnectorReadinessState.AUTH_FAILED));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED, GoogleAdsConnectorReadinessState.PUBLISH_FAILED));
        }

        @Test
        @DisplayName("Same state transition is not allowed")
        void sameStateTransitionNotAllowed() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.READY));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.NOT_READY));
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.AUTH_FAILED, GoogleAdsConnectorReadinessState.AUTH_FAILED));
        }

        @Test
        @DisplayName("Null from state returns false")
        void nullFromStateReturnsFalse() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertFalse(machine.canTransition(null, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("Null to state returns false")
        void nullToStateReturnsFalse() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.READY, null));
        }

        @Test
        @DisplayName("Both null states returns false")
        void bothNullStatesReturnsFalse() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertFalse(machine.canTransition(null, null));
        }
    }

    @Nested
    @DisplayName("State transition execution")
    class TransitionExecution {

        @Test
        @DisplayName("Valid transition executes without exception")
        void validTransitionExecutes() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            machine.transition(GoogleAdsConnectorReadinessState.NOT_READY, GoogleAdsConnectorReadinessState.READY);
        }

        @Test
        @DisplayName("Invalid transition throws IllegalStateException")
        void invalidTransitionThrows() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> machine.transition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.NOT_READY)
            );
            assertTrue(exception.getMessage().contains("Invalid connector readiness state transition"));
        }

        @Test
        @DisplayName("Same state transition throws IllegalStateException")
        void sameStateTransitionThrows() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> machine.transition(GoogleAdsConnectorReadinessState.READY, GoogleAdsConnectorReadinessState.READY)
            );
            assertTrue(exception.getMessage().contains("Invalid connector readiness state transition"));
        }
    }

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("Initial state is NOT_READY")
        void initialStateIsNotReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertEquals(GoogleAdsConnectorReadinessState.NOT_READY, machine.getInitialState());
        }
    }

    @Nested
    @DisplayName("Valid next states")
    class ValidNextStates {

        @Test
        @DisplayName("NOT_READY has one valid next state")
        void notReadyHasOneValidNextState() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertEquals(1, machine.getValidNextStates(GoogleAdsConnectorReadinessState.NOT_READY).size());
            assertTrue(machine.getValidNextStates(GoogleAdsConnectorReadinessState.NOT_READY).contains(GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("READY has five valid next states")
        void readyHasFiveValidNextStates() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertEquals(5, machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY).size());
            assertTrue(machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY).contains(GoogleAdsConnectorReadinessState.AUTH_FAILED));
            assertTrue(machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY).contains(GoogleAdsConnectorReadinessState.RATE_LIMITED));
            assertTrue(machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY).contains(GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED));
            assertTrue(machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY).contains(GoogleAdsConnectorReadinessState.PUBLISH_FAILED));
            assertTrue(machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY).contains(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED));
        }

        @Test
        @DisplayName("Failure states have one valid next state (READY)")
        void failureStatesHaveOneValidNextState() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertEquals(1, machine.getValidNextStates(GoogleAdsConnectorReadinessState.AUTH_FAILED).size());
            assertEquals(1, machine.getValidNextStates(GoogleAdsConnectorReadinessState.RATE_LIMITED).size());
            assertEquals(1, machine.getValidNextStates(GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED).size());
            assertEquals(1, machine.getValidNextStates(GoogleAdsConnectorReadinessState.PUBLISH_FAILED).size());
            assertEquals(1, machine.getValidNextStates(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED).size());
        }

        @Test
        @DisplayName("Valid next states returns immutable copy")
        void validNextStatesReturnsImmutableCopy() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            Set<GoogleAdsConnectorReadinessState> nextStates = machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY);
            int originalSize = nextStates.size();
            nextStates.add(GoogleAdsConnectorReadinessState.NOT_READY);
            assertEquals(originalSize, machine.getValidNextStates(GoogleAdsConnectorReadinessState.READY).size());
        }
    }
}
