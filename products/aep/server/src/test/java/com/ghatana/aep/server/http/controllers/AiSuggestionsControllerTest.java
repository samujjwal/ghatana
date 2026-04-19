/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiSuggestionsController}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for GET /api/v1/ai/suggestions — suggestion scoring and response shape
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AiSuggestionsController")
@ExtendWith(MockitoExtension.class)
class AiSuggestionsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private DataCloudAnalyticsStore analyticsStore;

    @Mock
    private AepSloMetrics sloMetrics;

    private AiSuggestionsController controller;

    @BeforeEach
    void setUp() {
        controller = new AiSuggestionsController(analyticsStore, sloMetrics);
    }

    private HttpRequest buildGetRequest(String tenantId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(any())).thenReturn(null);
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        when(request.getQueryParameter("limit")).thenReturn(null);
        return request;
    }

    private Map<String, Object> parseBody(HttpResponse response) throws Exception {
        String json = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readValue(json, new TypeReference<>() {});
    }

    // ─── No-store fallback ────────────────────────────────────────────────────

    @Nested
    @DisplayName("without analytics store")
    class WithoutAnalyticsStore {

        @Test
        @DisplayName("returns 200 with a fallback recommendation suggestion")
        void noAnalyticsStore_returnsFallback() throws Exception {
            AiSuggestionsController fallbackController = new AiSuggestionsController(null, null);
            HttpRequest request = buildGetRequest("tenant-test");

            Promise<HttpResponse> promise = fallbackController.handleGetSuggestions(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> body = parseBody(response);

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body).containsKey("suggestions");
            assertThat(body).containsKey("count");
            assertThat(body.get("tenantId")).isEqualTo("tenant-test");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions.get(0)).containsKey("message");
            assertThat(suggestions.get(0)).containsKey("severity");
            assertThat(suggestions.get(0)).containsKey("confidence");
        }
    }

    // ─── With analytics store ─────────────────────────────────────────────────

    @Nested
    @DisplayName("with analytics store configured")
    class WithAnalyticsStore {

        @Test
        @DisplayName("returns 200 with anomaly-derived suggestions when anomalies exist")
        void withAnomalies_returnsAnomalySuggestions() throws Exception {
            DataCloudAnalyticsStore.AnomalyRecord anomaly = new DataCloudAnalyticsStore.AnomalyRecord(
                    "anomaly-1", "FREQUENCY_SPIKE", "HIGH", 0.92,
                    "Event processing rate spiked beyond expected threshold",
                    "pipeline-abc", null, Instant.now(), false);
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt()))
                    .thenReturn(Promise.of(List.of(anomaly)));

            HttpRequest request = buildGetRequest("tenant-test");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> body = parseBody(response);

            assertThat(response.getCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty();

            Map<String, Object> first = suggestions.get(0);
            assertThat(first.get("severity")).isEqualTo("high");
            assertThat(first.get("resourceId")).isEqualTo("pipeline-abc");
            assertThat(first.get("message").toString()).contains("spiked beyond expected threshold");
        }

        @Test
        @DisplayName("returns 200 with system-healthy recommendation when no anomalies")
        void noAnomalies_returnsHealthyRecommendation() throws Exception {
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt()))
                    .thenReturn(Promise.of(List.of()));
            when(sloMetrics.runCountSnapshot()).thenReturn(Map.of("total", 0L, "failed", 0L, "failureRate", 0.0));

            HttpRequest request = buildGetRequest("tenant-healthy");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> body = parseBody(response);

            assertThat(response.getCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions.get(0).get("severity")).isEqualTo("low");
        }

        @Test
        @DisplayName("returns 500 when analytics store throws")
        void analyticsStoreThrows_returns500() throws Exception {
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt()))
                    .thenReturn(Promise.ofException(new RuntimeException("DataCloud unavailable")));

            HttpRequest request = buildGetRequest("tenant-error");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request);
            HttpResponse response = promise.getResult();

            assertThat(response.getCode()).isEqualTo(500);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("error").toString()).contains("Failed to generate suggestions");
        }

        @Test
        @DisplayName("SLO high-failure-rate produces a high-severity warning suggestion")
        void sloHighFailureRate_producesWarningSuggestion() throws Exception {
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt()))
                    .thenReturn(Promise.of(List.of()));
            when(sloMetrics.runCountSnapshot()).thenReturn(
                    Map.of("total", 100L, "failed", 30L, "failureRate", 0.30));

            HttpRequest request = buildGetRequest("tenant-failing");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request);
            HttpResponse response = promise.getResult();
            Map<String, Object> body = parseBody(response);

            assertThat(response.getCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty();
            boolean hasFailureWarning = suggestions.stream()
                    .anyMatch(s -> "high".equals(s.get("severity")) || "medium".equals(s.get("severity")));
            assertThat(hasFailureWarning).isTrue();
        }
    }
}
