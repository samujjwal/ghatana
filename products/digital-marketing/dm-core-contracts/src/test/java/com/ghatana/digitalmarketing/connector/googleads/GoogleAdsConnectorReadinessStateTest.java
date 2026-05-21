/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.connector.googleads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GoogleAdsConnectorReadinessState Tests")
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
}
