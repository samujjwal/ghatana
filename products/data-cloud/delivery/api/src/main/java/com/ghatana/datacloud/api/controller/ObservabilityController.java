package com.ghatana.datacloud.api.controller;

import static com.ghatana.datacloud.api.controller.ApiResponses.json;

import com.ghatana.datacloud.observability.MetricsService;
import com.ghatana.datacloud.observability.ObservabilityService;
import com.ghatana.platform.governance.security.Principal;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Observability management controller for Data Cloud.
 *
 * <p><b>Purpose</b><br>
 * Provides administrative endpoints for observability management, monitoring,
 * and correlation tracking. Enables operators to view system metrics, trace
 * requests, and manage observability configuration.
 *
 * <p><b>Endpoints</b><br>
 * - GET /api/v1/observability/metrics - System metrics and statistics
 * - GET /api/v1/observability/traces - Request tracing information
 * - GET /api/v1/observability/correlation/{correlationId} - Correlation context details
 * - POST /api/v1/observability/metrics/snapshot - Create metrics snapshot
 * - GET /api/v1/observability/health - Observability system health
 * - POST /api/v1/observability/metrics/reset - Reset metrics
 *
 * @see ObservabilityService
 * @see MetricsService
 * @doc.type class
 * @doc.purpose Observability management and monitoring endpoints
 * @doc.layer product
 * @doc.pattern Controller
 */
