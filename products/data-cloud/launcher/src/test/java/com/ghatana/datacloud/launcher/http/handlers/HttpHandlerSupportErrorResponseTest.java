/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.infrastructure.governance.http.dto.ErrorResponse;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Regression coverage for ErrorResponse integration in HttpHandlerSupport
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("HttpHandlerSupport ErrorResponse Integration")
class HttpHandlerSupportErrorResponseTest {

    private static final String ORIGIN = "http://localhost:3000";

    private final HttpHandlerSupport support = new HttpHandlerSupport( // GH-90000
        new ObjectMapper(), // GH-90000
        ORIGIN,
        "GET,POST,PUT,DELETE,OPTIONS",
        "Content-Type,X-Tenant-Id",
        true
    );

    @Test
    @DisplayName("errorResponse creates ApiResponse with error envelope")
    void errorResponseCreatesApiResponseWithErrorEnvelope() { // GH-90000
        HttpResponse response = support.errorResponse(400, "Invalid request"); // GH-90000
        
        assertThat(response).isNotNull(); // GH-90000
        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("envelopeResponse with ApiResponse preserves error structure")
    void envelopeResponseWithApiResponsePreservesErrorStructure() { // GH-90000
        ApiResponse apiResponse = ApiResponse.error("VALIDATION_ERROR", "Invalid input", "test-tenant", "test-request"); // GH-90000
        HttpResponse response = support.envelopeResponse(apiResponse, new ObjectMapper()); // GH-90000
        
        assertThat(response).isNotNull(); // GH-90000
        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("errorResponse with custom status code uses provided code")
    void errorResponseWithCustomStatusCodeUsesProvidedCode() { // GH-90000
        HttpResponse response = support.errorResponse(404, "Not found"); // GH-90000
        
        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("errorResponse with 503 uses service unavailable status")
    void errorResponseWith503UsesServiceUnavailableStatus() { // GH-90000
        HttpResponse response = support.errorResponse(503, "Service unavailable"); // GH-90000
        
        assertThat(response.getCode()).isEqualTo(503); // GH-90000
    }

    @Test
    @DisplayName("errorResponse with 429 uses rate limit status")
    void errorResponseWith429UsesRateLimitStatus() { // GH-90000
        HttpResponse response = support.errorResponse(429, "Rate limit exceeded"); // GH-90000
        
        assertThat(response.getCode()).isEqualTo(429); // GH-90000
    }

    @Test
    @DisplayName("errorResponse with 401 uses unauthorized status")
    void errorResponseWith401UsesUnauthorizedStatus() { // GH-90000
        HttpResponse response = support.errorResponse(401, "Unauthorized"); // GH-90000
        
        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }

    @Test
    @DisplayName("errorResponse with 403 uses forbidden status")
    void errorResponseWith403UsesForbiddenStatus() { // GH-90000
        HttpResponse response = support.errorResponse(403, "Forbidden"); // GH-90000
        
        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("errorResponse with 409 uses conflict status")
    void errorResponseWith409UsesConflictStatus() { // GH-90000
        HttpResponse response = support.errorResponse(409, "Conflict"); // GH-90000
        
        assertThat(response.getCode()).isEqualTo(409); // GH-90000
    }

    @Test
    @DisplayName("errorResponse with 500 uses internal server error status")
    void errorResponseWith500UsesInternalServerErrorStatus() { // GH-90000
        HttpResponse response = support.errorResponse(500, "Internal server error"); // GH-90000
        
        assertThat(response.getCode()).isEqualTo(500); // GH-90000
    }
}
