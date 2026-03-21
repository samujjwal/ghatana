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
        try {
            AepSoc2ControlFramework.Soc2Report report = soc2Framework.generateReport();
            return Promise.of(HttpHelper.jsonResponse(HttpHelper.mapper().convertValue(
                report, new TypeReference<Map<String, Object>>() {})));
        } catch (Exception e) {
            return Promise.of(HttpHelper.errorResponse(500,
                "Failed to generate SOC2 report: " + e.getMessage()));
        }
    }
}
