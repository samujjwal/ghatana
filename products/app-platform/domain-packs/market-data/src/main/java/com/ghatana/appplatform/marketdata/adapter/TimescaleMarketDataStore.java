package com.ghatana.appplatform.marketdata.adapter;

import com.ghatana.appplatform.marketdata.domain.MarketTick;
import com.ghatana.appplatform.marketdata.domain.TickSource;
import com.ghatana.appplatform.marketdata.port.MarketDataStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type       Driven Adapter (Repository)
 * @doc.purpose    PostgreSQL / TimescaleDB adapter for market tick storage.
 *                 The {@code normalized_ticks} table is a TimescaleDB hypertable
 *                 partitioned by {@code timestamp_utc} (hourly chunks).
 *                 Time-range queries benefit from the hypertable index pruning.
 *                 D04-001: timescale_hypertable, tick_write_api.
 * @doc.layer      Infrastructure
 * @doc.pattern    Hexagonal / Repository
 */
public class TimescaleMarketDataStore implements MarketDataStore {

    private static final Logger log = LoggerFactory.getLogger(TimescaleMarketDataStore.class);

    private final DataSource dataSource;
    private final Executor executor;

    public TimescaleMarketDataStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Void> ingestTick(MarketTick tick) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO normalized_ticks " +
                         "(instrument_id, timestamp_utc, calendar_date, bid, ask, last, volume, " +
                         " open, high, low, close, source, sequence, anomaly_flag) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, tick.instrumentId());
                ps.setObject(2, Timestamp.from(tick.timestampUtc()));
                ps.setObject(3, tick.calendarDate() != null
                        ? Date.valueOf(tick.calendarDate()) : null);
                ps.setBigDecimal(4, tick.bid());
                ps.setBigDecimal(5, tick.ask());
                ps.setBigDecimal(6, tick.last());
                ps.setLong(7, tick.volume());
                ps.setBigDecimal(8, tick.open());
                ps.setBigDecimal(9, tick.high());
                ps.setBigDecimal(10, tick.low());
                ps.setBigDecimal(11, tick.close());
                ps.setString(12, tick.source().name());
                ps.setLong(13, tick.sequence());
                ps.setBoolean(14, tick.anomalyFlag());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public Promise<List<MarketTick>> findTicks(String instrumentId, Instant from, Instant to) {
        return Promise.ofBlocking(executor, () -> {
            List<MarketTick> ticks = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM normalized_ticks " +
                         "WHERE instrument_id = ? " +
                         "  AND timestamp_utc BETWEEN ? AND ? " +
                         "ORDER BY timestamp_utc ASC")) {
                ps.setString(1, instrumentId);
                ps.setObject(2, Timestamp.from(from));
                ps.setObject(3, Timestamp.from(to));
                ResultSet rs = ps.executeQuery();
                while (rs.next()) ticks.add(mapRow(rs));
            }
            return ticks;
        });
    }

    @Override
    public Promise<Optional<MarketTick>> findLatestTick(String instrumentId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM normalized_ticks " +
                         "WHERE instrument_id = ? " +
                         "ORDER BY timestamp_utc DESC LIMIT 1")) {
                ps.setString(1, instrumentId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        });
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private MarketTick mapRow(ResultSet rs) throws SQLException {
        Date calDate = rs.getDate("calendar_date");
        return new MarketTick(
                rs.getString("instrument_id"),
                rs.getTimestamp("timestamp_utc").toInstant(),
                calDate != null ? calDate.toLocalDate() : null,
                rs.getBigDecimal("bid"),
                rs.getBigDecimal("ask"),
                rs.getBigDecimal("last"),
                rs.getLong("volume"),
                rs.getBigDecimal("open"),
                rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),
                rs.getBigDecimal("close"),
                TickSource.valueOf(rs.getString("source")),
                rs.getLong("sequence"),
                rs.getBoolean("anomaly_flag"));
    }
}
