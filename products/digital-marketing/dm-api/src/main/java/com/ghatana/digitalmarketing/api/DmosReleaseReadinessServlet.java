package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP API for DMOS release cockpit readiness truth.
 *
 * @doc.type class
 * @doc.purpose Exposes backend-owned release readiness evidence for DMOS cockpit
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosReleaseReadinessServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosReleaseReadinessServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";
    private static final String RELEASE_READINESS_CAPABILITY = "dmos.release_readiness";
    private static final Path DMOS_EVIDENCE =
        Path.of(".kernel/evidence/digital-marketing/dmos-release-readiness.json");
    private static final Path DATA_CLOUD_PROVIDER_EVIDENCE =
        Path.of(".kernel/evidence/data-cloud/platform-provider-readiness.json");
    private static final Path DATA_CLOUD_RUNTIME_EVIDENCE =
        Path.of(".kernel/evidence/data-cloud-release-runtime-profile.json");

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;
    private final Path workspaceRoot;

    public DmosReleaseReadinessServlet(Eventloop eventloop, DmosHttpContextFactory httpContextFactory) {
        this(eventloop, httpContextFactory, Path.of(""));
    }

    DmosReleaseReadinessServlet(Eventloop eventloop, DmosHttpContextFactory httpContextFactory, Path workspaceRoot) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(
                    HttpMethod.GET,
                    "/v1/workspaces/:workspaceId/release-readiness",
                    this::handleReleaseReadiness
                )
                .build(),
            DmosMetricsCollector.disabled(),
            "release-readiness"
        );
    }

    private Promise<HttpResponse> handleReleaseReadiness(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            httpContextFactory.buildContext(request, workspaceId, false, RELEASE_READINESS_CAPABILITY, "read");

            ObjectNode readiness = readObject(DMOS_EVIDENCE);
            readiness.put("requestedEnvironment", environmentFor(request));
            readiness.set("evidenceFreshness", evidenceFreshness(readiness));
            readiness.set(
                "dataCloudProviderReadiness",
                evidenceStatus(DATA_CLOUD_PROVIDER_EVIDENCE, "status", "data-cloud-platform-provider")
            );
            readiness.set(
                "dataCloudRuntimeProfile",
                evidenceStatus(DATA_CLOUD_RUNTIME_EVIDENCE, "validationStatus", "data-cloud-runtime-profile")
            );
            enrichCategoryRuntimeTruth(readiness, "connector", "connectorReadiness", "Google Ads connector");
            enrichCategoryRuntimeTruth(readiness, "rollback", "rollbackStatus", "Rollback proof");
            return Promise.of(jsonResponse(200, readiness));
        } catch (SecurityException e) {
            return Promise.of(DmosApiErrorResponses.error(
                403,
                "Access denied",
                resolveCorrelationId(request),
                Map.of()
            ));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(
                400,
                e.getMessage(),
                resolveCorrelationId(request),
                Map.of("request", e.getMessage())
            ));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to resolve release readiness", e);
            return Promise.of(DmosApiErrorResponses.error(
                503,
                "Release readiness evidence unavailable",
                resolveCorrelationId(request),
                Map.of("evidence", DMOS_EVIDENCE.toString())
            ));
        }
    }

    private ObjectNode readObject(Path evidencePath) throws Exception {
        Path resolved = workspaceRoot.resolve(evidencePath).normalize();
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalStateException("Missing release readiness evidence: " + evidencePath);
        }
        JsonNode node = MAPPER.readTree(Files.readString(resolved));
        if (!(node instanceof ObjectNode objectNode)) {
            throw new IllegalStateException("Release readiness evidence must be a JSON object: " + evidencePath);
        }
        return objectNode.deepCopy();
    }

    private ObjectNode evidenceStatus(Path evidencePath, String statusField, String source) {
        ObjectNode status = MAPPER.createObjectNode();
        status.put("status", "missing");
        status.put("evidenceRef", evidencePath.toString());
        status.put("source", source);
        try {
            ObjectNode evidence = readObject(evidencePath);
            String resolvedStatus = text(evidence, statusField, text(evidence, "status", "missing"));
            status.put("status", normalizeStatus(resolvedStatus));
            status.put("generatedAt", text(evidence, "generatedAt", null));
            status.put("targetCommitSha", text(evidence, "targetCommitSha", null));
            status.put("targetEnvironment", text(evidence, "targetEnvironment", null));
        } catch (Exception e) {
            status.put("reason", e.getMessage());
        }
        return status;
    }

    private ObjectNode evidenceFreshness(ObjectNode readiness) {
        ObjectNode freshness = MAPPER.createObjectNode();
        String validationStatus = text(readiness, "validationStatus", "missing");
        freshness.put("status", normalizeStatus(validationStatus));
        freshness.put("current", !"stale".equals(normalizeStatus(validationStatus)) && !"missing".equals(normalizeStatus(validationStatus)));
        String expiresAt = text(readiness, "expiresAt", null);
        if (expiresAt != null) {
            freshness.put("expiresAt", expiresAt);
        }
        return freshness;
    }

    private void enrichCategoryRuntimeTruth(
            ObjectNode readiness,
            String categoryName,
            String fieldName,
            String displayName) {
        if (readiness.has(fieldName)) {
            return;
        }
        JsonNode category = readiness.path("evidenceCategories").path(categoryName);
        if (!category.isObject()) {
            return;
        }
        ObjectNode runtimeTruth = MAPPER.createObjectNode();
        String categoryStatus = normalizeStatus(category.path("status").asText("missing"));
        runtimeTruth.put("overallStatus", categoryStatus);
        if ("connectorReadiness".equals(fieldName)) {
            ObjectNode googleAds = runtimeTruth.putObject("googleAds");
            googleAds.put("name", displayName);
            googleAds.put("status", categoryStatus);
            googleAds.put("lastChecked", category.path("lastChecked").asText(null));
            googleAds.put("oauthValid", categoryStatus.equals("passed") || categoryStatus.equals("ready"));
            googleAds.put("tokenRefreshWorking", categoryStatus.equals("passed") || categoryStatus.equals("ready"));
            googleAds.put("idempotencyValid", categoryStatus.equals("passed") || categoryStatus.equals("ready"));
            runtimeTruth.set("connectors", MAPPER.createObjectNode());
        } else {
            ObjectNode staging = runtimeTruth.putObject("staging");
            staging.put("hasEvidence", categoryStatus.equals("passed") || categoryStatus.equals("ready"));
            staging.put("lastRollbackTest", category.path("lastChecked").asText(null));
            ObjectNode production = runtimeTruth.putObject("production");
            production.put("hasEvidence", categoryStatus.equals("passed") || categoryStatus.equals("ready"));
            production.put("lastRollbackTest", category.path("lastChecked").asText(null));
        }
        readiness.set(fieldName, runtimeTruth);
    }

    private static String environmentFor(HttpRequest request) {
        String environment = request.getQueryParameter("environment");
        return environment == null || environment.isBlank() ? "production" : environment;
    }

    private static String text(ObjectNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        return switch (normalized) {
            case "pass", "passed", "ready", "ready-for-production", "validated", "valid", "current", "complete" -> "passed";
            case "fail", "failed", "blocked", "error" -> "blocked";
            case "partial", "pending", "degraded", "stale", "missing" -> normalized;
            default -> normalized.isBlank() ? "missing" : normalized;
        };
    }

    private static HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize release readiness response", e);
        }
    }

    private static String resolveCorrelationId(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        if (header == null || header.isBlank()) {
            return DmCorrelationId.generate().getValue();
        }
        return header;
    }
}
