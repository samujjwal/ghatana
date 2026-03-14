/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.adapter;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.port.CurrencyRegistry;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL-backed implementation of {@link CurrencyRegistry}.
 *
 * <p>Reads from and writes to the {@code currency_registry} table created by
 * {@code V001__create_ledger_schema.sql}. All blocking JDBC calls are wrapped
 * with {@code Promise.ofBlocking} so the ActiveJ event loop is never blocked.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for CurrencyRegistry port (K16-010)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresCurrencyRegistry implements CurrencyRegistry {

    private static final String GET_BY_CODE = """
            SELECT code, name, symbol, decimal_places, rounding_mode, is_active
              FROM currency_registry
             WHERE code = ?
            """;

    private static final String LIST_ACTIVE = """
            SELECT code, name, symbol, decimal_places, rounding_mode, is_active
              FROM currency_registry
             WHERE is_active = TRUE
             ORDER BY code
            """;

    private static final String UPSERT = """
            INSERT INTO currency_registry (code, name, symbol, decimal_places, rounding_mode, is_active)
            VALUES (?, ?, ?, ?, ?, TRUE)
            ON CONFLICT (code) DO UPDATE
               SET name           = EXCLUDED.name,
                   symbol         = EXCLUDED.symbol,
                   decimal_places = EXCLUDED.decimal_places,
                   rounding_mode  = EXCLUDED.rounding_mode,
                   is_active      = EXCLUDED.is_active
            """;

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresCurrencyRegistry(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Optional<Currency>> getCurrency(String code) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(GET_BY_CODE)) {
                ps.setString(1, code.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<Currency>> listActiveCurrencies() {
        return Promise.ofBlocking(executor, () -> {
            List<Currency> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(LIST_ACTIVE);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        });
    }

    @Override
    public Promise<Currency> register(Currency currency) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                ps.setString(1, currency.code());
                ps.setString(2, currency.name());
                ps.setString(3, currency.symbol());
                ps.setInt(4, currency.decimalPlaces());
                ps.setString(5, currency.roundingMode().name());
                ps.executeUpdate();
            }
            return currency;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static Currency mapRow(ResultSet rs) throws SQLException {
        return new Currency(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("symbol"),
                rs.getInt("decimal_places"),
                RoundingMode.valueOf(rs.getString("rounding_mode"))
        );
    }
}
