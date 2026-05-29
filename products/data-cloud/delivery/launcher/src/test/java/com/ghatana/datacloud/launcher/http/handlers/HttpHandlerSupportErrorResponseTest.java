/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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

        assertThat(response.getCode()).isEqualTo(400); 
        Map<String, Object> body = parseError(response);
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("error")).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message")).isEqualTo("Invalid request");
        assertThat(String.valueOf(body.get("traceId"))).isNotBlank();
    }

    @Test
    @DisplayName("envelopeResponse with ApiResponse preserves error structure")
    void envelopeResponseWithApiResponsePreservesErrorStructure() { 
        ApiResponse apiResponse = ApiResponse.error("VALIDATION_ERROR", "Invalid input", "test-tenant", "test-request"); 
        HttpResponse response = support.envelopeResponse(apiResponse, new ObjectMapper()); 

        assertThat(response.getCode()).isEqualTo(400); 
        Map<String, Object> body = parseError(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) body.get("meta");
        assertThat(error.get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("message")).isEqualTo("Invalid input");
        assertThat(meta.get("requestId")).isEqualTo("test-request");
        assertThat(meta.get("tenantId")).isEqualTo("test-tenant");
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseError(HttpResponse response) {
        try {
            String json = response.getBody().getString(StandardCharsets.UTF_8);
            return new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse error response body", e);
        }
    }
}
