package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Timer;
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
 * @doc.purpose Performance attribution: compare portfolio return vs benchmark return.
 *              Attribution by Brinson-Hood-Beebower model: allocation effect (sector weight
 *              difference × sector benchmark return) + selection effect (stock selection
 *              within sector). Benchmark data from D-11 (index definitions).
 *              Tracking error calculation: std dev of active returns over rolling window.
 *              Satisfies STORY-D03-006.
 * @doc.layer   Domain
 * @doc.pattern BHB attribution; sector-level + stock-level breakdown; D-11 BenchmarkPort;
 *              Timer performance SLA ≤ 5sec.
 */
public class PerformanceAttributionService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceAttributionService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final BenchmarkPort    benchmarkPort;
    private final Timer            attrTimer;

    public PerformanceAttributionService(HikariDataSource dataSource, Executor executor,
                                         BenchmarkPort benchmarkPort, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.benchmarkPort  = benchmarkPort;
        this.attrTimer      = Timer.builder("pms.attribution.duration_ms").register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /** D-11 benchmark port for index sector returns and weights. */
    public interface BenchmarkPort {
        double getBenchmarkReturn(String benchmarkId, LocalDate from, LocalDate to);
        List<SectorReturn> getSectorReturns(String benchmarkId, LocalDate from, LocalDate to);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SectorReturn(String sector, double benchmarkWeight, double benchmarkReturn) {}

    public record SectorAttribution(String sector, double portfolioWeight, double benchmarkWeight,
                                    double portfolioReturn, double benchmarkReturn,
                                    double allocationEffect, double selectionEffect,
                                    double totalEffect) {}

    public record AttributionResult(String portfolioId, String benchmarkId,
                                    LocalDate from, LocalDate to,
                                    double portfolioReturn, double benchmarkReturn,
                                    double activeReturn, double trackingError,
                                    List<SectorAttribution> sectors) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<AttributionResult> computeAttribution(String portfolioId, String benchmarkId,
                                                         LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () ->
                attrTimer.recordCallable(() -> {
                    double portReturn   = loadPortfolioReturn(portfolioId, from, to);
                    double benchReturn  = benchmarkPort.getBenchmarkReturn(benchmarkId, from, to);
                    double activeReturn = portReturn - benchReturn;
                    List<SectorReturn> benchSectors = benchmarkPort.getSectorReturns(benchmarkId, from, to);
                    List<SectorAttribution> attrs = computeSectorAttribution(
                            portfolioId, benchSectors, portReturn, benchReturn, from, to);
                    double trackingError = computeTrackingError(portfolioId, benchmarkId, to, 252);
                    return new AttributionResult(portfolioId, benchmarkId, from, to,
                            portReturn, benchReturn, activeReturn, trackingError, attrs);
                }));
    }

    // ─── Brinson-Hood-Beebower ────────────────────────────────────────────────

    private List<SectorAttribution> computeSectorAttribution(String portfolioId,
                                                              List<SectorReturn> benchSectors,
                                                              double portReturn, double benchReturn,
                                                              LocalDate from, LocalDate to)
            throws SQLException {
        List<SectorAttribution> result = new ArrayList<>();
        List<PortfolioSector> portSectors = loadPortfolioSectorWeightsAndReturns(portfolioId, from, to);

        for (SectorReturn bSec : benchSectors) {
            PortfolioSector pSec = portSectors.stream()
                    .filter(s -> s.sector().equals(bSec.sector()))
                    .findFirst()
                    .orElse(new PortfolioSector(bSec.sector(), 0.0, 0.0));

            // Allocation effect: (Wp - Wb) × Rb
            double allocEffect = (pSec.weight() - bSec.benchmarkWeight()) * bSec.benchmarkReturn();
            // Selection effect: Wp × (Rp - Rb)
            double selEffect = pSec.weight() * (pSec.sectorReturn() - bSec.benchmarkReturn());

            result.add(new SectorAttribution(bSec.sector(), pSec.weight(), bSec.benchmarkWeight(),
                    pSec.sectorReturn(), bSec.benchmarkReturn(), allocEffect, selEffect,
                    allocEffect + selEffect));
        }
        return result;
    }

    private record PortfolioSector(String sector, double weight, double sectorReturn) {}

    private List<PortfolioSector> loadPortfolioSectorWeightsAndReturns(String portfolioId,
                                                                         LocalDate from, LocalDate to)
            throws SQLException {
        List<PortfolioSector> list = new ArrayList<>();
        String sql = """
                SELECT r.sector, SUM(h.weight) AS portfolio_weight,
                       AVG(ph.period_return) AS sector_return
                FROM portfolio_holdings h
                JOIN reference_data r ON r.instrument_id = h.instrument_id
                JOIN portfolio_holding_returns ph ON ph.instrument_id = h.instrument_id
                    AND ph.period_start = ? AND ph.period_end = ?
                WHERE h.portfolio_id = ?
                GROUP BY r.sector
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            ps.setString(3, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PortfolioSector(rs.getString("sector"),
                            rs.getDouble("portfolio_weight"), rs.getDouble("sector_return")));
                }
            }
        }
        return list;
    }

    private double loadPortfolioReturn(String portfolioId, LocalDate from, LocalDate to)
            throws SQLException {
        String sql = """
                SELECT (MAX(CASE WHEN calc_date_ad = ? THEN nav END) /
                        NULLIF(MAX(CASE WHEN calc_date_ad = ? THEN nav END), 0)) - 1
                FROM nav_history WHERE portfolio_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, to);
            ps.setObject(2, from);
            ps.setString(3, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble(1);
                    return rs.wasNull() ? 0.0 : v;
                }
            }
        }
        return 0.0;
    }

    /** Standard deviation of daily active returns over rolling window (annualized × √252). */
    private double computeTrackingError(String portfolioId, String benchmarkId,
                                         LocalDate asOf, int days) throws SQLException {
        String sql = """
                SELECT STDDEV(active_return) * SQRT(252)
                FROM (
                    SELECT nh.calc_date_ad,
                           (nh.nav / LAG(nh.nav) OVER (ORDER BY nh.calc_date_ad) - 1)
                           - COALESCE(br.daily_return, 0) AS active_return
                    FROM nav_history nh
                    LEFT JOIN benchmark_daily_returns br ON br.benchmark_id = ?
                        AND br.return_date = nh.calc_date_ad
                    WHERE nh.portfolio_id = ?
                      AND nh.calc_date_ad > ? - (? || ' days')::interval
                ) t WHERE active_return IS NOT NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, benchmarkId);
            ps.setString(2, portfolioId);
            ps.setObject(3, asOf);
            ps.setInt(4, days);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble(1);
                    return rs.wasNull() ? 0.0 : v;
                }
            }
        }
        return 0.0;
    }
}
