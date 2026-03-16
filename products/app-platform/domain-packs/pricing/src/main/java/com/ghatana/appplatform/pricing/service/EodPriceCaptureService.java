package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Captures end-of-day official closing price for each instrument using a
 *              waterfall resolution strategy: (1) exchange official close, (2) session VWAP,
 *              (3) last traded price, (4) previous close (carry-forward). Stores the applied
 *              source for audit. Emits EODPriceCaptured event to downstream consumers
 *              (NAV engine, risk engine). Idempotent: re-running for same date UPSERTs.
 * @doc.layer   Domain
 * @doc.pattern EOD batch trigger; waterfall fallback; event emission via port;
 *              UPSERT idempotency via ON CONFLICT(instrument_id, price_date_ad) DO UPDATE.
 */
public class EodPriceCaptureService {

    private static final Logger log = LoggerFactory.getLogger(EodPriceCaptureService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final EodEventPort     eventPort;
    private final Counter          capturedCounter;
    private final Counter          carryForwardCounter;

    public EodPriceCaptureService(HikariDataSource dataSource, Executor executor,
                                  CalendarPort calendarPort, EodEventPort eventPort,
                                  MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.calendarPort      = calendarPort;
        this.eventPort         = eventPort;
        this.capturedCounter   = registry.counter("pricing.eod.captured");
        this.carryForwardCounter = registry.counter("pricing.eod.carry_forward");
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface CalendarPort {
        String adToBs(LocalDate adDate);
    }

    public interface EodEventPort {
        void emitEodPriceCaptured(EODPriceCaptured event);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record EODPriceCaptured(
        String    instrumentId,
        LocalDate priceDateAd,
        String    priceDateBs,
        double    closePrice,
        String    priceSource   // EXCHANGE_CLOSE | VWAP | LAST_TRADE | CARRY_FORWARD
    ) {}

    public record EodCaptureResult(
        String    instrumentId,
        double    closePrice,
        String    priceSource
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run EOD price capture for all active instruments.
     */
    public Promise<List<EodCaptureResult>> runEodBatch(LocalDate priceDateAd) {
        return Promise.ofBlocking(executor, () -> {
            String priceDateBs = calendarPort.adToBs(priceDateAd);
            List<String> instruments = loadActiveInstrumentIds();
            List<EodCaptureResult> results = new ArrayList<>();

            for (String instrumentId : instruments) {
                EodCaptureResult result = captureEodPrice(instrumentId, priceDateAd, priceDateBs);
                results.add(result);
                if ("CARRY_FORWARD".equals(result.priceSource())) {
                    carryForwardCounter.increment();
                } else {
                    capturedCounter.increment();
                }
            }
            log.info("EOD price capture date={} count={}", priceDateAd, results.size());
            return results;
        });
    }

    /**
     * Capture EOD price for a single instrument.
     */
    public Promise<EodCaptureResult> captureForInstrument(String instrumentId, LocalDate priceDateAd) {
        return Promise.ofBlocking(executor, () -> {
            String priceDateBs = calendarPort.adToBs(priceDateAd);
            return captureEodPrice(instrumentId, priceDateAd, priceDateBs);
        });
    }

    // ─── Core logic (waterfall) ───────────────────────────────────────────────

    private EodCaptureResult captureEodPrice(String instrumentId, LocalDate priceDateAd,
                                              String priceDateBs) throws SQLException {
        // Step 1: Exchange official close from market_session_closes
        Double price = queryExchangeClose(instrumentId, priceDateAd);
        String source = "EXCHANGE_CLOSE";

        // Step 2: Session VWAP from intraday ticks
        if (price == null) {
            price  = querySessionVwap(instrumentId, priceDateAd);
            source = "VWAP";
        }

        // Step 3: Last traded price
        if (price == null) {
            price  = queryLastTradePrice(instrumentId, priceDateAd);
            source = "LAST_TRADE";
        }

        // Step 4: Carry-forward previous close
        if (price == null) {
            price  = queryPreviousClose(instrumentId, priceDateAd);
            source = "CARRY_FORWARD";
        }

        if (price == null) {
            log.warn("No price available for instrumentId={} date={}", instrumentId, priceDateAd);
            return new EodCaptureResult(instrumentId, 0.0, "NO_PRICE");
        }

        persistEodPrice(instrumentId, priceDateAd, priceDateBs, price, source);
        EODPriceCaptured event = new EODPriceCaptured(instrumentId, priceDateAd, priceDateBs, price, source);
        eventPort.emitEodPriceCaptured(event);

        return new EodCaptureResult(instrumentId, price, source);
    }

    // ─── Waterfall queries ────────────────────────────────────────────────────

    private Double queryExchangeClose(String instrumentId, LocalDate date) throws SQLException {
        String sql = """
            SELECT close_price FROM market_session_closes
            WHERE instrument_id = ? AND session_date = ?
            LIMIT 1
            """;
        return queryDoubleOrNull(sql, instrumentId, date);
    }

    private Double querySessionVwap(String instrumentId, LocalDate date) throws SQLException {
        String sql = """
            SELECT SUM(price * volume) / NULLIF(SUM(volume), 0) AS vwap
            FROM intraday_ticks
            WHERE instrument_id = ? AND DATE(tick_time) = ?
            """;
        return queryDoubleOrNull(sql, instrumentId, date);
    }

    private Double queryLastTradePrice(String instrumentId, LocalDate date) throws SQLException {
        String sql = """
            SELECT price FROM intraday_ticks
            WHERE instrument_id = ? AND DATE(tick_time) = ?
            ORDER BY tick_time DESC LIMIT 1
            """;
        return queryDoubleOrNull(sql, instrumentId, date);
    }

    private Double queryPreviousClose(String instrumentId, LocalDate date) throws SQLException {
        String sql = """
            SELECT close_price FROM price_history
            WHERE instrument_id = ? AND price_date_ad < ?
            ORDER BY price_date_ad DESC LIMIT 1
            """;
        return queryDoubleOrNull(sql, instrumentId, date);
    }

    private Double queryDoubleOrNull(String sql, String instrumentId, LocalDate date) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setObject(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble(1);
                    return rs.wasNull() ? null : v;
                }
            }
        }
        return null;
    }

    private void persistEodPrice(String instrumentId, LocalDate priceDateAd, String priceDateBs,
                                 double closePrice, String source) throws SQLException {
        String sql = """
            INSERT INTO price_history (
                instrument_id, price_date_ad, price_date_bs, close_price, price_source, created_at
            ) VALUES (?, ?, ?, ?, ?, now())
            ON CONFLICT (instrument_id, price_date_ad) DO UPDATE SET
                close_price  = EXCLUDED.close_price,
                price_source = EXCLUDED.price_source,
                created_at   = now()
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setObject(2, priceDateAd);
            ps.setString(3, priceDateBs);
            ps.setDouble(4, closePrice);
            ps.setString(5, source);
            ps.executeUpdate();
        }
    }

    private List<String> loadActiveInstrumentIds() throws SQLException {
        String sql = "SELECT instrument_id FROM instruments WHERE status = 'ACTIVE'";
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("instrument_id"));
        }
        return ids;
    }
}
