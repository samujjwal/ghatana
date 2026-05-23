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

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    public ProductFamilyControlPlaneController(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull ObjectMapper objectMapper) {
        this.dataCloudClient = dataCloudClient;
        this.objectMapper = objectMapper;
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
                .map(records -> records.isEmpty()
                        ? Map.of(
                                "productKey", productKey.toLowerCase(),
                                "status", "NOT_READY",
                                "verdict", "BLOCKED",
                                "gateStatus", List.of(),
                                "blockers", List.of("No Data Cloud release readiness record found"),
                                "evidenceRefs", List.of(),
                                "foundationReadiness", List.of(),
                                "docTruthWarnings", List.of("release-readiness-record-missing"),
                                "traceId", traceId,
                                "updatedAt", Instant.now().toString())
                        : normalizeReleaseRecord(productKey, records.get(0).data()))
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
                .map(records -> records.stream()
                        .map(record -> normalizeAssetRecord(record.id(), record.data()))
                        .filter(asset -> assetMatches(asset, filters))
                        .toList())
                .map(assets -> Map.of(
                        "status", assets.isEmpty() ? "NOT_READY" : "READY",
                        "assets", assets,
                        "appliedFilters", filters,
                        "warnings", assets.isEmpty()
                                ? List.of("No reusable product-family assets registered in Data Cloud")
                                : List.of()))
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "asset registry", tenantId, null));
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
                .filter(DataCloudClient.Filter.eq("targetProduct", targetProduct))
                .limit(100)
                .build();
        return dataCloudClient.query(tenantId, REUSE_COLLECTION, query)
                .map(records -> Map.of(
                        "targetProduct", targetProduct,
                        "status", records.isEmpty() ? "NOT_READY" : "READY",
                        "recommendations", records.stream().map(record -> record.data()).toList()))
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
                .filter(DataCloudClient.Filter.eq("productUnitId", productUnitId))
                .sorts(List.of(DataCloudClient.Sort.asc("occurredAt")))
                .limit(200)
                .build();
        return dataCloudClient.query(tenantId, "kernel_lifecycle_truth", query)
                .map(records -> Map.of(
                        "productUnitId", productUnitId,
                        "status", records.isEmpty() ? "NOT_READY" : "READY",
                        "timeline", records.stream().map(record -> record.data()).toList(),
                        "rollbackVisibility", records.stream()
                                .map(DataCloudClient.Entity::data)
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
        Principal principal = request.getAttachment(Principal.class);
        if (principal != null && principal.getTenantId() != null && !principal.getTenantId().isBlank()) {
            return principal.getTenantId();
        }
        String header = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        return header == null || header.isBlank() ? null : header;
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
}
