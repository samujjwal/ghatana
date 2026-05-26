package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.ai.PromptLifecycleService;
import com.ghatana.yappc.ai.abtesting.ABTestingEvaluationService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin A/B experiment API backed by prompt evaluation and learning evidence.
 *
 * @doc.type class
 * @doc.purpose Provides tenant-scoped A/B experiment list, create, pause, and winner-promotion APIs
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class AdminAbTestingController {

    private static final Logger log = LoggerFactory.getLogger(AdminAbTestingController.class);
    public static final String EXPERIMENT_COLLECTION = "yappc_ab_experiments";
    public static final String AUDIT_COLLECTION = "yappc_ab_experiment_audit";
    public static final String LEARNING_EVIDENCE_COLLECTION = "yappc_learning_evidence";

    private final DataCloudClient dataCloudClient;
    private final ObjectMapper objectMapper;
    private final ABTestingEvaluationService evaluationService;
    private final PromptLifecycleService promptLifecycleService;

    public AdminAbTestingController(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull ObjectMapper objectMapper,
            @NotNull ABTestingEvaluationService evaluationService,
            @NotNull PromptLifecycleService promptLifecycleService) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService");
        this.promptLifecycleService = Objects.requireNonNull(promptLifecycleService, "promptLifecycleService");
    }

    public Promise<HttpResponse> listExperiments(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return badRequest("tenant context required");
        }
        DataCloudClient.Query.Builder builder = DataCloudClient.Query.builder().limit(500);
        String status = request.getQueryParameter("status");
        if (status != null && !status.isBlank()) {
            builder.filter(DataCloudClient.Filter.eq("status", status.toLowerCase(Locale.ROOT)));
        }
        return dataCloudClient.query(tenantId, EXPERIMENT_COLLECTION, builder.build())
                .map(records -> records.stream()
                        .map(record -> normalizeExperiment(record.id(), tenantId, record.data()))
                        .sorted(Comparator.comparing(item -> stringValue(item.get("createdAt")), Comparator.reverseOrder()))
                        .toList())
                .map(items -> Map.of("items", items, "total", items.size()))
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "list experiments", tenantId, null));
    }

    public Promise<HttpResponse> createExperiment(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return badRequest("tenant context required");
        }
        Principal principal = request.getAttachment(Principal.class);
        String actorId = principal == null ? "system" : principal.getName();
        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(body.asString(StandardCharsets.UTF_8), Map.class);
                String name = stringValue(payload.get("experimentName")).trim();
                String promptName = stringValue(payload.get("promptName")).trim();
                if (name.isBlank() || promptName.isBlank()) {
                    return badRequest("experimentName and promptName are required");
                }
                String experimentId = "ab-exp-" + UUID.randomUUID();
                Map<String, Object> experiment = experimentRecord(experimentId, tenantId, actorId, payload);
                Map<String, Object> audit = auditRecord(
                        "CREATED",
                        experimentId,
                        tenantId,
                        actorId,
                        stringValue(payload.get("description")),
                        request,
                        Map.of("promptName", promptName));
                Map<String, Object> evidence = learningEvidenceRecord(
                        "AB_EXPERIMENT_CREATED",
                        experimentId,
                        tenantId,
                        actorId,
                        request,
                        Map.of("promptName", promptName, "experimentName", name));
                return dataCloudClient.save(tenantId, EXPERIMENT_COLLECTION, experiment)
                        .then(saved -> dataCloudClient.save(tenantId, AUDIT_COLLECTION, audit)
                                .then(ignored -> dataCloudClient.save(tenantId, LEARNING_EVIDENCE_COLLECTION, evidence))
                                .map(ignored -> normalizeExperiment(saved.id(), tenantId, saved.data())))
                        .map(this::jsonResponse);
            } catch (Exception error) {
                return badRequest("Invalid A/B experiment request");
            }
        }).then((response, error) -> errorResponse(response, error, "create experiment", tenantId, null));
    }

    public Promise<HttpResponse> promoteWinner(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return badRequest("tenant context required");
        }
        Principal principal = request.getAttachment(Principal.class);
        String actorId = principal == null ? "system" : principal.getName();
        String experimentId = pathParameter(request, "experimentId");
        if (experimentId.isBlank()) {
            return badRequest("experimentId is required");
        }
        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(body.asString(StandardCharsets.UTF_8), Map.class);
                String variantId = stringValue(payload.get("variantId")).trim();
                String reason = stringValue(payload.get("reason")).trim();
                if (variantId.isBlank() || reason.isBlank()) {
                    return badRequest("variantId and reason are required");
                }
                return dataCloudClient.findById(tenantId, EXPERIMENT_COLLECTION, experimentId)
                        .then(existing -> promoteExistingWinner(
                                tenantId,
                                actorId,
                                experimentId,
                                variantId,
                                reason,
                                request,
                                existing));
            } catch (Exception error) {
                return badRequest("Invalid A/B winner promotion request");
            }
        }).then((response, error) -> errorResponse(response, error, "promote winner", tenantId, experimentId));
    }

    public Promise<HttpResponse> pauseExperiment(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return badRequest("tenant context required");
        }
        Principal principal = request.getAttachment(Principal.class);
        String actorId = principal == null ? "system" : principal.getName();
        String experimentId = pathParameter(request, "experimentId");
        if (experimentId.isBlank()) {
            return badRequest("experimentId is required");
        }
        return dataCloudClient.findById(tenantId, EXPERIMENT_COLLECTION, experimentId)
                .then(existing -> {
                    if (existing.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(404)
                                .withJson("{\"error\":\"experiment not found\"}")
                                .build());
                    }
                    Map<String, Object> updated = new LinkedHashMap<>(existing.get().data());
                    updated.put("id", experimentId);
                    updated.put("status", "paused");
                    updated.put("updatedAt", Instant.now().toString());
                    updated.put("updatedBy", actorId);
                    Map<String, Object> audit = auditRecord(
                            "PAUSED",
                            experimentId,
                            tenantId,
                            actorId,
                            "Paused from admin A/B testing dashboard",
                            request,
                            Map.of());
                    return dataCloudClient.save(tenantId, EXPERIMENT_COLLECTION, updated)
                            .then(saved -> dataCloudClient.save(tenantId, AUDIT_COLLECTION, audit)
                                    .map(ignored -> normalizeExperiment(saved.id(), tenantId, saved.data())))
                            .map(this::jsonResponse);
                }).then((response, error) -> errorResponse(response, error, "pause experiment", tenantId, experimentId));
    }

    private Promise<HttpResponse> promoteExistingWinner(
            String tenantId,
            String actorId,
            String experimentId,
            String variantId,
            String reason,
            HttpRequest request,
            Optional<DataCloudClient.Entity> existing) {
        if (existing.isEmpty()) {
            return Promise.of(HttpResponse.ofCode(404).withJson("{\"error\":\"experiment not found\"}").build());
        }
        Map<String, Object> current = existing.get().data();
        List<Map<String, Object>> variants = variantList(current.get("variants"));
        Optional<Map<String, Object>> winner = variants.stream()
                .filter(variant -> variantId.equals(stringValue(variant.get("variantId"))))
                .findFirst();
        if (winner.isEmpty()) {
            return badRequest("variantId does not belong to experiment");
        }
        List<ABTestingEvaluationService.VariantMetrics> metrics = variants.stream()
                .map(this::evaluationMetrics)
                .toList();
        return evaluationService.evaluateExperiment(experimentId, metrics, 0.95)
                .then(result -> {
                    String promptName = stringValue(current.get("promptName"));
                    String promptVersion = stringOr("active", stringValue(current.get("promptVersion")));
                    double score = boundedScore(winner.get());
                    promptLifecycleService.recordScore(promptName, promptVersion, variantId, score);
                    return promptLifecycleService.rebalanceWeights(promptName, promptVersion, 1, actorId, reason)
                            .map(decision -> Map.of(
                                    "evaluation", result,
                                    "rebalanceApplied", decision.applied(),
                                    "rebalanceAction", decision.action()));
                })
                .then(evaluation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> eval = (Map<String, Object>) evaluation;
                    ABTestingEvaluationService.StatisticalResult result =
                            (ABTestingEvaluationService.StatisticalResult) eval.get("evaluation");
                    String previousWinner = stringValue(current.get("winnerId"));
                    Map<String, Object> updated = new LinkedHashMap<>(current);
                    updated.put("id", experimentId);
                    updated.put("status", "completed");
                    updated.put("winnerId", variantId);
                    updated.put("endedAt", stringOr(Instant.now().toString(), stringValue(current.get("endedAt"))));
                    updated.put("updatedAt", Instant.now().toString());
                    updated.put("updatedBy", actorId);
                    updated.put("previousWinnerId", previousWinner);
                    updated.put("rollbackTargetWinnerId", previousWinner);
                    updated.put("reversible", true);
                    updated.put("modelEvaluation", Map.of(
                            "winnerId", result.winnerId(),
                            "confidence", result.confidence(),
                            "statisticallySignificant", result.isStatisticallySignificant(),
                            "effectSize", result.effectSize(),
                            "pValue", result.pValue(),
                            "selectedVariantId", variantId,
                            "rebalanceApplied", eval.get("rebalanceApplied"),
                            "rebalanceAction", eval.get("rebalanceAction")));
                    Map<String, Object> audit = auditRecord(
                            "WINNER_PROMOTED",
                            experimentId,
                            tenantId,
                            actorId,
                            reason,
                            request,
                            Map.of(
                                    "previousWinnerId", previousWinner,
                                    "winnerId", variantId,
                                    "rollbackTargetWinnerId", previousWinner,
                                    "reversible", true));
                    Map<String, Object> evidence = learningEvidenceRecord(
                            "AB_EXPERIMENT_WINNER_PROMOTED",
                            experimentId,
                            tenantId,
                            actorId,
                            request,
                            Map.of(
                                    "promptName", stringValue(current.get("promptName")),
                                    "winnerId", variantId,
                                    "previousWinnerId", previousWinner,
                                    "confidence", result.confidence(),
                                    "pValue", result.pValue(),
                                    "rollbackTargetWinnerId", previousWinner));
                    return dataCloudClient.save(tenantId, EXPERIMENT_COLLECTION, updated)
                            .then(saved -> dataCloudClient.save(tenantId, AUDIT_COLLECTION, audit)
                                    .then(ignored -> dataCloudClient.save(tenantId, LEARNING_EVIDENCE_COLLECTION, evidence))
                                    .map(ignored -> normalizeExperiment(saved.id(), tenantId, saved.data())))
                            .map(this::jsonResponse);
                });
    }

    private Map<String, Object> experimentRecord(
            String experimentId,
            String tenantId,
            String actorId,
            Map<String, Object> payload) {
        Instant now = Instant.now();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", experimentId);
        record.put("tenantId", tenantId);
        record.put("name", stringValue(payload.get("experimentName")).trim());
        record.put("description", stringValue(payload.get("description")).trim());
        record.put("status", "running");
        record.put("promptName", stringValue(payload.get("promptName")).trim());
        record.put("promptVersion", stringOr("active", stringValue(payload.get("promptVersion"))));
        record.put("createdAt", now.toString());
        record.put("updatedAt", now.toString());
        record.put("updatedBy", actorId);
        record.put("variants", List.of(
                variantRecord("var-a", "Variant A", stringValue(payload.get("variantA"))),
                variantRecord("var-b", "Variant B", stringValue(payload.get("variantB")))));
        return record;
    }

    private Map<String, Object> variantRecord(String id, String name, String promptContent) {
        return new LinkedHashMap<>(Map.of(
                "variantId", id,
                "variantName", name,
                "promptContent", promptContent,
                "impressions", 0,
                "conversions", 0,
                "conversionRate", 0.0,
                "avgResponseTimeMs", 0.0,
                "avgCostUsd", 0.0,
                "avgQualityScore", 0.0,
                "statisticalSignificance", false));
    }

    private ABTestingEvaluationService.VariantMetrics evaluationMetrics(Map<String, Object> variant) {
        long impressions = longValue(variant.get("impressions"));
        long conversions = longValue(variant.get("conversions"));
        double cost = doubleValue(variant.get("avgCostUsd")) * Math.max(1, impressions);
        return new ABTestingEvaluationService.VariantMetrics(
                stringValue(variant.get("variantId")),
                ABTestingEvaluationService.AIModelProvider.GPT_4,
                impressions,
                conversions,
                Math.max(0, impressions - conversions),
                doubleValue(variant.get("avgResponseTimeMs")),
                doubleValue(variant.get("avgResponseTimeMs")),
                doubleValue(variant.get("avgResponseTimeMs")),
                doubleValue(variant.get("avgResponseTimeMs")),
                doubleValue(variant.get("avgQualityScore")),
                impressions,
                0,
                cost,
                doubleValue(variant.get("avgCostUsd")),
                impressions <= 0 ? 0.0 : (double) Math.max(0, impressions - conversions) / impressions,
                doubleValue(variant.get("conversionRate")));
    }

    private Map<String, Object> normalizeExperiment(String id, String tenantId, Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", stringOr(id, stringValue(data.get("id"))));
        response.put("tenantId", stringOr(tenantId, stringValue(data.get("tenantId"))));
        response.put("name", stringValue(data.get("name")));
        response.put("description", stringValue(data.get("description")));
        response.put("status", stringOr("running", stringValue(data.get("status"))));
        response.put("promptName", stringValue(data.get("promptName")));
        response.put("createdAt", stringValue(data.get("createdAt")));
        putIfPresent(response, "endedAt", data.get("endedAt"));
        response.put("variants", variantList(data.get("variants")));
        putIfPresent(response, "winnerId", data.get("winnerId"));
        putIfPresent(response, "previousWinnerId", data.get("previousWinnerId"));
        putIfPresent(response, "rollbackTargetWinnerId", data.get("rollbackTargetWinnerId"));
        response.put("reversible", Boolean.TRUE.equals(data.get("reversible")));
        putIfPresent(response, "modelEvaluation", data.get("modelEvaluation"));
        return response;
    }

    private Map<String, Object> auditRecord(
            String eventType,
            String experimentId,
            String tenantId,
            String actorId,
            String reason,
            HttpRequest request,
            Map<String, Object> details) {
        return new LinkedHashMap<>(Map.of(
                "id", UUID.randomUUID().toString(),
                "eventType", eventType,
                "experimentId", experimentId,
                "tenantId", tenantId,
                "actorId", actorId,
                "reason", reason,
                "correlationId", correlationId(request),
                "details", details,
                "timestamp", Instant.now().toString()));
    }

    private Map<String, Object> learningEvidenceRecord(
            String eventType,
            String experimentId,
            String tenantId,
            String actorId,
            HttpRequest request,
            Map<String, Object> metadata) {
        String evidenceId = "learn-ab-experiment-" + experimentId + "-" + UUID.randomUUID();
        Map<String, Object> details = new LinkedHashMap<>(metadata);
        details.put("eventType", eventType);
        details.put("actorId", actorId);
        details.put("correlationId", correlationId(request));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("id", evidenceId);
        evidence.put("evidenceId", evidenceId);
        evidence.put("tenantId", tenantId);
        evidence.put("projectId", "admin-ab-testing");
        evidence.put("runId", experimentId);
        evidence.put("observationRef", "obs-" + evidenceId);
        evidence.put("insightsRef", "insight-" + evidenceId);
        evidence.put("patternCount", 0);
        evidence.put("anomalyCount", 0);
        evidence.put("recommendationCount", 1);
        evidence.put("provenance", List.of(experimentId, eventType));
        evidence.put("metadata", details);
        evidence.put("createdAt", Instant.now().toString());
        return evidence;
    }

    private List<Map<String, Object>> variantList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> variants = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> variant = new LinkedHashMap<>();
                map.forEach((key, val) -> variant.put(String.valueOf(key), val));
                variant.putIfAbsent("variantId", "");
                variant.putIfAbsent("variantName", "");
                variant.putIfAbsent("impressions", 0);
                variant.putIfAbsent("conversions", 0);
                variant.putIfAbsent("conversionRate", 0.0);
                variant.putIfAbsent("avgResponseTimeMs", 0.0);
                variant.putIfAbsent("avgCostUsd", 0.0);
                variant.putIfAbsent("avgQualityScore", 0.0);
                variant.putIfAbsent("statisticalSignificance", false);
                variants.add(Map.copyOf(variant));
            }
        }
        return List.copyOf(variants);
    }

    private Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"" + message + "\"}").build());
    }

    private Promise<HttpResponse> errorResponse(
            HttpResponse response,
            Exception error,
            String operation,
            String tenantId,
            String experimentId) {
        if (error == null) {
            return Promise.of(response);
        }
        log.error("A/B testing {} failed: tenantId={} experimentId={}", operation, tenantId, experimentId, error);
        return Promise.of(HttpResponse.ofCode(503)
                .withJson("{\"error\":\"A/B testing service unavailable\"}")
                .build());
    }

    private HttpResponse jsonResponse(Object value) {
        try {
            return HttpResponse.ok200().withJson(objectMapper.writeValueAsString(value)).build();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize A/B testing response", error);
        }
    }

    private String resolveTenantId(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        if (principal != null && principal.getTenantId() != null && !principal.getTenantId().isBlank()) {
            return principal.getTenantId();
        }
        String queryTenant = request.getQueryParameter("tenantId");
        return queryTenant == null || queryTenant.isBlank() ? null : queryTenant;
    }

    private String pathParameter(HttpRequest request, String name) {
        String value = request.getPathParameter(name);
        return value == null ? "" : value;
    }

    private String correlationId(HttpRequest request) {
        String value = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !stringValue(value).isBlank()) {
            target.put(key, value);
        }
    }

    private double boundedScore(Map<String, Object> variant) {
        double quality = doubleValue(variant.get("avgQualityScore")) / 5.0;
        double conversion = doubleValue(variant.get("conversionRate"));
        return Math.max(0.0, Math.min(1.0, Math.max(quality, conversion)));
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringOr(String fallback, String value) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
