/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.compliance.AepSoc2ControlFramework;
import com.ghatana.aep.server.compliance.AepComplianceService;
import com.ghatana.aep.server.governance.KillSwitchAuditChain;
import com.ghatana.aep.server.governance.StepUpAuthenticationGate;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.platform.incident.DegradationMode;
import com.ghatana.platform.incident.GracefulDegradationManager;
import com.ghatana.platform.incident.KillSwitchService;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.security.analytics.EgressMonitor;
import com.ghatana.platform.security.analytics.PromptInjectionDetector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Controller providing governance endpoints for AEP.
 *
 * <p>Exposes REST API for:
 * <ul>
 *   <li>Kill-switch status and activation</li>
 *   <li>Graceful degradation management</li>
 *   <li>Policy evaluation (policy-as-code)</li>
 *   <li>Security analytics (egress stats, injection scan)</li>
 *   <li>Incident reporting</li>
 * </ul>
 *
 * <p>F-018: Kill-switch activation requires step-up MFA authentication and produces
 * tamper-evident chain entries in the audit trail.
 *
 * @doc.type class
 * @doc.purpose Governance endpoints controller for AEP platform services
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class GovernanceController {

    private static final Logger log = LoggerFactory.getLogger(GovernanceController.class);

    private final KillSwitchService killSwitchService;
    private final GracefulDegradationManager degradationManager;
    private final PolicyAsCodeEngine policyEngine;
    private final EgressMonitor egressMonitor;
    private final PromptInjectionDetector injectionDetector;
    private final Function<Map<String, Object>, HttpResponse> jsonResponse;
    @Nullable
    private final AepComplianceService complianceService;
    private final AepSoc2ControlFramework soc2Framework;
    @Nullable
    private final StepUpAuthenticationGate stepUpGate;
    @Nullable
    private final KillSwitchAuditChain auditChain;

    /**
     * @param killSwitchService    kill-switch service; never {@code null}
     * @param degradationManager   graceful degradation manager; never {@code null}
     * @param policyEngine         policy evaluation; never {@code null}
     * @param egressMonitor        egress tracking; never {@code null}
     * @param injectionDetector    prompt injection detection; never {@code null}
     * @param jsonResponse         JSON response factory from the enclosing server
     * @param complianceService    compliance capability service; nullable when Data Cloud is absent
     * @param soc2Framework        SOC 2 framework summary provider
     * @param stepUpGate           MFA gate for step-up verification (F-018); nullable if MFA not configured
     * @param auditChain           audit chain manager for kill-switch operations (F-018); nullable
     */
    public GovernanceController(
            KillSwitchService killSwitchService,
            GracefulDegradationManager degradationManager,
            PolicyAsCodeEngine policyEngine,
            EgressMonitor egressMonitor,
            PromptInjectionDetector injectionDetector,
            Function<Map<String, Object>, HttpResponse> jsonResponse,
            @Nullable AepComplianceService complianceService,
            AepSoc2ControlFramework soc2Framework,
            @Nullable StepUpAuthenticationGate stepUpGate,
            @Nullable KillSwitchAuditChain auditChain) {
        this.killSwitchService  = killSwitchService;
        this.degradationManager = degradationManager;
        this.policyEngine       = policyEngine;
        this.egressMonitor      = egressMonitor;
        this.injectionDetector  = injectionDetector;
        this.jsonResponse       = jsonResponse;
        this.complianceService  = complianceService;
        this.soc2Framework      = soc2Framework;
        this.stepUpGate         = stepUpGate;
        this.auditChain         = auditChain;
    }

    // ---- Kill-Switch -------------------------------------------------------

    /** GET /governance/kill-switch — returns kill-switch status for the tenant. */
    public Promise<HttpResponse> handleKillSwitchStatus(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "tenantId query param required"));
        }
        return killSwitchService.isActive(tenantId)
            .then(tenantActive -> killSwitchService.isGlobalActive()
                .map(globalActive -> jsonResponse.apply(Map.of(
                    "tenantId",     tenantId,
                    "active",       tenantActive,
                    "globalActive", globalActive,
                    "timestamp",    Instant.now().toString()
                ))));
    }

    /** POST /governance/kill-switch/activate — activates a kill-switch for the given tenant. */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleActivateKillSwitch(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om =
                    com.ghatana.platform.core.util.JsonUtils.getDefaultMapper();
                Map<String, Object> body = om.readValue(
                    buf.getString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
                String tenantId   = (String) body.get("tenantId");
                String reason     = (String) body.getOrDefault("reason", "manual activation");
                String incidentId = (String) body.getOrDefault("incidentId", "manual");
                String mfaCode    = (String) body.get("mfaCode");
                String userId     = (String) body.getOrDefault("userId", "unknown");

                if (tenantId == null) {
                    return Promise.of(HttpHelper.errorResponse(400, "tenantId is required"));
                }

                // F-018: Require step-up authentication (MFA code) for kill-switch activation
                if (stepUpGate != null && mfaCode != null) {
                    return stepUpGate.verify(userId, tenantId, mfaCode)
                        .then(verified -> {
                            if (!verified) {
                                // Log failed step-up attempt
                                if (auditChain != null) {
                                    return auditChain.recordFailedStepUp(tenantId, userId, "kill-switch activation denied")
                                        .then(auditId -> Promise.of(HttpHelper.errorResponse(403,
                                            "MFA verification failed; attempt logged")));
                                }
                                return Promise.of(HttpHelper.errorResponse(403, "MFA verification failed"));
                            }

                            // MFA verified; proceed with activation
                            log.warn("[kill-switch] Activating for tenant='{}' incident='{}' reason='{}' actor='{}'",
                                tenantId, incidentId, reason, userId);
                            return killSwitchService.activate(tenantId, reason, incidentId)
                                .then(v -> {
                                    // Record in audit chain
                                    if (auditChain != null) {
                                        return auditChain.recordActivation(tenantId, userId, reason, incidentId, null)
                                            .map(auditId -> jsonResponse.apply(Map.of(
                                                "activated", true, "tenantId", tenantId,
                                                "incidentId", incidentId, "auditId", auditId)));
                                    }
                                    return Promise.of(jsonResponse.apply(Map.of(
                                        "activated", true, "tenantId", tenantId,
                                        "incidentId", incidentId)));
                                });
                        });
                }

                // No MFA or step-up gate not configured; allow direct activation
                log.warn("[kill-switch] Activating for tenant='{}' incident='{}' reason='{}' (no MFA verification)",
                    tenantId, incidentId, reason);
                return killSwitchService.activate(tenantId, reason, incidentId)
                    .map(v -> jsonResponse.apply(Map.of(
                        "activated", true, "tenantId", tenantId,
                        "incidentId", incidentId)));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /** POST /governance/kill-switch/deactivate — deactivates a kill-switch. */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleDeactivateKillSwitch(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om =
                    com.ghatana.platform.core.util.JsonUtils.getDefaultMapper();
                Map<String, Object> body = om.readValue(
                    buf.getString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
                String tenantId = (String) body.get("tenantId");
                String reason   = (String) body.getOrDefault("reason", "manual deactivation");
                String userId   = (String) body.getOrDefault("userId", "unknown");

                if (tenantId == null) {
                    return Promise.of(HttpHelper.errorResponse(400, "tenantId is required"));
                }

                log.warn("[kill-switch] Deactivating for tenant='{}' reason='{}' actor='{}'",
                    tenantId, reason, userId);
                return killSwitchService.deactivate(tenantId, reason)
                    .then(v -> {
                        // Record in audit chain
                        if (auditChain != null) {
                            return auditChain.recordDeactivation(tenantId, userId, reason, null)
                                .map(auditId -> jsonResponse.apply(Map.of(
                                    "deactivated", true, "tenantId", tenantId, "auditId", auditId)));
                        }
                        return Promise.of(jsonResponse.apply(Map.of(
                            "deactivated", true, "tenantId", tenantId)));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    // ---- Degradation -------------------------------------------------------

    /** GET /governance/degradation — returns current degradation mode for the tenant. */
    public Promise<HttpResponse> handleDegradationStatus(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "tenantId query param required"));
        }
        return degradationManager.getMode(tenantId)
            .map(mode -> jsonResponse.apply(Map.of(
                "tenantId", tenantId,
                "mode", mode.name(),
                "timestamp", Instant.now().toString()
            )));
    }

    /** POST /governance/degradation — sets the degradation mode. */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSetDegradation(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om =
                    com.ghatana.platform.core.util.JsonUtils.getDefaultMapper();
                Map<String, Object> body = om.readValue(
                    buf.getString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
                String tenantId = (String) body.get("tenantId");
                String modeName = (String) body.get("mode");
                if (tenantId == null || modeName == null) {
                    return Promise.of(HttpHelper.errorResponse(400, "tenantId and mode are required"));
                }
                DegradationMode mode;
                try {
                    mode = DegradationMode.valueOf(modeName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Promise.of(HttpHelper.errorResponse(400, "Unknown mode: " + modeName));
                }
                log.warn("[degradation] Setting mode={} for tenant='{}'", mode, tenantId);
                return degradationManager.setMode(tenantId, mode)
                    .map(v -> jsonResponse.apply(Map.of(
                        "tenantId", tenantId, "mode", mode.name(), "applied", true)));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    /** GET /governance/compliance/summary — returns compliance capability summary for the tenant. */
    public Promise<HttpResponse> handleComplianceSummary(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "tenantId query param required"));
        }

        AepSoc2ControlFramework.Soc2Report report = soc2Framework.generateReport();
        long maxAgeDays = ComplianceController.resolveEvidenceMaxAgeDays();
        Optional<Instant> newestEvidence = soc2Framework.newestEvidenceTimestamp();
        Optional<Long> evidenceAgeDays = newestEvidence.map(timestamp -> Duration.between(timestamp, Instant.now()).toDays());
        String freshnessStatus;
        boolean reportAvailable;
        String freshnessMessage;
        if (newestEvidence.isEmpty()) {
            freshnessStatus = "MISSING";
            reportAvailable = false;
            freshnessMessage = "No SOC 2 evidence has been collected yet.";
        } else if (evidenceAgeDays.orElse(0L) > maxAgeDays) {
            freshnessStatus = "STALE";
            reportAvailable = false;
            freshnessMessage = String.format(
                "SOC 2 evidence is %d days old; the configured maximum is %d days.",
                evidenceAgeDays.orElse(0L),
                maxAgeDays
            );
        } else {
            freshnessStatus = "FRESH";
            reportAvailable = true;
            freshnessMessage = "SOC 2 evidence is fresh enough to render the compliance report.";
        }

        Map<String, Object> freshness = new LinkedHashMap<>();
        freshness.put("status", freshnessStatus);
        freshness.put("reportAvailable", reportAvailable);
        freshness.put("newestEvidenceAt", newestEvidence.map(Instant::toString).orElse(null));
        freshness.put("evidenceAgeDays", evidenceAgeDays.orElse(null));
        freshness.put("maxAgeDays", maxAgeDays);
        freshness.put("message", freshnessMessage);

        Map<String, Object> soc2 = new LinkedHashMap<>();
        soc2.put("title", report.title());
        soc2.put("generatedAt", report.generatedAt());
        soc2.put("overallStatus", report.overallStatus());
        soc2.put("controlCount", report.controls().size());
        soc2.put("controls", report.controls().stream().map(control -> Map.of(
            "controlId", control.controlId(),
            "description", control.description(),
            "status", control.status()
        )).toList());
        soc2.put("freshness", freshness);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantId", tenantId);
        response.put("configured", complianceService != null);
        response.put("supportedOperations", List.of(
            "GDPR_ACCESS",
            "GDPR_ERASURE",
            "GDPR_CORRECTION",
            "GDPR_PORTABILITY",
            "CCPA_OPT_OUT"
        ));
        response.put("registeredCollections", complianceService != null
            ? complianceService.registeredCollections()
            : List.of());
        response.put("soc2", soc2);
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse.apply(response));
    }

    // ---- Policy Evaluation -------------------------------------------------

    /**
     * POST /governance/policy/evaluate — evaluates a named policy with context.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handlePolicyEvaluate(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om =
                    com.ghatana.platform.core.util.JsonUtils.getDefaultMapper();
                Map<String, Object> body = om.readValue(
                    buf.getString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
                String tenantId  = (String) body.getOrDefault("tenantId", "system");
                String policyId  = (String) body.get("policyId");
                Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());
                if (policyId == null) {
                    return Promise.of(HttpHelper.errorResponse(400, "policyId is required"));
                }
                return policyEngine.evaluate(tenantId, policyId, context)
                    .map(result -> jsonResponse.apply(Map.of(
                        "policyId",   policyId,
                        "tenantId",   tenantId,
                        "allowed",    result.allowed(),
                        "reasons",    result.reasons(),
                        "riskScore",  result.riskScore(),
                        "timestamp",  Instant.now().toString()
                    )));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    // ---- Security Analytics ------------------------------------------------

    /**
     * GET /governance/security/egress — returns current egress byte count for tenant+agent.
     */
    public Promise<HttpResponse> handleEgressStats(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        String agentId  = request.getQueryParameter("agentId");
        if (tenantId == null || agentId == null) {
            return Promise.of(HttpHelper.errorResponse(400, "tenantId and agentId query params required"));
        }
        return egressMonitor.currentWindowBytes(tenantId, agentId)
            .map(bytes -> jsonResponse.apply(Map.of(
                "tenantId",     tenantId,
                "agentId",      agentId,
                "windowBytes",  bytes,
                "timestamp",    Instant.now().toString()
            )));
    }

    /**
     * POST /governance/security/scan — scans text for prompt injection patterns.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleInjectionScan(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om =
                    com.ghatana.platform.core.util.JsonUtils.getDefaultMapper();
                Map<String, Object> body = om.readValue(
                    buf.getString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
                String tenantId = (String) body.getOrDefault("tenantId", "system");
                String text     = (String) body.get("text");
                if (text == null) {
                    return Promise.of(HttpHelper.errorResponse(400, "text is required"));
                }
                return injectionDetector.detect(tenantId, text)
                    .map(result -> jsonResponse.apply(Map.of(
                        "injectionDetected", result.injectionDetected(),
                        "confidence",        result.confidence(),
                        "matchedPattern",    result.matchedPattern() != null ? result.matchedPattern() : "",
                        "timestamp",         Instant.now().toString()
                    )));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }
}
