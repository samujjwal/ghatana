/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Chart of accounts entry representing a single account (K16-016).
 *
 * <p>Accounts form a tree hierarchy via {@code parentId} and are scoped
 * to a tenant and jurisdiction. T1 configurable per jurisdiction via K-02.
 *
 * @doc.type record
 * @doc.purpose Account in the chart of accounts hierarchy
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record Account(
        UUID accountId,
        String code,           // unique within (tenant_id, jurisdiction)
        String name,
        AccountType type,
        UUID parentId,         // null = root account
        String currency,       // ISO 4217 code
        AccountStatus status,
        String jurisdiction,   // e.g., 'NPL', 'SEBON' (nullable)
        UUID tenantId,         // null = platform-level account
        Instant createdAt
) {
    public Account {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Accounts in INACTIVE or SUSPENDED status cannot receive new entries. */
    public boolean isPostable() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Account lifecycle status.
     */
    public enum AccountStatus {
        /**
         * Active — accepts new journal entries.
         */
        ACTIVE,
        /**
         * Inactive — closed for business; no new entries allowed.
         */
        INACTIVE,
        /**
         * Suspended — temporarily frozen; requires review before reactivation.
         */
        SUSPENDED
    }
}
