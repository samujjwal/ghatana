/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link IncidentServiceLauncher} helper methods.
 *
 * @doc.type class
 * @doc.purpose Unit tests for IncidentServiceLauncher helpers
 * @doc.layer shared-service
 * @doc.pattern Test
 */
@Tag("unit")
@DisplayName("IncidentServiceLauncher — unit tests")
class IncidentServiceLauncherTest {

    @Test
    @DisplayName("errorJson creates valid JSON error response")
    void errorJson_createsValidJsonError() { // GH-90000
        String json = IncidentServiceLauncher.errorJson(400, "MISSING_TENANT_ID", "tenantId is required"); // GH-90000
        assertThat(json).contains("\"status\":400"); // GH-90000
        assertThat(json).contains("\"code\":\"MISSING_TENANT_ID\""); // GH-90000
        assertThat(json).contains("\"message\":\"tenantId is required\""); // GH-90000
    }

    @Test
    @DisplayName("errorJson creates valid JSON error with details")
    void errorJson_createsValidJsonErrorWithDetails() { // GH-90000
        String json = IncidentServiceLauncher.errorJson(500, "INTERNAL_ERROR", "Failed to activate", "Database connection failed"); // GH-90000
        assertThat(json).contains("\"status\":500"); // GH-90000
        assertThat(json).contains("\"code\":\"INTERNAL_ERROR\""); // GH-90000
        assertThat(json).contains("\"message\":\"Failed to activate\""); // GH-90000
        assertThat(json).contains("\"details\":\"Database connection failed\""); // GH-90000
    }

    @Test
    @DisplayName("errorJson handles special characters in message")
    void errorJson_handlesSpecialCharacters() { // GH-90000
        String json = IncidentServiceLauncher.errorJson(400, "INVALID_INPUT", "Field \"name\" contains invalid chars: <>&\""); // GH-90000
        assertThat(json).contains("\"status\":400"); // GH-90000
        assertThat(json).contains("\"code\":\"INVALID_INPUT\""); // GH-90000
        assertThat(json).contains("\"message\""); // GH-90000
    }
}
