/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void errorJson_createsValidJsonError() { 
        String json = IncidentServiceLauncher.errorJson(400, "MISSING_TENANT_ID", "tenantId is required"); 
        assertThat(json).contains("\"status\":400"); 
        assertThat(json).contains("\"code\":\"MISSING_TENANT_ID\""); 
        assertThat(json).contains("\"message\":\"tenantId is required\""); 
    }

    @Test
    @DisplayName("errorJson creates valid JSON error with details")
    void errorJson_createsValidJsonErrorWithDetails() { 
        String json = IncidentServiceLauncher.errorJson(500, "INTERNAL_ERROR", "Failed to activate", "Database connection failed"); 
        assertThat(json).contains("\"status\":500"); 
        assertThat(json).contains("\"code\":\"INTERNAL_ERROR\""); 
        assertThat(json).contains("\"message\":\"Failed to activate\""); 
        assertThat(json).contains("\"details\":\"Database connection failed\""); 
    }

    @Test
    @DisplayName("errorJson handles special characters in message")
    void errorJson_handlesSpecialCharacters() { 
        String json = IncidentServiceLauncher.errorJson(400, "INVALID_INPUT", "Field \"name\" contains invalid chars: <>&\""); 
        assertThat(json).contains("\"status\":400"); 
        assertThat(json).contains("\"code\":\"INVALID_INPUT\""); 
        assertThat(json).contains("\"message\""); 
    }
}
