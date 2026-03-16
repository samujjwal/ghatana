package com.ghatana.appplatform.refdata.adapter;

import com.ghatana.appplatform.refdata.domain.Benchmark;
import com.ghatana.appplatform.refdata.domain.BenchmarkConstituent;
import com.ghatana.appplatform.refdata.domain.BenchmarkType;
import com.ghatana.appplatform.refdata.domain.BenchmarkValue;
import com.ghatana.appplatform.refdata.port.BenchmarkStore;
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
 * @doc.purpose    PostgreSQL implementation of BenchmarkStore.
 *                 Handles three related tables: benchmark_definitions,
 *                 benchmark_constituents (SCD Type-2), and benchmark_values
 *                 which acts as a timeseries keyed by (benchmark_id, date_utc).
 * @doc.layer      Infrastructure
 * @doc.pattern    Hexagonal / Repository
 */
public class PostgresBenchmarkStore implements BenchmarkStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresBenchmarkStore.class);

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresBenchmarkStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    // -----------------------------------------------------------------------
    // Benchmark definition
    // -----------------------------------------------------------------------

    @Override
    public Promise<Benchmark> saveBenchmark(Benchmark benchmark) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO benchmark_definitions " +
                         "(id, name, type, base_date, base_value, currency, " +
                         " calculation_method, status) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                         "ON CONFLICT (id) DO UPDATE SET " +
                         "  name = EXCLUDED.name, status = EXCLUDED.status, " +
                         "  calculation_method = EXCLUDED.calculation_method")) {
                ps.setObject(1, benchmark.id());
                ps.setString(2, benchmark.name());
                ps.setString(3, benchmark.type().name());
                ps.setDate(4, Date.valueOf(benchmark.baseDate()));
                ps.setBigDecimal(5, benchmark.baseValue());
                ps.setString(6, benchmark.currency());
                ps.setString(7, benchmark.calculationMethod());
                ps.setString(8, benchmark.status());
                ps.executeUpdate();
            }
            return benchmark;
        });
    }

    @Override
    public Promise<Optional<Benchmark>> findBenchmarkById(UUID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM benchmark_definitions WHERE id = ?")) {
                ps.setObject(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return Optional.of(mapBenchmark(rs));
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<List<Benchmark>> listBenchmarks() {
        return Promise.ofBlocking(executor, () -> {
            List<Benchmark> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM benchmark_definitions WHERE status != 'DELETED' ORDER BY name")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(mapBenchmark(rs));
            }
            return list;
        });
    }

    // -----------------------------------------------------------------------
    // Constituents (SCD Type-2 on weight history)
    // -----------------------------------------------------------------------

    /**
     * Replace-all strategy: close all current constituents for the benchmark,
     * then insert the new set in a single transaction.
     */
    @Override
    public Promise<Void> saveConstituents(UUID benchmarkId, List<BenchmarkConstituent> constituents) {
        return Promise.ofBlocking(executor, () -> {
            Connection conn = dataSource.getConnection();
            try {
                conn.setAutoCommit(false);

                // Close all current constituents
                try (PreparedStatement close = conn.prepareStatement(
                        "UPDATE benchmark_constituents SET effective_to = CURRENT_DATE " +
                        "WHERE benchmark_id = ? AND effective_to IS NULL")) {
                    close.setObject(1, benchmarkId);
                    close.executeUpdate();
                }

                // Insert new set
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO benchmark_constituents " +
                        "(benchmark_id, instrument_id, weight, effective_from, effective_to) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
                    for (BenchmarkConstituent c : constituents) {
                        ins.setObject(1, c.benchmarkId());
                        ins.setObject(2, c.instrumentId());
                        ins.setBigDecimal(3, c.weight());
                        ins.setDate(4, Date.valueOf(c.effectiveFrom()));
                        ins.setDate(5, c.effectiveTo() != null
                                ? Date.valueOf(c.effectiveTo()) : null);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }

                conn.commit();
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
    public Promise<List<BenchmarkConstituent>> findCurrentConstituents(UUID benchmarkId) {
        return Promise.ofBlocking(executor, () -> {
            List<BenchmarkConstituent> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM benchmark_constituents " +
                         "WHERE benchmark_id = ? AND effective_to IS NULL " +
                         "ORDER BY weight DESC")) {
                ps.setObject(1, benchmarkId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(mapConstituent(rs));
            }
            return list;
        });
    }

    // -----------------------------------------------------------------------
    // Values / time-series
    // -----------------------------------------------------------------------

    @Override
    public Promise<Void> saveValue(BenchmarkValue value) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO benchmark_values " +
                         "(benchmark_id, date_utc, date_bs, open_value, high_value, " +
                         " low_value, close_value, volume, daily_return) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                         "ON CONFLICT (benchmark_id, date_utc) DO UPDATE SET " +
                         "  open_value = EXCLUDED.open_value, " +
                         "  high_value = EXCLUDED.high_value, " +
                         "  low_value = EXCLUDED.low_value, " +
                         "  close_value = EXCLUDED.close_value, " +
                         "  volume = EXCLUDED.volume, " +
                         "  daily_return = EXCLUDED.daily_return")) {
                ps.setObject(1, value.benchmarkId());
                ps.setDate(2, Date.valueOf(value.dateUtc()));
                ps.setString(3, value.dateBs());
                ps.setBigDecimal(4, value.openValue());
                ps.setBigDecimal(5, value.highValue());
                ps.setBigDecimal(6, value.lowValue());
                ps.setBigDecimal(7, value.closeValue());
                ps.setLong(8, value.volume());
                ps.setBigDecimal(9, value.dailyReturn());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public Promise<List<BenchmarkValue>> findValues(UUID benchmarkId, LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> {
            List<BenchmarkValue> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM benchmark_values " +
                         "WHERE benchmark_id = ? AND date_utc BETWEEN ? AND ? " +
                         "ORDER BY date_utc")) {
                ps.setObject(1, benchmarkId);
                ps.setDate(2, Date.valueOf(from));
                ps.setDate(3, Date.valueOf(to));
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(mapValue(rs));
            }
            return list;
        });
    }

    @Override
    public Promise<Optional<BenchmarkValue>> findLatestValue(UUID benchmarkId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM benchmark_values " +
                         "WHERE benchmark_id = ? " +
                         "ORDER BY date_utc DESC LIMIT 1")) {
                ps.setObject(1, benchmarkId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return Optional.of(mapValue(rs));
                return Optional.empty();
            }
        });
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private Benchmark mapBenchmark(ResultSet rs) throws SQLException {
        return new Benchmark(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                BenchmarkType.valueOf(rs.getString("type")),
                rs.getDate("base_date").toLocalDate(),
                rs.getBigDecimal("base_value"),
                rs.getString("currency"),
                rs.getString("calculation_method"),
                rs.getString("status"));
    }

    private BenchmarkConstituent mapConstituent(ResultSet rs) throws SQLException {
        return new BenchmarkConstituent(
                UUID.fromString(rs.getString("benchmark_id")),
                UUID.fromString(rs.getString("instrument_id")),
                rs.getBigDecimal("weight"),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null
                        ? rs.getDate("effective_to").toLocalDate() : null);
    }

    private BenchmarkValue mapValue(ResultSet rs) throws SQLException {
        return new BenchmarkValue(
                UUID.fromString(rs.getString("benchmark_id")),
                rs.getDate("date_utc").toLocalDate(),
                rs.getString("date_bs"),
                rs.getBigDecimal("open_value"),
                rs.getBigDecimal("high_value"),
                rs.getBigDecimal("low_value"),
                rs.getBigDecimal("close_value"),
                rs.getLong("volume"),
                rs.getBigDecimal("daily_return"));
    }
}
