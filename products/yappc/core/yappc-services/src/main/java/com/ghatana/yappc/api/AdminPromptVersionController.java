package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.ai.PromptLifecycleService;
import com.ghatana.yappc.ai.PromptTemplateVersion;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin prompt-version API backed by canonical Data Cloud prompt records.
 *
 * @doc.type class
 * @doc.purpose Lists prompt versions and applies audited rollback and weight-rebalance decisions
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class AdminPromptVersionController {

    private static final Logger log = LoggerFactory.getLogger(AdminPromptVersionController.class);
    public static final String VERSION_COLLECTION = "yappc_prompt_versions";
    public static final String AUDIT_COLLECTION = "yappc_prompt_version_audit";

    private final DataCloudClient dataCloudClient;
    private final ObjectMapper objectMapper;
    private final PromptLifecycleService promptLifecycleService;

    public AdminPromptVersionController(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull ObjectMapper objectMapper,
            @NotNull PromptLifecycleService promptLifecycleService) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.promptLifecycleService = Objects.requireNonNull(promptLifecycleService, "promptLifecycleService");
    }

    public Promise<HttpResponse> listVersions(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return badRequest("tenant context required");
        }
        DataCloudClient.Query.Builder builder = DataCloudClient.Query.builder().limit(500);
        String promptName = request.getQueryParameter("promptName");
        if (promptName != null && !promptName.isBlank()) {
            builder.filter(DataCloudClient.Filter.eq("promptName", promptName));
        }
        return dataCloudClient.query(tenantId, VERSION_COLLECTION, builder.build())
                .map(records -> records.stream()
                        .map(record -> normalizeVersion(record.id(), record.data()))
                        .sorted(Comparator.comparing(item -> stringValue(item.get("createdAt")), Comparator.reverseOrder()))
                        .toList())
                .map(items -> Map.of("items", items, "total", items.size()))
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "list prompt versions", tenantId, null));
    }

    public Promise<HttpResponse> rollbackVersion(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return badRequest("tenant context required");
        }
        Principal principal = request.getAttachment(Principal.class);
        String actorId = principal == null ? "system" : principal.getName();
        String versionId = pathParameter(request, "versionId");
        if (versionId.isBlank()) {
            return badRequest("versionId is required");
        }
        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(body.asString(StandardCharsets.UTF_8), Map.class);
                String reason = stringValue(payload.get("reason")).trim();
                if (reason.isBlank()) {
                    return badRequest("reason is required");
                }
                return dataCloudClient.findById(tenantId, VERSION_COLLECTION, versionId)
                        .then(target -> rollbackExistingVersion(tenantId, actorId, versionId, reason, request, target));
            } catch (Exception error) {
                return badRequest("Invalid prompt rollback request");
            }
        }).then((response, error) -> errorResponse(response, error, "rollback prompt version", tenantId, versionId));
    }

    public Promise<HttpResponse> updateWeights(HttpRequest request) {
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
                Object rawWeights = payload.get("weights");
                if (!(rawWeights instanceof Map<?, ?> weights) || weights.isEmpty()) {
                    return badRequest("weights are required");
                }
                return persistWeightUpdates(tenantId, actorId, request, weights);
            } catch (Exception error) {
                return badRequest("Invalid prompt weight request");
            }
        }).then((response, error) -> errorResponse(response, error, "update prompt weights", tenantId, null));
    }

    private Promise<HttpResponse> rollbackExistingVersion(
            String tenantId,
            String actorId,
            String versionId,
            String reason,
            HttpRequest request,
            Optional<DataCloudClient.Entity> target) {
        if (target.isEmpty()) {
            return Promise.of(HttpResponse.ofCode(404).withJson("{\"error\":\"prompt version not found\"}").build());
        }
        Map<String, Object> targetData = target.get().data();
        String promptName = stringValue(targetData.get("promptName"));
        return dataCloudClient.query(
                        tenantId,
                        VERSION_COLLECTION,
                        DataCloudClient.Query.builder()
                                .filter(DataCloudClient.Filter.eq("promptName", promptName))
                                .limit(200)
                                .build())
                .then(records -> {
                    records.forEach(record -> registerVersion(record.id(), record.data()));
                    return promptLifecycleService.rollback(promptName, versionId, actorId, reason)
                            .map(decision -> Map.of("records", records, "decision", decision));
                })
                .then(context -> {
                    @SuppressWarnings("unchecked")
                    List<DataCloudClient.Entity> records = (List<DataCloudClient.Entity>) ((Map<?, ?>) context).get("records");
                    PromptLifecycleService.PromptLifecycleDecision decision =
                            (PromptLifecycleService.PromptLifecycleDecision) ((Map<?, ?>) context).get("decision");
                    String previousActiveId = records.stream()
                            .filter(record -> Boolean.TRUE.equals(record.data().get("active")))
                            .map(DataCloudClient.Entity::id)
                            .findFirst()
                            .orElse("");
                    Promise<DataCloudClient.Entity> saveChain = Promise.<DataCloudClient.Entity>of(null);
                    for (DataCloudClient.Entity record : records) {
                        Map<String, Object> updated = new LinkedHashMap<>(record.data());
                        updated.put("id", record.id());
                        updated.put("active", versionId.equals(record.id()));
                        updated.put("updatedAt", Instant.now().toString());
                        updated.put("updatedBy", actorId);
                        saveChain = saveChain.then(ignored -> dataCloudClient.save(tenantId, VERSION_COLLECTION, updated));
                    }
                    Map<String, Object> audit = auditRecord(
                            "ROLLED_BACK",
                            versionId,
                            tenantId,
                            actorId,
                            reason,
                            request,
                            Map.of(
                                    "promptName", promptName,
                                    "previousActiveVersionId", previousActiveId,
                                    "applied", decision.applied()));
                    Map<String, Object> response = new LinkedHashMap<>(targetData);
                    response.put("id", versionId);
                    response.put("active", true);
                    response.put("previousActiveVersionId", previousActiveId);
                    response.put("rollbackApplied", decision.applied());
                    return saveChain
                            .then(ignored -> dataCloudClient.save(tenantId, AUDIT_COLLECTION, audit))
                            .map(ignored -> normalizeVersion(versionId, response))
                            .map(this::jsonResponse);
                });
    }

    private Promise<HttpResponse> persistWeightUpdates(
            String tenantId,
            String actorId,
            HttpRequest request,
            Map<?, ?> weights) {
        Promise<Map<String, Object>> chain = Promise.of(new LinkedHashMap<>());
        for (Map.Entry<?, ?> entry : weights.entrySet()) {
            String versionId = String.valueOf(entry.getKey());
            double weight = boundedWeight(entry.getValue());
            chain = chain.then(updatedWeights -> dataCloudClient.findById(tenantId, VERSION_COLLECTION, versionId)
                    .then(found -> {
                        if (found.isEmpty()) {
                            return Promise.of(updatedWeights);
                        }
                        Map<String, Object> data = new LinkedHashMap<>(found.get().data());
                        String promptName = stringValue(data.get("promptName"));
                        String promptVersion = stringValue(data.getOrDefault("promptVersion", versionId));
                        registerVersion(found.get().id(), data);
                        promptLifecycleService.recordScore(promptName, promptVersion, found.get().id(), weight);
                        data.put("id", found.get().id());
                        data.put("weight", weight);
                        data.put("updatedAt", Instant.now().toString());
                        data.put("updatedBy", actorId);
                        return dataCloudClient.save(tenantId, VERSION_COLLECTION, data)
                                .then(saved -> promptLifecycleService.rebalanceWeights(
                                                promptName,
                                                promptVersion,
                                                1,
                                                actorId,
                                                "Admin prompt weight rebalance")
                                        .map(decision -> {
                                            updatedWeights.put(saved.id(), weight);
                                            updatedWeights.put(saved.id() + ":rebalanceApplied", decision.applied());
                                            return updatedWeights;
                                        }));
                    }));
        }
        return chain.then(updatedWeights -> {
            Map<String, Object> audit = auditRecord(
                    "WEIGHTS_REBALANCED",
                    "weights",
                    tenantId,
                    actorId,
                    "Admin prompt weight update",
                    request,
                    Map.of("weights", Map.copyOf(updatedWeights)));
            return dataCloudClient.save(tenantId, AUDIT_COLLECTION, audit)
                    .map(ignored -> HttpResponse.ok200().withJson("{\"updated\":true}").build());
        });
    }

    private void registerVersion(String id, Map<String, Object> data) {
        String promptName = stringValue(data.get("promptName"));
        if (promptName.isBlank()) {
            return;
        }
        String promptVersion = stringValue(data.getOrDefault("promptVersion", id));
        String content = stringValue(data.get("content"));
        int weight = Math.max(0, (int) Math.round(boundedWeight(data.get("weight")) * 100.0));
        promptLifecycleService.register(PromptTemplateVersion.of(promptName, promptVersion, id, content, weight));
        if (!id.equals(promptVersion)) {
            promptLifecycleService.register(PromptTemplateVersion.of(promptName, id, id, content, weight));
        }
    }

    private Map<String, Object> normalizeVersion(String id, Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", stringOr(id, stringValue(data.get("id"))));
        response.put("promptName", stringValue(data.get("promptName")));
        response.put("content", stringValue(data.get("content")));
        response.put("contentHash", stringOr(hashFallback(response.get("content")), stringValue(data.get("contentHash"))));
        response.put("description", stringValue(data.get("description")));
        response.put("author", stringValue(data.getOrDefault("author", data.get("updatedBy"))));
        response.put("active", Boolean.TRUE.equals(data.get("active")));
        response.put("weight", boundedWeight(data.get("weight")));
        response.put("createdAt", stringValue(data.get("createdAt")));
        putIfPresent(response, "metrics", data.get("metrics"));
        putIfPresent(response, "previousActiveVersionId", data.get("previousActiveVersionId"));
        putIfPresent(response, "rollbackApplied", data.get("rollbackApplied"));
        return response;
    }

    private Map<String, Object> auditRecord(
            String eventType,
            String versionId,
            String tenantId,
            String actorId,
            String reason,
            HttpRequest request,
            Map<String, Object> details) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", UUID.randomUUID().toString());
        record.put("eventType", eventType);
        record.put("versionId", versionId);
        record.put("tenantId", tenantId);
        record.put("actorId", actorId);
        record.put("reason", reason);
        record.put("correlationId", correlationId(request));
        record.put("details", details);
        record.put("timestamp", Instant.now().toString());
        return record;
    }

    private Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"" + message + "\"}").build());
    }

    private Promise<HttpResponse> errorResponse(
            HttpResponse response,
            Exception error,
            String operation,
            String tenantId,
            String versionId) {
        if (error == null) {
            return Promise.of(response);
        }
        log.error("Prompt admin {} failed: tenantId={} versionId={}", operation, tenantId, versionId, error);
        return Promise.of(HttpResponse.ofCode(503)
                .withJson("{\"error\":\"prompt version service unavailable\"}")
                .build());
    }

    private HttpResponse jsonResponse(Object value) {
        try {
            return HttpResponse.ok200().withJson(objectMapper.writeValueAsString(value)).build();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize prompt version response", error);
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

    private double boundedWeight(Object value) {
        double weight = value instanceof Number number ? number.doubleValue() : 1.0;
        return Math.max(0.0, Math.min(1.0, weight));
    }

    private String hashFallback(Object value) {
        return Integer.toHexString(String.valueOf(value).hashCode());
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringOr(String fallback, String value) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
