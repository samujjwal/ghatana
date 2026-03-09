#!/usr/bin/env python3
"""Fix services method names and rewrite controllers and JDBC repos."""

import os

BASE = "products/yappc/backend/api/src/main/java/com/ghatana/yappc/api"

# ============================================================
# Fix SecurityScanService: ScanJob.of() -> ScanJob.pending()
# ============================================================
scan_svc = os.path.join(os.getcwd(), f"{BASE}/service/SecurityScanService.java")
with open(scan_svc, 'r') as f:
    content = f.read()
content = content.replace('ScanJob.of(workspaceId, input.projectId(), input.scanType())',
                           'ScanJob.pending(workspaceId, input.projectId(), input.scanType())')
with open(scan_svc, 'w') as f:
    f.write(content)
print("FIXED: SecurityScanService ScanJob.of -> ScanJob.pending")

# ============================================================
# Fix ComplianceService: completeAssessment() -> complete()
# ============================================================
comp_svc = os.path.join(os.getcwd(), f"{BASE}/service/ComplianceService.java")
with open(comp_svc, 'r') as f:
    content = f.read()
content = content.replace(
    'assessment.completeAssessment(input.score());',
    '''assessment.setTotalControls(input.passedControls() + input.failedControls() + input.naControls());
                assessment.complete();'''
)
content = content.replace(
    'assessment.startAssessment();',
    '''assessment.setStatus("IN_PROGRESS");
                assessment.setStartedAt(java.time.Instant.now());'''
)
with open(comp_svc, 'w') as f:
    f.write(content)
print("FIXED: ComplianceService method names")

files = {}

# ============================================================
# CONTROLLERS
# ============================================================

