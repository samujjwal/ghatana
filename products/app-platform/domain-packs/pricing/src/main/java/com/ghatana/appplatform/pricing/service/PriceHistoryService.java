package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 * @doc.purpose Serves historical price data for instruments with OHLCV aggregations across
 *              multiple time intervals (TICK, 1MIN, 5MIN, 15MIN, 1H, 1D). BS date range
 *              conversion is handled via K-15 CalendarPort. Supports pagination for large
 *              date ranges. Data is read from price_history (for 1D) and intraday_ticks
 *              (for sub-daily intervals). Returns sorted ASC by bar start time.
 * @doc.layer   Domain
 * @doc.pattern Read-only query service; K-15 CalendarPort for BS→AD date conversion;
 *              keyset-based pagination for large result sets.
 */
public class PriceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final Timer            queryTimer;

    public PriceHistoryService(HikariDataSource dataSource, Executor executor,
                               CalendarPort calendarPort, MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.calendarPort = calendarPort;
        this.queryTimer   = registry.timer("pricing.history.query_duration");
    }

    // ─── Inner port (K-15) ───────────────────────────────────────────────────

    public interface CalendarPort {
        LocalDate bsToAd(int bsYear, int bsMonth, int bsDay);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum Interval { TICK, ONE_MIN, FIVE_MIN, FIFTEEN_MIN, ONE_HOUR, ONE_DAY }

    public record OhlcvBar(
        String    instrumentId,
        String    barStart,     // ISO timestamp or date string
        double    open,
        double    high,
        double    low,
        double    close,
        double    volume
    ) {}

    public record PriceHistoryRequest(
        String   instrumentId,
        Interval interval,
        LocalDate fromDateAd,
        LocalDate toDateAd,
        int      pageSize,
        String   afterBarStart  // keyset cursor; null for first page
    ) {}

    public record PriceHistoryResponse(
        List<OhlcvBar> bars,
        String         nextCursor  // null if no more pages
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Fetch OHLCV bars (AD date range, any interval).
     */
    public Promise<PriceHistoryResponse> getHistory(PriceHistoryRequest req) {
        return Promise.ofBlocking(executor, () ->
            queryTimer.recordCallable(() -> fetchHistory(req))
        );
    }

    /**
     * Fetch daily close prices between two BS dates.
     */
    public Promise<PriceHistoryResponse> getHistoryBs(
        String instrumentId, int fromBsYear, int fromBsMonth, int fromBsDay,
        int toBsYear, int toBsMonth, int toBsDay, int pageSize, String cursor
    ) {
        return Promise.ofBlocking(executor, () -> {
            LocalDate from = calendarPort.bsToAd(fromBsYear, fromBsMonth, fromBsDay);
            LocalDate to   = calendarPort.bsToAd(toBsYear, toBsMonth, toBsDay);
            return queryTimer.recordCallable(() ->
                fetchHistory(new PriceHistoryRequest(instrumentId, Interval.ONE_DAY,
                                                     from, to, pageSize, cursor))
            );
        });
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private PriceHistoryResponse fetchHistory(PriceHistoryRequest req) throws SQLException {
        return switch (req.interval()) {
            case ONE_DAY -> fetchDailyBars(req);
            case TICK    -> fetchTicks(req);
            default      -> fetchIntradayBars(req);
        };
    }

    private PriceHistoryResponse fetchDailyBars(PriceHistoryRequest req) throws SQLException {
        String sql = """
            SELECT instrument_id, price_date_ad::text AS bar_start,
                   open_price, high_price, low_price, close_price, volume
            FROM price_history
            WHERE instrument_id = ?
              AND price_date_ad >= ?
              AND price_date_ad <= ?
              AND (? IS NULL OR price_date_ad::text > ?)
            ORDER BY price_date_ad ASC
            LIMIT ?
            """;
        return executeBarQuery(sql, req);
    }

    private PriceHistoryResponse fetchIntradayBars(PriceHistoryRequest req) throws SQLException {
        String truncExpr = switch (req.interval()) {
            case ONE_MIN      -> "minute";
            case FIVE_MIN     -> "5 minutes";
            case FIFTEEN_MIN  -> "15 minutes";
            case ONE_HOUR     -> "hour";
            default           -> "minute";
        };
        String sql = String.format("""
            SELECT instrument_id,
                   date_trunc('%s', tick_time)::text AS bar_start,
                   FIRST_VALUE(price) OVER (PARTITION BY date_trunc('%s', tick_time) ORDER BY tick_time ASC ROWS UNBOUNDED PRECEDING)  AS open_price,
                   MAX(price)  AS high_price,
                   MIN(price)  AS low_price,
                   LAST_VALUE(price) OVER (PARTITION BY date_trunc('%s', tick_time) ORDER BY tick_time ASC ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS close_price,
                   SUM(volume) AS volume
            FROM intraday_ticks
            WHERE instrument_id = ?
              AND DATE(tick_time) >= ?
              AND DATE(tick_time) <= ?
              AND (? IS NULL OR date_trunc('%s', tick_time)::text > ?)
            GROUP BY instrument_id, date_trunc('%s', tick_time)
            ORDER BY bar_start ASC
            LIMIT ?
            """, truncExpr, truncExpr, truncExpr, truncExpr, truncExpr);
        return executeBarQuery(sql, req);
    }

    private PriceHistoryResponse fetchTicks(PriceHistoryRequest req) throws SQLException {
        String sql = """
            SELECT instrument_id, tick_time::text AS bar_start,
                   price AS open_price, price AS high_price, price AS low_price, price AS close_price, volume
            FROM intraday_ticks
            WHERE instrument_id = ?
              AND DATE(tick_time) >= ?
              AND DATE(tick_time) <= ?
              AND (? IS NULL OR tick_time::text > ?)
            ORDER BY tick_time ASC
            LIMIT ?
            """;
        return executeBarQuery(sql, req);
    }

    private PriceHistoryResponse executeBarQuery(String sql, PriceHistoryRequest req) throws SQLException {
        List<OhlcvBar> bars = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, req.instrumentId());
            ps.setObject(2, req.fromDateAd());
            ps.setObject(3, req.toDateAd());
            ps.setString(4, req.afterBarStart());
            ps.setString(5, req.afterBarStart());
            ps.setInt(6, req.pageSize());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bars.add(new OhlcvBar(
                        rs.getString("instrument_id"),
                        rs.getString("bar_start"),
                        rs.getDouble("open_price"),
                        rs.getDouble("high_price"),
                        rs.getDouble("low_price"),
                        rs.getDouble("close_price"),
                        rs.getDouble("volume")
                    ));
                }
            }
        }
        String nextCursor = bars.size() == req.pageSize()
            ? bars.getLast().barStart()
            : null;
        return new PriceHistoryResponse(bars, nextCursor);
    }
}
