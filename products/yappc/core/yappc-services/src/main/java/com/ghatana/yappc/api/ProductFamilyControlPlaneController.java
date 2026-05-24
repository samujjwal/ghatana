package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Product-family control-plane read API for YAPPC release cockpits and reusable assets.
 *
 * @doc.type class
 * @doc.purpose Backend-owned product-family release, asset, doc-truth, and reuse read models
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class ProductFamilyControlPlaneController {

    private static final Logger log = LoggerFactory.getLogger(ProductFamilyControlPlaneController.class);

    private static final String RELEASE_COLLECTION = "product_release_readiness";
    private static final String ASSET_COLLECTION = "product_family_assets";
    private static final String DOC_TRUTH_COLLECTION = "yappc_truth_checks";
    private static final String REUSE_COLLECTION = "product_family_reuse_recommendations";

    private final DataCloudClient dataCloudClient;
    private final ObjectMapper objectMapper;
    private final Path repoRoot;

    public ProductFamilyControlPlaneController(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull ObjectMapper objectMapper) {
        this(dataCloudClient, objectMapper, Path.of("."));
    }

    ProductFamilyControlPlaneController(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull ObjectMapper objectMapper,
            @NotNull Path repoRoot) {
        this.dataCloudClient = dataCloudClient;
        this.objectMapper = objectMapper;
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
    }

    public Promise<HttpResponse> getReleaseReadiness(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        String productKey = request.getPathParameter("productKey");
        if (productKey == null || productKey.isBlank()) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"productKey is required\"}").build());
        }

        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("product_key", productKey.toLowerCase()))
                .limit(1)
                .build();
        String correlationId = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        String traceId = correlationId == null || correlationId.isBlank() ? "" : correlationId;
        return dataCloudClient.query(tenantId, RELEASE_COLLECTION, query)
            .then(records -> {
                if (!records.isEmpty()) {
                return Promise.of(normalizeReleaseRecord(productKey, records.get(0).data()));
                }

                return ingestReleaseReadinessFromEvidence(tenantId, productKey)
                    .map(ingested -> ingested
                        .map(data -> normalizeReleaseRecord(productKey, data))
                        .orElseGet(() -> Map.of(
                            "productKey", productKey.toLowerCase(),
                            "status", "NOT_READY",
                            "verdict", "BLOCKED",
                            "gateStatus", List.of(),
                            "blockers", List.of("No Data Cloud release readiness record found"),
                            "evidenceRefs", List.of(),
                            "foundationReadiness", List.of(),
                            "docTruthWarnings", List.of("release-readiness-record-missing"),
                            "traceId", traceId,
                            "updatedAt", Instant.now().toString())));
            })
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "release readiness", tenantId, productKey));
    }

    public Promise<HttpResponse> listAssets(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        Map<String, String> filters = assetFilters(request);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .limit(500)
                .build();
        return dataCloudClient.query(tenantId, ASSET_COLLECTION, query)
                .then(records -> {
                    if (!records.isEmpty()) {
                        return Promise.of(records);
                    }
                    return ingestAssetCandidatesFromEvidence(tenantId)
                            .then(ignored -> dataCloudClient.query(tenantId, ASSET_COLLECTION, query));
                })
                .map(records -> {
                    List<Map<String, Object>> allAssets = records.stream()
                            .map(record -> normalizeAssetRecord(record.id(), record.data()))
                            .toList();
                    List<Map<String, Object>> matchedAssets = allAssets.stream()
                            .filter(asset -> assetMatches(asset, filters))
                            .toList();
                    return Map.of(
                            "status", assetRegistryStatus(allAssets, matchedAssets, filters),
                            "assets", matchedAssets,
                            "appliedFilters", filters,
                            "warnings", assetRegistryWarnings(allAssets, matchedAssets, filters));
                })
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "asset registry", tenantId, null));
    }

    private Promise<Optional<Map<String, Object>>> ingestReleaseReadinessFromEvidence(String tenantId, String productKey) {
        Optional<Map<String, Object>> evidence = loadReleaseReadinessEvidence(productKey.toLowerCase(java.util.Locale.ROOT));
        if (evidence.isEmpty()) {
            return Promise.of(Optional.empty());
        }

        Map<String, Object> record = evidence.get();
        return dataCloudClient.save(tenantId, RELEASE_COLLECTION, record)
                .map(saved -> Optional.of(saved.data()));
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> loadReleaseReadinessEvidence(String productKey) {
        Path productSpecific = repoRoot.resolve(".kernel/evidence/product-release-readiness." + productKey + ".json");
        Path fallback = repoRoot.resolve(".kernel/evidence/product-release-readiness.json");

        List<Path> candidates = List.of(productSpecific, fallback);
        for (Path candidate : candidates) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try {
                Map<String, Object> json = objectMapper.readValue(Files.readString(candidate, StandardCharsets.UTF_8), Map.class);
                String evidenceProduct = String.valueOf(json.getOrDefault("productId", "")).toLowerCase(java.util.Locale.ROOT);
                if (!evidenceProduct.isBlank() && !productKey.equals(evidenceProduct)) {
                    continue;
                }
                List<Object> blockers = coerceList(json.get("blockingGaps"));
                List<String> blockerMessages = blockers.stream()
                        .map(v -> v instanceof Map<?, ?> m
                        ? String.valueOf(getOrDefaultRaw(m, "gate", "unknown-gate")) + ":" + String.valueOf(getOrDefaultRaw(m, "reason", "unknown"))
                                : String.valueOf(v))
                        .toList();

                Map<String, Object> record = new LinkedHashMap<>();
                record.put("id", "release-readiness-" + productKey);
                record.put("product_key", productKey);
                record.put("verdict", String.valueOf(json.getOrDefault("releaseVerdict", "BLOCKED")).toUpperCase(java.util.Locale.ROOT));
                record.put("gate_status", coerceList(json.get("dimensions")));
                record.put("blockers", blockerMessages);
                record.put("evidence_refs", coerceList(json.get("evidencePaths")));
                record.put("foundation_readiness", coerceList(json.get("releaseProfiles")));
                record.put("doc_truth_warnings", List.of());
                record.put("trace_id", "evidence:" + productKey);
                record.put("updated_at", String.valueOf(json.getOrDefault("generatedAt", Instant.now().toString())));
                return Optional.of(Map.copyOf(record));
            } catch (IOException error) {
                log.warn("Failed to parse release readiness evidence file: {}", candidate, error);
            }
        }

        return Optional.empty();
    }

    private Promise<Void> ingestAssetCandidatesFromEvidence(String tenantId) {
        Path phrAssets = repoRoot.resolve(".kernel/evidence/phr/reusable-assets-registration.json");
        Path dmosAssets = repoRoot.resolve(".kernel/evidence/digital-marketing/reusable-assets-registration.json");

        return Promise.complete()
                .then(() -> ingestAssetFile(tenantId, phrAssets))
                .then(() -> ingestAssetFile(tenantId, dmosAssets));
    }

    @SuppressWarnings("unchecked")
    private Promise<Void> ingestAssetFile(String tenantId, Path filePath) {
        if (!Files.exists(filePath)) {
            return Promise.complete();
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(Files.readString(filePath, StandardCharsets.UTF_8), Map.class);
            List<Object> assets = coerceList(payload.get("assets"));
            Promise<Void> chain = Promise.complete();
            for (Object value : assets) {
                if (!(value instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> asset = new LinkedHashMap<>();
                String assetId = String.valueOf(getOrDefaultRaw(raw, "asset_id", UUID.randomUUID().toString()));
                asset.put("id", assetId);
                asset.put("asset_id", assetId);
                asset.put("asset_type", String.valueOf(getOrDefaultRaw(raw, "asset_type", "unknown")));
                asset.put("source_product", String.valueOf(getOrDefaultRaw(raw, "source_product", "unknown")));
                asset.put("display_name", String.valueOf(getOrDefaultRaw(raw, "asset_name", assetId)));
                asset.put("domain", String.valueOf(getOrDefaultRaw(raw, "domain", "unknown")));
                asset.put("paths", coerceList(raw.get("paths")));
                asset.put("maturity", String.valueOf(getOrDefaultRaw(raw, "maturity_level", "candidate")));
                asset.put("reuse_mode", String.valueOf(getOrDefaultRaw(raw, "reuse_mode", "reference")));
                asset.put("dependencies", coerceList(raw.get("dependencies")));
                asset.put("tests", coerceList(raw.get("tests")));
                asset.put("product_usage", coerceList(raw.get("product_usage")));
                asset.put("owner", String.valueOf(getOrDefaultRaw(raw, "owner", "unassigned")));
                asset.put("promotion_target", String.valueOf(getOrDefaultRaw(raw, "promotion_target", "")));
                asset.put("promotion_state", String.valueOf(getOrDefaultRaw(raw, "promotion_status", "candidate")));
                asset.put("compatibility", getOrDefaultRaw(raw, "compatibility", Map.of()));
                asset.put("evidence_refs", extractEvidenceRefs(raw.get("promotion_evidence")));

                chain = chain.then(() -> dataCloudClient.save(tenantId, ASSET_COLLECTION, asset).map(ignored -> null));
            }
            return chain;
        } catch (IOException error) {
            log.warn("Failed to parse asset registration evidence file: {}", filePath, error);
            return Promise.complete();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractEvidenceRefs(Object promotionEvidence) {
        if (!(promotionEvidence instanceof Map<?, ?> evidenceMap)) {
            return List.of();
        }
        Object refs = evidenceMap.get("evidence_refs");
        return coerceList(refs);
    }

    @SuppressWarnings("unchecked")
    private List<Object> coerceList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    private Object getOrDefaultRaw(Map<?, ?> map, String key, Object defaultValue) {
        Object value = map.get(key);
        return value != null ? value : defaultValue;
    }

    public Promise<HttpResponse> promoteAsset(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        String assetId = request.getPathParameter("assetId");
        if (assetId == null || assetId.isBlank()) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"assetId is required\"}").build());
        }
        String actorId = principal != null && principal.getName() != null ? principal.getName() : "system";
        String correlationId = correlationId(request);

        return request.loadBody().then(body -> {
            try {
                String json = body.asString(StandardCharsets.UTF_8);
                AssetPromotionRequest payload = objectMapper.readValue(json, AssetPromotionRequest.class);
                String targetState = normalizePromotionState(payload.targetState());
                if (targetState == null) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"targetState must be hardened, production, shared-package, plugin, template, or schema\"}")
                            .build());
                }

                return findAsset(tenantId, assetId)
                        .then(found -> {
                            if (found.isEmpty()) {
                                return Promise.of(HttpResponse.ofCode(404)
                                        .withJson("{\"error\":\"asset not found\"}")
                                        .build());
                            }
                            Map<String, Object> current = found.get().data();
                            String currentState = currentPromotionState(current);
                            if (!isAllowedPromotion(currentState, targetState)) {
                                return Promise.of(HttpResponse.ofCode(409)
                                        .withJson(promotionError(currentState, targetState))
                                        .build());
                            }

                            // YAPPC-006: Validate asset schema before promotion
                            List<String> schemaViolations = validateAssetSchema(current, targetState);
                            if (!schemaViolations.isEmpty()) {
                                try {
                                    return Promise.of(HttpResponse.ofCode(422)
                                            .withJson(objectMapper.writeValueAsString(Map.of(
                                                    "error", "asset schema validation failed",
                                                    "violations", schemaViolations)))
                                            .build());
                                } catch (JsonProcessingException e) {
                                    return Promise.of(HttpResponse.ofCode(500)
                                            .withJson("{\"error\":\"failed to serialize validation errors\"}")
                                            .build());
                                }
                            }

                            Instant now = Instant.now();
                            Map<String, Object> updatedAsset = promotedAsset(found.get().id(), assetId, current, payload, targetState, actorId, correlationId, now);
                            Map<String, Object> history = promotionHistory(assetId, updatedAsset, payload, targetState, actorId, correlationId, now);
                            return dataCloudClient.save(tenantId, ASSET_COLLECTION, updatedAsset)
                                    .then(saved -> dataCloudClient.save(tenantId, "product_family_asset_history", history)
                                            .map(ignored -> Map.of(
                                                    "status", "PROMOTED",
                                                    "asset", normalizeAssetRecord(assetId, saved.data()),
                                                    "promotion", history)))
                                    .map(this::jsonResponse);
                        });
            } catch (Exception e) {
                return Promise.of(HttpResponse.ofCode(400)
                        .withJson("{\"error\":\"Invalid asset promotion request\"}")
                        .build());
            }
        }).then((response, error) -> errorResponse(response, error, "asset promotion", tenantId, assetId));
    }

    private Promise<Optional<DataCloudClient.Entity>> findAsset(String tenantId, String assetId) {
        return dataCloudClient.findById(tenantId, ASSET_COLLECTION, assetId)
                .then(found -> {
                    if (found.isPresent()) {
                        return Promise.of(found);
                    }
                    DataCloudClient.Query query = DataCloudClient.Query.builder()
                            .filter(DataCloudClient.Filter.eq("asset_id", assetId))
                            .limit(1)
                            .build();
                    return dataCloudClient.query(tenantId, ASSET_COLLECTION, query)
                            .map(records -> records.stream().findFirst());
                });
    }

    private Map<String, Object> promotedAsset(
            String entityId,
            String assetId,
            Map<String, Object> current,
            AssetPromotionRequest payload,
            String targetState,
            String actorId,
            String correlationId,
            Instant now) {
        Map<String, Object> updated = new LinkedHashMap<>(current);
        updated.put("id", entityId);
        updated.put("asset_id", value(current, "asset_id", assetId));
        updated.put("promotion_state", targetState);
        updated.put("promotion_target", payload.promotionTarget() == null ? "" : payload.promotionTarget());
        updated.put("promotion_reason", payload.reason() == null ? "" : payload.reason());
        updated.put("promotion_evidence_refs", payload.evidenceRefs() == null ? List.of() : payload.evidenceRefs());
        updated.put("promoted_by", actorId);
        updated.put("promotion_correlation_id", correlationId);
        updated.put("version", nextVersion(current));
        updated.put("updated_at", now.toString());
        if ("hardened".equals(targetState) || "production".equals(targetState)) {
            updated.put("maturity", targetState);
        } else {
            updated.put("maturity", "production");
            updated.put("reuse_mode", targetState);
        }
        return updated;
    }

    private Map<String, Object> promotionHistory(
            String assetId,
            Map<String, Object> updatedAsset,
            AssetPromotionRequest payload,
            String targetState,
            String actorId,
            String correlationId,
            Instant now) {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "asset_id", assetId,
                "tenant_id", value(updatedAsset, "tenant_id", ""),
                "version", value(updatedAsset, "version", 1),
                "promotion_state", targetState,
                "actor_id", actorId,
                "correlation_id", correlationId,
                "payload", Map.of(
                        "targetState", targetState,
                        "promotionTarget", payload.promotionTarget() == null ? "" : payload.promotionTarget(),
                        "reason", payload.reason() == null ? "" : payload.reason(),
                        "evidenceRefs", payload.evidenceRefs() == null ? List.of() : payload.evidenceRefs()),
                "occurred_at", now.toString());
    }

    private int nextVersion(Map<String, Object> asset) {
        Object value = asset.get("version");
        if (value instanceof Number number) {
            return number.intValue() + 1;
        }
        return 1;
    }

    private String currentPromotionState(Map<String, Object> asset) {
        Object explicit = asset.get("promotion_state");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return normalizePromotionState(String.valueOf(explicit));
        }
        String reuseMode = String.valueOf(asset.getOrDefault("reuse_mode", ""));
        String normalizedReuseMode = normalizePromotionState(reuseMode);
        if (normalizedReuseMode != null && normalizedReuseMode.startsWith("shared")) {
            return normalizedReuseMode;
        }
        return normalizePromotionState(String.valueOf(asset.getOrDefault("maturity", "candidate")));
    }

    private String normalizePromotionState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (normalized.equals("shared")) {
            return "shared-package";
        }
        return switch (normalized) {
            case "candidate", "hardened", "production", "shared-package", "plugin", "template", "schema" -> normalized;
            default -> null;
        };
    }

    private boolean isAllowedPromotion(String currentState, String targetState) {
        return ("candidate".equals(currentState) && "hardened".equals(targetState))
                || ("hardened".equals(currentState) && "production".equals(targetState))
                || ("production".equals(currentState)
                && List.of("shared-package", "plugin", "template", "schema").contains(targetState));
    }

    private String promotionError(String currentState, String targetState) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "error", "asset promotion transition is not allowed",
                "currentState", currentState == null ? "unknown" : currentState,
                "targetState", targetState));
    }

    private List<String> validateAssetSchema(Map<String, Object> asset, String targetState) {
        List<String> violations = new java.util.ArrayList<>();
        
        // Validate required fields
        if (asset.get("asset_id") == null || String.valueOf(asset.get("asset_id")).isBlank()) {
            violations.add("asset_id is required");
        }
        if (asset.get("asset_type") == null || String.valueOf(asset.get("asset_type")).isBlank()) {
            violations.add("asset_type is required");
        }
        if (asset.get("source_product") == null || String.valueOf(asset.get("source_product")).isBlank()) {
            violations.add("source_product is required");
        }
        
        // Validate target state-specific requirements
        if ("production".equals(targetState) || "shared-package".equals(targetState)) {
            if (asset.get("paths") == null || ((List<?>) asset.get("paths")).isEmpty()) {
                violations.add("paths must be non-empty for production/shared-package promotion");
            }
            if (asset.get("tests") == null || ((List<?>) asset.get("tests")).isEmpty()) {
                violations.add("tests must be non-empty for production/shared-package promotion");
            }
            if (asset.get("dependencies") == null) {
                violations.add("dependencies must be specified for production/shared-package promotion");
            }
        }
        
        // Validate maturity consistency
        String maturity = String.valueOf(asset.getOrDefault("maturity", ""));
        if ("production".equals(targetState) && !"hardened".equals(maturity) && !"production".equals(maturity)) {
            violations.add("asset must be in 'hardened' or 'production' maturity to promote to production");
        }
        
        // Validate evidence refs for production promotion
        if ("production".equals(targetState)) {
            Object evidenceRefs = asset.get("evidence_refs");
            if (evidenceRefs == null || ((List<?>) evidenceRefs).isEmpty()) {
                violations.add("evidence_refs must be non-empty for production promotion");
            }
        }
        
        return violations;
    }

    private String assetRegistryStatus(
            List<Map<String, Object>> allAssets,
            List<Map<String, Object>> matchedAssets,
            Map<String, String> filters) {
        if (!matchedAssets.isEmpty()) {
            return "READY";
        }
        return filters.isEmpty() || allAssets.isEmpty() ? "NOT_READY" : "NO_MATCHES";
    }

    private List<String> assetRegistryWarnings(
            List<Map<String, Object>> allAssets,
            List<Map<String, Object>> matchedAssets,
            Map<String, String> filters) {
        if (!matchedAssets.isEmpty()) {
            return List.of();
        }
        if (!filters.isEmpty() && !allAssets.isEmpty()) {
            return List.of("No reusable product-family assets match the applied filters");
        }
        return List.of("No reusable product-family assets registered in Data Cloud");
    }

    public Promise<HttpResponse> listDocTruthWarnings(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        return dataCloudClient.query(tenantId, DOC_TRUTH_COLLECTION, DataCloudClient.Query.limit(200))
                .map(records -> Map.of(
                        "status", records.isEmpty() ? "NOT_READY" : "READY",
                        "warnings", records.stream().map(record -> record.data()).toList()))
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "doc truth", tenantId, null));
    }

    public Promise<HttpResponse> listGuidedReuse(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        String targetProduct = request.getPathParameter("targetProduct");
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .limit(100)
                .build();
        return dataCloudClient.query(tenantId, REUSE_COLLECTION, query)
                .map(records -> records.stream()
                        .map(DataCloudClient.Entity::data)
                        .filter(data -> targetProduct.equals(value(data, "targetProduct", value(data, "target_product", ""))))
                        .toList())
                .map(records -> Map.of(
                        "targetProduct", targetProduct,
                        "status", records.isEmpty() ? "NOT_READY" : "READY",
                        "recommendations", records))
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "guided reuse", tenantId, targetProduct));
    }

    public Promise<HttpResponse> getKernelTimeline(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        String productUnitId = request.getPathParameter("productUnitId");
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .sorts(List.of(DataCloudClient.Sort.asc("occurredAt")))
                .limit(200)
                .build();
        return dataCloudClient.query(tenantId, "kernel_lifecycle_truth", query)
                .map(records -> records.stream()
                        .map(DataCloudClient.Entity::data)
                        .filter(data -> productUnitId.equals(value(data, "productUnitId", value(data, "product_unit_id", ""))))
                        .toList())
                .map(records -> Map.of(
                        "productUnitId", productUnitId,
                        "status", records.isEmpty() ? "NOT_READY" : "READY",
                        "timeline", records,
                        "rollbackVisibility", records.stream()
                                .filter(data -> Boolean.TRUE.equals(data.get("rollbackAvailable")))
                                .findFirst()
                                .orElse(Map.of("rollbackAvailable", false, "executedBy", "kernel"))))
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "kernel timeline", tenantId, productUnitId));
    }

    private Map<String, Object> normalizeReleaseRecord(String productKey, Map<String, Object> data) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("productKey", value(data, "product_key", productKey.toLowerCase()));
        result.put("status", "READY");
        result.put("verdict", value(data, "verdict", "BLOCKED"));
        result.put("gateStatus", value(data, "gate_status", List.of()));
        result.put("blockers", value(data, "blockers", List.of()));
        result.put("evidenceRefs", value(data, "evidence_refs", List.of()));
        result.put("foundationReadiness", value(data, "foundation_readiness", List.of()));
        result.put("connectorGates", value(data, "connector_gates", List.of()));
        result.put("approvalGates", value(data, "approval_gates", List.of()));
        result.put("aiActionGates", value(data, "ai_action_gates", List.of()));
        result.put("docTruthWarnings", value(data, "doc_truth_warnings", List.of()));
        result.put("traceId", value(data, "trace_id", ""));
        result.put("updatedAt", value(data, "updated_at", Instant.now().toString()));
        return Map.copyOf(result);
    }

    private Map<String, Object> normalizeAssetRecord(String assetId, Map<String, Object> data) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("assetId", value(data, "asset_id", assetId));
        result.put("type", value(data, "asset_type", "unknown"));
        result.put("sourceProduct", value(data, "source_product", "unknown"));
        result.put("displayName", value(data, "display_name", assetId));
        result.put("domain", value(data, "domain", "unknown"));
        result.put("paths", value(data, "paths", List.of()));
        result.put("maturity", value(data, "maturity", "candidate"));
        result.put("reuseMode", value(data, "reuse_mode", "reference"));
        result.put("dependencies", value(data, "dependencies", List.of()));
        result.put("tests", value(data, "tests", List.of()));
        result.put("productUsage", value(data, "product_usage", List.of()));
        result.put("owner", value(data, "owner", "unassigned"));
        result.put("promotionTarget", value(data, "promotion_target", ""));
        result.put("promotionState", value(data, "promotion_state", value(data, "maturity", "candidate")));
        result.put("compatibility", value(data, "compatibility", Map.of()));
        return Map.copyOf(result);
    }

    private Map<String, String> assetFilters(HttpRequest request) {
        Map<String, String> filters = new java.util.LinkedHashMap<>();
        putFilter(filters, "search", request.getQueryParameter("search"));
        putFilter(filters, "product", request.getQueryParameter("product"));
        putFilter(filters, "domain", request.getQueryParameter("domain"));
        putFilter(filters, "type", request.getQueryParameter("type"));
        putFilter(filters, "maturity", request.getQueryParameter("maturity"));
        putFilter(filters, "reuseMode", request.getQueryParameter("reuseMode"));
        putFilter(filters, "compatibility", request.getQueryParameter("compatibility"));
        return Map.copyOf(filters);
    }

    private void putFilter(Map<String, String> filters, String key, String value) {
        if (value != null && !value.isBlank()) {
            filters.put(key, value.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private boolean assetMatches(Map<String, Object> asset, Map<String, String> filters) {
        if (filters.isEmpty()) {
            return true;
        }
        return matchesSearch(asset, filters.get("search"))
                && matchesField(asset, "sourceProduct", filters.get("product"))
                && matchesField(asset, "domain", filters.get("domain"))
                && matchesField(asset, "type", filters.get("type"))
                && matchesField(asset, "maturity", filters.get("maturity"))
                && matchesField(asset, "reuseMode", filters.get("reuseMode"))
                && matchesCompatibility(asset, filters.get("compatibility"));
    }

    private boolean matchesSearch(Map<String, Object> asset, String search) {
        if (search == null) {
            return true;
        }
        return List.of("assetId", "displayName", "sourceProduct", "domain", "type", "maturity", "reuseMode", "owner")
                .stream()
                .map(asset::get)
                .filter(value -> value != null)
                .map(value -> String.valueOf(value).toLowerCase(java.util.Locale.ROOT))
                .anyMatch(value -> value.contains(search));
    }

    private boolean matchesField(Map<String, Object> asset, String key, String expected) {
        return expected == null
                || String.valueOf(asset.getOrDefault(key, ""))
                .toLowerCase(java.util.Locale.ROOT)
                .equals(expected);
    }

    private boolean matchesCompatibility(Map<String, Object> asset, String expected) {
        if (expected == null) {
            return true;
        }
        Object compatibility = asset.get("compatibility");
        return compatibility != null
                && String.valueOf(compatibility).toLowerCase(java.util.Locale.ROOT).contains(expected);
    }

    private Object value(Map<String, Object> data, String key, Object fallback) {
        Object value = data.get(key);
        return value == null ? fallback : value;
    }

    private String resolveTenantId(HttpRequest request) {
        // YAPPC-003: Harden tenant resolution - only accept signed tenant ID from Principal
        // Remove unsigned X-Tenant-Id header fallback to prevent tenant spoofing
        Principal principal = request.getAttachment(Principal.class);
        if (principal != null && principal.getTenantId() != null && !principal.getTenantId().isBlank()) {
            return principal.getTenantId();
        }
        // Reject requests without signed tenant context
        return null;
    }

    private String correlationId(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
    }

    private HttpResponse jsonResponse(Object payload) {
        try {
            return HttpResponse.ok200().withJson(objectMapper.writeValueAsString(payload)).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize product-family payload", e);
        }
    }

    private Promise<HttpResponse> errorResponse(
            HttpResponse response,
            Throwable error,
            String surface,
            String tenantId,
            String subjectId) {
        if (error == null) {
            return Promise.of(response);
        }
        log.error("Failed to load {} read model: tenantId={}, subjectId={}", surface, tenantId, subjectId, error);
        return Promise.of(HttpResponse.ofCode(503)
                .withJson("{\"status\":\"UNAVAILABLE\",\"error\":\"Data Cloud read model unavailable\"}")
                .build());
    }

    private record AssetPromotionRequest(
            String targetState,
            String promotionTarget,
            List<Object> evidenceRefs,
            String reason) {
    }
}
