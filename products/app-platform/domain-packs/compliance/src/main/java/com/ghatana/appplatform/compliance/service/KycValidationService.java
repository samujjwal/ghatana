package com.ghatana.appplatform.compliance.service;

import com.ghatana.appplatform.compliance.domain.*;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Validates KYC status before trade execution (D07-006).
 *              Rules: VERIFIED → allow; EXPIRED → block new purchases but allow sells;
 *              PENDING/REJECTED/SUSPENDED → block all trading.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class KycValidationService {

    /**
     * Evaluate the KYC constraint for the given order request.
     * KYC status is sourced from request (originally from K-01 profile lookup).
     */
    public ComplianceCheckResult.RuleEvaluationDetail evaluate(ComplianceCheckRequest request) {
        KycStatus status;
        try {
            status = KycStatus.valueOf(request.kycStatus());
        } catch (IllegalArgumentException e) {
            return new ComplianceCheckResult.RuleEvaluationDetail(
                    "KYC_CHECK", "KYC Status Validation", ComplianceStatus.FAIL,
                    "Unknown KYC status: " + request.kycStatus());
        }

        return switch (status) {
            case VERIFIED -> new ComplianceCheckResult.RuleEvaluationDetail(
                    "KYC_CHECK", "KYC Status Validation", ComplianceStatus.PASS, null);

            case EXPIRED -> {
                // EXPIRED clients may manage existing positions (SELL) but no new purchases
                if ("BUY".equals(request.orderSide())) {
                    yield new ComplianceCheckResult.RuleEvaluationDetail(
                            "KYC_CHECK", "KYC Status Validation", ComplianceStatus.FAIL,
                            "KYC expired: new purchases blocked. Client must renew KYC.");
                }
                yield new ComplianceCheckResult.RuleEvaluationDetail(
                        "KYC_CHECK", "KYC Status Validation", ComplianceStatus.PASS,
                        "KYC expired but sell allowed for position management");
            }

            case PENDING -> new ComplianceCheckResult.RuleEvaluationDetail(
                    "KYC_CHECK", "KYC Status Validation", ComplianceStatus.FAIL,
                    "KYC pending verification: trading suspended until KYC is complete");

            case REJECTED -> new ComplianceCheckResult.RuleEvaluationDetail(
                    "KYC_CHECK", "KYC Status Validation", ComplianceStatus.FAIL,
                    "KYC rejected: trading suspended. Contact compliance team.");

            case SUSPENDED -> new ComplianceCheckResult.RuleEvaluationDetail(
                    "KYC_CHECK", "KYC Status Validation", ComplianceStatus.FAIL,
                    "Account suspended: trading not permitted");
        };
    }
}