files[f"{BASE}/operations/IncidentController.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.operations;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.IncidentService;
import com.ghatana.yappc.api.service.IncidentService.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP controller for incident management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for incident CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class IncidentController {

    private static final Logger logger = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidentService;
    private final ObjectMapper objectMapper;

    @Inject
    public IncidentController(IncidentService incidentService, ObjectMapper objectMapper) {
        this.incidentService = incidentService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/incidents */
    public Promise<HttpResponse> createIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CreateIncidentInput input = objectMapper.readValue(body, CreateIncidentInput.class);
                    return incidentService.createIncident(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/incidents/:id */
    public Promise<HttpResponse> getIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.getIncident(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Incident not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/incidents */
    public Promise<HttpResponse> listProjectIncidents(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return incidentService.listProjectIncidents(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/incidents/open */
    public Promise<HttpResponse> listOpenIncidents(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    return incidentService.listOpenIncidents(workspaceId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/incidents/severity/:severity */
    public Promise<HttpResponse> listIncidentsBySeverity(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String severity = request.getPathParameter("severity");
                    return incidentService.listIncidentsBySeverity(workspaceId, severity)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/investigate */
    public Promise<HttpResponse> startInvestigation(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.startInvestigation(workspaceId, id)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/resolve */
    public Promise<HttpResponse> resolveIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    ResolveInput input = objectMapper.readValue(body, ResolveInput.class);
                    return incidentService.resolveIncident(workspaceId, id, input)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/close */
    public Promise<HttpResponse> closeIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.closeIncident(workspaceId, id)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/assign */
    public Promise<HttpResponse> assignIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    var node = objectMapper.readTree(body);
                    UUID assigneeId = UUID.fromString(node.get("assigneeId").asText());
                    return incidentService.assignIncident(workspaceId, id, assigneeId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/incidents/statistics */
    public Promise<HttpResponse> getIncidentStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return incidentService.getIncidentStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/incidents/:id */
    public Promise<HttpResponse> deleteIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.deleteIncident(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
"""

files[f"{BASE}/operations/AlertController.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.operations;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.AlertService;
import com.ghatana.yappc.api.service.AlertService.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP controller for alert management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for alert CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    @Inject
    public AlertController(AlertService alertService, ObjectMapper objectMapper) {
        this.alertService = alertService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/alerts */
    public Promise<HttpResponse> createAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CreateAlertInput input = objectMapper.readValue(body, CreateAlertInput.class);
                    return alertService.createAlert(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/alerts/:id */
    public Promise<HttpResponse> getAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return alertService.getAlert(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Alert not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/alerts */
    public Promise<HttpResponse> listProjectAlerts(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return alertService.listProjectAlerts(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/alerts/open */
    public Promise<HttpResponse> listOpenAlerts(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    return alertService.listOpenAlerts(workspaceId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/alerts/severity/:severity */
    public Promise<HttpResponse> listAlertsBySeverity(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String severity = request.getPathParameter("severity");
                    return alertService.listAlertsBySeverity(workspaceId, severity)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/alerts/:id/acknowledge */
    public Promise<HttpResponse> acknowledgeAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    UUID userId = UUID.fromString(ctx.userId());
                    return alertService.acknowledgeAlert(workspaceId, id, userId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/alerts/:id/resolve */
    public Promise<HttpResponse> resolveAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    UUID userId = UUID.fromString(ctx.userId());
                    return alertService.resolveAlert(workspaceId, id, userId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/alerts/statistics */
    public Promise<HttpResponse> getAlertStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return alertService.getAlertStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/alerts/:id */
    public Promise<HttpResponse> deleteAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return alertService.deleteAlert(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
"""

files[f"{BASE}/security/SecurityScanController.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.security;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.SecurityScanService;
import com.ghatana.yappc.api.service.SecurityScanService.*;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP controller for security scan endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for security scan CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class SecurityScanController {

    private static final Logger logger = LoggerFactory.getLogger(SecurityScanController.class);

    private final SecurityScanService scanService;
    private final ObjectMapper objectMapper;

    @Inject
    public SecurityScanController(SecurityScanService scanService, ObjectMapper objectMapper) {
        this.scanService = scanService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/security-scans */
    public Promise<HttpResponse> startScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    StartScanInput input = objectMapper.readValue(body, StartScanInput.class);
                    return scanService.startScan(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/security-scans/:id */
    public Promise<HttpResponse> getScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return scanService.getScan(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Scan not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/security-scans */
    public Promise<HttpResponse> listProjectScans(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return scanService.listProjectScans(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/security-scans/type/:type */
    public Promise<HttpResponse> listScansByType(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    ScanType type = ScanType.valueOf(request.getPathParameter("type").toUpperCase());
                    return scanService.listScansByType(workspaceId, type)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/security-scans/running */
    public Promise<HttpResponse> listRunningScans(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    return scanService.listRunningScans(workspaceId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/security-scans/:id/complete */
    public Promise<HttpResponse> completeScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CompleteScanInput input = objectMapper.readValue(body, CompleteScanInput.class);
                    return scanService.completeScan(workspaceId, id, input)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/security-scans/:id/fail */
    public Promise<HttpResponse> failScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    var node = objectMapper.readTree(body);
                    String reason = node.get("reason").asText();
                    return scanService.failScan(workspaceId, id, reason)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/security-scans/stats */
    public Promise<HttpResponse> getScanStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return scanService.getScanStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/security-scans/:id */
    public Promise<HttpResponse> deleteScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return scanService.deleteScan(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
"""

files[f"{BASE}/security/ComplianceController.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.security;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.ComplianceService;
import com.ghatana.yappc.api.service.ComplianceService.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP controller for compliance assessment endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for compliance assessment CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class ComplianceController {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceController.class);

    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;

    @Inject
    public ComplianceController(ComplianceService complianceService, ObjectMapper objectMapper) {
        this.complianceService = complianceService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/compliance */
    public Promise<HttpResponse> createAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CreateAssessmentInput input = objectMapper.readValue(body, CreateAssessmentInput.class);
                    return complianceService.createAssessment(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/compliance/:id */
    public Promise<HttpResponse> getAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return complianceService.getAssessment(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Assessment not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/compliance */
    public Promise<HttpResponse> listProjectAssessments(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return complianceService.listProjectAssessments(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/compliance/:id/start */
    public Promise<HttpResponse> startAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    var node = objectMapper.readTree(body);
                    String assessorName = node.get("assessorName").asText();
                    return complianceService.startAssessment(workspaceId, id, assessorName)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/compliance/:id/complete */
    public Promise<HttpResponse> completeAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CompleteAssessmentInput input = objectMapper.readValue(body, CompleteAssessmentInput.class);
                    return complianceService.completeAssessment(workspaceId, id, input)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/compliance/statistics */
    public Promise<HttpResponse> getComplianceStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return complianceService.getComplianceStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/compliance/:id */
    public Promise<HttpResponse> deleteAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return complianceService.deleteAssessment(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
"""

# ============================================================
# Write all files
# ============================================================
for path, content in files.items():
    full_path = os.path.join(os.getcwd(), path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, 'w') as f:
        f.write(content.lstrip('\n'))
    print(f"OK: {path}")

print(f"\nWrote {len(files)} files + 2 fixes")
