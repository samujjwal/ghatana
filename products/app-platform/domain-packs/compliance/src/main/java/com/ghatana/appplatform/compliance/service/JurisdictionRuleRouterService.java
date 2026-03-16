package com.ghatana.appplatform.compliance.service;

import com.ghatana.appplatform.compliance.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Routes compliance checks to jurisdiction-specific rule sets via K-03 OPA (D07-002).
 *              Falls through to GLOBAL rules when jurisdiction-specific bundle is not found.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service, Strategy
 */
public class JurisdictionRuleRouterService {

    private static final Logger log = LoggerFactory.getLogger(JurisdictionRuleRouterService.class);

    /** K-03 OPA evaluation port - evaluates a policy at the given path with the given input JSON. */
    private final OpaEvaluationPort opaPort;

    public JurisdictionRuleRouterService(OpaEvaluationPort opaPort) {
        this.opaPort = opaPort;
    }

    public ComplianceCheckResult.RuleEvaluationDetail evaluate(ComplianceCheckRequest request) {
        try {
            String policyPath = "compliance/" + request.jurisdiction().toLowerCase();
            String inputJson = buildInputJson(request);

            OpaDecision decision;
            try {
                // Try jurisdiction-specific rules first (D07-002)
                decision = opaPort.evaluate(policyPath, inputJson);
            } catch (PolicyNotFoundException e) {
                // Fall through to GLOBAL rules
                log.debug("No jurisdiction-specific rules for {}, using GLOBAL", request.jurisdiction());
                decision = opaPort.evaluate("compliance/global", inputJson);
            }

            if (decision.allow()) {
                return new ComplianceCheckResult.RuleEvaluationDetail(
                        "JURISDICTION_RULES", "Jurisdiction Rules (" + request.jurisdiction() + ")",
                        ComplianceStatus.PASS, null);
            }

            String reason = decision.reason() != null ? decision.reason()
                    : "Jurisdiction rule denied trade for " + request.jurisdiction();
            ComplianceStatus status = decision.requiresReview()
                    ? ComplianceStatus.REVIEW : ComplianceStatus.FAIL;
            return new ComplianceCheckResult.RuleEvaluationDetail(
                    "JURISDICTION_RULES", "Jurisdiction Rules (" + request.jurisdiction() + ")",
                    status, reason);

        } catch (Exception e) {
            // Circuit-breaker fallback: fail-safe DENY for compliance rules
            log.error("OPA jurisdiction check failed — defaulting to FAIL (fail-safe)", e);
            return new ComplianceCheckResult.RuleEvaluationDetail(
                    "JURISDICTION_RULES", "Jurisdiction Rules",
                    ComplianceStatus.FAIL, "Compliance rule engine unavailable: " + e.getMessage());
        }
    }

    private String buildInputJson(ComplianceCheckRequest request) {
        return String.format("""
                {"order_id":"%s","client_id":"%s","instrument_id":"%s",
                 "side":"%s","quantity":"%s","kyc_status":"%s","aml_risk_score":%d}""",
                request.orderId(), request.clientId(), request.instrumentId(),
                request.orderSide(), request.quantity(), request.kycStatus(), request.amlRiskScore());
    }

    // ─── Ports ───────────────────────────────────────────────────────────────

    public interface OpaEvaluationPort {
        OpaDecision evaluate(String policyPath, String inputJson) throws PolicyNotFoundException;
    }

    public record OpaDecision(boolean allow, boolean requiresReview, String reason) {}

    public static class PolicyNotFoundException extends RuntimeException {
        public PolicyNotFoundException(String path) {
            super("OPA policy not found: " + path);
        }
    }
}
