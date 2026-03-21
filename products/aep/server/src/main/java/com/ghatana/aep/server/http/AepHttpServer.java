package com.ghatana.aep.server.http;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.compliance.AepSoc2ControlFramework;
import com.ghatana.aep.server.compliance.AepComplianceService;
import com.ghatana.aep.server.store.DataCloudPipelineStore;
import com.ghatana.aep.security.AepInputValidator;
import com.ghatana.aep.security.AepSecurityFilter;
import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.aep.server.http.controllers.AgentController;
import com.ghatana.aep.server.http.controllers.AnalyticsController;
import com.ghatana.aep.server.http.controllers.CapabilitiesController;
import com.ghatana.aep.server.http.controllers.ComplianceController;
import com.ghatana.aep.server.http.controllers.DeploymentController;
import com.ghatana.aep.server.http.controllers.HealthController;
import com.ghatana.aep.server.http.controllers.HitlController;
import com.ghatana.aep.server.http.controllers.LearningController;
import com.ghatana.aep.server.http.controllers.PatternController;
import com.ghatana.aep.server.http.controllers.PipelineController;
import com.ghatana.aep.server.http.controllers.SseController;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.orchestrator.deployment.http.DeploymentHttpAdapter;
import com.ghatana.orchestrator.deployment.service.DeploymentOrchestrator;
import com.ghatana.orchestrator.deployment.service.EventCloudDeploymentEventPublisher;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.repository.InMemoryPipelineRepository;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.pipeline.registry.service.CapabilitiesService;
import com.ghatana.pipeline.registry.validation.PipelineValidator;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP Server for AEP Standalone deployment.
 * Provides REST API endpoints for event processing, pattern management,
 * anomaly detection, and forecasting.
 *
 * @since 1.0.0
 */
public class AepHttpServer {

    private static final Logger log = LoggerFactory.getLogger(AepHttpServer.class);

    private final AepEngine engine;
    private final int port;
    private final ObjectMapper objectMapper;
    private final DeploymentHttpAdapter deploymentAdapter;
    private final PipelineRepository pipelineRepository;
    private final PipelineValidator pipelineValidator;
    private final CapabilitiesService capabilitiesService;
    /** Optional: wired in when AepLearningModule is active. Null-safe throughout. */
    private final HumanReviewQueue humanReviewQueue;
    /**
     * Optional Data-Cloud client for agent registry queries (AEP-P5).
     * {@code null} if Data-Cloud is not configured; endpoint falls back to an empty response.
     */
    @Nullable
    private final DataCloudClient agentDataCloud;
    private HttpServer server;
    private Eventloop eventloop;

    // Controllers (new architecture - Week 3 decomposition)
    private final HealthController healthController;
    private final PipelineController pipelineController;
    private final AgentController agentController;
    private final PatternController patternController;
    private final AnalyticsController analyticsController;
    private final DeploymentController deploymentController;
    private final HitlController hitlController;
    private final LearningController learningController;
    private final ComplianceController complianceController;
    private final SseController sseController;
    private final CapabilitiesController capabilitiesController;

    /** Compliance services — non-null when agentDataCloud is configured. */
    @Nullable
    private final AepComplianceService complianceService;
    private final AepSoc2ControlFramework soc2Framework = new AepSoc2ControlFramework();

    /**
     * Whether pipelines are backed by Data-Cloud durable storage.
     * {@code false} means the in-memory repository is used (standalone / no-DC mode).
     */
    private final boolean durablePipelines;

    /** In-memory circular buffer of recent pipeline runs (event-loop thread only). */
    private final java.util.Deque<Map<String, Object>> recentRuns = new java.util.ArrayDeque<>();
    private static final int MAX_RECENT_RUNS = 1_000;

    /**
     * Active SSE subscriber queues keyed by tenantId (event-loop thread only).
     * Managed by {@link SseController}.
     */
    @SuppressWarnings("unused")
    private final Map<String, List<Object>> sseSubscribers = new java.util.HashMap<>();

    /**
     * Creates a new AEP HTTP server (without learning-loop endpoints or Data-Cloud registry).
     *
     * @param engine the AEP engine instance
     * @param port the port to listen on
     */
    public AepHttpServer(AepEngine engine, int port) {
        this(engine, port, null, null, MetricsCollectorFactory.createNoop());
    }

