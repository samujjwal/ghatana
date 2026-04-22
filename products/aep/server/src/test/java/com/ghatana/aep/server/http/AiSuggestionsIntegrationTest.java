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
 *   <li>AiSuggestionsController -> DataCloudAnalyticsStore.queryAnomalies()</li> // GH-90000
 *   <li>AiSuggestionsController -> AepSloMetrics.runCountSnapshot()</li> // GH-90000
 *   <li>Fallback behavior when analytics store is null</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for AI suggestions call chain
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AI Suggestions Call Chain Integration Tests [GH-90000]")
@Tag("integration [GH-90000]")
class AiSuggestionsIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("AI suggestions endpoint calls DataCloudAnalyticsStore when available [GH-90000]")
    void aiSuggestionsCallsAnalyticsStore() { // GH-90000
        DataCloudAnalyticsStore mockAnalyticsStore = mock(DataCloudAnalyticsStore.class); // GH-90000
        when(mockAnalyticsStore.queryAnomalies(anyString(), any(), any(), any(), anyInt())) // GH-90000
            .thenReturn(Promise.of(List.of(new DataCloudAnalyticsStore.AnomalyRecord( // GH-90000
                "anomaly-1",
                "HIGH_ERROR_RATE",
                "HIGH",
                0.85,
                "Pipeline error rate exceeded threshold",
                "pipeline-123",
                null,
                Instant.now(), // GH-90000
                false))));

        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop(); // GH-90000
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector); // GH-90000
        AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10 [GH-90000]").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request)); // GH-90000

        assertThat(response).isNotNull(); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
            .contains("suggestions [GH-90000]")
            .contains("Pipeline error rate exceeded threshold [GH-90000]")
            .contains("high [GH-90000]");
    }

    @Test
    @DisplayName("AI suggestions degrade gracefully when analytics store is null [GH-90000]")
    void aiSuggestionsDegradesGracefullyWhenAnalyticsStoreNull() { // GH-90000
        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop(); // GH-90000
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector); // GH-90000
        AiSuggestionsController controller = new AiSuggestionsController(null, sloMetrics); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10 [GH-90000]").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request)); // GH-90000

        assertThat(response).isNotNull(); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Connect DataCloud to enable AI-scored anomaly detection [GH-90000]");
    }

    @Test
    @DisplayName("AI suggestions return meaningful results when anomalies are found [GH-90000]")
    void aiSuggestionsReturnMeaningfulResults() { // GH-90000
        DataCloudAnalyticsStore mockAnalyticsStore = mock(DataCloudAnalyticsStore.class); // GH-90000
        when(mockAnalyticsStore.queryAnomalies(anyString(), any(), any(), any(), anyInt())) // GH-90000
            .thenReturn(Promise.of(List.of( // GH-90000
                new DataCloudAnalyticsStore.AnomalyRecord( // GH-90000
                    "anomaly-1",
                    "HIGH_ERROR_RATE",
                    "HIGH",
                    0.92,
                    "Pipeline error rate 92% in last hour",
                    "pipeline-123",
                    null,
                    Instant.now(), // GH-90000
                    false),
                new DataCloudAnalyticsStore.AnomalyRecord( // GH-90000
                    "anomaly-2",
                    "SLOW_EXECUTION",
                    "MEDIUM",
                    0.65,
                    "Pipeline execution time exceeded SLA",
                    "pipeline-456",
                    null,
                    Instant.now(), // GH-90000
                    false))));

        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop(); // GH-90000
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector); // GH-90000
        AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10 [GH-90000]").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request)); // GH-90000

        assertThat(response).isNotNull(); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getBody().asString(StandardCharsets.UTF_8)) // GH-90000
            .contains("suggestions [GH-90000]")
            .contains("Pipeline error rate 92% in last hour [GH-90000]")
            .contains("Pipeline execution time exceeded SLA [GH-90000]")
            .contains("high [GH-90000]")
            .contains("medium [GH-90000]");
    }

    @Test
    @DisplayName("AI suggestions include SLO-based hints when metrics available [GH-90000]")
    void aiSuggestionsIncludeSloMetrics() { // GH-90000
        DataCloudAnalyticsStore mockAnalyticsStore = mock(DataCloudAnalyticsStore.class); // GH-90000
        when(mockAnalyticsStore.queryAnomalies(anyString(), any(), any(), any(), anyInt())) // GH-90000
            .thenReturn(Promise.of(List.of())); // GH-90000

        MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop(); // GH-90000
        AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector); // GH-90000
        for (int index = 0; index < 85; index++) { // GH-90000
            sloMetrics.recordRunCompleted("test-tenant", "pipeline-1", 100); // GH-90000
        }
        for (int index = 0; index < 15; index++) { // GH-90000
            sloMetrics.recordRunFailed("test-tenant", "pipeline-1", 100, "engine_error"); // GH-90000
        }

        AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/ai/suggestions?tenantId=test-tenant&limit=10 [GH-90000]").build();

        HttpResponse response = runPromise(() -> controller.handleGetSuggestions(request)); // GH-90000

        assertThat(response).isNotNull(); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("15.0% [GH-90000]").contains("failed [GH-90000]");
    }
}
