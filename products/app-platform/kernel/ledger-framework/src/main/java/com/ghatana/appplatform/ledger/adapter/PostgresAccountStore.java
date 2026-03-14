/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.adapter;

import com.ghatana.appplatform.ledger.domain.Account;
import com.ghatana.appplatform.ledger.domain.AccountType;
import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.port.AccountStore;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * PostgreSQL-backed implementation of {@link AccountStore}.
 *
 * <p>Reads from and writes to the {@code chart_of_accounts} table created by
 * {@code V001__create_ledger_schema.sql}.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for AccountStore port (K16-016)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresAccountStore implements AccountStore {

    private static final String INSERT = """
            INSERT INTO chart_of_accounts
                (account_id, code, name, account_type, parent_id, currency_code,
                 status, jurisdiction, tenant_id, created_at)
            VALUES (?, ?, ?, ?::account_type, ?, ?, ?::account_status, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT account_id, code, name, account_type, parent_id, currency_code,
                   cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode,
                   status, jurisdiction, tenant_id, created_at
              FROM chart_of_accounts coa
              JOIN currency_registry cr ON cr.code = coa.currency_code
             WHERE account_id = ?
            """;

    private static final String SELECT_BY_CODE = """
            SELECT account_id, code, name, account_type, parent_id, currency_code,
                   cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode,
                   status, jurisdiction, tenant_id, created_at
              FROM chart_of_accounts coa
              JOIN currency_registry cr ON cr.code = coa.currency_code
             WHERE coa.code = ? AND coa.tenant_id = ?
            """;

    private static final String LIST_BY_TENANT = """
            SELECT account_id, code, name, account_type, parent_id, currency_code,
                   cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode,
                   status, jurisdiction, tenant_id, created_at
              FROM chart_of_accounts coa
              JOIN currency_registry cr ON cr.code = coa.currency_code
             WHERE coa.tenant_id = ?
             ORDER BY coa.code
            """;

    private static final String UPDATE_STATUS = """
            UPDATE chart_of_accounts SET status = ?::account_status WHERE account_id = ?
            """;

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAccountStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Account> createAccount(Account account) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT)) {
                ps.setObject(1, account.accountId());
                ps.setString(2, account.code());
                ps.setString(3, account.name());
                ps.setString(4, account.type().name());
                ps.setObject(5, account.parentId().orElse(null));
                ps.setString(6, account.currency().code());
                ps.setString(7, account.status().name());
                ps.setString(8, account.jurisdiction().orElse(null));
                ps.setObject(9, account.tenantId());
                ps.setTimestamp(10, Timestamp.from(account.createdAt()));
                ps.executeUpdate();
            }
            return account;
        });
    }

    @Override
    public Promise<Optional<Account>> getAccount(UUID accountId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
                ps.setObject(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<Optional<Account>> getAccountByCode(String code, UUID tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_CODE)) {
                ps.setString(1, code);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<Account>> listAccounts(UUID tenantId) {
        return Promise.ofBlocking(executor, () -> {
            List<Account> accounts = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(LIST_BY_TENANT)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        accounts.add(mapRow(rs));
                    }
                }
            }
            return accounts;
        });
    }

    @Override
    public Promise<Account> updateStatus(UUID accountId, Account.AccountStatus status) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement updatePs = conn.prepareStatement(UPDATE_STATUS)) {
                    updatePs.setString(1, status.name());
                    updatePs.setObject(2, accountId);
                    updatePs.executeUpdate();
                }
                // Re-fetch to return the updated account
                try (PreparedStatement selectPs = conn.prepareStatement(SELECT_BY_ID)) {
                    selectPs.setObject(1, accountId);
                    try (ResultSet rs = selectPs.executeQuery()) {
                        if (rs.next()) {
                            return mapRow(rs);
                        }
                    }
                }
                throw new IllegalStateException("Account not found after status update: " + accountId);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static Account mapRow(ResultSet rs) throws SQLException {
        Currency currency = new Currency(
                rs.getString("currency_code"),
                rs.getString("cur_name"),
                rs.getString("cur_symbol"),
                rs.getInt("decimal_places"),
                RoundingMode.valueOf(rs.getString("rounding_mode"))
        );

        UUID parentId = (UUID) rs.getObject("parent_id");
        String jurisdiction = rs.getString("jurisdiction");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();

        return new Account(
                (UUID) rs.getObject("account_id"),
                rs.getString("code"),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("account_type")),
                Optional.ofNullable(parentId),
                currency,
                Account.AccountStatus.valueOf(rs.getString("status")),
                Optional.ofNullable(jurisdiction),
                (UUID) rs.getObject("tenant_id"),
                createdAt
        );
    }
}
