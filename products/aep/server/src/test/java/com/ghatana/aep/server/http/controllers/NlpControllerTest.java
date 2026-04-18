/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
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
@DisplayName("NlpController")
class NlpControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NlpController controller;

    @BeforeEach
    void setUp() {
        controller = new NlpController();
    }

    private HttpRequest buildPostRequest(String body) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"))).thenReturn(null);
        when(request.getQueryParameter("tenantId")).thenReturn(null);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        io.activej.bytebuf.ByteBuf buf = io.activej.bytebuf.ByteBuf.wrapForReading(bytes);
        when(request.loadBody()).thenReturn(Promise.of(buf));
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(HttpResponse response) throws Exception {
        String json = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, new TypeReference<>() {});
    }

    // ─── Missing query ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("returns 400 when query field is missing")
        void missingQuery_returns400() throws Exception {
            HttpRequest request = buildPostRequest("""
                    {"tenantId":"tenant-a"}
                    """);
            Promise<HttpResponse> promise = controller.handleParseQuery(request);
            HttpResponse response = promise.getResult();

            assertThat(response.getCode()).isEqualTo(400);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("error").toString()).contains("query");
        }

        @Test
        @DisplayName("returns 400 when body is blank")
        void blankQuery_returns400() throws Exception {
            HttpRequest request = buildPostRequest("""
                    {"query":"   ","tenantId":"tenant-a"}
                    """);
            Promise<HttpResponse> promise = controller.handleParseQuery(request);
            HttpResponse response = promise.getResult();

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    // ─── Intent classification ────────────────────────────────────────────────

    @Nested
    @DisplayName("intent classification")
    class IntentClassification {

        @ParameterizedTest(name = "query=''{0}'' → intent=''{1}''")
        @CsvSource({
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
        @DisplayName("classifies intent correctly")
        void classifiesIntent(String query, String expectedIntent) throws Exception {
            String body = MAPPER.writeValueAsString(Map.of("query", query, "tenantId", "t1"));
            HttpRequest request = buildPostRequest(body);

            Promise<HttpResponse> promise = controller.handleParseQuery(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> result = parseBody(response);

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(result.get("intent")).isEqualTo(expectedIntent);
            assertThat(((Number) result.get("confidence")).doubleValue()).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("returns unknown intent for unrecognised query")
        void unrecognisedQuery_returnsUnknown() throws Exception {
            String body = MAPPER.writeValueAsString(Map.of("query", "xyzzy frobozz", "tenantId", "t1"));
            HttpRequest request = buildPostRequest(body);

            Promise<HttpResponse> promise = controller.handleParseQuery(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> result = parseBody(response);

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(result.get("intent")).isEqualTo("unknown");
        }
    }

    // ─── Entity extraction ────────────────────────────────────────────────────

    @Nested
    @DisplayName("entity extraction")
    class EntityExtraction {

        @Test
        @DisplayName("extracts time_window entity correctly")
        void extractsTimeWindow() throws Exception {
            String body = MAPPER.writeValueAsString(Map.of(
                    "query", "show me failing pipelines last 2 hours", "tenantId", "t1"));
            HttpRequest request = buildPostRequest(body);

            Promise<HttpResponse> promise = controller.handleParseQuery(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> result = parseBody(response);

            assertThat(response.getCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");
            assertThat(entities).isNotEmpty();

            Map<String, Object> timeEntity = entities.stream()
                    .filter(e -> "time_window".equals(e.get("type")))
                    .findFirst()
                    .orElse(null);
            assertThat(timeEntity).isNotNull();
            assertThat(timeEntity.get("amount")).isEqualTo(2);
            assertThat(timeEntity.get("unit")).isEqualTo("hours");
            assertThat(timeEntity.get("iso8601")).isEqualTo("PT2H");
        }

        @Test
        @DisplayName("extracts status entity correctly")
        void extractsStatus() throws Exception {
            String body = MAPPER.writeValueAsString(Map.of(
                    "query", "list all failed runs", "tenantId", "t1"));
            HttpRequest request = buildPostRequest(body);

            Promise<HttpResponse> promise = controller.handleParseQuery(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> result = parseBody(response);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");
            boolean hasStatus = entities.stream()
                    .anyMatch(e -> "status".equals(e.get("type")) && "FAILED".equals(e.get("value")));
            assertThat(hasStatus).isTrue();
        }

        @Test
        @DisplayName("returns tenant in response")
        void returnsTenantId() throws Exception {
            String body = MAPPER.writeValueAsString(Map.of("query", "list runs", "tenantId", "my-tenant"));
            HttpRequest request = buildPostRequest(body);

            Promise<HttpResponse> promise = controller.handleParseQuery(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> result = parseBody(response);

            assertThat(result.get("tenantId")).isEqualTo("my-tenant");
        }
    }
}
