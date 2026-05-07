/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.compliance.AepSoc2ControlFramework;
import com.ghatana.aep.server.compliance.AepComplianceService;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.aep.security.AepInputValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller for compliance endpoints (GDPR, CCPA, SOC2).
 *
 * @doc.type class
 * @doc.purpose Regulatory compliance operations
 * @doc.layer product
 * @doc.pattern Service
 */
public class ComplianceController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceController.class);

    /**
     * Default maximum age of SOC 2 evidence before the report is refused.
     * Can be overridden via {@code AEP_SOC2_EVIDENCE_MAX_AGE_DAYS} environment variable.
     */
    private static final long DEFAULT_EVIDENCE_MAX_AGE_DAYS = 90L;

    @Nullable
    private final AepComplianceService complianceService;
    private final AepSoc2ControlFramework soc2Framework;

    public ComplianceController(@Nullable AepComplianceService complianceService,
                                 AepSoc2ControlFramework soc2Framework) {
        this.complianceService = complianceService;
        this.soc2Framework = soc2Framework;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleGdprAccess(HttpRequest request) {
        if (complianceService == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Compliance service not available — DataCloudClient not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = HttpHelper.mapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = AepInputValidator.validateTenantId(
                    HttpHelper.resolveTenantId(request, body));
                String subjectId = AepInputValidator.requireNonBlank(
                    (String) body.get("subjectId"), "subjectId");
                return complianceService.accessRequest(tenantId, subjectId)
                    .map(report -> HttpHelper.jsonResponse(HttpHelper.mapper().convertValue(
                        report, new TypeReference<Map<String, Object>>() {})))
                    .then(Promise::of, e -> Promise.of(HttpHelper.errorResponse(500,
                        "Access request failed: " + e.getMessage())));
            } catch (AepInputValidator.ValidationException ve) {
                return Promise.of(HttpHelper.errorResponse(400, ve.getMessage()));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleGdprErasure(HttpRequest request) {
        if (complianceService == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Compliance service not available — DataCloudClient not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = HttpHelper.mapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = AepInputValidator.validateTenantId(
                    HttpHelper.resolveTenantId(request, body));
                String subjectId = AepInputValidator.requireNonBlank(
                    (String) body.get("subjectId"), "subjectId");
                return complianceService.deletionRequest(tenantId, subjectId)
                    .map(report -> HttpHelper.jsonResponse(HttpHelper.mapper().convertValue(
                        report, new TypeReference<Map<String, Object>>() {})))
                    .then(Promise::of, e -> Promise.of(HttpHelper.errorResponse(500,
                        "Erasure request failed: " + e.getMessage())));
            } catch (AepInputValidator.ValidationException ve) {
                return Promise.of(HttpHelper.errorResponse(400, ve.getMessage()));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleGdprPortability(HttpRequest request) {
        if (complianceService == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Compliance service not available — DataCloudClient not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = HttpHelper.mapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = AepInputValidator.validateTenantId(
                    HttpHelper.resolveTenantId(request, body));
                String subjectId = AepInputValidator.requireNonBlank(
                    (String) body.get("subjectId"), "subjectId");
                return complianceService.portabilityRequest(tenantId, subjectId)
                    .map(export -> HttpHelper.jsonResponse(export))
                    .then(Promise::of, e -> Promise.of(HttpHelper.errorResponse(500,
                        "Portability request failed: " + e.getMessage())));
            } catch (AepInputValidator.ValidationException ve) {
                return Promise.of(HttpHelper.errorResponse(400, ve.getMessage()));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCcpaOptOut(HttpRequest request) {
        if (complianceService == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Compliance service not available — DataCloudClient not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = HttpHelper.mapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = AepInputValidator.validateTenantId(
                    HttpHelper.resolveTenantId(request, body));
                String consumerId = AepInputValidator.requireNonBlank(
                    (String) body.get("consumerId"), "consumerId");
                return complianceService.ccpaOptOut(tenantId, consumerId)
                    .map(report -> HttpHelper.jsonResponse(HttpHelper.mapper().convertValue(
                        report, new TypeReference<Map<String, Object>>() {})))
                    .then(Promise::of, e -> Promise.of(HttpHelper.errorResponse(500,
                        "CCPA opt-out failed: " + e.getMessage())));
            } catch (AepInputValidator.ValidationException ve) {
                return Promise.of(HttpHelper.errorResponse(400, ve.getMessage()));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    public Promise<HttpResponse> handleSoc2Report(HttpRequest request) {
        // F-034: Enforce evidence freshness — refuse report if evidence is stale.
        long maxAgeDays = resolveEvidenceMaxAgeDays();
        java.util.Optional<Instant> newestEvidence = soc2Framework.newestEvidenceTimestamp();

        if (newestEvidence.isEmpty()) {
            log.warn("[F-034] SOC2 report refused: no evidence has been collected yet");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "AEP_SOC2_NO_EVIDENCE");
            body.put("message", "No SOC 2 evidence has been collected. Evidence collection must run before a report can be generated.");
            body.put("maxAgeDays", maxAgeDays);
            return Promise.of(HttpHelper.jsonResponse(503, body));
        }

        Instant newest = newestEvidence.get();
        Duration evidenceAge = Duration.between(newest, Instant.now());
        if (evidenceAge.toDays() > maxAgeDays) {
            log.warn("[F-034] SOC2 report refused: newest evidence is {} days old (max {})",
                evidenceAge.toDays(), maxAgeDays);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "AEP_SOC2_EVIDENCE_STALE");
            body.put("message", String.format(
                "SOC 2 evidence is %d days old; the configured maximum is %d days. "
                + "Re-run evidence collection before generating the report.",
                evidenceAge.toDays(), maxAgeDays));
            body.put("newestEvidenceAt", newest.toString());
            body.put("evidenceAgeDays", evidenceAge.toDays());
            body.put("maxAgeDays", maxAgeDays);
            return Promise.of(HttpHelper.jsonResponse(503, body));
        }

        try {
            AepSoc2ControlFramework.Soc2Report report = soc2Framework.generateReport();
            log.info("[F-034] SOC2 report generated; newestEvidence={} evidenceAgeDays={}",
                newest, evidenceAge.toDays());
            return Promise.of(HttpHelper.jsonResponse(HttpHelper.mapper().convertValue(
                report, new TypeReference<Map<String, Object>>() {})));
        } catch (Exception e) {
            log.error("[F-034] Failed to generate SOC2 report", e);
            return Promise.of(HttpHelper.errorResponse(500,
                "Failed to generate SOC2 report: " + e.getMessage()));
        }
    }

    static long resolveEvidenceMaxAgeDays() {
        String raw = System.getenv("AEP_SOC2_EVIDENCE_MAX_AGE_DAYS");
        if (raw == null || raw.isBlank()) return DEFAULT_EVIDENCE_MAX_AGE_DAYS;
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : DEFAULT_EVIDENCE_MAX_AGE_DAYS;
        } catch (NumberFormatException e) {
            return DEFAULT_EVIDENCE_MAX_AGE_DAYS;
        }
    }
}
