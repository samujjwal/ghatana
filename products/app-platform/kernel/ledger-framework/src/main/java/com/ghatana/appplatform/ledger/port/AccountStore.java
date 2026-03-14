/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.port;

import com.ghatana.appplatform.ledger.domain.Account;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for chart-of-accounts storage operations (K16-016).
 *
 * @doc.type interface
 * @doc.purpose Storage port for chart-of-accounts CRUD operations
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface AccountStore {

    /**
     * Creates a new account in the chart of accounts.
     *
     * @param account the account to create (accountId already assigned)
     * @return the persisted account
     */
    Promise<Account> createAccount(Account account);

    /**
     * Retrieves an account by its ID.
     *
     * @param accountId account identifier
     * @return Optional containing the account, or empty if not found
     */
    Promise<Optional<Account>> getAccount(UUID accountId);

    /**
     * Retrieves an account by code and tenant.
     *
     * @param code     account code
     * @param tenantId tenant scoping (null = platform-level)
     * @return Optional containing the account, or empty if not found
     */
    Promise<Optional<Account>> getAccountByCode(String code, UUID tenantId);

    /**
     * Lists all accounts for a tenant, optionally filtering by type.
     *
     * @param tenantId tenant scoping (null = platform-level)
     * @return ordered list of accounts (by code)
     */
    Promise<List<Account>> listAccounts(UUID tenantId);

    /**
     * Updates account status (ACTIVE → INACTIVE or SUSPENDED).
     *
     * @param accountId account to update
     * @param status    new status
     * @return updated account
     */
    Promise<Account> updateStatus(UUID accountId, Account.AccountStatus status);
}
