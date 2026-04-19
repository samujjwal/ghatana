package com.ghatana.aep.server.http;

import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.server.http.controllers.AiSuggestionsController;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AI suggestions endpoint call chain.
 *
 * <p>Verifies the full call chain from HTTP route to analytics store:
 * <ul>
 *   <li>HTTP route /api/v1/ai/suggestions -> AiSuggestionsController</li>
 *   <li>AiSuggestionsController -> DataCloudAnalyticsStore.queryAnomalies()</li>
 *   <li>AiSuggestionsController -> AepSloMetrics.runCountSnapshot()</li>
 *   <li>Fallback behavior when analytics store is null</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for AI suggestions call chain
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AI Suggestions Call Chain Integration Tests")
@Tag("integration")
class AiSuggestionsIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("AI suggestions endpoint calls DataCloudAnalyticsStore when available")
    void aiSuggestionsCallsAnalyticsStore() {
        DataCloudAnalyticsStore mockAnalyticsStore = mock(DataCloudAnalyticsStore.class);
        when(mockAnalyticsStore.queryAnomalies(anyString(), any(), any(), any(), anyInt()))
            .thenReturn(Promise.of(List.of(new DataCloudAnalyticsStore.AnomalyRecord(
                "anomaly-1",
                "HIGH_ERROR_RATE",
                "HIGH",
                0.85,
                "Pipeline error rate exceeded threshold",
                "pipeline-123",
                null,
                Instant.now(),
                false))));

        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector);
        AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics);
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request));

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8))
            .contains("suggestions")
            .contains("Pipeline error rate exceeded threshold")
            .contains("high");
    }

    @Test
    @DisplayName("AI suggestions degrade gracefully when analytics store is null")
    void aiSuggestionsDegradesGracefullyWhenAnalyticsStoreNull() {
        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector);
        AiSuggestionsController controller = new AiSuggestionsController(null, sloMetrics);
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request));

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Connect DataCloud to enable AI-scored anomaly detection");
    }

    @Test
    @DisplayName("AI suggestions return meaningful results when anomalies are found")
    void aiSuggestionsReturnMeaningfulResults() {
        DataCloudAnalyticsStore mockAnalyticsStore = mock(DataCloudAnalyticsStore.class);
        when(mockAnalyticsStore.queryAnomalies(anyString(), any(), any(), any(), anyInt()))
            .thenReturn(Promise.of(List.of(
                new DataCloudAnalyticsStore.AnomalyRecord(
                    "anomaly-1",
                    "HIGH_ERROR_RATE",
                    "HIGH",
                    0.92,
                    "Pipeline error rate 92% in last hour",
                    "pipeline-123",
                    null,
                    Instant.now(),
                    false),
                new DataCloudAnalyticsStore.AnomalyRecord(
                    "anomaly-2",
                    "SLOW_EXECUTION",
                    "MEDIUM",
                    0.65,
                    "Pipeline execution time exceeded SLA",
                    "pipeline-456",
                    null,
                    Instant.now(),
                    false))));

        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector);
        AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics);
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request));

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8))
            .contains("suggestions")
            .contains("Pipeline error rate 92% in last hour")
            .contains("Pipeline execution time exceeded SLA")
            .contains("high")
            .contains("medium");
    }

    @Test
    @DisplayName("AI suggestions include SLO-based hints when metrics available")
    void aiSuggestionsIncludeSloMetrics() {
        DataCloudAnalyticsStore mockAnalyticsStore = mock(DataCloudAnalyticsStore.class);
        when(mockAnalyticsStore.queryAnomalies(anyString(), any(), any(), any(), anyInt()))
            .thenReturn(Promise.of(List.of()));

        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector);
        for (int index = 0; index < 85; index++) {
            sloMetrics.recordRunCompleted("test-tenant", "pipeline-1", 100);
        }
        for (int index = 0; index < 15; index++) {
            sloMetrics.recordRunFailed("test-tenant", "pipeline-1", 100, "engine_error");
        }

        AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics);
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request));

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("15.0%").contains("failed");
    }
}
