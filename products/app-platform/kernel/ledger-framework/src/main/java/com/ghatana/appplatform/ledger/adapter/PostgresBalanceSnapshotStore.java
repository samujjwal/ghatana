/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.adapter;

import com.ghatana.appplatform.ledger.domain.BalanceSnapshot;
import com.ghatana.appplatform.ledger.port.BalanceSnapshotStore;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for {@link BalanceSnapshotStore} (K16-008).
 *
 * <p>Schema: {@code ledger_balance_snapshots} (see {@code V003__ledger_balance_snapshots.sql}).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for balance snapshot storage (K16-008)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresBalanceSnapshotStore implements BalanceSnapshotStore {

    private static final String SQL_INSERT = """
            INSERT INTO ledger_balance_snapshots
                (snapshot_id, account_id, currency_code, net_balance, snapshot_at, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_LATEST = """
            SELECT snapshot_id, account_id, currency_code, net_balance, snapshot_at, tenant_id
              FROM ledger_balance_snapshots
             WHERE account_id = ? AND currency_code = ? AND tenant_id = ?
             ORDER BY snapshot_at DESC
             LIMIT 1
            """;

    private static final String SQL_ALL = """
            SELECT snapshot_id, account_id, currency_code, net_balance, snapshot_at, tenant_id
              FROM ledger_balance_snapshots
             WHERE account_id = ? AND currency_code = ? AND tenant_id = ?
             ORDER BY snapshot_at DESC
             LIMIT ?
            """;

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresBalanceSnapshotStore(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Promise<Void> save(BalanceSnapshot snapshot) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
                ps.setObject(1, snapshot.snapshotId());
                ps.setObject(2, snapshot.accountId());
                ps.setString(3, snapshot.currencyCode());
                ps.setBigDecimal(4, snapshot.netBalance());
                ps.setTimestamp(5, Timestamp.from(snapshot.snapshotAt()));
                ps.setObject(6, snapshot.tenantId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Optional<BalanceSnapshot>> findLatest(UUID accountId, String currencyCode,
                                                          UUID tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_LATEST)) {
                ps.setObject(1, accountId);
                ps.setString(2, currencyCode.toUpperCase());
                ps.setObject(3, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<BalanceSnapshot>> findAll(UUID accountId, String currencyCode,
                                                   UUID tenantId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<BalanceSnapshot> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_ALL)) {
                ps.setObject(1, accountId);
                ps.setString(2, currencyCode.toUpperCase());
                ps.setObject(3, tenantId);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            }
            return result;
        });
    }

    private static BalanceSnapshot mapRow(ResultSet rs) throws SQLException {
        return new BalanceSnapshot(
            (UUID) rs.getObject("snapshot_id"),
            (UUID) rs.getObject("account_id"),
            rs.getString("currency_code"),
            rs.getBigDecimal("net_balance"),
            rs.getTimestamp("snapshot_at").toInstant(),
            (UUID) rs.getObject("tenant_id")
        );
    }
}
