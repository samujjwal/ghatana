/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.platform.security.ratelimit.RateLimiter;
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
import static org.mockito.Mockito.lenient;
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
@ExtendWith(MockitoExtension.class) // GH-90000
class AiSuggestionsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

    @Mock
    private DataCloudAnalyticsStore analyticsStore;

    @Mock
    private AepSloMetrics sloMetrics;

    @Mock
    private RateLimiter suggestionsRateLimiter;

    private AiSuggestionsController controller;

    @BeforeEach
    void setUp() { // GH-90000
        lenient().when(suggestionsRateLimiter.tryAcquire(anyString())) // GH-90000
            .thenReturn(new RateLimiter.AcquireResult(true, 59, 0L, 0L)); // GH-90000
        controller = new AiSuggestionsController(analyticsStore, sloMetrics, null, suggestionsRateLimiter, true); // GH-90000
    }

    private HttpRequest buildGetRequest(String tenantId) { // GH-90000
        return buildGetRequest(tenantId, null); // GH-90000
    }

    private HttpRequest buildGetRequest(String tenantId, AepAuthFilter.JwtPayload jwtPayload) { // GH-90000
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getHeader(any())).thenReturn(null); // GH-90000
        when(request.getQueryParameter("tenantId")).thenReturn(tenantId);
        when(request.getQueryParameter("limit")).thenReturn(null);
        when(request.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT)).thenReturn(jwtPayload); // GH-90000
        return request;
    }

    private Map<String, Object> parseBody(HttpResponse response) throws Exception { // GH-90000
        String json = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        return MAPPER.readValue(json, new TypeReference<>() {}); // GH-90000
    }

    // ─── No-store fallback ────────────────────────────────────────────────────

    @Nested
    @DisplayName("without analytics store")
    class WithoutAnalyticsStore {

        @Test
        @DisplayName("returns 200 with a fallback recommendation suggestion")
        void noAnalyticsStore_returnsFallback() throws Exception { // GH-90000
            AiSuggestionsController fallbackController = new AiSuggestionsController(null, null); // GH-90000
            HttpRequest request = buildGetRequest("tenant-test");

            Promise<HttpResponse> promise = fallbackController.handleGetSuggestions(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(body).containsKey("suggestions");
            assertThat(body).containsKey("count");
            assertThat(body.get("tenantId")).isEqualTo("tenant-test");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty(); // GH-90000
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
        void withAnomalies_returnsAnomalySuggestions() throws Exception { // GH-90000
            DataCloudAnalyticsStore.AnomalyRecord anomaly = new DataCloudAnalyticsStore.AnomalyRecord( // GH-90000
                    "anomaly-1", "FREQUENCY_SPIKE", "HIGH", 0.92,
                    "Event processing rate spiked beyond expected threshold",
                    "pipeline-abc", null, Instant.now(), false); // GH-90000
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(List.of(anomaly))); // GH-90000

            HttpRequest request = buildGetRequest("tenant-test");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty(); // GH-90000

            Map<String, Object> first = suggestions.get(0); // GH-90000
            assertThat(first.get("severity")).isEqualTo("high");
            assertThat(first.get("resourceId")).isEqualTo("pipeline-abc");
            assertThat(first.get("message").toString()).contains("spiked beyond expected threshold");
        }

        @Test
        @DisplayName("returns 200 with system-healthy recommendation when no anomalies")
        void noAnomalies_returnsHealthyRecommendation() throws Exception { // GH-90000
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(sloMetrics.runCountSnapshot()).thenReturn(Map.of("total", 0L, "failed", 0L, "failureRate", 0.0)); // GH-90000

            HttpRequest request = buildGetRequest("tenant-healthy");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty(); // GH-90000
            assertThat(suggestions.get(0).get("severity")).isEqualTo("low");
        }

        @Test
        @DisplayName("returns 500 when analytics store throws")
        void analyticsStoreThrows_returns500() throws Exception { // GH-90000
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("DataCloud unavailable")));

            HttpRequest request = buildGetRequest("tenant-error");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000

            assertThat(response.getCode()).isEqualTo(500); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000
            assertThat(body.get("message").toString()).contains("Failed to generate suggestions");
        }

        @Test
        @DisplayName("SLO high-failure-rate produces a high-severity warning suggestion")
        void sloHighFailureRate_producesWarningSuggestion() throws Exception { // GH-90000
            when(analyticsStore.queryAnomalies(anyString(), isNull(), any(Instant.class), isNull(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(sloMetrics.runCountSnapshot()).thenReturn( // GH-90000
                    Map.of("total", 100L, "failed", 30L, "failureRate", 0.30)); // GH-90000

            HttpRequest request = buildGetRequest("tenant-failing");
            Promise<HttpResponse> promise = controller.handleGetSuggestions(request); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(suggestions).isNotEmpty(); // GH-90000
            boolean hasFailureWarning = suggestions.stream() // GH-90000
                    .anyMatch(s -> "high".equals(s.get("severity")) || "medium".equals(s.get("severity")));
            assertThat(hasFailureWarning).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns 403 when authenticated tenant context does not match request tenant")
        void jwtTenantMismatch_returns403() throws Exception { // GH-90000
            AiSuggestionsController securedController = new AiSuggestionsController( // GH-90000
                analyticsStore, sloMetrics, null, suggestionsRateLimiter, false);
            AepAuthFilter.JwtPayload jwtPayload = new AepAuthFilter.JwtPayload( // GH-90000
                "user-1",
                "test-issuer",
                Instant.now().plusSeconds(3600).getEpochSecond(), // GH-90000
                Instant.now().getEpochSecond(), // GH-90000
                List.of("viewer"),
                List.of("ai:read"),
                "tenant-auth");
            HttpRequest request = buildGetRequest("tenant-query", jwtPayload); // GH-90000

            HttpResponse response = securedController.handleGetSuggestions(request).getResult(); // GH-90000

            assertThat(response.getCode()).isEqualTo(403); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000
            assertThat(body.get("message").toString()).contains("Tenant context mismatch");
        }

        @Test
        @DisplayName("returns 429 when AI suggestions endpoint rate limit is exceeded")
        void rateLimitExceeded_returns429() throws Exception { // GH-90000
            when(suggestionsRateLimiter.tryAcquire("tenant-burst"))
                .thenReturn(new RateLimiter.AcquireResult(false, 0, 15L, 0L)); // GH-90000
            HttpRequest request = buildGetRequest("tenant-burst");

            HttpResponse response = controller.handleGetSuggestions(request).getResult(); // GH-90000

            assertThat(response.getCode()).isEqualTo(429); // GH-90000
            Map<String, Object> body = parseBody(response); // GH-90000
            assertThat(body.get("message").toString()).contains("rate limit exceeded");
        }
    }
}
