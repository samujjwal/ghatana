package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.application.ProductReleaseReadinessService;
import com.ghatana.datacloud.application.ProductReleaseReadinessService.ProductReleaseReadiness;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP handler for Data Cloud product release readiness records.
 *
 * @doc.type class
 * @doc.purpose Backend API for PHR/DMOS release cockpits backed by Data Cloud records
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class ProductReleaseReadinessHandler {

    private final HttpHandlerSupport http;
    private final ProductReleaseReadinessService service;

    public ProductReleaseReadinessHandler(HttpHandlerSupport http, ProductReleaseReadinessService service) {
        this.http = Objects.requireNonNull(http, "http must not be null");
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    public Promise<HttpResponse> handleProduceReleaseReadiness(HttpRequest request) {
        String tenantId = resolveTenantOrError(request);
        if (tenantId == null) {
            return Promise.of(tenantError(request));
        }
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = http.objectMapper()
                        .readValue(body.getString(StandardCharsets.UTF_8), Map.class);
                    ProductReleaseReadiness readiness = toReadiness(payload, tenantId);
                    return service.produceReleaseReadiness(readiness)
                        .map(saved -> http.createdResponse(toMap(saved)));
                } catch (IllegalArgumentException ex) {
                    return Promise.of(http.errorResponse(400, ex.getMessage(), http.resolveCorrelationId(request)));
                } catch (Exception ex) {
                    return Promise.of(http.errorResponse(400, "Malformed release readiness payload: " + ex.getMessage(), http.resolveCorrelationId(request)));
                }
            });
    }

    public Promise<HttpResponse> handleGetReleaseReadiness(HttpRequest request) {
        String tenantId = resolveTenantOrError(request);
        if (tenantId == null) {
            return Promise.of(tenantError(request));
        }
        String productId = request.getPathParameter("productId");
        String productVersion = request.getPathParameter("productVersion");
        String releaseTarget = request.getPathParameter("releaseTarget");
        return service.getReleaseReadiness(productId, productVersion, releaseTarget, tenantId)
            .map(record -> record
                .map(value -> http.jsonResponse(toMap(value)))
                .orElseGet(() -> http.errorResponse(404, "Release readiness record not found", http.resolveCorrelationId(request))));
    }

    public Promise<HttpResponse> handleListReleaseReadiness(HttpRequest request) {
        String tenantId = resolveTenantOrError(request);
        if (tenantId == null) {
            return Promise.of(tenantError(request));
        }
        String productId = request.getQueryParameter("productId");
        String productVersion = request.getQueryParameter("productVersion");
        String releaseTarget = request.getQueryParameter("releaseTarget");
        String releaseVerdict = request.getQueryParameter("releaseVerdict");
        int limit = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 100);
        int offset = HttpHandlerSupport.parseIntParam(request.getQueryParameter("offset"), 0);

        Promise<List<ProductReleaseReadiness>> records = productId == null || productId.isBlank()
            ? service.listReleaseReadiness(tenantId)
            : service.listReleaseReadiness(productId, tenantId);
        return records.map(items -> {
            List<Map<String, Object>> filtered = items.stream()
                .filter(item -> productVersion == null || productVersion.isBlank() || productVersion.equals(item.productVersion()))
                .filter(item -> releaseTarget == null || releaseTarget.isBlank() || releaseTarget.equals(item.releaseTarget()))
                .filter(item -> releaseVerdict == null || releaseVerdict.isBlank() || releaseVerdict.equals(item.releaseVerdict()))
                .skip(Math.max(0, offset))
                .limit(Math.max(1, limit))
                .map(ProductReleaseReadinessHandler::toMap)
                .toList();
            return http.jsonBodyResponse(filtered);
        });
    }

    public Promise<HttpResponse> handleReleaseReadinessStats(HttpRequest request) {
        String tenantId = resolveTenantOrError(request);
        if (tenantId == null) {
            return Promise.of(tenantError(request));
        }
        return service.listReleaseReadiness(tenantId)
            .map(ProductReleaseReadinessHandler::stats)
            .map(stats -> http.jsonResponse(stats));
    }

    public Promise<HttpResponse> handleDeleteReleaseReadiness(HttpRequest request) {
        String tenantId = resolveTenantOrError(request);
        if (tenantId == null) {
            return Promise.of(tenantError(request));
        }
        return service.deleteReleaseReadiness(request.getPathParameter("id"), tenantId)
            .map(ignored -> http.noContentResponse());
    }

    private String resolveTenantOrError(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult result = http.requireTenantIdWithError(request);
        return result.isSuccess() ? result.tenantId() : null;
    }

    private HttpResponse tenantError(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult result = http.requireTenantIdWithError(request);
        return http.errorResponse(result.errorCode(), result.errorMessage(), http.resolveCorrelationId(request));
    }

    private static Map<String, Object> stats(List<ProductReleaseReadiness> records) {
        Map<String, Map<String, Integer>> byProduct = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> byTarget = new LinkedHashMap<>();
        double scoreSum = 0;
        int scoreCount = 0;
        int passed = 0;
        int failed = 0;
        for (ProductReleaseReadiness record : records) {
            if ("pass".equals(record.releaseVerdict())) {
                passed++;
            } else {
                failed++;
            }
            if (record.averageScore() != null) {
                scoreSum += record.averageScore();
                scoreCount++;
            }
            increment(byProduct, record.productId(), record.releaseVerdict());
            increment(byTarget, record.releaseTarget(), record.releaseVerdict());
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReleases", records.size());
        stats.put("passedReleases", passed);
        stats.put("failedReleases", failed);
        stats.put("averageScore", scoreCount == 0 ? 0 : scoreSum / scoreCount);
        stats.put("byProduct", byProduct);
        stats.put("byTarget", byTarget);
        return stats;
    }

    private static void increment(Map<String, Map<String, Integer>> aggregate, String key, String verdict) {
        Map<String, Integer> bucket = aggregate.computeIfAbsent(key, ignored -> new LinkedHashMap<>(Map.of(
            "total", 0,
            "passed", 0,
            "failed", 0
        )));
        bucket.put("total", bucket.get("total") + 1);
        bucket.put("pass".equals(verdict) ? "passed" : "failed", bucket.get("pass".equals(verdict) ? "passed" : "failed") + 1);
    }

    private ProductReleaseReadiness toReadiness(Map<String, Object> payload, String tenantId) {
        String bodyTenant = string(payload.get("tenantId"));
        if (!bodyTenant.isBlank() && !tenantId.equals(bodyTenant)) {
            throw new IllegalArgumentException("Payload tenantId must match authenticated tenant");
        }
        return ProductReleaseReadiness.builder()
            .id(blankToNull(string(payload.get("id"))))
            .productId(required(payload, "productId"))
            .productVersion(required(payload, "productVersion"))
            .releaseTarget(required(payload, "releaseTarget"))
            .releaseVerdict(required(payload, "releaseVerdict"))
            .averageScore(doubleOrNull(payload.get("averageScore")))
            .releaseTargetScore(doubleOrNull(payload.get("releaseTargetScore")))
            .generatedAt(instant(payload.get("generatedAt"), Instant.now()))
            .evidence(map(payload.get("evidence")))
            .blockingGaps(listOfMaps(payload.get("blockingGaps")))
            .belowTargetDimensions(listOfMaps(payload.get("belowTargetDimensions")))
            .tenantId(tenantId)
            .commitSha(required(payload, "commitSha"))
            .evidenceEnvironment(required(payload, "evidenceEnvironment"))
            .build();
    }

    private static Map<String, Object> toMap(ProductReleaseReadiness readiness) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", readiness.id());
        map.put("productId", readiness.productId());
        map.put("productVersion", readiness.productVersion());
        map.put("releaseTarget", readiness.releaseTarget());
        map.put("releaseVerdict", readiness.releaseVerdict());
        map.put("averageScore", readiness.averageScore());
        map.put("releaseTargetScore", readiness.releaseTargetScore());
        map.put("generatedAt", readiness.generatedAt().toString());
        map.put("evidence", readiness.evidence());
        map.put("blockingGaps", readiness.blockingGaps());
        map.put("belowTargetDimensions", readiness.belowTargetDimensions());
        map.put("tenantId", readiness.tenantId());
        map.put("commitSha", readiness.commitSha());
        map.put("evidenceEnvironment", readiness.evidenceEnvironment());
        map.put("createdAt", readiness.createdAt().toString());
        map.put("updatedAt", readiness.updatedAt().toString());
        return map;
    }

    private static String required(Map<String, Object> payload, String key) {
        String value = string(payload.get(key));
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Double doubleOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }

    private static Instant instant(Object value, Instant fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : Instant.parse(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> source ? (List<Map<String, Object>>) source : List.of();
    }
}
