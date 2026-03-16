package com.ghatana.appplatform.risk.service;

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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Backtests VaR predictions against actual P&L using the Basel II traffic-light
 *              framework. Counts exception days (actual loss > predicted VaR) over a 250-day
 *              window and classifies the model into Green (0-4), Yellow (5-9), or Red (10+).
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking over HikariCP DataSource; inner ExceptionRecord for results
 */
public class VarBacktestingService {

    private static final Logger log = LoggerFactory.getLogger(VarBacktestingService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    // Basel II traffic-light thresholds
    private static final int YELLOW_THRESHOLD = 5;
    private static final int RED_THRESHOLD    = 10;
    private static final int BACKTEST_WINDOW  = 250;  // trading days

    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Counter backtestGreenCounter;
    private final Counter backtestYellowCounter;
    private final Counter backtestRedCounter;

    public VarBacktestingService(HikariDataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.backtestGreenCounter  = registry.counter("risk.var.backtest", "zone", "green");
        this.backtestYellowCounter = registry.counter("risk.var.backtest", "zone", "yellow");
        this.backtestRedCounter    = registry.counter("risk.var.backtest", "zone", "red");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    /**
     * Represents one day's VaR vs actual P&L comparison entry.
     */
    public record BacktestEntry(
        LocalDate tradeDate,
        double    predictedVar,    // negative: represents maximum expected loss
        double    actualPnl,       // realised P&L for that day
        boolean   isException      // true when actualPnl < predictedVar (loss > VaR)
    ) {}

    /**
     * Full backtesting result for a portfolio with Basel II zone classification.
     */
    public record BacktestResult(
        String           portfolioId,
        String           method,           // parametric | historical | montecarlo
        double           confidenceLevel,
        int              windowDays,
        int              exceptionCount,
        String           zone,             // GREEN | YELLOW | RED
        LocalDate        periodStart,
        LocalDate        periodEnd,
        List<BacktestEntry> entries
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run full backtesting for a portfolio over the last {@link #BACKTEST_WINDOW} days.
     *
     * @param portfolioId target portfolio
     * @param method      var method that produced the predictions
     * @param confidence  confidence level (e.g. 99.0)
     */
    public Promise<BacktestResult> backtest(String portfolioId, String method, double confidence) {
        return Promise.ofBlocking(executor, () -> doBacktest(portfolioId, method, confidence));
    }

    /**
     * Persist a completed backtest result for audit and regulatory reporting.
     */
    public Promise<Void> saveBacktestResult(BacktestResult result) {
        return Promise.ofBlocking(executor, () -> {
            persistResult(result);
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private BacktestResult doBacktest(String portfolioId, String method, double confidence) {
        List<BacktestEntry> entries = loadHistoricalEntries(portfolioId, method, confidence);
        long exceptionCount = entries.stream().filter(BacktestEntry::isException).count();

        String zone;
        if (exceptionCount < YELLOW_THRESHOLD) {
            zone = "GREEN";
            backtestGreenCounter.increment();
        } else if (exceptionCount < RED_THRESHOLD) {
            zone = "YELLOW";
            backtestYellowCounter.increment();
        } else {
            zone = "RED";
            backtestRedCounter.increment();
        }
        log.info("VaR backtest portfolioId={} method={} exceptions={} zone={}", portfolioId, method, exceptionCount, zone);

        LocalDate periodEnd   = LocalDate.now(NST);
        LocalDate periodStart = entries.isEmpty()
            ? periodEnd.minusDays(BACKTEST_WINDOW)
            : entries.getFirst().tradeDate();

        return new BacktestResult(
            portfolioId, method, confidence, BACKTEST_WINDOW,
            (int) exceptionCount, zone, periodStart, periodEnd, entries
        );
    }

    /**
     * Loads the last BACKTEST_WINDOW rows from {@code var_history} joined to actual P&L.
     * Rows where actual_pnl < predicted_var (i.e. loss exceeded VaR) are marked as exceptions.
     */
    private List<BacktestEntry> loadHistoricalEntries(String portfolioId, String method, double confidence) {
        String sql = """
            SELECT vh.trade_date, vh.var_amount, COALESCE(pl.actual_pnl, 0) AS actual_pnl
            FROM var_history        vh
            LEFT JOIN portfolio_pnl pl ON pl.portfolio_id = vh.portfolio_id
                                       AND pl.trade_date   = vh.trade_date
            WHERE vh.portfolio_id   = ?
              AND vh.method         = ?
              AND vh.confidence_pct = ?
            ORDER BY vh.trade_date DESC
            LIMIT ?
            """;
        List<BacktestEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setString(2, method);
            ps.setDouble(3, confidence);
            ps.setInt(4, BACKTEST_WINDOW);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date    = rs.getDate("trade_date").toLocalDate();
                    double    predVar = rs.getDouble("var_amount");   // stored as positive magnitude
                    double    pnl     = rs.getDouble("actual_pnl");
                    // Exception: actual loss (negative pnl) exceeds predicted VaR magnitude
                    boolean isEx = pnl < -Math.abs(predVar);
                    result.add(new BacktestEntry(date, -Math.abs(predVar), pnl, isEx));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load VaR history for backtest portfolioId={}", portfolioId, ex);
        }
        return result;
    }

    private void persistResult(BacktestResult result) {
        String sql = """
            INSERT INTO var_backtest_results
                (result_id, portfolio_id, method, confidence_pct, window_days,
                 exception_count, zone, period_start, period_end, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (portfolio_id, method, period_end) DO UPDATE
                SET exception_count = EXCLUDED.exception_count,
                    zone            = EXCLUDED.zone
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, result.portfolioId());
            ps.setString(3, result.method());
            ps.setDouble(4, result.confidenceLevel());
            ps.setInt(5, result.windowDays());
            ps.setInt(6, result.exceptionCount());
            ps.setString(7, result.zone());
            ps.setObject(8, result.periodStart());
            ps.setObject(9, result.periodEnd());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist backtest result portfolioId={}", result.portfolioId(), ex);
        }
    }
}
