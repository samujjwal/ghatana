package com.ghatana.aep.launcher.http;

import com.ghatana.aep.AepEngine;
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
    private HttpServer server;
    private Eventloop eventloop;

    /**
     * Creates a new AEP HTTP server.
     *
     * @param engine the AEP engine instance
     * @param port the port to listen on
     */
    public AepHttpServer(AepEngine engine, int port) {
        this.engine = engine;
        this.port = port;
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
            
            .build();

        server = HttpServer.builder(eventloop, router)
            .withListenPort(port)
            .build();

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
        if (server != null) {
            server.close();
        }
        if (eventloop != null) {
            eventloop.breakEventloop();
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
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> eventData = objectMapper.readValue(body, Map.class);
            
            String tenantId = (String) eventData.getOrDefault("tenantId", "default");
            String eventType = (String) eventData.getOrDefault("type", "unknown");
            Map<String, Object> payload = (Map<String, Object>) eventData.getOrDefault("payload", Map.of());
            
            AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(), Instant.now());
            
            return engine.process(tenantId, event)
                .map(result -> jsonResponse(Map.of(
                    "eventId", result.eventId(),
                    "success", result.success(),
                    "detections", result.detections().size(),
                    "timestamp", Instant.now().toString()
                )));
        } catch (Exception e) {
            log.error("Error processing event", e);
            return Promise.of(errorResponse(400, "Invalid event data: " + e.getMessage()));
        }
    }

    private Promise<HttpResponse> handleProcessBatch(HttpRequest request) {
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
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
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
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
    }

    private Promise<HttpResponse> handleUpdatePipeline(HttpRequest request) {
        try {
            String tenantId = resolveTenantId(request, null);
            String pipelineId = request.getPathParameter("pipelineId");

            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
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
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
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
    }

    // ==================== Deployment Endpoints ====================

    private Promise<HttpResponse> handleCreateDeployment(HttpRequest request) {
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            DeploymentRequest deploymentRequest = objectMapper.readValue(body, DeploymentRequest.class);
            DeploymentResponse response = deploymentAdapter.handleDeploymentRequest(deploymentRequest);
            return Promise.of(jsonResponse(toMap(response)));
        } catch (Exception e) {
            log.error("Error creating deployment", e);
            return Promise.of(errorResponse(400, "Invalid deployment request: " + e.getMessage()));
        }
    }

    private Promise<HttpResponse> handleUpdateDeployment(HttpRequest request) {
        try {
            String deploymentId = request.getPathParameter("deploymentId");
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            DeploymentRequest deploymentRequest = objectMapper.readValue(body, DeploymentRequest.class);
            DeploymentResponse response = deploymentAdapter.handleUpdateRequest(deploymentId, deploymentRequest);
            return Promise.of(jsonResponse(toMap(response)));
        } catch (Exception e) {
            log.error("Error updating deployment", e);
            return Promise.of(errorResponse(400, "Invalid update deployment request: " + e.getMessage()));
        }
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
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
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
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
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
    }

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleForecast(HttpRequest request) {
        try {
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
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
    }

    // ==================== Helper Methods ====================

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
