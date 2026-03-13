package com.ghatana.aep.launcher.http;

import com.ghatana.aep.AepEngine;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewDecision;
import com.ghatana.agent.learning.review.ReviewFilter;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.agent.learning.review.ReviewItemType;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import com.ghatana.orchestrator.deployment.contract.DeploymentResponse;
import com.ghatana.orchestrator.deployment.http.DeploymentHttpAdapter;
import com.ghatana.orchestrator.deployment.service.DeploymentOrchestrator;
import com.ghatana.orchestrator.deployment.service.EventCloudDeploymentEventPublisher;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.repository.InMemoryPipelineRepository;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.pipeline.registry.service.CapabilitiesService;
import com.ghatana.pipeline.registry.validation.PipelineValidator;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.csp.queue.ChannelBuffer;
import io.activej.bytebuf.ByteBuf;
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

    /** In-memory circular buffer of recent pipeline runs (event-loop thread only). */
    private final java.util.Deque<Map<String, Object>> recentRuns = new java.util.ArrayDeque<>();
    private static final int MAX_RECENT_RUNS = 1_000;

    /**
     * Active SSE subscriber queues keyed by tenantId (event-loop thread only).
     * Each entry is a live ChannelBuffer that pushes bytes to a connected client.
     */
    private final Map<String, List<ChannelBuffer<ByteBuf>>> sseSubscribers = new java.util.HashMap<>();

    /**
     * Creates a new AEP HTTP server (without learning-loop endpoints or Data-Cloud registry).
     *
     * @param engine the AEP engine instance
     * @param port the port to listen on
     */
    public AepHttpServer(AepEngine engine, int port) {
        this(engine, port, null, null);
    }

    /**
     * Creates a new AEP HTTP server with optional learning-loop (HITL) endpoints.
     *
     * @param engine the AEP engine instance
     * @param port the port to listen on
     * @param humanReviewQueue HITL queue; may be {@code null} to disable HITL endpoints
     */
    public AepHttpServer(AepEngine engine, int port, HumanReviewQueue humanReviewQueue) {
        this(engine, port, humanReviewQueue, null);
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
        this.engine = engine;
        this.port = port;
        this.humanReviewQueue = humanReviewQueue;
        this.agentDataCloud = agentDataCloud;
        this.objectMapper = JsonUtils.getDefaultMapper();
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(
            new EventCloudDeploymentEventPublisher(engine.eventCloud()),
            new NoopMetricsCollector());
        this.deploymentAdapter = new DeploymentHttpAdapter(orchestrator);
        this.pipelineRepository = new InMemoryPipelineRepository();
        this.pipelineValidator = new PipelineValidator();
        this.capabilitiesService = new CapabilitiesService();
    }

    /**
     * Starts the HTTP server.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        eventloop = Eventloop.create();
        
        RoutingServlet router = RoutingServlet.builder(eventloop)
            // Health endpoints
            .with(HttpMethod.GET, "/health", this::handleHealth)
            .with(HttpMethod.GET, "/ready", this::handleReady)
            .with(HttpMethod.GET, "/live", this::handleLive)
            
            // Info endpoints
            .with(HttpMethod.GET, "/info", this::handleInfo)
            .with(HttpMethod.GET, "/metrics", this::handleMetrics)
            
            // Event processing endpoints
            .with(HttpMethod.POST, "/api/v1/events", this::handleProcessEvent)
            .with(HttpMethod.POST, "/api/v1/events/batch", this::handleProcessBatch)

            // Deployment orchestration endpoints
            .with(HttpMethod.POST, "/api/v1/deployments", this::handleCreateDeployment)
            .with(HttpMethod.PUT, "/api/v1/deployments/:deploymentId", this::handleUpdateDeployment)
            .with(HttpMethod.DELETE, "/api/v1/deployments/:deploymentId", this::handleDeleteDeployment)
            
            // Pattern management endpoints
            .with(HttpMethod.GET, "/api/v1/patterns", this::handleListPatterns)
            .with(HttpMethod.POST, "/api/v1/patterns", this::handleRegisterPattern)
            .with(HttpMethod.GET, "/api/v1/patterns/:patternId", this::handleGetPattern)
            .with(HttpMethod.DELETE, "/api/v1/patterns/:patternId", this::handleDeletePattern)

            // Pipeline management endpoints (UI integration)
            .with(HttpMethod.GET, "/api/v1/pipelines", this::handleListPipelines)
            .with(HttpMethod.POST, "/api/v1/pipelines", this::handleCreatePipeline)
            .with(HttpMethod.POST, "/api/v1/pipelines/validate", this::handleValidatePipeline)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId", this::handleGetPipeline)
            .with(HttpMethod.PUT, "/api/v1/pipelines/:pipelineId", this::handleUpdatePipeline)
            .with(HttpMethod.DELETE, "/api/v1/pipelines/:pipelineId", this::handleDeletePipeline)

            // Capability endpoints (AEP UI integration)
            .with(HttpMethod.GET, "/admin/capabilities/schemas", this::handleSchemaCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/connectors", this::handleConnectorCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/encodings", this::handleEncodingCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/transforms", this::handleTransformCapabilities)
            
            // Analytics endpoints
            .with(HttpMethod.POST, "/api/v1/analytics/anomalies", this::handleDetectAnomalies)
            .with(HttpMethod.POST, "/api/v1/analytics/forecast", this::handleForecast)

            // Agent management endpoints (AEP-P5/P7)
            .with(HttpMethod.GET, "/api/v1/agents", this::handleListAgents)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId", this::handleGetAgent)
            .with(HttpMethod.POST, "/api/v1/agents/:agentId/execute", this::handleExecuteAgent)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory", this::handleGetAgentMemory)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/episodes", this::handleGetAgentEpisodes)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/facts", this::handleGetAgentFacts)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/policies", this::handleGetAgentPolicies)
            .with(HttpMethod.DELETE, "/api/v1/agents/:agentId", this::handleDeregisterAgent)

            // Pipeline run & metrics endpoints (AEP-P7)
            .with(HttpMethod.GET, "/api/v1/runs", this::handleListPipelineRuns)
            .with(HttpMethod.POST, "/api/v1/runs/:runId/cancel", this::handleCancelRun)
            .with(HttpMethod.GET, "/api/v1/metrics/pipelines", this::handleGetPipelineMetrics)

            // HITL (Human-in-the-Loop) endpoints (AEP-P7)
            .with(HttpMethod.GET, "/api/v1/hitl/pending", this::handleListHitlPending)
            .with(HttpMethod.POST, "/api/v1/hitl/:reviewId/approve", this::handleHitlApprove)
            .with(HttpMethod.POST, "/api/v1/hitl/:reviewId/reject", this::handleHitlReject)

            // Learning system endpoints (AEP-P7)
            .with(HttpMethod.GET, "/api/v1/learning/episodes", this::handleListEpisodes)
            .with(HttpMethod.GET, "/api/v1/learning/policies", this::handleListPolicies)
            .with(HttpMethod.POST, "/api/v1/learning/policies/:policyId/approve", this::handleApprovePolicy)
            .with(HttpMethod.POST, "/api/v1/learning/policies/:policyId/reject", this::handleRejectPolicy)
            .with(HttpMethod.POST, "/api/v1/learning/reflect", this::handleTriggerReflection)

            // Server-Sent Events endpoints for real-time UI updates (AEP-P7)
            .with(HttpMethod.GET, "/events/stream", this::handleSseStream)

            .build();

        server = HttpServer.builder(eventloop, router)
            .withListenPort(port)
            .build();

        // Schedule heartbeat before the event loop starts so it begins on the first iteration.
        eventloop.execute(this::scheduleHeartbeat);
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

    // ==================== Health Endpoints ====================

    private Promise<HttpResponse> handleHealth(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString(),
            "service", "aep"
        )));
    }

    private Promise<HttpResponse> handleReady(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "status", "READY",
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleLive(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "status", "LIVE",
            "timestamp", Instant.now().toString()
        )));
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

                String tenantId = (String) eventData.getOrDefault("tenantId", "default");
                String eventType = (String) eventData.getOrDefault("type", "unknown");
                Map<String, Object> payload = (Map<String, Object>) eventData.getOrDefault("payload", Map.of());

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

                        Pipeline existing = optExisting.get();
                        Pipeline updatePatch = mapToPipeline(updateData, tenantId);
                        updatePatch.setName(updatePatch.getName() != null ? updatePatch.getName() : existing.getName());
                        updatePatch.setTenantId(existing.getTenantId());
                        updatePatch.setVersion(existing.getVersion() + 1);

                        Pipeline candidate = existing.newVersion();
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

    // ==================== Deployment Endpoints ====================

    private Promise<HttpResponse> handleCreateDeployment(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                DeploymentRequest deploymentRequest = objectMapper.readValue(body, DeploymentRequest.class);
                DeploymentResponse response = deploymentAdapter.handleDeploymentRequest(deploymentRequest);
                return Promise.of(jsonResponse(toMap(response)));
            } catch (Exception e) {
                log.error("Error creating deployment", e);
                return Promise.of(errorResponse(400, "Invalid deployment request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read deployment body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleUpdateDeployment(HttpRequest request) {
        String deploymentId = request.getPathParameter("deploymentId");
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                DeploymentRequest deploymentRequest = objectMapper.readValue(body, DeploymentRequest.class);
                DeploymentResponse response = deploymentAdapter.handleUpdateRequest(deploymentId, deploymentRequest);
                return Promise.of(jsonResponse(toMap(response)));
            } catch (Exception e) {
                log.error("Error updating deployment", e);
                return Promise.of(errorResponse(400, "Invalid update deployment request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read deployment update body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleDeleteDeployment(HttpRequest request) {
        try {
            String deploymentId = request.getPathParameter("deploymentId");
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null || tenantId.isBlank()) {
                return Promise.of(errorResponse(400, "tenantId query parameter is required"));
            }

            DeploymentResponse response = deploymentAdapter.handleUndeployRequest(deploymentId, tenantId);
            return Promise.of(jsonResponse(toMap(response)));
        } catch (Exception e) {
            log.error("Error deleting deployment", e);
            return Promise.of(errorResponse(400, "Invalid delete deployment request: " + e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(
            value,
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
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

    private Map<String, Object> toPipelineResponse(Pipeline pipeline) {
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

    // ==================== Capability Endpoints ====================

    private Promise<HttpResponse> handleSchemaCapabilities(HttpRequest request) {
        Map<String, Object> response = new java.util.HashMap<>(capabilitiesService.getSchemaFormats());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse(response));
    }

    private Promise<HttpResponse> handleConnectorCapabilities(HttpRequest request) {
        Map<String, Object> response = new java.util.HashMap<>(capabilitiesService.getConnectors());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse(response));
    }

    private Promise<HttpResponse> handleEncodingCapabilities(HttpRequest request) {
        Map<String, Object> response = new java.util.HashMap<>(capabilitiesService.getEncodings());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse(response));
    }

    private Promise<HttpResponse> handleTransformCapabilities(HttpRequest request) {
        Map<String, Object> response = new java.util.HashMap<>(capabilitiesService.getTransforms());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse(response));
    }

    // ==================== Pattern Management Endpoints ====================

    private Promise<HttpResponse> handleListPatterns(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        
        return engine.listPatterns(tenantId)
            .map(patterns -> jsonResponse(Map.of(
                "patterns", patterns,
                "count", patterns.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleRegisterPattern(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> patternData = objectMapper.readValue(body, Map.class);

                String tenantId = (String) patternData.getOrDefault("tenantId", "default");
                String name = (String) patternData.get("name");
                String description = (String) patternData.getOrDefault("description", "");
                String typeStr = (String) patternData.getOrDefault("type", "CUSTOM");
                Map<String, Object> config = (Map<String, Object>) patternData.getOrDefault("config", Map.of());

                AepEngine.PatternType type = AepEngine.PatternType.valueOf(typeStr.toUpperCase());
                AepEngine.PatternDefinition definition = new AepEngine.PatternDefinition(name, description, type, config);

                return engine.registerPattern(tenantId, definition)
                    .map(pattern -> jsonResponse(Map.of(
                        "pattern", Map.of(
                            "id", pattern.id(),
                            "name", pattern.name(),
                            "type", pattern.type().name()
                        ),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error registering pattern", e);
                return Promise.of(errorResponse(400, "Invalid pattern data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pattern body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleGetPattern(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        String patternId = request.getPathParameter("patternId");
        
        return engine.getPattern(tenantId, patternId)
            .map(optPattern -> {
                if (optPattern.isPresent()) {
                    AepEngine.Pattern pattern = optPattern.get();
                    return jsonResponse(Map.of(
                        "pattern", Map.of(
                            "id", pattern.id(),
                            "name", pattern.name(),
                            "type", pattern.type().name(),
                            "createdAt", pattern.createdAt().toString()
                        )
                    ));
                } else {
                    return errorResponse(404, "Pattern not found: " + patternId);
                }
            });
    }

    private Promise<HttpResponse> handleDeletePattern(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        String patternId = request.getPathParameter("patternId");
        
        return engine.deletePattern(tenantId, patternId)
            .map(v -> jsonResponse(Map.of(
                "deleted", true,
                "patternId", patternId,
                "timestamp", Instant.now().toString()
            )));
    }

    // ==================== Analytics Endpoints ====================

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleDetectAnomalies(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = objectMapper.readValue(body, Map.class);

                String tenantId = (String) data.getOrDefault("tenantId", "default");
                List<Map<String, Object>> eventsData = (List<Map<String, Object>>) data.getOrDefault("events", List.of());

                List<AepEngine.Event> events = eventsData.stream()
                    .map(e -> new AepEngine.Event(
                        (String) e.getOrDefault("type", "unknown"),
                        (Map<String, Object>) e.getOrDefault("payload", Map.of()),
                        Map.of(),
                        Instant.now()
                    ))
                    .toList();

                return engine.detectAnomalies(tenantId, events)
                    .map(anomalies -> jsonResponse(Map.of(
                        "anomalies", anomalies,
                        "count", anomalies.size(),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error detecting anomalies", e);
                return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read anomaly detection body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleForecast(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = objectMapper.readValue(body, Map.class);

                String tenantId = (String) data.getOrDefault("tenantId", "default");
                String metric = (String) data.getOrDefault("metric", "default");
                List<Map<String, Object>> pointsData = (List<Map<String, Object>>) data.getOrDefault("points", List.of());

                List<AepEngine.DataPoint> points = pointsData.stream()
                    .map(p -> new AepEngine.DataPoint(
                        Instant.parse((String) p.get("timestamp")),
                        ((Number) p.get("value")).doubleValue()
                    ))
                    .toList();

                AepEngine.TimeSeriesData tsData = new AepEngine.TimeSeriesData(metric, points);

                return engine.forecast(tenantId, tsData)
                    .map(forecast -> jsonResponse(Map.of(
                        "metric", forecast.metric(),
                        "predictions", forecast.predictions(),
                        "confidence", forecast.confidence(),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error forecasting", e);
                return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read forecast body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    // ==================== Agent Management Endpoints (AEP-P5/P7) ====================

    /**
     * Lists all registered agents for the requesting tenant.
     * Tenant is resolved from the {@code X-Tenant-Id} request header (defaults to {@code "default"}).
     *
     * <p>Queries the {@code agent-registry} collection in Data-Cloud and returns a lightweight
     * summary (id, name, type, status) for each registered agent.
     * Falls back to an empty list when Data-Cloud is not configured.
     */
    private Promise<HttpResponse> handleListAgents(HttpRequest request) {
        String tenantId = resolveTenantId(request);

        if (agentDataCloud == null) {
            log.debug("[agents] DataCloudClient not configured — returning empty agent list for tenant={}", tenantId);
            return Promise.of(jsonResponse(Map.of(
                "tenantId", tenantId,
                "agents", List.of(),
                "count", 0,
                "timestamp", Instant.now().toString(),
                "note", "DataCloud not configured; start with DC_SERVER_URL to enable agent registry"
            )));
        }

        return agentDataCloud.query(tenantId, "agent-registry", DataCloudClient.Query.limit(1000))
            .map(entities -> {
                List<Map<String, Object>> summaries = entities.stream()
                    .map(e -> Map.<String, Object>of(
                        "id", e.id(),
                        "name", e.data().getOrDefault("name", e.id()),
                        "type", e.data().getOrDefault("type", "unknown"),
                        "status", e.data().getOrDefault("status", "ACTIVE"),
                        "tenantId", tenantId
                    ))
                    .toList();
                return jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "agents", summaries,
                    "count", summaries.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] list failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to list agents: " + e.getMessage()));
            });
    }

    /**
     * Returns the detail of a single agent by ID.
     *
     * <p>Queries the {@code agent-registry} collection in Data-Cloud.
     *
     * @return 200 with agent detail, 404 if not found, 503 if Data-Cloud not configured
     */
    private Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }

        if (agentDataCloud == null) {
            return Promise.of(errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }

        String tenantId = resolveTenantId(request);
        return agentDataCloud.findById(tenantId, "agent-registry", agentId)
            .map(opt -> opt
                .map(e -> jsonResponse(Map.of(
                    "id", e.id(),
                    "tenantId", tenantId,
                    "data", e.data(),
                    "timestamp", Instant.now().toString()
                )))
                .orElse(errorResponse(404, "Agent not found: " + agentId)))
            .then(Promise::of, e -> {
                log.error("[agents] get failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to get agent: " + e.getMessage()));
            });
    }

    /**
     * Executes a targeted agent invocation by submitting an agent-execute event to the AEP engine.
     *
     * <p>Request body: {@code {"input": {...}, "tenantId": "..."}} (tenantId optional; falls back to header).
     * The event is submitted as type {@code "agent.invocation"} with the agentId encoded in the payload,
     * allowing the AEP operator catalog to route it to the correct agent pipeline.
     *
     * @return 200 with processing result, 400 on bad request, 500 on engine failure
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleExecuteAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        // loadBody() is asynchronous — use .then() to process after body is loaded
        return request.loadBody()
            .then(buf -> {
                try {
                    String bodyStr = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> reqBody = bodyStr.isBlank()
                        ? Map.of()
                        : objectMapper.readValue(bodyStr, Map.class);

                    String tenantId = reqBody.containsKey("tenantId")
                        ? (String) reqBody.get("tenantId")
                        : resolveTenantId(request);

                    Map<String, Object> input = reqBody.containsKey("input")
                        ? (Map<String, Object>) reqBody.get("input")
                        : Map.of();

                    // Wrap as an AEP agent-invocation event; the operator catalog will route by agentId.
                    Map<String, Object> payload = new java.util.HashMap<>(input);
                    payload.put("agentId", agentId);
                    payload.put("tenantId", tenantId);

                    AepEngine.Event event = new AepEngine.Event(
                        "agent.invocation",
                        Map.copyOf(payload),
                        Map.of("agentId", agentId),
                        Instant.now()
                    );

                    return engine.process(tenantId, event)
                        .map(result -> jsonResponse(Map.of(
                            "agentId", agentId,
                            "tenantId", tenantId,
                            "eventId", result.eventId(),
                            "success", result.success(),
                            "detections", result.detections().size(),
                            "timestamp", Instant.now().toString()
                        )))
                        .then(Promise::of, e -> {
                            log.error("[agents] execute failed for agentId={}: {}", agentId, e.getMessage(), e);
                            return Promise.of(errorResponse(500, "Agent execution failed: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.error("[agents] bad request for execute agentId={}: {}", agentId, e.getMessage(), e);
                    return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
                }
            }, e -> {
                log.error("[agents] failed to read body for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(400, "Failed to read request body"));
            });
    }

    /**
     * Returns a summary of stored memory items for a given agent.
     *
     * <p>Queries the {@code dc_memory} collection in Data-Cloud filtered by {@code agentId}.
     * Returns item counts grouped by memory type (episodic, semantic, procedural, preference).
     * Falls back to a 503 when Data-Cloud is not configured.
     *
     * @return 200 with memory summary, 503 if Data-Cloud not configured
     */
    private Promise<HttpResponse> handleGetAgentMemory(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }

        if (agentDataCloud == null) {
            return Promise.of(errorResponse(503,
                "Agent memory not available — DataCloudClient not configured"));
        }

        String tenantId = resolveTenantId(request);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq("agentId", agentId))
            .limit(10_000)
            .build();

        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                long episodic = items.stream()
                    .filter(e -> "EPISODIC".equals(e.data().get("type"))).count();
                long semantic = items.stream()
                    .filter(e -> "SEMANTIC".equals(e.data().get("type"))).count();
                long procedural = items.stream()
                    .filter(e -> "PROCEDURAL".equals(e.data().get("type"))).count();
                long preference = items.stream()
                    .filter(e -> "PREFERENCE".equals(e.data().get("type"))).count();
                long other = items.size() - episodic - semantic - procedural - preference;

                return jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "total", items.size(),
                    "byType", Map.of(
                        "episodic", episodic,
                        "semantic", semantic,
                        "procedural", procedural,
                        "preference", preference,
                        "other", other
                    ),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] memory query failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to query agent memory: " + e.getMessage()));
            });
    }

    /**
     * Lists episodic memory items for a specific agent, including input/output summaries.
     *
     * <p>Queries the {@code dc_memory} collection for items of type {@code EPISODIC}
     * belonging to the given agent and tenant. Supports a {@code limit} query parameter
     * (default 50, max 500).
     *
     * @return 200 with list of episode records, 503 if Data-Cloud not configured
     */
    private Promise<HttpResponse> handleGetAgentEpisodes(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(errorResponse(503,
                "Episode store not available — DataCloudClient not configured"));
        }
        String tenantId = resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 500) : 50;

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "EPISODIC")
            ))
            .limit(limit)
            .build();

        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                List<Map<String, Object>> episodes = items.stream()
                    .map(e -> {
                        Map<String, Object> ep = new java.util.HashMap<>(e.data());
                        ep.put("id", e.id());
                        return ep;
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "episodes", episodes,
                    "count", episodes.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] episodes query failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to query agent episodes: " + e.getMessage()));
            });
    }

    /**
     * Lists semantic fact items (SEMANTIC type) for a specific agent.
     *
     * <p>These represent distilled knowledge extracted from episodic memories
     * during consolidation (e.g., "user-event-v2 requires field userId").
     *
     * @return 200 with list of semantic facts, 503 if Data-Cloud not configured
     */
    private Promise<HttpResponse> handleGetAgentFacts(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(errorResponse(503,
                "Fact store not available — DataCloudClient not configured"));
        }
        String tenantId = resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 500) : 100;

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "SEMANTIC")
            ))
            .limit(limit)
            .build();

        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                List<Map<String, Object>> facts = items.stream()
                    .map(e -> {
                        Map<String, Object> fact = new java.util.HashMap<>(e.data());
                        fact.put("id", e.id());
                        return fact;
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "facts", facts,
                    "count", facts.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] facts query failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to query agent facts: " + e.getMessage()));
            });
    }

    /**
     * Lists procedural policy items (PROCEDURAL type) for a specific agent.
     *
     * <p>These represent learned action procedures extracted by the
     * EpisodicToProceduralConsolidator. Policies with confidence &lt; 0.7
     * may have pending HITL review items.
     *
     * @return 200 with list of procedural policies, 503 if Data-Cloud not configured
     */
    private Promise<HttpResponse> handleGetAgentPolicies(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(errorResponse(503,
                "Policy store not available — DataCloudClient not configured"));
        }
        String tenantId = resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 200) : 50;

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "PROCEDURAL")
            ))
            .limit(limit)
            .build();

        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                List<Map<String, Object>> policies = items.stream()
                    .map(e -> {
                        Map<String, Object> policy = new java.util.HashMap<>(e.data());
                        policy.put("id", e.id());
                        return policy;
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "policies", policies,
                    "count", policies.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] policies query failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to query agent policies: " + e.getMessage()));
            });
    }

    /**
     * Lists all pending HITL review items for the requesting tenant.
     *
     * @return 200 with list of pending items, or 501 if HITL queue is not configured
     */
    private Promise<HttpResponse> handleListHitlPending(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(errorResponse(501, "HITL queue not configured — start AEP with AepLearningModule"));
        }
        String tenantId = resolveTenantId(request);
        ReviewFilter filter = ReviewFilter.forTenant(tenantId);
        return humanReviewQueue.getPending(filter)
            .map(items -> {
                List<Map<String, Object>> summaries = items.stream()
                    .map(item -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("reviewId", item.getReviewId());
                        m.put("agentId", item.getSkillId());
                        m.put("type", item.getItemType().name());
                        m.put("status", item.getStatus().name());
                        m.put("confidence", item.getConfidenceScore());
                        m.put("summary", item.getEvaluationSummary() != null ? item.getEvaluationSummary() : "");
                        m.put("createdAt", item.getCreatedAt().toString());
                        return m;
                    })
                    .collect(java.util.stream.Collectors.toList());
                return jsonResponse(Map.of(
                    "items", summaries,
                    "count", summaries.size(),
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[hitl] getPending failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to list pending reviews: " + e.getMessage()));
            });
    }

    /**
     * Approves a HITL review item, promoting the candidate to the memory/policy store.
     *
     * <p>Request body: {@code {"reviewer": "...", "rationale": "...", "notes": "..."}}.
     *
     * @return 200 with approved item, or 404 if not found, 501 if not configured
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleHitlApprove(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(errorResponse(400, "reviewId path parameter is required"));
        }
        // loadBody() is asynchronous — use .then() to process after body is loaded
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> data = body.isBlank()
                        ? Map.of()
                        : objectMapper.readValue(body, Map.class);
                    String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                    String rationale = (String) data.getOrDefault("rationale", "Approved via API");
                    String notes = (String) data.get("notes");
                    ReviewDecision decision = new ReviewDecision(reviewer, rationale, Instant.now(), notes);
                    return humanReviewQueue.approve(reviewId, decision)
                        .map(item -> {
                            Map<String, Object> resp = Map.of(
                                "reviewId", item.getReviewId(),
                                "status", item.getStatus().name(),
                                "decidedAt", Instant.now().toString()
                            );
                            // Notify SSE subscribers that HITL queue changed.
                            publishSseTo(resolveTenantId(request), "hitl.update", resp);
                            return jsonResponse(resp);
                        })
                        .then(Promise::of, e -> {
                            log.warn("HITL approve failed for reviewId={}: {}", reviewId, e.getMessage());
                            return Promise.of(errorResponse(404, "Review item not found: " + reviewId));
                        });
                } catch (Exception e) {
                    log.warn("HITL approve bad request for reviewId={}: {}", reviewId, e.getMessage());
                    return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
                }
            }, e -> {
                log.warn("HITL approve failed to read body for reviewId={}: {}", reviewId, e.getMessage());
                return Promise.of(errorResponse(400, "Failed to read request body"));
            });
    }

    /**
     * Rejects a HITL review item, discarding the candidate.
     *
     * <p>Request body: {@code {"reviewer": "...", "rationale": "...", "notes": "..."}}.
     *
     * @return 200 with rejected item, or 404 if not found, 501 if not configured
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleHitlReject(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(errorResponse(400, "reviewId path parameter is required"));
        }
        // loadBody() is asynchronous — use .then() to process after body is loaded
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> data = body.isBlank()
                        ? Map.of()
                        : objectMapper.readValue(body, Map.class);
                    String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                    String rationale = (String) data.getOrDefault("rationale", "Rejected via API");
                    String notes = (String) data.get("notes");
                    ReviewDecision decision = new ReviewDecision(reviewer, rationale, Instant.now(), notes);
                    return humanReviewQueue.reject(reviewId, decision)
                        .map(item -> {
                            Map<String, Object> resp = Map.of(
                                "reviewId", item.getReviewId(),
                                "status", item.getStatus().name(),
                                "decidedAt", Instant.now().toString()
                            );
                            // Notify SSE subscribers that HITL queue changed.
                            publishSseTo(resolveTenantId(request), "hitl.update", resp);
                            return jsonResponse(resp);
                        })
                        .then(Promise::of, e -> {
                            log.warn("HITL reject failed for reviewId={}: {}", reviewId, e.getMessage());
                            return Promise.of(errorResponse(404, "Review item not found: " + reviewId));
                        });
                } catch (Exception e) {
                    log.warn("HITL reject bad request for reviewId={}: {}", reviewId, e.getMessage());
                    return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
                }
            }, e -> {
                log.warn("HITL reject failed to read body for reviewId={}: {}", reviewId, e.getMessage());
                return Promise.of(errorResponse(400, "Failed to read request body"));
            });
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

    // ==================== Learning System Endpoints (AEP-P7) ====================

    /**
     * Lists episodic memory items for the requesting tenant.
     * Queries the {@code dc_memory} collection in Data-Cloud filtered by {@code type=EPISODIC}.
     * Falls back to 503 when Data-Cloud is not configured.
     *
     * @return 200 with list of episodes
     */
    private Promise<HttpResponse> handleListEpisodes(HttpRequest request) {
        if (agentDataCloud == null) {
            return Promise.of(errorResponse(503,
                "Episode store not available — DataCloudClient not configured"));
        }
        String tenantId = resolveTenantId(request);
        String agentFilter = request.getQueryParameter("agentId");
        List<DataCloudClient.Filter> filters = new ArrayList<>();
        filters.add(DataCloudClient.Filter.eq("type", "EPISODIC"));
        if (agentFilter != null && !agentFilter.isBlank()) {
            filters.add(DataCloudClient.Filter.eq("agentId", agentFilter));
        }
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(filters)
            .limit(200)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                List<Map<String, Object>> episodes = items.stream()
                    .map(e -> {
                        Map<String, Object> ep = new java.util.HashMap<>(e.data());
                        ep.put("id", e.id());
                        return ep;
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "episodes", episodes,
                    "count", episodes.size(),
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[learning] episodes query failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to list episodes: " + e.getMessage()));
            });
    }

    /**
     * Lists policies (POLICY-type review items) for the tenant via the HITL queue.
     * Falls back to 501 when the HITL queue is not configured.
     *
     * @return 200 with list of policies
     */
    private Promise<HttpResponse> handleListPolicies(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(errorResponse(501,
                "Policy store not available — start AEP with AepLearningModule"));
        }
        String tenantId = resolveTenantId(request);
        ReviewFilter filter = new ReviewFilter(tenantId, ReviewItemType.POLICY, null, null, 200);
        return humanReviewQueue.getPending(filter)
            .map(items -> {
                List<Map<String, Object>> policies = items.stream()
                    .map(item -> {
                        Map<String, Object> p = new java.util.HashMap<>();
                        p.put("id", item.getReviewId());
                        p.put("agentId", item.getSkillId());
                        p.put("version", item.getProposedVersion());
                        p.put("status", item.getStatus().name());
                        p.put("confidence", item.getConfidenceScore());
                        p.put("summary", item.getEvaluationSummary() != null ? item.getEvaluationSummary() : "");
                        p.put("context", item.getContext());
                        p.put("createdAt", item.getCreatedAt().toString());
                        if (item.getDecidedAt() != null) {
                            p.put("decidedAt", item.getDecidedAt().toString());
                        }
                        return p;
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "policies", policies,
                    "count", policies.size(),
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[learning] policies query failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to list policies: " + e.getMessage()));
            });
    }

    /**
     * Approves a specific learning policy by policyId.
     * Delegates to {@link HumanReviewQueue#approve}.
     *
     * @return 200 with updated policy, 404 if not found, 501 if not configured
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleApprovePolicy(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(errorResponse(501, "Learning module not configured"));
        }
        String policyId = request.getPathParameter("policyId");
        if (policyId == null || policyId.isBlank()) {
            return Promise.of(errorResponse(400, "policyId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale", "Approved via learning API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(reviewer, rationale, Instant.now(), notes);
                return humanReviewQueue.approve(policyId, decision)
                    .map(item -> jsonResponse(Map.of(
                        "id", item.getReviewId(),
                        "status", item.getStatus().name(),
                        "decidedAt", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.warn("[learning] approve policy failed policyId={}: {}", policyId, e.getMessage());
                        return Promise.of(errorResponse(404, "Policy not found: " + policyId));
                    });
            } catch (Exception e) {
                log.warn("[learning] approve policy bad request policyId={}: {}", policyId, e.getMessage());
                return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(errorResponse(400, "Failed to read request body")));
    }

    /**
     * Rejects a specific learning policy by policyId.
     * Delegates to {@link HumanReviewQueue#reject}.
     *
     * @return 200 with updated policy, 404 if not found, 501 if not configured
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleRejectPolicy(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(errorResponse(501, "Learning module not configured"));
        }
        String policyId = request.getPathParameter("policyId");
        if (policyId == null || policyId.isBlank()) {
            return Promise.of(errorResponse(400, "policyId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale", "Rejected via learning API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(reviewer, rationale, Instant.now(), notes);
                return humanReviewQueue.reject(policyId, decision)
                    .map(item -> jsonResponse(Map.of(
                        "id", item.getReviewId(),
                        "status", item.getStatus().name(),
                        "decidedAt", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.warn("[learning] reject policy failed policyId={}: {}", policyId, e.getMessage());
                        return Promise.of(errorResponse(404, "Policy not found: " + policyId));
                    });
            } catch (Exception e) {
                log.warn("[learning] reject policy bad request policyId={}: {}", policyId, e.getMessage());
                return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(errorResponse(400, "Failed to read request body")));
    }

    /**
     * Triggers an on-demand learning reflection cycle.
     * The {@link com.ghatana.agent.learning.consolidation.ConsolidationScheduler} runs
     * periodically; this endpoint acknowledges the request (202 Accepted) and the scheduler
     * will pick it up in its next cycle.
     *
     * @return 202 Accepted with trigger confirmation
     */
    private Promise<HttpResponse> handleTriggerReflection(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        log.info("[learning] reflect triggered for tenant={}", tenantId);
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "triggered", true,
                "tenantId", tenantId,
                "message", "Reflection cycle scheduled; the consolidation scheduler will process it in the next interval",
                "timestamp", Instant.now().toString()
            ));
            return Promise.of(HttpResponse.ofCode(202)
                .withHeader(HttpHeaders.CONTENT_TYPE,
                    HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build());
        } catch (Exception e) {
            return Promise.of(errorResponse(500, "Failed to trigger reflection"));
        }
    }

    // ==================== Agent Deregister Endpoint ====================

    /**
     * Deregisters an agent from the agent registry by removing it from Data-Cloud.
     *
     * @return 200 with confirmation, 404 if not found, 503 if Data-Cloud not configured
     */
    private Promise<HttpResponse> handleDeregisterAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }
        String tenantId = resolveTenantId(request);
        return agentDataCloud.findById(tenantId, "agent-registry", agentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(errorResponse(404, "Agent not found: " + agentId));
                }
                return agentDataCloud.delete(tenantId, "agent-registry", agentId)
                    .map(ignored -> jsonResponse(Map.of(
                        "deleted", true,
                        "agentId", agentId,
                        "tenantId", tenantId,
                        "timestamp", Instant.now().toString()
                    )));
            })
            .then(Promise::of, e -> {
                log.error("[agents] deregister failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to deregister agent: " + e.getMessage()));
            });
    }

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
        // Push real-time update to all SSE subscribers for this tenant.
        publishSseTo(tenantId, "run.update", run);
    }

    // ==================== SSE Publisher Infrastructure ====================

    /**
     * Schedules a recurring 30-second heartbeat for all active SSE connections.
     * Called once from the event-loop thread during startup and rescheduled automatically.
     */
    private void scheduleHeartbeat() {
        eventloop.delay(30_000L, () -> {
            byte[] heartbeat = ("event: heartbeat\ndata: {\"ts\":\"" + Instant.now() + "\"}\n\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            sseSubscribers.forEach((tenant, queues) -> {
                for (int i = queues.size() - 1; i >= 0; i--) {
                    ChannelBuffer<ByteBuf> q = queues.get(i);
                    if (q.isSaturated()) {
                        queues.remove(i);
                        q.closeEx(new java.io.IOException("SSE heartbeat: subscriber removed (backpressure)"));
                    } else {
                        q.put(ByteBuf.wrapForReading(heartbeat))
                            .whenException(e -> {
                                log.debug("SSE subscriber lost during heartbeat: {}", e.getMessage());
                                queues.remove(q);
                            });
                    }
                }
            });
            scheduleHeartbeat(); // reschedule indefinitely
        });
    }

    /**
     * Pushes an SSE event to all active subscribers of the given tenant.
     * MUST be called from the event-loop thread.
     *
     * @param tenantId target tenant
     * @param type     SSE event type (e.g. {@code run.update}, {@code hitl.update})
     * @param data     payload to JSON-serialize as the event data field
     */
    private void publishSseTo(String tenantId, String type, Map<String, Object> data) {
        List<ChannelBuffer<ByteBuf>> queues = sseSubscribers.get(tenantId);
        if (queues == null || queues.isEmpty()) return;
        String msg;
        try {
            String json = objectMapper.writeValueAsString(data);
            msg = "event: " + type + "\ndata: " + json + "\n\n";
        } catch (Exception e) {
            log.warn("SSE serialization failed for type={}: {}", type, e.getMessage());
            return;
        }
        byte[] bytes = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = queues.size() - 1; i >= 0; i--) {
            ChannelBuffer<ByteBuf> q = queues.get(i);
            if (q.isSaturated()) {
                queues.remove(i);
                q.closeEx(new java.io.IOException("SSE publisher: subscriber removed (backpressure)"));
            } else {
                final int idx = i;
                q.put(ByteBuf.wrapForReading(bytes))
                    .whenException(e -> {
                        log.debug("SSE subscriber disconnected during publish: {}", e.getMessage());
                        queues.remove(idx < queues.size() ? idx : queues.size() - 1);
                    });
            }
        }
    }

    /**
     * Broadcasts an SSE event to all active subscribers of the given tenant.
     * Thread-safe: schedules publishing on the event-loop thread when called externally
     * (e.g. from a {@code ReviewNotificationSpi} callback).
     *
     * @param tenantId target tenant (or {@code "*"} to broadcast to all tenants)
     * @param type     SSE event type
     * @param data     payload map
     */
    public void broadcastSseEvent(String tenantId, String type, Map<String, Object> data) {
        if (eventloop == null) return;
        eventloop.execute(() -> {
            if ("*".equals(tenantId)) {
                sseSubscribers.keySet().forEach(t -> publishSseTo(t, type, data));
            } else {
                publishSseTo(tenantId, type, data);
            }
        });
    }

    // ==================== SSE Endpoints ====================

    /**
     * Real Server-Sent Events stream for real-time AEP updates.
     *
     * <p>Opens a persistent keep-alive connection via a {@link ChannelBuffer}.
     * Events pushed through the buffer:
     * <ul>
     *   <li>{@code connected} — sent immediately on connection</li>
     *   <li>{@code heartbeat} — sent every 30 s to keep the TCP connection alive</li>
     *   <li>{@code run.update} — sent whenever a pipeline run is recorded</li>
     *   <li>{@code hitl.update} — sent whenever a HITL item is approved or rejected</li>
     * </ul>
     *
     * <p>Returns {@code retry: 5000} so browsers reconnect after 5 s on disconnect.
     *
     * @param request the incoming HTTP request
     * @return a streaming SSE response (connection stays open until the client disconnects)
     */
    private Promise<HttpResponse> handleSseStream(HttpRequest request) {
        String tenantId = resolveTenantId(request);

        // ChannelBuffer must be created on the event-loop thread — this handler runs on it.
        ChannelBuffer<ByteBuf> queue = new ChannelBuffer<>(8, 256);
        sseSubscribers.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(queue);

        // Send the initial "connected" frame immediately (buffer has capacity, resolves sync).
        byte[] connected = ("retry: 5000\nevent: connected\ndata: {\"service\":\"aep\",\"ts\":\"" + Instant.now() + "\"}\n\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        queue.put(ByteBuf.wrapForReading(connected));

        // When the client disconnects, the supplier's end-of-stream fires — remove from registry.
        List<ChannelBuffer<ByteBuf>> tenantQueues = sseSubscribers.get(tenantId);
        ChannelBuffer<ByteBuf> finalQueue = queue;

        return Promise.of(
            HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
                .withHeader(HttpHeaders.of("Cache-Control"), HttpHeaderValue.of("no-cache"))
                .withHeader(HttpHeaders.of("X-Accel-Buffering"), HttpHeaderValue.of("no"))
                .withHeader(HttpHeaders.of("Connection"), HttpHeaderValue.of("keep-alive"))
                .withBodyStream(
                    finalQueue.getSupplier()
                        .withEndOfStream(eos -> eos.whenComplete(() -> tenantQueues.remove(finalQueue)))
                )
                .build()
        );
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

    private HttpResponse jsonResponse(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
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
            String json = objectMapper.writeValueAsString(Map.of(
                "error", message,
                "code", code,
                "timestamp", Instant.now().toString()
            ));
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code)
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }
}