public class ObservabilityController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityController.class);

    private final ObservabilityService observabilityService;
    private final MetricsService metricsService;

    public ObservabilityController(
            ObservabilityService observabilityService,
            MetricsService metricsService) {
        this.observabilityService = observabilityService;
        this.metricsService = metricsService;
    }

    /**
     * Gets system observability metrics and statistics.
     */
    public Promise<HttpResponse> getObservabilityMetrics() {
        try {
            ObservabilityService.ObservabilityStats obsStats = observabilityService.getStats();
            MetricsService.ServiceMetricsSummary metricsSummary = metricsService.getServiceMetricsSummary();

            Map<String, Object> response = new HashMap<>();
            response.put("observability", Map.of(
                "serviceName", obsStats.getServiceName(),
                "activeContexts", obsStats.getActiveContexts(),
                "totalContextsCreated", obsStats.getTotalContextsCreated()
            ));
            
            response.put("metrics", Map.of(
                "serviceName", metricsSummary.getServiceName(),
                "totalMetrics", metricsSummary.getTotalMetrics(),
                "metricCounts", metricsSummary.getMetricCounts(),
                "snapshotCount", metricsSummary.getSnapshotCount()
            ));

            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get observability metrics: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve observability metrics")));
        }
    }

    /**
     * Gets detailed metrics with filtering options.
     */
    public Promise<HttpResponse> getDetailedMetrics(
            String metricName,
            String tagFilter) {
        
        try {
            Map<String, MetricsService.MetricSnapshot> snapshots;
            
            if (metricName != null) {
                Optional<MetricsService.MetricSnapshot> snapshot = metricsService.getMetricSnapshot(metricName);
                snapshots = snapshot.isPresent() ? 
                    Map.of(metricName, snapshot.get()) : Map.of();
            } else {
                snapshots = metricsService.getAllMetricSnapshots();
            }

            // Apply tag filtering if specified
            if (tagFilter != null) {
                String[] keyValue = tagFilter.split("=", 2);
                if (keyValue.length == 2) {
                    Map<String, String> tagFilters = Map.of(keyValue[0], keyValue[1]);
                    snapshots = metricsService.getMetricsByTags(tagFilters);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("metrics", snapshots);
            response.put("count", snapshots.size());
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get detailed metrics: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve detailed metrics")));
        }
    }

    /**
     * Gets request tracing information.
     */
    public Promise<HttpResponse> getTraceInformation(
            String tenantId,
            String surface,
            int limit) {
        
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Get current observability context if available
            Optional<ObservabilityService.ObservabilityContext> currentContext = 
                observabilityService.getCurrentContext();
            
            if (currentContext.isPresent()) {
                ObservabilityService.ObservabilityContext context = currentContext.get();
                response.put("currentContext", Map.of(
                    "correlationId", context.getCorrelationId(),
                    "tenantId", context.getTenantId(),
                    "surface", context.getSurface(),
                    "allContext", context.getAllContext(),
                    "metricsCount", context.getAllMetrics().size(),
                    "eventsCount", context.getAllEvents().size()
                ));
            }

            response.put("activeContexts", observabilityService.getStats().getActiveContexts());
            response.put("filter", Map.of(
                "tenantId", tenantId,
                "surface", surface,
                "limit", limit
            ));
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get trace information: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve trace information")));
        }
    }

    /**
     * Gets correlation context details.
     */
    public Promise<HttpResponse> getCorrelationContext(String correlationId) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            response.put("correlationId", correlationId);
            response.put("status", "not_found");
            response.put("message", "Correlation context is not active in this API process");
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get correlation context: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve correlation context")));
        }
    }

    /**
     * Creates a metrics snapshot for historical tracking.
     */
    public Promise<HttpResponse> createMetricsSnapshot() {
        try {
            metricsService.createSnapshot();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Metrics snapshot created successfully");
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to create metrics snapshot: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to create metrics snapshot")));
        }
    }

    /**
     * Gets observability system health.
     */
    public Promise<HttpResponse> getObservabilityHealth() {
        try {
            ObservabilityService.ObservabilityStats obsStats = observabilityService.getStats();
            MetricsService.ServiceMetricsSummary metricsSummary = metricsService.getServiceMetricsSummary();

            Map<String, Object> response = new HashMap<>();
            
            // Determine overall health status
            String status = "healthy";
            if (obsStats.getActiveContexts() > 1000) {
                status = "warning"; // Too many active contexts
            }
            if (metricsSummary.getTotalMetrics() > 10000) {
                status = "warning"; // Too many metrics
            }

            response.put("status", status);
            response.put("observability", Map.of(
                "serviceName", obsStats.getServiceName(),
                "activeContexts", obsStats.getActiveContexts(),
                "totalContextsCreated", obsStats.getTotalContextsCreated(),
                "status", obsStats.getActiveContexts() < 1000 ? "healthy" : "warning"
            ));
            
            response.put("metrics", Map.of(
                "serviceName", metricsSummary.getServiceName(),
                "totalMetrics", metricsSummary.getTotalMetrics(),
                "metricCounts", metricsSummary.getMetricCounts(),
                "snapshotCount", metricsSummary.getSnapshotCount(),
                "status", metricsSummary.getTotalMetrics() < 10000 ? "healthy" : "warning"
            ));

            response.put("checks", Map.of(
                "contextManagement", obsStats.getActiveContexts() < 1000 ? "pass" : "warn",
                "metricsCollection", metricsSummary.getTotalMetrics() < 10000 ? "pass" : "warn",
                "snapshotRetention", metricsSummary.getSnapshotCount() < 1000 ? "pass" : "warn"
            ));

            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get observability health: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve observability health")));
        }
    }

    /**
     * Resets all metrics (admin only).
     */
    public Promise<HttpResponse> resetMetrics(Map<String, Object> request) {
        try {
            String metricName = request != null ? (String) request.get("metricName") : null;
            
            if (metricName != null) {
                metricsService.resetMetric(metricName);
                log.info("Metric {} reset by admin", metricName);
            } else {
                metricsService.resetAllMetrics();
                log.info("All metrics reset by admin");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", metricName != null ? 
                "Metric " + metricName + " reset successfully" : 
                "All metrics reset successfully");
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to reset metrics: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to reset metrics")));
        }
    }

    /**
     * Gets correlation statistics.
     */
    public Promise<HttpResponse> getCorrelationStats() {
        try {
            ObservabilityService.ObservabilityStats stats = observabilityService.getStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("serviceName", stats.getServiceName());
            response.put("activeContexts", stats.getActiveContexts());
            response.put("totalContextsCreated", stats.getTotalContextsCreated());
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get correlation stats: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve correlation stats")));
        }
    }

    /**
     * Creates a new observability context for testing.
     */
    public Promise<HttpResponse> createTestContext(Map<String, Object> request) {
        try {
            String correlationId = (String) request.getOrDefault("correlationId", "test-" + System.currentTimeMillis());
            String tenantId = (String) request.getOrDefault("tenantId", "test-tenant");
            String surface = (String) request.getOrDefault("surface", "test");
            String runId = (String) request.get("runId");
            String jobId = (String) request.get("jobId");

            try (ObservabilityService.ObservabilityContext context = 
                 observabilityService.createContext(correlationId, tenantId, surface, runId, jobId)) {
                
                context.recordEvent("test_context_created", Map.of(
                    "test", true,
                    "createdBy", "ObservabilityController"
                ));
                
                context.recordMetric("test_metric", 42.0);

                Map<String, Object> response = new HashMap<>();
                response.put("contextId", correlationId);
                response.put("tenantId", tenantId);
                response.put("surface", surface);
                response.put("message", "Test context created successfully");
                response.put("timestamp", java.time.Instant.now().toString());

                return Promise.of(json(200, response));
            }

        } catch (Exception e) {
            log.error("Failed to create test context: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to create test context")));
        }
    }
}
