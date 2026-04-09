package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.featurestore.MLFeature;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.datacloud.launcher.http.DataCloudHttpMetrics;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles AI model registry and feature store HTTP endpoints (DC-11).
 *
 * @doc.type class
 * @doc.purpose AI model and feature store HTTP handlers (DC-11)
 * @doc.layer product
 * @doc.pattern Handler
 */
public class AiModelHandler {

    private static final String HANDLER_NAME = "AiModelHandler";
    private static final Logger log = LoggerFactory.getLogger(AiModelHandler.class);

    private final AIModelManager aiModelManager;
    private final FeatureStoreService featureStoreService;
    private final HttpHandlerSupport http;
    private DataCloudHttpMetrics httpMetrics = DataCloudHttpMetrics.noop();

    public AiModelHandler(AIModelManager aiModelManager,
                          FeatureStoreService featureStoreService,
                          HttpHandlerSupport http) {
        this.aiModelManager = aiModelManager;
        this.featureStoreService = featureStoreService;
        this.http = http;
    }

    public AiModelHandler withMetrics(DataCloudHttpMetrics metrics) {
        this.httpMetrics = metrics;
        return this;
    }

    // ==================== Model Registry ====================

    public Promise<HttpResponse> handleListAiModels(HttpRequest request) {
        if (aiModelManager == null) {
            return Promise.of(http.errorResponse(503, "AI model manager not available in this deployment"));
        }
        String tenantId = http.resolveTenantId(request);
        long start = System.currentTimeMillis();
        return aiModelManager.getAllModels(tenantId)
            .map(models -> {
                List<Map<String, Object>> modelList = models.stream()
                        .map(this::modelMetadataToMap)
                        .toList();
                HttpResponse response = http.jsonResponse(Map.of("models", modelList, "count", modelList.size()));
                httpMetrics.recordRequest(HANDLER_NAME, "handleListAiModels", tenantId, response.getCode());
                httpMetrics.recordLatency(HANDLER_NAME, "handleListAiModels", System.currentTimeMillis() - start);
                return response;
            })
            .then(Promise::of, e -> {
                log.error("[DC-11] failed to list models for tenant={}: {}", tenantId, e.getMessage(), e);
                httpMetrics.recordError(HANDLER_NAME, "handleListAiModels", e);
                return Promise.of(http.errorResponse(500, "Failed to list models: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRegisterAiModel(HttpRequest request) {
        if (aiModelManager == null) {
            return Promise.of(http.errorResponse(503, "AI model manager not available in this deployment"));
        }
        long start = System.currentTimeMillis();
        return request.loadBody()
            .then(buf -> {
                try {
                    String bodyStr = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(bodyStr, Map.class);

                    String tenantId = http.resolveTenantId(request);
                    String name = (String) payload.get("name");
                    String version = (String) payload.get("version");
                    if (name == null || name.isBlank()) {
                        return Promise.of(http.errorResponse(400, "Missing required field: name"));
                    }
                    if (version == null || version.isBlank()) {
                        return Promise.of(http.errorResponse(400, "Missing required field: version"));
                    }

                    DeploymentStatus status = DeploymentStatus.STAGED;
                    if (payload.containsKey("deploymentStatus")) {
                        try {
                            status = DeploymentStatus.valueOf(((String) payload.get("deploymentStatus")).toUpperCase());
                        } catch (IllegalArgumentException e) {
                            return Promise.of(http.errorResponse(400, "Invalid deploymentStatus: " + payload.get("deploymentStatus")));
                        }
                    }

                    ModelMetadata model = ModelMetadata.builder()
                            .id(java.util.UUID.randomUUID())
                            .tenantId(tenantId)
                            .name(name)
                            .version(version)
                            .framework((String) payload.getOrDefault("framework", "unknown"))
                            .deploymentStatus(status)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    return aiModelManager.registerModel(tenantId, model)
                        .map(registered -> {
                            Map<String, Object> response = modelMetadataToMap(registered);
                            try {
                                HttpResponse httpResponse = HttpResponse.ofCode(201)
                                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                        .withBody(ByteBuf.wrapForReading(
                                                http.objectMapper().writeValueAsBytes(response)))
                                        .build();
                                httpMetrics.recordRequest(HANDLER_NAME, "handleRegisterAiModel", tenantId, 201);
                                httpMetrics.recordLatency(HANDLER_NAME, "handleRegisterAiModel", System.currentTimeMillis() - start);
                                return httpResponse;
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        })
                        .then(Promise::of, e -> {
                            log.error("[DC-11] model registration failed: {}", e.getMessage(), e);
                            httpMetrics.recordError(HANDLER_NAME, "handleRegisterAiModel", e);
                            return Promise.of(http.errorResponse(500, "Model registration failed: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.warn("[DC-11] invalid model registration request: {}", e.getMessage());
                    return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> Promise.of(http.errorResponse(400, "Failed to read request body: " + e.getMessage())));
    }

    public Promise<HttpResponse> handleGetAiModel(HttpRequest request) {
        if (aiModelManager == null) {
            return Promise.of(http.errorResponse(503, "AI model manager not available in this deployment"));
        }
        String tenantId = http.resolveTenantId(request);
        String modelName = request.getPathParameter("modelName");
        return aiModelManager.getActiveModel(tenantId, modelName)
            .map(model -> http.jsonResponse(modelMetadataToMap(model)))
            .then(Promise::of, e -> {
                if (e instanceof IllegalStateException && e.getMessage() != null
                        && e.getMessage().contains("No active model")) {
                    return Promise.of(http.errorResponse(404, "No active model found: " + modelName));
                }
                log.error("[DC-11] failed to get model '{}'  tenant={}: {}", modelName, tenantId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to get model: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handlePromoteAiModel(HttpRequest request) {
        if (aiModelManager == null) {
            return Promise.of(http.errorResponse(503, "AI model manager not available in this deployment"));
        }
        return request.loadBody()
            .then(buf -> {
                try {
                    String bodyStr = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(bodyStr, Map.class);
                    String tenantId = http.resolveTenantId(request);
                    String modelName = request.getPathParameter("modelName");
                    String version = (String) payload.get("version");
                    if (version == null || version.isBlank()) {
                        return Promise.of(http.errorResponse(400, "Missing required field: version"));
                    }
                    return aiModelManager.promoteToProduction(tenantId, modelName, version)
                        .map(model -> http.jsonResponse(modelMetadataToMap(model)))
                        .then(Promise::of, e -> {
                            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            if (msg.contains("not found")) {
                                return Promise.of(http.errorResponse(404, msg));
                            }
                            if (msg.contains("STAGED")) {
                                return Promise.of(http.errorResponse(400, msg));
                            }
                            log.error("[DC-11] promote failed model='{}' version='{}': {}", modelName, version, msg, e);
                            return Promise.of(http.errorResponse(500, "Promotion failed: " + msg));
                        });
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> Promise.of(http.errorResponse(400, "Failed to read request body: " + e.getMessage())));
    }

    // ==================== Feature Store ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleIngestFeature(HttpRequest request) {
        if (featureStoreService == null) {
            return Promise.of(http.errorResponse(503, "Feature store not available in this deployment"));
        }
        return request.loadBody()
            .then(buf -> {
                try {
                    String bodyStr = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(bodyStr, Map.class);
                    String tenantId = http.resolveTenantId(request);

                    String entityId = (String) payload.get("entityId");
                    String name = (String) payload.get("name");
                    Object valueObj = payload.get("value");
                    if (entityId == null || entityId.isBlank()) {
                        return Promise.of(http.errorResponse(400, "Missing required field: entityId"));
                    }
                    if (name == null || name.isBlank()) {
                        return Promise.of(http.errorResponse(400, "Missing required field: name"));
                    }
                    if (valueObj == null) {
                        return Promise.of(http.errorResponse(400, "Missing required field: value"));
                    }
                    double value;
                    try {
                        value = ((Number) valueObj).doubleValue();
                    } catch (ClassCastException e) {
                        return Promise.of(http.errorResponse(400, "Field 'value' must be a number"));
                    }

                        MLFeature.Builder fb = MLFeature.builder()
                            .entityId(entityId)
                            .name(name)
                            .value(value)
                            .timestamp(Instant.now());

                    if (payload.containsKey("version")) {
                        fb.version((String) payload.get("version"));
                    }
                    Map<String, String> meta = (Map<String, String>) payload.get("metadata");
                    if (meta != null) {
                        fb.metadata(meta);
                    }

                    MLFeature feature = fb.build();
                    try {
                        featureStoreService.ingest(tenantId, feature);
                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("entityId", entityId);
                        resp.put("name", name);
                        resp.put("value", value);
                        resp.put("status", "ingested");
                        return Promise.of(HttpResponse.ofCode(201)
                                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody(ByteBuf.wrapForReading(http.objectMapper().writeValueAsBytes(resp)))
                                .build());
                    } catch (Exception e) {
                        log.error("[DC-11] feature ingest failed entity={} name={}: {}", entityId, name, e.getMessage(), e);
                        return Promise.of(http.errorResponse(500, "Feature ingest failed: " + e.getMessage()));
                    }
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> Promise.of(http.errorResponse(400, "Failed to read request body: " + e.getMessage())));
    }

    public Promise<HttpResponse> handleGetFeatures(HttpRequest request) {
        if (featureStoreService == null) {
            return Promise.of(http.errorResponse(503, "Feature store not available in this deployment"));
        }
        String tenantId = http.resolveTenantId(request);
        String entityId = request.getPathParameter("entityId");
        String featuresParam = request.getQueryParameter("features");
        if (featuresParam == null || featuresParam.isBlank()) {
            return Promise.of(http.errorResponse(400, "Query parameter 'features' is required (comma-separated feature names)"));
        }
        List<String> featureNames = Arrays.asList(featuresParam.split(","));

        try {
            Map<String, Double> features = featureStoreService.getFeatures(tenantId, entityId, featureNames);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("entityId", entityId);
            response.put("features", features);
            response.put("count", features.size());
            return Promise.of(http.jsonResponse(response));
        } catch (Exception e) {
            log.error("[DC-11] feature retrieval failed entity={}: {}", entityId, e.getMessage(), e);
            return Promise.of(http.errorResponse(500, "Feature retrieval failed: " + e.getMessage()));
        }
    }

    // ==================== Helper ====================

    private Map<String, Object> modelMetadataToMap(ModelMetadata model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",               model.getId().toString());
        map.put("tenantId",         model.getTenantId());
        map.put("name",             model.getName());
        map.put("version",          model.getVersion());
        map.put("framework",        model.getFramework());
        map.put("deploymentStatus", model.getDeploymentStatus().name());
        map.put("trainingMetrics",  model.getTrainingMetrics());
        map.put("createdAt",        model.getCreatedAt().toString());
        map.put("updatedAt",        model.getUpdatedAt().toString());
        model.getDeployedAt().ifPresent(t -> map.put("deployedAt", t.toString()));
        return map;
    }
}
