/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NlpController}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for POST /api/v1/nlp/parse — intent classification and entity extraction
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("NlpController [GH-90000]")
class NlpControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

    private NlpController controller;

    @BeforeEach
    void setUp() { // GH-90000
        controller = new NlpController(); // GH-90000
    }

    private HttpRequest buildPostRequest(String body) { // GH-90000
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.POST); // GH-90000
        when(request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id [GH-90000]"))).thenReturn(null);
        when(request.getQueryParameter("tenantId [GH-90000]")).thenReturn(null);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); // GH-90000
        io.activej.bytebuf.ByteBuf buf = io.activej.bytebuf.ByteBuf.wrapForReading(bytes); // GH-90000
        when(request.loadBody()).thenReturn(Promise.of(buf)); // GH-90000
        return request;
    }

    private Map<String, Object> parseBody(HttpResponse response) throws Exception { // GH-90000
        String json = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        return MAPPER.readValue(json, new TypeReference<>() {}); // GH-90000
    }

    // ─── Missing query ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validation [GH-90000]")
    class Validation {

        @Test
        @DisplayName("returns 400 when query field is missing [GH-90000]")
        void missingQuery_returns400() throws Exception { // GH-90000
            HttpRequest request = buildPostRequest("""
                    {"tenantId":"tenant-a"}
                    """);
            Promise<HttpResponse> promise = controller.handleParseQuery(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).contains("query [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when body is blank [GH-90000]")
        void blankQuery_returns400() throws Exception { // GH-90000
            HttpRequest request = buildPostRequest("""
                    {"query":"   ","tenantId":"tenant-a"}
                    """);
            Promise<HttpResponse> promise = controller.handleParseQuery(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }
    }

    // ─── Intent classification ────────────────────────────────────────────────

    @Nested
    @DisplayName("intent classification [GH-90000]")
    class IntentClassification {

        @ParameterizedTest(name = "query=''{0}'' → intent=''{1}''") // GH-90000
        @CsvSource({ // GH-90000
                "show me all runs,                         list_runs",
                "list all pipelines,                       list_pipelines",
                "get me the agents,                        list_agents",
                "find all anomalies,                       list_anomalies",
                "show me failed pipelines,                 filter_failed",
                "what is currently running,                filter_running",
                "show me succeeded runs last hour,         list_runs",
                "trigger reflect for tenant-a,             trigger_reflect",
                "what is the status of the system,         status_query",
        })
        @DisplayName("classifies intent correctly [GH-90000]")
        void classifiesIntent(String query, String expectedIntent) throws Exception { // GH-90000
            String body = MAPPER.writeValueAsString(Map.of("query", query, "tenantId", "t1")); // GH-90000
            HttpRequest request = buildPostRequest(body); // GH-90000

            Promise<HttpResponse> promise = controller.handleParseQuery(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> result = parseBody(response); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(result.get("intent [GH-90000]")).isEqualTo(expectedIntent);
            assertThat(((Number) result.get("confidence [GH-90000]")).doubleValue()).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("returns unknown intent for unrecognised query [GH-90000]")
        void unrecognisedQuery_returnsUnknown() throws Exception { // GH-90000
            String body = MAPPER.writeValueAsString(Map.of("query", "xyzzy frobozz", "tenantId", "t1")); // GH-90000
            HttpRequest request = buildPostRequest(body); // GH-90000

            Promise<HttpResponse> promise = controller.handleParseQuery(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> result = parseBody(response); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(result.get("intent [GH-90000]")).isEqualTo("unknown [GH-90000]");
        }
    }

    // ─── Entity extraction ────────────────────────────────────────────────────

    @Nested
    @DisplayName("entity extraction [GH-90000]")
    class EntityExtraction {

        @Test
        @DisplayName("extracts time_window entity correctly [GH-90000]")
        void extractsTimeWindow() throws Exception { // GH-90000
            String body = MAPPER.writeValueAsString(Map.of( // GH-90000
                    "query", "show me failing pipelines last 2 hours", "tenantId", "t1"));
            HttpRequest request = buildPostRequest(body); // GH-90000

            Promise<HttpResponse> promise = controller.handleParseQuery(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> result = parseBody(response); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities [GH-90000]");
            assertThat(entities).isNotEmpty(); // GH-90000

            Map<String, Object> timeEntity = entities.stream() // GH-90000
                    .filter(e -> "time_window".equals(e.get("type [GH-90000]")))
                    .findFirst() // GH-90000
                    .orElse(null); // GH-90000
            assertThat(timeEntity).isNotNull(); // GH-90000
            assertThat(timeEntity.get("amount [GH-90000]")).isEqualTo(2);
            assertThat(timeEntity.get("unit [GH-90000]")).isEqualTo("hours [GH-90000]");
            assertThat(timeEntity.get("iso8601 [GH-90000]")).isEqualTo("PT2H [GH-90000]");
        }

        @Test
        @DisplayName("extracts status entity correctly [GH-90000]")
        void extractsStatus() throws Exception { // GH-90000
            String body = MAPPER.writeValueAsString(Map.of( // GH-90000
                    "query", "list all failed runs", "tenantId", "t1"));
            HttpRequest request = buildPostRequest(body); // GH-90000

            Promise<HttpResponse> promise = controller.handleParseQuery(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> result = parseBody(response); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities [GH-90000]");
            boolean hasStatus = entities.stream() // GH-90000
                    .anyMatch(e -> "status".equals(e.get("type [GH-90000]")) && "FAILED".equals(e.get("value [GH-90000]")));
            assertThat(hasStatus).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns tenant in response [GH-90000]")
        void returnsTenantId() throws Exception { // GH-90000
            String body = MAPPER.writeValueAsString(Map.of("query", "list runs", "tenantId", "my-tenant")); // GH-90000
            HttpRequest request = buildPostRequest(body); // GH-90000

            Promise<HttpResponse> promise = controller.handleParseQuery(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> result = parseBody(response); // GH-90000

            assertThat(result.get("tenantId [GH-90000]")).isEqualTo("my-tenant [GH-90000]");
        }
    }
}
