/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.dlq.DlqController;
import com.ghatana.yappc.api.observability.HealthAggregationController;
import com.ghatana.yappc.api.observability.MicrometerMetricsCollector;
import com.ghatana.yappc.api.workflow.WorkflowExecutionController;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import java.nio.charset.StandardCharsets;

/**
 * Route registrations for Observability and Operations APIs.
 *
 * <ul>
 *   <li>/metrics - Prometheus scrape endpoint
 *   <li>/health/detailed - aggregated health check
 *   <li>/api/v1/workflows/{templateId}/start, /runs/{runId}/status - workflow execution
 *   <li>/api/v1/dlq/* - dead-letter queue management
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Register observability, health, workflow execution, and DLQ routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class OperationsRoutes {

  private OperationsRoutes() {}

  /**
   * Registers all operations and observability routes on the given builder.
   *
   * @param builder the routing servlet builder
   * @param metrics Micrometer metrics collector (provides Prometheus scrape data)
   * @param healthCtrl health aggregation controller
   * @param workflowCtrl workflow execution controller
   * @param dlqCtrl dead-letter queue controller
   */
  public static void register(
      RoutingServlet.Builder builder,
      MicrometerMetricsCollector metrics,
      HealthAggregationController healthCtrl,
      WorkflowExecutionController workflowCtrl,
      DlqController dlqCtrl) {

    builder
        // Prometheus scrape endpoint
        .with(
            GET,
            "/metrics",
            request ->
                io.activej.promise.Promise.of(
                    HttpResponse.ok200()
                        .withHeader(
                            HttpHeaders.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8")
                        .withBody(metrics.scrape().getBytes(StandardCharsets.UTF_8))
                        .build()))

        // Health Aggregation (Observability 6.6)
        .with(GET, "/health/detailed", healthCtrl::getDetailedHealth)

        // Workflow Execution (Orchestration 7.3)
        .with(
            POST,
            "/api/v1/workflows/:templateId/start",
            request -> {
              String templateId = request.getPathParameter("templateId");
              return workflowCtrl.startWorkflow(request, templateId);
            })
        .with(
            GET,
            "/api/v1/workflows/runs/:runId/status",
            request -> {
              String runId = request.getPathParameter("runId");
              return workflowCtrl.getRunStatus(request, runId);
            })

        // DLQ Management (Orchestration 7.4)
        .with(GET, "/api/v1/dlq", dlqCtrl::listEntries)
        .with(
            POST,
            "/api/v1/dlq/:id/retry",
            request -> {
              String entryId = request.getPathParameter("id");
              return dlqCtrl.retryEntry(request, entryId);
            });
  }
}
