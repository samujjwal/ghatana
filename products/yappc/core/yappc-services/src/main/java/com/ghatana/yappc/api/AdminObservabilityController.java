package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Admin observability API for release-gate evidence used by the YAPPC admin dashboard.
 *
 * @doc.type class
 * @doc.purpose Serves SLO, cost, domain invariant, and OpenAPI release-gate evidence to admin UI clients
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class AdminObservabilityController {

    private static final Logger log = LoggerFactory.getLogger(AdminObservabilityController.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Path repoRoot;
    private final Executor executor;

    public AdminObservabilityController(@NotNull ObjectMapper objectMapper) {
        this(objectMapper, Path.of("."), Executors.newVirtualThreadPerTaskExecutor());
    }

    public AdminObservabilityController(
            @NotNull ObjectMapper objectMapper,
            @NotNull Path repoRoot,
            @NotNull Executor executor) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Promise<HttpResponse> listReleaseGates(HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> items = releaseGateDescriptors().stream()
                    .map(this::loadGateEvidence)
                    .toList();
            return objectMapper.writeValueAsString(Map.of(
                    "status", aggregateStatus(items),
                    "items", items));
        }).map(json -> HttpResponse.ok200().withJson(json).build())
                .then((response, error) -> {
                    if (error == null) {
                        return Promise.of(response);
                    }
                    log.error("Failed to load admin observability release-gate evidence", error);
                    return Promise.of(HttpResponse.ofCode(503)
                            .withJson("{\"error\":\"release gate evidence is unavailable\"}")
                            .build());
                });
    }

    private Map<String, Object> loadGateEvidence(ReleaseGateDescriptor descriptor) {
        Path evidencePath = repoRoot.resolve(descriptor.evidencePath()).normalize();
        if (!evidencePath.startsWith(repoRoot)) {
            return unavailable(descriptor, "Evidence path escapes repository root");
        }
        if (!Files.exists(evidencePath)) {
            return unavailable(descriptor, descriptor.label() + " evidence file is missing");
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(evidencePath.toFile(), MAP_TYPE);
            Map<String, Object> record = baseRecord(descriptor);
            record.put("status", normalizeStatus(payload));
            record.put("refreshedAt", firstString(payload, "refreshedAt", "generatedAt", "createdAt", "timestamp"));
            if (String.valueOf(record.get("refreshedAt")).isBlank()) {
                record.put("refreshedAt", Instant.now().toString());
            }
            record.put("summary", summary(payload, descriptor));
            record.put("source", ".kernel/evidence");
            record.put("evidence", payload);
            return record;
        } catch (Exception error) {
            log.warn("Invalid release-gate evidence file {}", evidencePath, error);
            return unavailable(descriptor, descriptor.label() + " evidence file is invalid: " + error.getMessage());
        }
    }

    private Map<String, Object> unavailable(ReleaseGateDescriptor descriptor, String summary) {
        Map<String, Object> record = baseRecord(descriptor);
        record.put("status", "down");
        record.put("refreshedAt", Instant.now().toString());
        record.put("summary", summary);
        record.put("source", ".kernel/evidence");
        return record;
    }

    private Map<String, Object> baseRecord(ReleaseGateDescriptor descriptor) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", descriptor.id());
        record.put("label", descriptor.label());
        record.put("category", descriptor.category());
        record.put("evidenceHref", descriptor.evidenceHref());
        return record;
    }

    private String aggregateStatus(List<Map<String, Object>> items) {
        if (items.stream().anyMatch(item -> "down".equals(item.get("status")))) {
            return "down";
        }
        if (items.stream().anyMatch(item -> "degraded".equals(item.get("status")))) {
            return "degraded";
        }
        return "healthy";
    }

    private String normalizeStatus(Map<String, Object> payload) {
        String explicit = firstString(payload, "status", "verdict", "releaseVerdict").toLowerCase(java.util.Locale.ROOT);
        if (List.of("healthy", "passed", "pass", "ok").contains(explicit)) {
            return "healthy";
        }
        if (List.of("degraded", "warning", "warn").contains(explicit)) {
            return "degraded";
        }
        if (List.of("down", "failed", "fail", "error").contains(explicit)) {
            return "down";
        }

        Object passed = payload.get("passed");
        if (!(passed instanceof Boolean)) {
            passed = payload.get("success");
        }
        if (!(passed instanceof Boolean)) {
            passed = payload.get("ok");
        }
        if (passed instanceof Boolean value) {
            return value ? "healthy" : "down";
        }
        if (hasNonEmptyList(payload.get("violations")) || hasNonEmptyList(payload.get("errors"))) {
            return "down";
        }
        if (hasNonEmptyList(payload.get("warnings"))) {
            return "degraded";
        }
        return "healthy";
    }

    private boolean hasNonEmptyList(Object value) {
        return value instanceof List<?> list && !list.isEmpty();
    }

    private String summary(Map<String, Object> payload, ReleaseGateDescriptor descriptor) {
        String summary = firstString(payload, "summary", "message", "description");
        return summary.isBlank() ? descriptor.label() + " evidence loaded from CI." : summary;
    }

    private String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return "";
    }

    private List<ReleaseGateDescriptor> releaseGateDescriptors() {
        return List.of(
                new ReleaseGateDescriptor(
                        "product-slo-budgets",
                        "Product SLO budgets",
                        "SLO",
                        ".kernel/evidence/product-slo-budgets.json",
                        "/release-evidence/product-slo-budgets.json"),
                new ReleaseGateDescriptor(
                        "product-cost-budgets",
                        "Product cost budgets",
                        "Cost",
                        ".kernel/evidence/product-cost-budgets.json",
                        "/release-evidence/product-cost-budgets.json"),
                new ReleaseGateDescriptor(
                        "product-domain-invariants",
                        "Product domain invariants",
                        "Domain",
                        ".kernel/evidence/product-domain-invariants.json",
                        "/release-evidence/product-domain-invariants.json"),
                new ReleaseGateDescriptor(
                        "openapi-breaking-changes",
                        "OpenAPI breaking changes",
                        "API",
                        ".kernel/evidence/openapi-breaking-changes.json",
                        "/release-evidence/openapi-breaking-changes.json"));
    }

    private record ReleaseGateDescriptor(
            String id,
            String label,
            String category,
            String evidencePath,
            String evidenceHref) {
    }
}
