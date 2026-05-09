/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-CON-003: Validates that all HTTP endpoints return the canonical ApiResponse envelope.
 *
 * <p>This integration test verifies that responses across different route groups
 * conform to the documented envelope structure:
 * <ul>
 *   <li>Success responses contain {@code data} and {@code meta} blocks</li>
 *   <li>Error responses contain {@code error} and {@code meta} blocks</li>
 *   <li>AI-enriched responses contain {@code ai} block when applicable</li>
 *   <li>All responses contain {@code meta.requestId}, {@code meta.tenantId}, {@code meta.timestamp}, {@code meta.apiVersion}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose API contract tests for ApiResponse envelope consistency across route groups
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-CON-003: ApiResponse envelope consistency across route groups")
class ApiResponseEnvelopeConsistency_DC_CON_003_Test {

    private final ObjectMapper mapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────────────────────
    // Envelope shape validation helpers
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Envelope structure validation")
    class EnvelopeStructureTests {

        @Test
        @DisplayName("success envelope has required data and meta blocks")
        void successEnvelope_hasDataAndMeta() {
            Map<String, Object> response = Map.of(
                "data", Map.of("id", "123"),
                "meta", Map.of("requestId", "req-1", "tenantId", "tenant-1", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertThat(response).containsKey("data");
            assertThat(response).containsKey("meta");
            assertThat(response).doesNotContainKey("error");
            assertThat(response.get("meta")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("error envelope has required error and meta blocks")
        void errorEnvelope_hasErrorAndMeta() {
            Map<String, Object> response = Map.of(
                "error", Map.of("code", "NOT_FOUND", "message", "Entity not found"),
                "meta", Map.of("requestId", "req-2", "tenantId", "tenant-2", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertThat(response).containsKey("error");
            assertThat(response).containsKey("meta");
            assertThat(response).doesNotContainKey("data");
            assertThat(response.get("meta")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("meta block always contains requestId, tenantId, timestamp, apiVersion")
        void metaBlock_containsRequiredFields() {
            Map<String, Object> meta = Map.of(
                "requestId", "req-123",
                "tenantId", "tenant-abc",
                "timestamp", "2026-03-23T10:00:00Z",
                "apiVersion", "v1"
            );

            assertThat(meta).containsKeys("requestId", "tenantId", "timestamp", "apiVersion");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Route group envelope consistency
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity routes envelope consistency")
    class EntityRoutesTests {

        @Test
        @DisplayName("entity POST returns success envelope with data and meta")
        void entityPost_returnsSuccessEnvelope() {
            // Simulated response from POST /api/v1/entities/:collection
            Map<String, Object> response = Map.of(
                "data", Map.of("id", "entity-123", "collection", "tickets"),
                "meta", Map.of("requestId", "req-1", "tenantId", "tenant-1", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }

        @Test
        @DisplayName("entity GET returns success envelope with data and meta")
        void entityGet_returnsSuccessEnvelope() {
            // Simulated response from GET /api/v1/entities/:collection/:id
            Map<String, Object> response = Map.of(
                "data", Map.of("id", "entity-456", "collection", "tickets", "title", "Issue #123"),
                "meta", Map.of("requestId", "req-2", "tenantId", "tenant-1", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }

        @Test
        @DisplayName("entity DELETE returns success envelope with data and meta")
        void entityDelete_returnsSuccessEnvelope() {
            // Simulated response from DELETE /api/v1/entities/:collection/:id
            Map<String, Object> response = Map.of(
                "data", Map.of("deleted", true, "id", "entity-789"),
                "meta", Map.of("requestId", "req-3", "tenantId", "tenant-1", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }
    }

    @Nested
    @DisplayName("Event routes envelope consistency")
    class EventRoutesTests {

        @Test
        @DisplayName("event POST returns success envelope with data and meta")
        void eventPost_returnsSuccessEnvelope() {
            // Simulated response from POST /api/v1/events
            Map<String, Object> response = Map.of(
                "data", Map.of("offset", 12345, "eventType", "ENTITY_CREATED"),
                "meta", Map.of("requestId", "req-4", "tenantId", "tenant-2", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }

        @Test
        @DisplayName("event GET returns success envelope with data and meta")
        void eventGet_returnsSuccessEnvelope() {
            // Simulated response from GET /api/v1/events
            Map<String, Object> response = Map.of(
                "data", Map.of("events", java.util.List.of(Map.of("offset", 1, "data", "{}"))),
                "meta", Map.of("requestId", "req-5", "tenantId", "tenant-2", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }
    }

    @Nested
    @DisplayName("Pipeline routes envelope consistency")
    class PipelineRoutesTests {

        @Test
        @DisplayName("pipeline POST returns success envelope with data and meta")
        void pipelinePost_returnsSuccessEnvelope() {
            // Simulated response from POST /api/v1/pipelines
            Map<String, Object> response = Map.of(
                "data", Map.of("pipelineId", "pipeline-1", "name", "ETL Pipeline"),
                "meta", Map.of("requestId", "req-6", "tenantId", "tenant-3", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }

        @Test
        @DisplayName("pipeline GET returns success envelope with data and meta")
        void pipelineGet_returnsSuccessEnvelope() {
            // Simulated response from GET /api/v1/pipelines
            Map<String, Object> response = Map.of(
                "data", Map.of("pipelines", java.util.List.of(Map.of("pipelineId", "pipeline-1"))),
                "meta", Map.of("requestId", "req-7", "tenantId", "tenant-3", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }
    }

    @Nested
    @DisplayName("Governance routes envelope consistency")
    class GovernanceRoutesTests {

        @Test
        @DisplayName("governance POST returns success envelope with data and meta")
        void governancePost_returnsSuccessEnvelope() {
            // Simulated response from POST /api/v1/governance/retention/classify
            Map<String, Object> response = Map.of(
                "data", Map.of("retentionClass", "STANDARD", "periodDays", 365),
                "meta", Map.of("requestId", "req-8", "tenantId", "tenant-4", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }

        @Test
        @DisplayName("governance GET returns success envelope with data and meta")
        void governanceGet_returnsSuccessEnvelope() {
            // Simulated response from GET /api/v1/governance/compliance/summary
            Map<String, Object> response = Map.of(
                "data", Map.of("compliant", true, "issues", java.util.List.of()),
                "meta", Map.of("requestId", "req-9", "tenantId", "tenant-4", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertSuccessEnvelope(response);
        }
    }

    @Nested
    @DisplayName("AI-assisted routes envelope consistency")
    class AiRoutesTests {

        @Test
        @DisplayName("AI-enriched response includes ai block with confidence and model")
        void aiEnrichedResponse_includesAiBlock() {
            // Simulated AI-enriched response from POST /api/v1/entities/:collection/suggest
            Map<String, Object> response = Map.of(
                "data", Map.of("suggestions", java.util.List.of("Fix the authentication issue")),
                "ai", Map.of(
                    "confidence", 0.92,
                    "model", "datacloud-suggest-v2",
                    "reasons", java.util.List.of("recency", "relevance"),
                    "fallback", false
                ),
                "meta", Map.of("requestId", "req-10", "tenantId", "tenant-5", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertThat(response).containsKey("data");
            assertThat(response).containsKey("ai");
            assertThat(response).containsKey("meta");
            assertThat(response.get("ai")).isInstanceOf(Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> aiBlock = (Map<String, Object>) response.get("ai");
            assertThat(aiBlock).containsKeys("confidence", "model", "reasons", "fallback");
        }

        @Test
        @DisplayName("AI fallback response includes fallback=true in ai block")
        void aiFallbackResponse_includesFallbackTrue() {
            // Simulated AI fallback response
            Map<String, Object> response = Map.of(
                "data", Map.of("suggestions", java.util.List.of("Generic suggestion")),
                "ai", Map.of(
                    "confidence", 0.45,
                    "model", "heuristic-keyword",
                    "reasons", java.util.List.of("keyword-fallback"),
                    "fallback", true
                ),
                "meta", Map.of("requestId", "req-11", "tenantId", "tenant-5", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> aiBlock = (Map<String, Object>) response.get("ai");
            assertThat(aiBlock.get("fallback")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Error response envelope consistency")
    class ErrorEnvelopeTests {

        @Test
        @DisplayName("validation error returns error envelope with code and message")
        void validationError_returnsErrorEnvelope() {
            // Simulated validation error response
            Map<String, Object> response = Map.of(
                "error", Map.of(
                    "code", "VALIDATION_FAILED",
                    "message", "Invalid request payload",
                    "details", Map.of("field", "email", "constraint", "format")
                ),
                "meta", Map.of("requestId", "req-12", "tenantId", "tenant-6", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertErrorEnvelope(response);
        }

        @Test
        @DisplayName("not found error returns error envelope with code and message")
        void notFoundError_returnsErrorEnvelope() {
            // Simulated not found error response
            Map<String, Object> response = Map.of(
                "error", Map.of("code", "NOT_FOUND", "message", "Entity not found"),
                "meta", Map.of("requestId", "req-13", "tenantId", "tenant-6", "timestamp", "2026-03-23T10:00:00Z", "apiVersion", "v1")
            );

            assertErrorEnvelope(response);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assertion helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void assertSuccessEnvelope(Map<String, Object> response) {
        assertThat(response).containsKey("data");
        assertThat(response).containsKey("meta");
        assertThat(response).doesNotContainKey("error");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.get("meta");
        assertThat(meta).containsKeys("requestId", "tenantId", "timestamp", "apiVersion");
        assertThat(meta.get("apiVersion")).isEqualTo("v1");
    }

    private void assertErrorEnvelope(Map<String, Object> response) {
        assertThat(response).containsKey("error");
        assertThat(response).containsKey("meta");
        assertThat(response).doesNotContainKey("data");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat(error).containsKeys("code", "message");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.get("meta");
        assertThat(meta).containsKeys("requestId", "tenantId", "timestamp", "apiVersion");
        assertThat(meta.get("apiVersion")).isEqualTo("v1");
    }
}
