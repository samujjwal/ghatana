/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.ErrorResponse;
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

    private final HttpHandlerSupport support = new HttpHandlerSupport(
        new ObjectMapper(),
        ORIGIN,
        "GET,POST,PUT,DELETE,OPTIONS",
        "Content-Type,X-Tenant-Id",
        true
    );

    @Test
    @DisplayName("errorResponse creates ApiResponse with error envelope")
    void errorResponseCreatesApiResponseWithErrorEnvelope() {
        HttpResponse response = support.errorResponse(400, "Invalid request");
        
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("errorResponse with ApiResponse preserves error structure")
    void errorResponseWithApiResponsePreservesErrorStructure() {
        ApiResponse apiResponse = ApiResponse.error("VALIDATION_ERROR", "Invalid input");
        HttpResponse response = support.errorResponse(apiResponse);
        
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("errorResponse with custom status code uses provided code")
    void errorResponseWithCustomStatusCodeUsesProvidedCode() {
        HttpResponse response = support.errorResponse(404, "Not found");
        
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("errorResponse with 503 uses service unavailable status")
    void errorResponseWith503UsesServiceUnavailableStatus() {
        HttpResponse response = support.errorResponse(503, "Service unavailable");
        
        assertThat(response.getCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("errorResponse with 429 uses rate limit status")
    void errorResponseWith429UsesRateLimitStatus() {
        HttpResponse response = support.errorResponse(429, "Rate limit exceeded");
        
        assertThat(response.getCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("errorResponse with 401 uses unauthorized status")
    void errorResponseWith401UsesUnauthorizedStatus() {
        HttpResponse response = support.errorResponse(401, "Unauthorized");
        
        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("errorResponse with 403 uses forbidden status")
    void errorResponseWith403UsesForbiddenStatus() {
        HttpResponse response = support.errorResponse(403, "Forbidden");
        
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("errorResponse with 409 uses conflict status")
    void errorResponseWith409UsesConflictStatus() {
        HttpResponse response = support.errorResponse(409, "Conflict");
        
        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("errorResponse with 500 uses internal server error status")
    void errorResponseWith500UsesInternalServerErrorStatus() {
        HttpResponse response = support.errorResponse(500, "Internal server error");
        
        assertThat(response.getCode()).isEqualTo(500);
    }
}
