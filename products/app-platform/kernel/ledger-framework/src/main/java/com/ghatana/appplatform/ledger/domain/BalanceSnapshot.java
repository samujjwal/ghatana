/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A point-in-time snapshot of an account's net balance (K16-008).
 *
 * <p>Snapshots are immutable once taken. They serve as efficient starting
 * points for balance queries, reducing the need to replay every journal entry.
 *
 * @doc.type record
 * @doc.purpose Immutable balance snapshot for an account at a specific instant (K16-008)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record BalanceSnapshot(
        UUID snapshotId,
        UUID accountId,
        String currencyCode,
        BigDecimal netBalance,
        Instant snapshotAt,
        UUID tenantId
) {
    public BalanceSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currencyCode, "currencyCode");
        Objects.requireNonNull(netBalance, "netBalance");
        Objects.requireNonNull(snapshotAt, "snapshotAt");
        Objects.requireNonNull(tenantId, "tenantId");
    }

    public static BalanceSnapshot of(UUID accountId, MonetaryAmount balance, UUID tenantId) {
        return new BalanceSnapshot(
            UUID.randomUUID(),
            accountId,
            balance.currencyCode(),
            balance.getAmount(),
            Instant.now(),
            tenantId
        );
    }
}
