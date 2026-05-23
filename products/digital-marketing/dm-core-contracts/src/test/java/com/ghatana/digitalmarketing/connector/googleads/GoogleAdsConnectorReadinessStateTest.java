/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.connector.googleads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        @DisplayName("AUTH_FAILED cannot transition to READY without re-authentication")
        void authFailedCannotTransitionToReady() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertFalse(machine.canTransition(GoogleAdsConnectorReadinessState.AUTH_FAILED, GoogleAdsConnectorReadinessState.READY));
        }

        @Test
        @DisplayName("ENVIRONMENT_BLOCKED is a terminal state")
        void environmentBlockedIsTerminal() {
            GoogleAdsConnectorReadinessStateMachine machine = new GoogleAdsConnectorReadinessStateMachine();
            assertTrue(machine.isTerminal(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED));
        }
    }
}
