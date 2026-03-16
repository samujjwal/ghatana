package com.ghatana.appplatform.risk.domain;

import java.math.BigDecimal;

/**
 * @doc.type    Domain Object (Record)
 * @doc.purpose Immutable result of a risk check pipeline evaluation (D06-001, D06-002, D06-003).
 *              Carries the outcome status, reason text, and margin figures for audit.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 *
 * @param status            APPROVE or DENY.
 * @param reason            Human-readable reason — populated on DENY.
 * @param requiredMargin    Margin required for this order.
 * @param availableMargin   Client's current available margin balance.
 * @param marginUtilization Percentage of total margin being used after this order.
 */
public record RiskCheckResult(
        RiskStatus status,
        String reason,
        BigDecimal requiredMargin,
        BigDecimal availableMargin,
        double marginUtilization
) {
    public boolean isApproved() {
        return status == RiskStatus.APPROVE;
    }

    public enum RiskStatus {
        APPROVE, DENY
    }
}
