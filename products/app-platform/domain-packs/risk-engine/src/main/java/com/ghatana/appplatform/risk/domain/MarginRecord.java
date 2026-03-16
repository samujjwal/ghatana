package com.ghatana.appplatform.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type    Domain Object (Record)
 * @doc.purpose Represents a client's margin account state (D06-001, D06-009).
 *              Tracks deposited collateral, used margin, and available balance.
 *              Stored in both Redis (hot cache) and PostgreSQL (source of truth).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 *
 * @param clientId      Owning client identifier.
 * @param accountId     Sub-account within the client portfolio.
 * @param deposited     Total collateral deposited.
 * @param used          Margin currently allocated to open positions.
 * @param updatedAt     Last modification timestamp.
 */
public record MarginRecord(
        String clientId,
        String accountId,
        BigDecimal deposited,
        BigDecimal used,
        Instant updatedAt
) {
    /** Available = deposited − used. */
    public BigDecimal available() {
        return deposited.subtract(used);
    }

    /** Utilization ratio in range [0.0, 1.0+]. */
    public double utilizationRatio() {
        if (deposited.compareTo(BigDecimal.ZERO) == 0) return 1.0;
        return used.divide(deposited, 4, java.math.RoundingMode.HALF_EVEN).doubleValue();
    }

    /** Return a new MarginRecord with additional margin reserved. */
    public MarginRecord withReserved(BigDecimal additional) {
        return new MarginRecord(clientId, accountId,
                deposited, used.add(additional), Instant.now());
    }

    /** Return a new MarginRecord releasing previously reserved margin. */
    public MarginRecord withReleased(BigDecimal amount) {
        BigDecimal newUsed = used.subtract(amount).max(BigDecimal.ZERO);
        return new MarginRecord(clientId, accountId, deposited, newUsed, Instant.now());
    }
}
