package com.ghatana.appplatform.compliance.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Lock-in period record — tracks shares locked for a specific client
 *              and instrument (D07-004). Dates kept in Bikram Sambat for legal accuracy.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record LockInRecord(
        String lockInId,
        String clientId,
        String instrumentId,
        BigDecimal lockedQuantity,
        String lockInStartBs,       // Bikram Sambat start date
        String lockInEndBs,         // Bikram Sambat end date
        LockInType lockInType,
        Instant createdAt
) {
    public boolean isActive(String todayBs) {
        // String comparison works for YYYY-MM-DD BS dates
        return lockInStartBs.compareTo(todayBs) <= 0
                && lockInEndBs.compareTo(todayBs) >= 0;
    }

    public boolean isExpired(String todayBs) {
        return lockInEndBs.compareTo(todayBs) < 0;
    }
}