    /**
     * Creates a new AEP HTTP server with optional learning-loop (HITL) endpoints.
     *
     * @param engine the AEP engine instance
     * @param port the port to listen on
     * @param humanReviewQueue HITL queue; may be {@code null} to disable HITL endpoints
     */
    public AepHttpServer(AepEngine engine, int port, HumanReviewQueue humanReviewQueue) {
        this(engine, port, humanReviewQueue, null, MetricsCollectorFactory.createNoop());
    }

    /**
     * Creates a new AEP HTTP server with optional learning-loop and Data-Cloud agent registry.
     *
     * @param engine           the AEP engine instance
     * @param port             the port to listen on
     * @param humanReviewQueue HITL queue; may be {@code null} to disable HITL endpoints
     * @param agentDataCloud   Data-Cloud client for agent registry queries (AEP-P5);
     *                         may be {@code null} if Data-Cloud is not configured
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable HumanReviewQueue humanReviewQueue,
                         @Nullable DataCloudClient agentDataCloud) {
        this(engine, port, humanReviewQueue, agentDataCloud, MetricsCollectorFactory.createNoop());
    }

    /**
     * Creates a new AEP HTTP server with full observability support.
     *
     * @param engine           the AEP engine instance
     * @param port             the port to listen on
     * @param humanReviewQueue HITL queue; may be {@code null} to disable HITL endpoints
     * @param agentDataCloud   Data-Cloud client for agent registry queries (AEP-P5);
     *                         may be {@code null} if Data-Cloud is not configured
     * @param metricsCollector metrics collector for observability; never {@code null}
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable HumanReviewQueue humanReviewQueue,
                         @Nullable DataCloudClient agentDataCloud,
                         MetricsCollector metricsCollector) {
        this.engine = engine;
        this.port = port;
        this.humanReviewQueue = humanReviewQueue;
        this.agentDataCloud = agentDataCloud;
        this.complianceService = agentDataCloud != null ? new AepComplianceService(agentDataCloud) : null;
        this.objectMapper = JsonUtils.getDefaultMapper();
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(
            new EventCloudDeploymentEventPublisher(engine.eventCloud()),
            metricsCollector);
        this.deploymentAdapter = new DeploymentHttpAdapter(orchestrator);
        if (agentDataCloud != null) {
            this.pipelineRepository = new DataCloudPipelineStore(agentDataCloud);
            this.durablePipelines = true;
            log.info("[init] PipelineRepository backed by Data-Cloud (durable storage)");
        } else {
            this.pipelineRepository = new InMemoryPipelineRepository();
            this.durablePipelines = false;
            log.info("[init] PipelineRepository backed by in-memory store (set DC_SERVER_URL for durable pipelines)");
        }
        this.pipelineValidator = new PipelineValidator();
        this.capabilitiesService = new CapabilitiesService();
        this.capabilitiesController = new CapabilitiesController(this.capabilitiesService, this::jsonResponse);

        // Initialize controllers (Week 3 decomposition)
        this.healthController = new HealthController("1.0.0-SNAPSHOT");
        this.pipelineController = new PipelineController(this.pipelineRepository, this.objectMapper);
        this.agentController = new AgentController(this.engine, this.agentDataCloud);
        this.patternController = new PatternController(this.engine);
        this.analyticsController = new AnalyticsController(this.engine);
        this.deploymentController = new DeploymentController(this.deploymentAdapter);
        this.sseController = new SseController();
        this.hitlController = new HitlController(this.humanReviewQueue,
            (tenantId, data) -> sseController.publishSseTo(tenantId, "hitl.update", data));
        this.learningController = new LearningController(this.agentDataCloud, this.humanReviewQueue);
        this.complianceController = new ComplianceController(this.complianceService, this.soc2Framework);
    }

    /**
     * Starts the HTTP server.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        eventloop = Eventloop.create();

        RoutingServlet router = RoutingServlet.builder(eventloop)
            // Health endpoints (delegated to HealthController)
            .with(HttpMethod.GET, "/health", request -> healthController.handle(request, "health"))
            .with(HttpMethod.GET, "/ready", request -> healthController.handle(request, "ready"))
            .with(HttpMethod.GET, "/live", request -> healthController.handle(request, "live"))

            // Info endpoints
            .with(HttpMethod.GET, "/info", this::handleInfo)
            .with(HttpMethod.GET, "/metrics", this::handleMetrics)

            // Event processing endpoints
            .with(HttpMethod.POST, "/api/v1/events", this::handleProcessEvent)
            .with(HttpMethod.POST, "/api/v1/events/batch", this::handleProcessBatch)

            // Deployment orchestration endpoints (delegated to DeploymentController)
            .with(HttpMethod.POST, "/api/v1/deployments", deploymentController::handleCreateDeployment)
            .with(HttpMethod.PUT, "/api/v1/deployments/:deploymentId", deploymentController::handleUpdateDeployment)
            .with(HttpMethod.DELETE, "/api/v1/deployments/:deploymentId", deploymentController::handleDeleteDeployment)

            // Pattern management endpoints (delegated to PatternController)
            .with(HttpMethod.GET, "/api/v1/patterns", patternController::handleListPatterns)
            .with(HttpMethod.POST, "/api/v1/patterns", patternController::handleRegisterPattern)
            .with(HttpMethod.GET, "/api/v1/patterns/:patternId", patternController::handleGetPattern)
            .with(HttpMethod.DELETE, "/api/v1/patterns/:patternId", patternController::handleDeletePattern)

            // Pipeline management endpoints (UI integration)
            .with(HttpMethod.GET, "/api/v1/pipelines", this::handleListPipelines)
            .with(HttpMethod.POST, "/api/v1/pipelines", this::handleCreatePipeline)
            .with(HttpMethod.POST, "/api/v1/pipelines/validate", this::handleValidatePipeline)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId", this::handleGetPipeline)
            .with(HttpMethod.PUT, "/api/v1/pipelines/:pipelineId", this::handleUpdatePipeline)
            .with(HttpMethod.DELETE, "/api/v1/pipelines/:pipelineId", this::handleDeletePipeline)

            // Capability endpoints (delegated to CapabilitiesController – P7-2c)
            .with(HttpMethod.GET, "/admin/capabilities/schemas", capabilitiesController::handleSchemaCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/connectors", capabilitiesController::handleConnectorCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/encodings", capabilitiesController::handleEncodingCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/transforms", capabilitiesController::handleTransformCapabilities)

            // Analytics endpoints (delegated to AnalyticsController)
            .with(HttpMethod.POST, "/api/v1/analytics/anomalies", analyticsController::handleDetectAnomalies)
            .with(HttpMethod.POST, "/api/v1/analytics/forecast", analyticsController::handleForecast)

            // Agent management endpoints (delegated to AgentController)
            .with(HttpMethod.GET, "/api/v1/agents", agentController::handleListAgents)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId", agentController::handleGetAgent)
            .with(HttpMethod.POST, "/api/v1/agents/:agentId/execute", agentController::handleExecuteAgent)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory", agentController::handleGetAgentMemory)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/episodes", agentController::handleGetAgentEpisodes)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/facts", agentController::handleGetAgentFacts)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/policies", agentController::handleGetAgentPolicies)
            .with(HttpMethod.DELETE, "/api/v1/agents/:agentId", agentController::handleDeregisterAgent)

            // Pipeline run & metrics endpoints (AEP-P7)
            .with(HttpMethod.GET, "/api/v1/runs", this::handleListPipelineRuns)
            .with(HttpMethod.POST, "/api/v1/runs/:runId/cancel", this::handleCancelRun)
            .with(HttpMethod.GET, "/api/v1/metrics/pipelines", this::handleGetPipelineMetrics)

            // HITL (Human-in-the-Loop) endpoints (delegated to HitlController)
            .with(HttpMethod.GET, "/api/v1/hitl/pending", hitlController::handleListPending)
            .with(HttpMethod.POST, "/api/v1/hitl/:reviewId/approve", hitlController::handleApprove)
            .with(HttpMethod.POST, "/api/v1/hitl/:reviewId/reject", hitlController::handleReject)

            // Learning system endpoints (delegated to LearningController)
            .with(HttpMethod.GET, "/api/v1/learning/episodes", learningController::handleListEpisodes)
            .with(HttpMethod.GET, "/api/v1/learning/policies", learningController::handleListPolicies)
            .with(HttpMethod.POST, "/api/v1/learning/policies/:policyId/approve", learningController::handleApprovePolicy)
            .with(HttpMethod.POST, "/api/v1/learning/policies/:policyId/reject", learningController::handleRejectPolicy)
            .with(HttpMethod.POST, "/api/v1/learning/reflect", learningController::handleTriggerReflection)

            // Server-Sent Events endpoints (delegated to SseController)
            .with(HttpMethod.GET, "/events/stream", sseController::handleSseStream)

            // Compliance endpoints (delegated to ComplianceController)
            .with(HttpMethod.POST, "/api/v1/compliance/gdpr/access", complianceController::handleGdprAccess)
            .with(HttpMethod.POST, "/api/v1/compliance/gdpr/erasure", complianceController::handleGdprErasure)
            .with(HttpMethod.POST, "/api/v1/compliance/gdpr/portability", complianceController::handleGdprPortability)
            .with(HttpMethod.POST, "/api/v1/compliance/ccpa/opt-out", complianceController::handleCcpaOptOut)
            .with(HttpMethod.GET,  "/api/v1/compliance/soc2/report", complianceController::handleSoc2Report)

            .build();

        // Wrap the router with the OWASP security filter (headers, CORS, rate limiting, payload size)
        String allowedOrigins = System.getenv().getOrDefault("AEP_CORS_ORIGINS", "*");
        AepSecurityFilter securityFilter = new AepSecurityFilter(router, allowedOrigins);

        // Wrap with authentication filter - enforces JWT auth when AEP_JWT_SECRET is set
        // Public endpoints (/health, /ready, /live, /info, /metrics, /events/stream) bypass auth
        AepAuthFilter authFilter = new AepAuthFilter(securityFilter);

        server = HttpServer.builder(eventloop, authFilter)
            .withListenPort(port)
            .build();

        // Initialize SSE heartbeat via SseController before the event loop starts.
        sseController.init(eventloop);
        CompletableFuture.runAsync(() -> {
            try {
                server.listen();
                log.info("AEP HTTP Server started on port {}", port);
                eventloop.run();
            } catch (Exception e) {
                log.error("Failed to start HTTP server", e);
            }
        });
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (eventloop != null) {
            // server.close() must be called from the reactor thread;
            // schedule it there and then break the eventloop.
            eventloop.execute(() -> {
                if (server != null) {
                    server.close();
                }
                eventloop.breakEventloop();
            });
        } else if (server != null) {
            // eventloop was never started — safe to call directly
            server.close();
        }
        log.info("AEP HTTP Server stopped");
    }

    // ==================== Info Endpoints ====================

    private Promise<HttpResponse> handleInfo(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "service", "AEP",
            "version", "1.0.0-SNAPSHOT",
            "description", "Agentic Event Processor",
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleMetrics(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "service", "aep",
            "uptime_seconds", System.currentTimeMillis() / 1000,
            "memory_used_mb", Runtime.getRuntime().totalMemory() / (1024 * 1024),
            "memory_free_mb", Runtime.getRuntime().freeMemory() / (1024 * 1024),
            "processors", Runtime.getRuntime().availableProcessors(),
            "timestamp", Instant.now().toString()
        )));
    }

    // ==================== Event Processing Endpoints ====================

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleProcessEvent(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> eventData = objectMapper.readValue(body, Map.class);

                String tenantId = AepInputValidator.validateTenantId(
                    (String) eventData.getOrDefault("tenantId", "default"));
                String eventType = AepInputValidator.validateEventType(
                    (String) eventData.getOrDefault("type", "unknown"));
                @SuppressWarnings("unchecked")
                Map<String, Object> rawPayload =
                    (Map<String, Object>) eventData.getOrDefault("payload", Map.of());
                Map<String, Object> payload = AepInputValidator.validatePayload(rawPayload);

                AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(), Instant.now());
                Instant startedAt = Instant.now();

                return engine.process(tenantId, event)
                    .map(result -> {
                        recordRun(result.eventId(), tenantId, null,
                            result.success() ? "SUCCEEDED" : "FAILED", startedAt);
                        return jsonResponse(Map.of(
                            "eventId", result.eventId(),
                            "success", result.success(),
                            "detections", result.detections().size(),
                            "timestamp", Instant.now().toString()
                        ));
                    });
            } catch (Exception e) {
                log.error("Error processing event", e);
                return Promise.of(errorResponse(400, "Invalid event data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read event body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleProcessBatch(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> batchData = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

                String tenantId = (String) batchData.getOrDefault("tenantId", "default");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> eventsData =
                    (List<Map<String, Object>>) batchData.getOrDefault("events", List.of());

                AepInputValidator.validateBatchSize(eventsData.size());
                if (eventsData.isEmpty()) {
                    return Promise.of(errorResponse(400, "Batch request must include non-empty events array"));
                }

                List<Promise<AepEngine.ProcessingResult>> processingPromises = eventsData.stream()
                    .map(eventData -> {
                        String eventType = (String) eventData.getOrDefault("type", "unknown");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload =
                            (Map<String, Object>) eventData.getOrDefault("payload", Map.of());
                        AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(), Instant.now());
                        return engine.process(tenantId, event);
                    })
                    .toList();

                return Promises.toList(processingPromises)
                    .map(results -> {
                        long successCount = results.stream().filter(AepEngine.ProcessingResult::success).count();
                        int totalDetections =
                            results.stream().mapToInt(result -> result.detections().size()).sum();
                        List<Map<String, Object>> events = results.stream()
                            .map(result -> Map.<String, Object>of(
                                "eventId", result.eventId(),
                                "success", result.success(),
                                "detections", result.detections().size()))
                            .toList();
                        return jsonResponse(Map.of(
                            "tenantId", tenantId,
                            "total", results.size(),
                            "successCount", successCount,
                            "failureCount", results.size() - successCount,
                            "totalDetections", totalDetections,
                            "events", events,
                            "timestamp", Instant.now().toString()
                        ));
                    })
                    .mapException(e -> new RuntimeException("Batch processing failed: " + e.getMessage(), e))
                    .then(Promise::of, e -> Promise.of(errorResponse(500, e.getMessage())));
            } catch (Exception e) {
                log.error("Error processing batch events", e);
                return Promise.of(errorResponse(400, "Invalid batch data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read batch body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    // ==================== Pipeline Management Endpoints ====================

    private Promise<HttpResponse> handleListPipelines(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String nameFilter = request.getQueryParameter("name");
        Boolean activeOnly = parseBooleanQuery(request.getQueryParameter("activeOnly"));
        int page = parseIntQuery(request.getQueryParameter("page"), 1);
        int size = parseIntQuery(request.getQueryParameter("size"), 50);

        return pipelineRepository.findAll(TenantId.of(tenantId), nameFilter, activeOnly, page, size)
            .map(result -> {
                List<Map<String, Object>> pipelines = result.content().stream()
                    .map(this::toPipelineResponse)
                    .toList();
                return jsonResponse(Map.of(
                    "pipelines", pipelines,
                    "count", pipelines.size(),
                    "total", result.totalElements(),
                    "page", result.pageNumber() + 1,
                    "size", result.pageSize(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to list pipelines: " + e.getMessage())));
    }

    private Promise<HttpResponse> handleGetPipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        return pipelineRepository.findById(pipelineId, TenantId.of(tenantId))
            .map(optPipeline -> optPipeline
                .map(pipeline -> jsonResponse(toPipelineResponse(pipeline)))
                .orElseGet(() -> errorResponse(404, "Pipeline not found: " + pipelineId)))
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to get pipeline: " + e.getMessage())));
    }

    private Promise<HttpResponse> handleCreatePipeline(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> pipeline = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String tenantId = resolveTenantId(request, pipeline);
                Pipeline candidate = mapToPipeline(pipeline, tenantId);

                if (candidate.getName() == null || candidate.getName().isBlank()) {
                    return Promise.of(errorResponse(400, "Pipeline name is required"));
                }

                return pipelineRepository.nextVersion(candidate.getName(), TenantId.of(tenantId))
                    .then(nextVersion -> {
                        candidate.setVersion(candidate.getVersion() > 0 ? candidate.getVersion() : nextVersion);
                        List<String> errors = new ArrayList<>(pipelineValidator.validate(candidate, null));
                        errors.addAll(pipelineValidator.validateDag(candidate.getConfig()));
                        if (!errors.isEmpty()) {
                            return Promise.of(jsonResponse(Map.of(
                                "valid", false,
                                "errors", errors,
                                "warnings", List.of(),
                                "timestamp", Instant.now().toString()
                            )));
                        }
                        return pipelineRepository.save(candidate)
                            .map(saved -> jsonResponse(toPipelineResponse(saved)));
                    })
                    .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to create pipeline: " + e.getMessage())));
            } catch (Exception e) {
                log.error("Error creating pipeline", e);
                return Promise.of(errorResponse(400, "Invalid pipeline data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pipeline body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleUpdatePipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> updateData = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                TenantId tenant = TenantId.of(tenantId);

                return pipelineRepository.findById(pipelineId, tenant)
                    .then(optExisting -> {
                        if (optExisting.isEmpty()) {
                            return Promise.of(errorResponse(404, "Pipeline not found: " + pipelineId));
                        }

                        PipelineRegistration existing = optExisting.get();
                        Pipeline updatePatch = mapToPipeline(updateData, tenantId);
                        updatePatch.setName(updatePatch.getName() != null ? updatePatch.getName() : existing.getName());
                        updatePatch.setTenantId(existing.getTenantId());
                        updatePatch.setVersion(existing.getVersion() + 1);

                        PipelineRegistration candidate = existing.newVersion();
                        candidate.setId(existing.getId());
                        candidate.setVersion(existing.getVersion() + 1);
                        candidate.updateFrom(updatePatch);
                        if (candidate.getUpdatedBy() == null || candidate.getUpdatedBy().isBlank()) {
                            candidate.setUpdatedBy("aep-http");
                        }

                        List<String> errors = new ArrayList<>(pipelineValidator.validate(candidate, existing));
                        errors.addAll(pipelineValidator.validateDag(candidate.getConfig()));
                        if (!errors.isEmpty()) {
                            return Promise.of(jsonResponse(Map.of(
                                "valid", false,
                                "errors", errors,
                                "warnings", List.of(),
                                "timestamp", Instant.now().toString()
                            )));
                        }

                        return pipelineRepository.save(candidate)
                            .map(saved -> jsonResponse(toPipelineResponse(saved)));
                    })
                    .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to update pipeline: " + e.getMessage())));
            } catch (Exception e) {
                log.error("Error updating pipeline", e);
                return Promise.of(errorResponse(400, "Invalid pipeline data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pipeline update body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleDeletePipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        TenantId tenant = TenantId.of(tenantId);
        return pipelineRepository.exists(pipelineId, tenant)
            .then(exists -> {
                if (!exists) {
                    return Promise.of(errorResponse(404, "Pipeline not found: " + pipelineId));
                }
                return pipelineRepository.delete(pipelineId, tenant, false, "aep-http")
                    .map(ignored -> jsonResponse(Map.of(
                        "deleted", true,
                        "pipelineId", pipelineId,
                        "timestamp", Instant.now().toString()
                    )));
            })
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to delete pipeline: " + e.getMessage())));
    }

    private Promise<HttpResponse> handleValidatePipeline(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> pipeline = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String tenantId = resolveTenantId(request, pipeline);
                Pipeline candidate = mapToPipeline(pipeline, tenantId);
                List<String> errors = new ArrayList<>(pipelineValidator.validate(candidate, null));
                errors.addAll(pipelineValidator.validateDag(candidate.getConfig()));
                List<String> warnings = new ArrayList<>();
                if (candidate.getId() == null || candidate.getId().isBlank()) {
                    warnings.add("Pipeline has no explicit id");
                }

                return Promise.of(jsonResponse(Map.of(
                    "valid", errors.isEmpty(),
                    "errors", errors,
                    "warnings", warnings,
                    "timestamp", Instant.now().toString()
                )));
            } catch (Exception e) {
                log.error("Error validating pipeline", e);
                return Promise.of(errorResponse(400, "Invalid pipeline validation payload: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pipeline validate body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Pipeline mapToPipeline(Map<String, Object> payload, String tenantId) {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(asString(payload.get("id")));
        pipeline.setTenantId(TenantId.of(tenantId));
        pipeline.setName(asString(payload.get("name")));
        pipeline.setDescription(asString(payload.get("description")));
        pipeline.setActive(payload.get("active") == null || Boolean.TRUE.equals(payload.get("active")));
        pipeline.setVersion(payload.get("version") instanceof Number n ? n.intValue() : 0);
        pipeline.setConfig(buildPipelineConfig(payload));
        pipeline.setCreatedAt(Instant.now());
        pipeline.setUpdatedAt(Instant.now());
        pipeline.setCreatedBy(asString(payload.getOrDefault("createdBy", "aep-http")));
        pipeline.setUpdatedBy(asString(payload.getOrDefault("updatedBy", "aep-http")));
        return pipeline;
    }

    private String buildPipelineConfig(Map<String, Object> payload) {
        Object rawConfig = payload.get("config");
        try {
            if (rawConfig instanceof String configText && !configText.isBlank()) {
                return configText;
            }
            if (rawConfig != null) {
                return objectMapper.writeValueAsString(rawConfig);
            }
            Object stages = payload.get("stages");
            if (stages instanceof List<?>) {
                return objectMapper.writeValueAsString(Map.of("stages", stages));
            }
            return objectMapper.writeValueAsString(Map.of("stages", List.of()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline config payload", e);
        }
    }

    private Map<String, Object> toPipelineResponse(PipelineRegistration pipeline) {
        return Map.of(
            "id", pipeline.getId() != null ? pipeline.getId() : "",
            "tenantId", pipeline.getTenantId() != null ? pipeline.getTenantId().value() : "default",
            "name", pipeline.getName() != null ? pipeline.getName() : "",
            "description", pipeline.getDescription() != null ? pipeline.getDescription() : "",
            "version", pipeline.getVersion(),
            "active", pipeline.isActive(),
            "config", pipeline.getConfig() != null ? parseJsonObject(pipeline.getConfig()) : Map.of(),
            "createdAt", pipeline.getCreatedAt() != null ? pipeline.getCreatedAt().toString() : Instant.now().toString(),
            "updatedAt", pipeline.getUpdatedAt() != null ? pipeline.getUpdatedAt().toString() : Instant.now().toString(),
            "updatedBy", pipeline.getUpdatedBy() != null ? pipeline.getUpdatedBy() : "aep-http"
        );
    }

    private Object parseJsonObject(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return json;
        }
    }

    // ==================== Pipeline Runs & Metrics Endpoints (AEP-P7) ====================

    /**
     * Lists recent pipeline runs tracked in-memory.
     * Runs are recorded whenever an event or batch is processed.
     * Optionally filtered by {@code pipelineId} query parameter.
     *
     * @return 200 with list of recent runs
     */
    private Promise<HttpResponse> handleListPipelineRuns(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String pipelineFilter = request.getQueryParameter("pipelineId");
        List<Map<String, Object>> runs = recentRuns.stream()
            .filter(r -> tenantId.equals(r.get("tenantId")))
            .filter(r -> pipelineFilter == null || pipelineFilter.equals(r.get("pipelineId")))
            .collect(java.util.stream.Collectors.toList());
        return Promise.of(jsonResponse(Map.of(
            "runs", runs,
            "count", runs.size(),
            "tenantId", tenantId,
            "timestamp", Instant.now().toString()
        )));
    }

