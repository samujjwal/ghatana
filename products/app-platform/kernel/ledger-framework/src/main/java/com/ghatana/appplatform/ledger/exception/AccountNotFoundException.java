/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.exception;

import java.util.UUID;

/**
 * Thrown when an operation references an account that does not exist in the
 * chart of accounts, or when an account is not in ACTIVE status.
 *
 * @doc.type class
 * @doc.purpose Domain exception for missing or inactive account references
 * @doc.layer core
 * @doc.pattern Service
 */
public class AccountNotFoundException extends RuntimeException {

    private final UUID accountId;

    public AccountNotFoundException(UUID accountId) {
        super("Account not found or not active: " + accountId);
        this.accountId = accountId;
    }

    public UUID getAccountId() { return accountId; }
}
