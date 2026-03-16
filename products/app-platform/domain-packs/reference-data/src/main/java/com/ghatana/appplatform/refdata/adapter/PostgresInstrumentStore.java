package com.ghatana.appplatform.refdata.adapter;

import com.ghatana.appplatform.refdata.domain.Instrument;
import com.ghatana.appplatform.refdata.domain.InstrumentStatus;
import com.ghatana.appplatform.refdata.domain.InstrumentType;
import com.ghatana.appplatform.refdata.port.InstrumentStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type       Driven Adapter (Repository)
 * @doc.purpose    PostgreSQL implementation of InstrumentStore.
 *                 SCD Type-2 versioning: every state transition closes the current
 *                 row (sets effective_to) and inserts a new open row
 *                 (effective_to IS NULL) inside a single transaction.
 * @doc.layer      Infrastructure
 * @doc.pattern    Hexagonal / Repository
 */
public class PostgresInstrumentStore implements InstrumentStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresInstrumentStore.class);

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresInstrumentStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Instrument> save(Instrument instrument) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO instrument_master " +
                         "(id, symbol, exchange, isin, name, type, status, sector, " +
                         " lot_size, tick_size, currency, effective_from, effective_to, " +
                         " created_at_utc, created_at_bs, metadata) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)")) {
                bindInstrument(ps, instrument);
                ps.executeUpdate();
            }
            log.debug("instrument.saved id={} symbol={}", instrument.id(), instrument.symbol());
            return instrument;
        });
    }

    /**
     * Atomic SCD Type-2 transition: close the previous version, insert the new one.
     * Both writes execute inside a single JDBC transaction.
     */
    @Override
    public Promise<Instrument> saveNewVersion(Instrument closedPrevious, Instrument newVersion) {
        return Promise.ofBlocking(executor, () -> {
            Connection conn = dataSource.getConnection();
            try {
                conn.setAutoCommit(false);

                // 1. Close the current (previous) version
                try (PreparedStatement close = conn.prepareStatement(
                        "UPDATE instrument_master SET effective_to = ? " +
                        "WHERE id = ? AND effective_to IS NULL")) {
                    close.setDate(1, Date.valueOf(closedPrevious.effectiveTo()));
                    close.setObject(2, closedPrevious.id());
                    close.executeUpdate();
                }

                // 2. Insert the new open version
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO instrument_master " +
                        "(id, symbol, exchange, isin, name, type, status, sector, " +
                        " lot_size, tick_size, currency, effective_from, effective_to, " +
                        " created_at_utc, created_at_bs, metadata) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)")) {
                    bindInstrument(ins, newVersion);
                    ins.executeUpdate();
                }

                conn.commit();
                log.info("instrument.versioned id={} newStatus={}", newVersion.id(), newVersion.status());
                return newVersion;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
                conn.close();
            }
        });
    }

    @Override
    public Promise<Optional<Instrument>> findCurrentById(UUID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM instrument_master WHERE id = ? AND effective_to IS NULL")) {
                ps.setObject(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<Optional<Instrument>> findByIdAsOf(UUID id, LocalDate asOf) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM instrument_master " +
                         "WHERE id = ? " +
                         "  AND effective_from <= ? " +
                         "  AND (effective_to IS NULL OR effective_to > ?)")) {
                ps.setObject(1, id);
                ps.setDate(2, Date.valueOf(asOf));
                ps.setDate(3, Date.valueOf(asOf));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<List<Instrument>> search(String query, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<Instrument> results = new ArrayList<>();
            String pattern = "%" + query.toLowerCase() + "%";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM instrument_master " +
                         "WHERE effective_to IS NULL " +
                         "  AND (LOWER(symbol) LIKE ? OR LOWER(isin) LIKE ? OR LOWER(name) LIKE ?) " +
                         "LIMIT ?")) {
                ps.setString(1, pattern);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setInt(4, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) results.add(mapRow(rs));
            }
            return results;
        });
    }

    @Override
    public Promise<List<Instrument>> listCurrent(InstrumentStatus statusFilter) {
        return Promise.ofBlocking(executor, () -> {
            List<Instrument> results = new ArrayList<>();
            String sql = "SELECT * FROM instrument_master WHERE effective_to IS NULL" +
                    (statusFilter != null ? " AND status = ?" : "") +
                    " ORDER BY symbol";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (statusFilter != null) ps.setString(1, statusFilter.name());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) results.add(mapRow(rs));
            }
            return results;
        });
    }

    @Override
    public Promise<Boolean> existsBySymbolAndExchange(String symbol, String exchange) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM instrument_master " +
                         "WHERE symbol = ? AND exchange = ? AND effective_to IS NULL LIMIT 1")) {
                ps.setString(1, symbol);
                ps.setString(2, exchange);
                ResultSet rs = ps.executeQuery();
                return rs.next();
            }
        });
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private void bindInstrument(PreparedStatement ps, Instrument i) throws SQLException {
        ps.setObject(1, i.id());
        ps.setString(2, i.symbol());
        ps.setString(3, i.exchange());
        ps.setString(4, i.isin());
        ps.setString(5, i.name());
        ps.setString(6, i.type().name());
        ps.setString(7, i.status().name());
        ps.setString(8, i.sector());
        ps.setInt(9, i.lotSize());
        ps.setBigDecimal(10, i.tickSize());
        ps.setString(11, i.currency());
        ps.setDate(12, Date.valueOf(i.effectiveFrom()));
        ps.setDate(13, i.effectiveTo() != null ? Date.valueOf(i.effectiveTo()) : null);
        ps.setObject(14, java.sql.Timestamp.from(i.createdAtUtc()));
        ps.setString(15, i.createdAtBs());
        ps.setString(16, i.metadata() != null ? i.metadata() : "{}");
    }

    private Instrument mapRow(ResultSet rs) throws SQLException {
        return new Instrument(
                UUID.fromString(rs.getString("id")),
                rs.getString("symbol"),
                rs.getString("exchange"),
                rs.getString("isin"),
                rs.getString("name"),
                InstrumentType.valueOf(rs.getString("type")),
                InstrumentStatus.valueOf(rs.getString("status")),
                rs.getString("sector"),
                rs.getInt("lot_size"),
                rs.getBigDecimal("tick_size"),
                rs.getString("currency"),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                rs.getTimestamp("created_at_utc").toInstant(),
                rs.getString("created_at_bs"),
                rs.getString("metadata"));
    }
}
