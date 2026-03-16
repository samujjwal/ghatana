package com.ghatana.appplatform.risk.service;

import java.math.BigDecimal;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Provides the dashboard and reporting API for margin call metrics,
 *              aggregating open calls, total deficit, average age, and overdue count (D06-014).
 * @doc.layer   Domain — risk engine (read side)
 * @doc.pattern Query — read-only aggregation; no writes. Business logic in MarginCallService.
 */
public class MarginCallDashboardService {

    /** Snapshot record returned by the dashboard API and used by MarginCallService. */
    public record MarginDashboard(
        int openCalls,
        BigDecimal totalDeficit,
        double avgAgeDays,
        int overdueCount
    ) {}

    /** Per-client margin status returned by GET /risk/margin/{clientId}. */
    public record ClientMarginStatus(
        String clientId,
        BigDecimal positionValue,
        BigDecimal requiredMaintenance,
        BigDecimal postedCollateral,
        BigDecimal excess,
        boolean hasOpenCall,
        String openCallId,
        BigDecimal openCallDeficit
    ) {}
}
