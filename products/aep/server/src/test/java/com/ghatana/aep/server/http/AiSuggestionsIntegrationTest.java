package com.ghatana.aep.server.http;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AI suggestions endpoint call chain.
 *
 * <p>Verifies the full call chain from HTTP route to analytics store:
 * <ul>
 *   <li>HTTP route /api/v1/ai/suggestions → AiSuggestionsController</li>
 *   <li>AiSuggestionsController → DataCloudAnalyticsStore.queryAnomalies()</li>
 *   <li>AiSuggestionsController → AepSloMetrics.runCountSnapshot()</li>
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
class AiSuggestionsIntegrationTest {

    /**
     * Test that verifies AI suggestions endpoint calls DataCloudAnalyticsStore.
     */
    @Test
    @DisplayName("AI suggestions endpoint calls DataCloudAnalyticsStore when available")
    void aiSuggestionsCallsAnalyticsStore() {
        Eventloop eventloop = Eventloop.create();
        eventloop.run(() -> {
            // Create mock analytics store that returns test anomalies
            DataCloudAnalyticsStore mockAnalyticsStore = new DataCloudAnalyticsStore() {
                @Override
                public Promise<List<AnomalyRecord>> queryAnomalies(
                        String tenantId, String entityType, Instant after, Instant before, int limit) {
                    // Return a test anomaly
                    AnomalyRecord anomaly = new AnomalyRecord(
                            "anomaly-1",
                            "pipeline",
                            "pipeline-123",
                            "HIGH_ERROR_RATE",
                            "HIGH",
                            0.85,
                            Instant.now(),
                            "Pipeline error rate exceeded threshold"
                    );
                    return Promise.of(List.of(anomaly));
                }
            };

            // Create mock SLO metrics
            MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
            AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector);

            // Create controller with mock dependencies
            AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics);

            // Create mock HTTP request
            HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/v1/ai/suggestions?tenantId=test-tenant&limit=10");

            // Call the handler
            Promise<HttpResponse> responsePromise = controller.handleGetSuggestions(request);

            return responsePromise.then(response -> {
                assertThat(response).isNotNull();
                assertThat(response.getCode()).isEqualTo(200);
                
                // Parse response body
                String body = response.getBody().asString();
                assertThat(body).contains("suggestions");
                assertThat(body).contains("HIGH_ERROR_RATE");
                
                return Promise.complete(null);
            });
        });
    }

    /**
     * Test that verifies AI suggestions degrade gracefully when DataCloudAnalyticsStore is null.
     */
    @Test
    @DisplayName("AI suggestions degrade gracefully when analytics store is null")
    void aiSuggestionsDegradesGracefullyWhenAnalyticsStoreNull() {
        Eventloop eventloop = Eventloop.create();
        eventloop.run(() -> {
            // Create controller with null analytics store
            MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
            AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector);
            AiSuggestionsController controller = new AiSuggestionsController(null, sloMetrics);

            // Create mock HTTP request
            HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/v1/ai/suggestions?tenantId=test-tenant&limit=10");

            // Call the handler
            Promise<HttpResponse> responsePromise = controller.handleGetSuggestions(request);

            return responsePromise.then(response -> {
                assertThat(response).isNotNull();
                assertThat(response.getCode()).isEqualTo(200);
                
                // Parse response body - should contain fallback message
                String body = response.getBody().asString();
                assertThat(body).contains("Connect DataCloud to enable AI-scored anomaly detection");
                
                return Promise.complete(null);
            });
        });
    }

    /**
     * Test that verifies AI suggestions return meaningful results when anomalies are found.
     */
    @Test
    @DisplayName("AI suggestions return meaningful results when anomalies are found")
    void aiSuggestionsReturnMeaningfulResults() {
        Eventloop eventloop = Eventloop.create();
        eventloop.run(() -> {
            // Create mock analytics store that returns multiple test anomalies
            DataCloudAnalyticsStore mockAnalyticsStore = new DataCloudAnalyticsStore() {
                @Override
                public Promise<List<AnomalyRecord>> queryAnomalies(
                        String tenantId, String entityType, Instant after, Instant before, int limit) {
                    // Return multiple test anomalies
                    List<AnomalyRecord> anomalies = List.of(
                            new AnomalyRecord(
                                    "anomaly-1",
                                    "pipeline",
                                    "pipeline-123",
                                    "HIGH_ERROR_RATE",
                                    "HIGH",
                                    0.92,
                                    Instant.now(),
                                    "Pipeline error rate 92% in last hour"
                            ),
                            new AnomalyRecord(
                                    "anomaly-2",
                                    "pipeline",
                                    "pipeline-456",
                                    "SLOW_EXECUTION",
                                    "MEDIUM",
                                    0.65,
                                    Instant.now(),
                                    "Pipeline execution time exceeded SLA"
                            )
                    );
                    return Promise.of(anomalies);
                }
            };

            MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
            AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector);
            AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics);

            HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/v1/ai/suggestions?tenantId=test-tenant&limit=10");

            return controller.handleGetSuggestions(request).then(response -> {
                assertThat(response).isNotNull();
                assertThat(response.getCode()).isEqualTo(200);
                
                String body = response.getBody().asString();
                assertThat(body).contains("suggestions");
                assertThat(body).contains("HIGH_ERROR_RATE");
                assertThat(body).contains("SLOW_EXECUTION");
                assertThat(body).contains("HIGH");
                assertThat(body).contains("MEDIUM");
                
                return Promise.complete(null);
            });
        });
    }

    /**
     * Test that verifies AI suggestions use SLO metrics when available.
     */
    @Test
    @DisplayName("AI suggestions include SLO-based hints when metrics available")
    void aiSuggestionsIncludeSloMetrics() {
        Eventloop eventloop = Eventloop.create();
        eventloop.run(() -> {
            // Create mock analytics store that returns empty anomalies
            DataCloudAnalyticsStore mockAnalyticsStore = new DataCloudAnalyticsStore() {
                @Override
                public Promise<List<AnomalyRecord>> queryAnomalies(
                        String tenantId, String entityType, Instant after, Instant before, int limit) {
                    return Promise.of(List.of());
                }
            };

            // Create mock SLO metrics that returns failure rate data
            MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();
            AepSloMetrics sloMetrics = new AepSloMetrics(metricsCollector) {
                @Override
                public Map<String, Object> runCountSnapshot() {
                    return Map.of(
                            "total", 100,
                            "failed", 15,
                            "failureRate", 0.15
                    );
                }
            };

            AiSuggestionsController controller = new AiSuggestionsController(mockAnalyticsStore, sloMetrics);

            HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/v1/ai/suggestions?tenantId=test-tenant&limit=10");

            return controller.handleGetSuggestions(request).then(response -> {
                assertThat(response).isNotNull();
                assertThat(response.getCode()).isEqualTo(200);
                
                String body = response.getBody().asString();
                // Should include SLO-based warning about failure rate
                assertThat(body).contains("15.0%");
                assertThat(body).contains("failed");
                
                return Promise.complete(null);
            });
        });
    }

    /**
     * Mock AnomalyRecord for testing.
     */
    static class AnomalyRecord implements DataCloudAnalyticsStore.AnomalyRecord {
        private final String id;
        private final String entityType;
        private final String entityId;
        private final String anomalyType;
        private final String severity;
        private final Double score;
        private final Instant detectedAt;
        private final String description;

        AnomalyRecord(String id, String entityType, String entityId, String anomalyType,
                     String severity, Double score, Instant detectedAt, String description) {
            this.id = id;
            this.entityType = entityType;
            this.entityId = entityId;
            this.anomalyType = anomalyType;
            this.severity = severity;
            this.score = score;
            this.detectedAt = detectedAt;
            this.description = description;
        }

        @Override
        public String id() { return id; }

        @Override
        public String entityType() { return entityType; }

        @Override
        public String entityId() { return entityId; }

        @Override
        public String anomalyType() { return anomalyType; }

        @Override
        public String severity() { return severity; }

        @Override
        public Double score() { return score; }

        @Override
        public Instant detectedAt() { return detectedAt; }

        @Override
        public String description() { return description; }
    }
}
