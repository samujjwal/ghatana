package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose EOD mark-to-market batch engine: value all positions at EOD market price.
 *              For each portfolio × instrument: market_value = qty × latest_price.
 *              Unrealized P&amp;L = market_value - cost_basis. Runs after D05-002 EOD
 *              price capture. MTM results stored per date with idempotency.
 *              Satisfies STORY-D05-009.
 * @doc.layer   Domain
 * @doc.pattern EOD batch MTM; idempotent ON CONFLICT upsert; Timer+Counter+Gauge metrics.
 */
public class MtmBatchEngineService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Timer            batchTimer;
    private final Counter          positionsValued;
    private final AtomicLong       lastBatchPositions = new AtomicLong(0);

    public MtmBatchEngineService(HikariDataSource dataSource, Executor executor,
                                  MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.batchTimer      = Timer.builder("pricing.mtm.batch_duration_ms").register(registry);
        this.positionsValued = Counter.builder("pricing.mtm.positions_valued_total").register(registry);
        Gauge.builder("pricing.mtm.last_batch_positions", lastBatchPositions, AtomicLong::get)
                .register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record MtmPosition(String portfolioId, String instrumentId, LocalDate mtmDate,
                               BigDecimal qty, BigDecimal eodPrice, BigDecimal marketValue,
                               BigDecimal costBasis, BigDecimal unrealizedPnl,
                               BigDecimal unrealizedPnlPct) {}

    public record MtmBatchResult(LocalDate runDate, int portfoliosProcessed,
                                  int positionsValued, BigDecimal totalMarketValue,
                                  BigDecimal totalUnrealizedPnl, List<String> failedPortfolios) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<MtmBatchResult> runEodMtm(LocalDate runDate) {
        return Promise.ofBlocking(executor, () ->
                batchTimer.recordCallable(() -> executeEodMtm(runDate)));
    }

    public Promise<List<MtmPosition>> getMtmSnapshot(String portfolioId, LocalDate mtmDate) {
        return Promise.ofBlocking(executor, () -> loadSnapshot(portfolioId, mtmDate));
    }

    // ─── Batch execution ─────────────────────────────────────────────────────

    private MtmBatchResult executeEodMtm(LocalDate runDate) throws SQLException {
        List<String> portfolioIds = loadActivePortfolios();
        List<String> failed = new ArrayList<>();
        BigDecimal totalMv  = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        int totalPositions  = 0;

        for (String portfolioId : portfolioIds) {
            try {
                MtmBatchSummary summary = valuatePortfolio(portfolioId, runDate);
                totalMv       = totalMv.add(summary.totalMv());
                totalPnl      = totalPnl.add(summary.totalPnl());
                totalPositions += summary.positionCount();
                positionsValued.increment(summary.positionCount());
            } catch (Exception ex) {
                failed.add(portfolioId);
            }
        }

        lastBatchPositions.set(totalPositions);
        persistBatchRun(runDate, portfolioIds.size() - failed.size(), totalPositions, totalMv, totalPnl);
        return new MtmBatchResult(runDate, portfolioIds.size() - failed.size(),
                totalPositions, totalMv, totalPnl, failed);
    }

    private record MtmBatchSummary(int positionCount, BigDecimal totalMv, BigDecimal totalPnl) {}

    private MtmBatchSummary valuatePortfolio(String portfolioId, LocalDate runDate)
            throws SQLException {
        String sql = """
                INSERT INTO mtm_positions
                    (portfolio_id, instrument_id, mtm_date, qty, eod_price,
                     market_value, cost_basis, unrealized_pnl, unrealized_pnl_pct)
                SELECT h.portfolio_id, h.instrument_id, ? AS mtm_date,
                       h.quantity AS qty, p.price AS eod_price,
                       h.quantity * p.price AS market_value,
                       h.quantity * h.avg_cost AS cost_basis,
                       (h.quantity * p.price) - (h.quantity * h.avg_cost) AS unrealized_pnl,
                       CASE WHEN h.avg_cost = 0 THEN 0
                            ELSE ((p.price - h.avg_cost) / h.avg_cost) END AS unrealized_pnl_pct
                FROM portfolio_holdings_latest h
                JOIN instrument_prices_eod p ON p.instrument_id = h.instrument_id
                    AND p.price_date = ?
                WHERE h.portfolio_id = ? AND h.quantity > 0
                ON CONFLICT (portfolio_id, instrument_id, mtm_date)
                DO UPDATE SET qty = EXCLUDED.qty, eod_price = EXCLUDED.eod_price,
                    market_value = EXCLUDED.market_value, cost_basis = EXCLUDED.cost_basis,
                    unrealized_pnl = EXCLUDED.unrealized_pnl,
                    unrealized_pnl_pct = EXCLUDED.unrealized_pnl_pct
                RETURNING market_value, unrealized_pnl
                """;

        int count         = 0;
        BigDecimal sumMv  = BigDecimal.ZERO;
        BigDecimal sumPnl = BigDecimal.ZERO;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setObject(2, runDate);
            ps.setString(3, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    count++;
                    sumMv  = sumMv.add(rs.getBigDecimal("market_value"));
                    sumPnl = sumPnl.add(rs.getBigDecimal("unrealized_pnl"));
                }
            }
        }
        return new MtmBatchSummary(count, sumMv, sumPnl);
    }

    private List<String> loadActivePortfolios() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT portfolio_id FROM portfolios WHERE status = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("portfolio_id"));
        }
        return list;
    }

    private void persistBatchRun(LocalDate runDate, int portfolios, int positions,
                                  BigDecimal totalMv, BigDecimal totalPnl) throws SQLException {
        String sql = """
                INSERT INTO mtm_batch_runs (run_date, portfolios_processed, positions_valued,
                    total_market_value, total_unrealized_pnl, completed_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (run_date) DO UPDATE
                SET portfolios_processed = EXCLUDED.portfolios_processed,
                    positions_valued = EXCLUDED.positions_valued,
                    total_market_value = EXCLUDED.total_market_value,
                    total_unrealized_pnl = EXCLUDED.total_unrealized_pnl,
                    completed_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setInt(2, portfolios);
            ps.setInt(3, positions);
            ps.setBigDecimal(4, totalMv);
            ps.setBigDecimal(5, totalPnl);
            ps.executeUpdate();
        }
    }

    private List<MtmPosition> loadSnapshot(String portfolioId, LocalDate mtmDate)
            throws SQLException {
        List<MtmPosition> list = new ArrayList<>();
        String sql = """
                SELECT portfolio_id, instrument_id, mtm_date, qty, eod_price,
                       market_value, cost_basis, unrealized_pnl, unrealized_pnl_pct
                FROM mtm_positions WHERE portfolio_id = ? AND mtm_date = ?
                ORDER BY market_value DESC
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setObject(2, mtmDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MtmPosition(rs.getString("portfolio_id"),
                            rs.getString("instrument_id"),
                            rs.getObject("mtm_date", LocalDate.class),
                            rs.getBigDecimal("qty"), rs.getBigDecimal("eod_price"),
                            rs.getBigDecimal("market_value"), rs.getBigDecimal("cost_basis"),
                            rs.getBigDecimal("unrealized_pnl"), rs.getBigDecimal("unrealized_pnl_pct")));
                }
            }
        }
        return list;
    }
}
