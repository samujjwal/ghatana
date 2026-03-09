package com.ghatana.refactorer.server;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.server.HttpServerBuilder;
import com.ghatana.refactorer.config.RefactorerConfig;
import com.ghatana.refactorer.server.auth.JwtAuthFilter;
import com.ghatana.refactorer.server.auth.TenantContextFilter;
import com.ghatana.refactorer.server.rest.DiagnoseController;
import com.ghatana.refactorer.server.rest.JobsController;
import com.ghatana.refactorer.server.rest.RunController;
import io.activej.http.HttpMethod;
import io.activej.http.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Refactorer HTTP server factory using core/http-server abstractions.
 *
 * Consolidates HTTP server setup by using HttpServerBuilder from
 * core/http-server instead of directly constructing ActiveJ HTTP components.
 * This ensures:
 *
 * - Platform-level control over security (JWT auth, CORS) - Consistent response
 * formatting (via ResponseBuilder) - Centralized observability integration -
 * Abstraction of ActiveJ-specific details from product module
 *
 * Binding Decision #1: HTTP abstraction layer - product modules MUST use
 * core/http-server for operations (not direct ActiveJ HTTP imports).
 *
 * Satisfies: ghatana architectural constraint that product modules depend on
 * core abstractions, not third-party libraries directly.
 *
 * @since 1.0.0
 *
 * @doc.type class
 * @doc.purpose Compose REST, SSE, and WebSocket routes along with filters,
 * health, and metrics endpoints.
 * @doc.layer product
 * @doc.pattern HTTP Adapter
 */
@Slf4j

public final class RefactorerHttpServer {

    private final RefactorerConfig config;
    private final JwtAuthFilter jwtAuthFilter;
    private final TenantContextFilter tenantContextFilter;
    private final DiagnoseController diagnoseController;
    private final JobsController jobsController;
    private final RunController runController;

    public RefactorerHttpServer(
            RefactorerConfig config,
            JwtAuthFilter jwtAuthFilter,
            TenantContextFilter tenantContextFilter,
            DiagnoseController diagnoseController,
            JobsController jobsController,
            RunController runController) {
        this.config = config;
        this.jwtAuthFilter = jwtAuthFilter;
        this.tenantContextFilter = tenantContextFilter;
        this.diagnoseController = diagnoseController;
        this.jobsController = jobsController;
        this.runController = runController;
    }

    /**
     * Builds the HTTP server with all routes and filters configured.
     *
     * Uses HttpServerBuilder (core/http-server abstraction) to construct the
     * server instead of direct ActiveJ HTTP API calls. This provides: - Fluent,
     * readable configuration - Built-in support for common patterns (health
     * checks, metrics) - Filter chain management - Consistent error handling
     *
     * @return Configured HTTP server ready to start
     */
    public HttpServer build() {
        return HttpServerBuilder.create()
                .withHost(config.http().host())
                .withPort(config.http().port())
                // Health and readiness checks
                .addRoute(HttpMethod.GET, "/health", request
                        -> ResponseBuilder.ok().json(Map.of("status", "healthy")).build())
                .addRoute(HttpMethod.GET, "/ready", request
                        -> ResponseBuilder.ok().json(Map.of("ready", true)).build())
                // Diagnosis endpoints
                // NOTE: Controllers in this module expose concrete method names. Route
                // wiring must match those methods. POST /v1/diagnose delegates to the
                // DiagnoseController.diagnose implementation.
                .addAsyncRoute(HttpMethod.POST, "/v1/diagnose", request
                        -> diagnoseController.diagnose(request))
                // Expose a lightweight config endpoint (delegates to DiagnoseController.getConfig)
                .addAsyncRoute(HttpMethod.GET, "/v1/config", request
                        -> diagnoseController.getConfig(request))
                // Job management endpoints
                // Adapt routes to the controller methods actually implemented in this module.
                // - Create job: reuse RunController.run
                .addAsyncRoute(HttpMethod.POST, "/api/v1/jobs", request
                        -> runController.run(request))
                // - Job status/report/cancel map to JobsController.status/report/cancel
                .addAsyncRoute(HttpMethod.GET, "/api/v1/jobs/:jobId", request
                        -> jobsController.status(request))
                .addAsyncRoute(HttpMethod.GET, "/api/v1/jobs/:jobId/report", request
                        -> jobsController.report(request))
                .addAsyncRoute(HttpMethod.DELETE, "/api/v1/jobs/:jobId", request
                        -> jobsController.cancel(request))
                // - Start/stop job not implemented in JobsController: return 501
                .addAsyncRoute(HttpMethod.POST, "/api/v1/jobs/:jobId/start", request
                        -> io.activej.promise.Promise.of(
                        ResponseBuilder.status(501).json(java.util.Map.of("error", "Not Implemented")).build()))
                .addAsyncRoute(HttpMethod.POST, "/api/v1/jobs/:jobId/stop", request
                        -> io.activej.promise.Promise.of(
                        ResponseBuilder.status(501).json(java.util.Map.of("error", "Not Implemented")).build()))
                // Run execution endpoints - reuse RunController.run for job creation and
                // leave run listing/inspection unimplemented (stubbed 501)
                .addAsyncRoute(HttpMethod.POST, "/api/v1/jobs/:jobId/runs", request
                        -> runController.run(request))
                .addAsyncRoute(HttpMethod.GET, "/api/v1/jobs/:jobId/runs", request
                        -> io.activej.promise.Promise.of(
                        ResponseBuilder.status(501).json(java.util.Map.of("error", "Not Implemented")).build()))
                .addAsyncRoute(HttpMethod.GET, "/api/v1/jobs/:jobId/runs/:runId", request
                        -> io.activej.promise.Promise.of(
                        ResponseBuilder.status(501).json(java.util.Map.of("error", "Not Implemented")).build()))
                .addAsyncRoute(HttpMethod.GET, "/api/v1/jobs/:jobId/runs/:runId/logs", request
                        -> io.activej.promise.Promise.of(
                        ResponseBuilder.status(501).json(java.util.Map.of("error", "Not Implemented")).build()))
                // WebSocket endpoints for real-time updates
                .addAsyncRoute(HttpMethod.GET, "/ws/jobs/:jobId", request
                        -> io.activej.promise.Promise.of(
                        ResponseBuilder.status(501).json(java.util.Map.of("error", "Not Implemented")).build()))
                // Observability endpoints (health, metrics)
                .withHealthCheck("/healthz")
                .withMetrics("/internal/metrics")
                // Security filter (JWT authentication)
                // Wrap AsyncServlet-based filters to the core FilterChain.Filter functional interface
                .addFilter((request, next) -> jwtAuthFilter.serve(request))
                // Tenant context extraction (runs AFTER JWT auth to use tenant from JWT if available)
                .addFilter((request, next) -> tenantContextFilter.serve(request))
                // Global error handler filter
                .addFilter((request, next)
                        -> // Execute next in chain and map exceptions to a friendly HTTP response.
                        // Note: io.activej.promise.Promise does not have thenApply (that's
                        // a Promise method). Use mapException directly.
                        next.serve(request)
                        .whenComplete((response, error) -> {
                            if (error != null) {
                                log.error("Request processing error", error);
                            }
                        }))
                .build();
    }

    /**
     * Starts the HTTP server and initializes the eventloop.
     *
     * Note: The actual server startup (listening, eventloop thread management)
     * is handled by ActivejServerHandle or a dedicated server starter. This
     * factory just builds the configured server object.
     *
     * @return The built HTTP server
     */
    public HttpServer getServer() {
        return build();
    }
}