    /**
     * Cancels a pipeline run. In-memory runs cannot be preempted mid-flight; returns 200
     * once the run entry is marked CANCELLED (idempotent — run may already be complete).
     *
     * @return 200 with cancel confirmation, 404 if run not found
     */
    private Promise<HttpResponse> handleCancelRun(HttpRequest request) {
        String runId = request.getPathParameter("runId");
        if (runId == null || runId.isBlank()) {
            return Promise.of(errorResponse(400, "runId path parameter is required"));
        }
        boolean found = recentRuns.stream().anyMatch(r -> runId.equals(r.get("runId")));
        if (!found) {
            return Promise.of(errorResponse(404, "Run not found: " + runId));
        }
        recentRuns.stream()
            .filter(r -> runId.equals(r.get("runId")))
            .findFirst()
            .ifPresent(r -> r.put("status", "CANCELLED"));
        return Promise.of(jsonResponse(Map.of(
            "runId", runId,
            "status", "CANCELLED",
            "timestamp", Instant.now().toString()
        )));
    }

    /**
     * Returns aggregate pipeline metrics computed from the in-memory run buffer.
     *
     * @return 200 with pipeline metrics summary
     */
    private Promise<HttpResponse> handleGetPipelineMetrics(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        List<Map<String, Object>> tenantRuns = recentRuns.stream()
            .filter(r -> tenantId.equals(r.get("tenantId")))
            .collect(java.util.stream.Collectors.toList());
        long succeeded = tenantRuns.stream().filter(r -> "SUCCEEDED".equals(r.get("status"))).count();
        long failed = tenantRuns.stream().filter(r -> "FAILED".equals(r.get("status"))).count();
        long cancelled = tenantRuns.stream().filter(r -> "CANCELLED".equals(r.get("status"))).count();
        double successRate = tenantRuns.isEmpty() ? 0.0 : (double) succeeded / tenantRuns.size();

        return pipelineRepository.findAll(TenantId.of(tenantId), null, null, 1, 1_000)
            .map(result -> {
                List<Map<String, Object>> metrics = result.content().stream()
                    .map(p -> {
                        long pRuns = tenantRuns.stream()
                            .filter(r -> p.getId() != null && p.getId().equals(r.get("pipelineId")))
                            .count();
                        return Map.<String, Object>of(
                            "pipelineId", p.getId() != null ? p.getId() : "",
                            "pipelineName", p.getName() != null ? p.getName() : "",
                            "runCount", pRuns,
                            "active", p.isActive()
                        );
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "metrics", metrics,
                    "summary", Map.of(
                        "totalRuns", tenantRuns.size(),
                        "succeeded", succeeded,
                        "failed", failed,
                        "cancelled", cancelled,
                        "successRate", successRate
                    ),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e ->
                Promise.of(errorResponse(500, "Failed to get pipeline metrics: " + e.getMessage())));
    }

    // ==================== Run Tracking ====================

    /**
     * Records a processing result into the bounded in-memory run buffer (event-loop thread only)
     * and publishes a {@code run.update} SSE event to all subscribers of that tenant.
     */
    private void recordRun(String runId, String tenantId, @Nullable String pipelineId,
                           String status, Instant startedAt) {
        if (recentRuns.size() >= MAX_RECENT_RUNS) {
            recentRuns.pollFirst();
        }
        Map<String, Object> run = new java.util.HashMap<>();
        run.put("runId", runId);
        run.put("tenantId", tenantId);
        run.put("pipelineId", pipelineId != null ? pipelineId : "event");
        run.put("status", status);
        run.put("startedAt", startedAt.toString());
        run.put("completedAt", Instant.now().toString());
        recentRuns.addLast(run);
        sseController.publishSseTo(tenantId, "run.update", run);
    }

    // ==================== SSE Broadcast (Public API) ====================

    /**
     * Broadcasts an SSE event to all active subscribers of the given tenant.
     * Thread-safe: delegates to {@link SseController#broadcastSseEvent}.
     *
     * @param tenantId target tenant (or {@code "*"} to broadcast to all tenants)
     * @param type     SSE event type
     * @param data     payload map
     */
    public void broadcastSseEvent(String tenantId, String type, Map<String, Object> data) {
        sseController.broadcastSseEvent(tenantId, type, data);
    }

    // ==================== Helper Methods ====================

    /**
     * Resolves the tenant ID from the {@code X-Tenant-Id} header or {@code tenantId} query parameter,
     * defaulting to {@code "default"}.
     */
    private String resolveTenantId(HttpRequest request) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        return (fromQuery != null && !fromQuery.isBlank()) ? fromQuery : "default";
    }

    private String resolveTenantId(HttpRequest request, Map<String, Object> payload) {
        String tenantId = request.getQueryParameter("tenantId");
        if ((tenantId == null || tenantId.isBlank()) && payload != null) {
            tenantId = asString(payload.get("tenantId"));
        }
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    private int parseIntQuery(String value, int fallback) {
        try {
            return value != null ? Integer.parseInt(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Boolean parseBooleanQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private HttpResponse jsonResponse(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), HttpHeaderValue.of("nosniff"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    private HttpResponse errorResponse(int code, String message) {
        try {
            String safeMessage = message != null ? message.replace("\\", "\\\\").replace("\"", "\\\"") : "error";
            String json = objectMapper.writeValueAsString(Map.of(
                "error", safeMessage,
                "code", code,
                "timestamp", Instant.now().toString()
            ));
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), HttpHeaderValue.of("nosniff"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code)
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }
}
