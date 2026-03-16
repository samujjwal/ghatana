package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
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
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose What-if scenario simulation: apply hypothetical trades to a portfolio snapshot
 *              and compute projected NAV, return, risk (standard deviation), and drift vs target.
 *              No actual orders are created. Results are stored for side-by-side comparison.
 *              Satisfies STORY-D03-009.
 * @doc.layer   Domain
 * @doc.pattern Snapshot-based simulation; zero external side effects; scenario cache in
 *              portfolio_scenarios table; Timer for simulation latency.
 */
public class WhatIfScenarioService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final PricePort        pricePort;
    private final Timer            simTimer;

    public WhatIfScenarioService(HikariDataSource dataSource, Executor executor,
                                  PricePort pricePort, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor   = executor;
        this.pricePort  = pricePort;
        this.simTimer   = Timer.builder("pms.whatif.simulation_ms").register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    public interface PricePort {
        BigDecimal getMarketPrice(String instrumentId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record HypotheticalTrade(String instrumentId, String side, // BUY or SELL
                                    BigDecimal qty) {}

    public record SimulatedHolding(String instrumentId, BigDecimal qty, BigDecimal price,
                                   BigDecimal marketValue, BigDecimal weight) {}

    public record ScenarioResult(String scenarioId, String portfolioId, String label,
                                 BigDecimal currentNav, BigDecimal simulatedNav,
                                 BigDecimal projectedReturn, BigDecimal portfolioVolatility,
                                 BigDecimal maxDrift, List<SimulatedHolding> holdings) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ScenarioResult> simulate(String portfolioId, String label,
                                             List<HypotheticalTrade> trades) {
        return Promise.ofBlocking(executor, () ->
                simTimer.recordCallable(() -> runSimulation(portfolioId, label, trades)));
    }

    public Promise<List<ScenarioResult>> listScenarios(String portfolioId) {
        return Promise.ofBlocking(executor, () -> loadScenarios(portfolioId));
    }

    // ─── Simulation logic ────────────────────────────────────────────────────

    private ScenarioResult runSimulation(String portfolioId, String label,
                                          List<HypotheticalTrade> trades) throws SQLException {
        List<HoldingRow> currentHoldings = loadCurrentHoldings(portfolioId);
        BigDecimal currentNav = currentHoldings.stream()
                .map(h -> h.marketValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Apply hypothetical trades to clone
        List<HoldingRow> simHoldings = new ArrayList<>(currentHoldings.stream()
                .map(h -> new HoldingRow(h.instrumentId(), h.qty(), h.price(), h.marketValue()))
                .toList());

        for (HypotheticalTrade trade : trades) {
            BigDecimal price = pricePort.getMarketPrice(trade.instrumentId());
            if (price == null || price.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal tradeMv = price.multiply(trade.qty());

            int idx = findHolding(simHoldings, trade.instrumentId());
            if (idx >= 0) {
                HoldingRow existing = simHoldings.get(idx);
                BigDecimal newQty = "BUY".equals(trade.side())
                        ? existing.qty().add(trade.qty())
                        : existing.qty().subtract(trade.qty());
                BigDecimal newMv  = newQty.multiply(existing.price());
                simHoldings.set(idx, new HoldingRow(trade.instrumentId(), newQty, existing.price(), newMv));
            } else if ("BUY".equals(trade.side())) {
                simHoldings.add(new HoldingRow(trade.instrumentId(), trade.qty(), price, tradeMv));
            }
        }

        BigDecimal simNav = simHoldings.stream().map(HoldingRow::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projReturn = currentNav.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : simNav.subtract(currentNav).divide(currentNav, 6, RoundingMode.HALF_UP);

        BigDecimal volatility = computeSimulatedVolatility(portfolioId, trades);
        BigDecimal maxDrift   = computeMaxDrift(portfolioId, simHoldings, simNav);

        List<SimulatedHolding> holdingResults = simHoldings.stream()
                .filter(h -> h.qty().compareTo(BigDecimal.ZERO) > 0)
                .map(h -> new SimulatedHolding(h.instrumentId(), h.qty(), h.price(), h.marketValue(),
                        simNav.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                                : h.marketValue().divide(simNav, 6, RoundingMode.HALF_UP)))
                .toList();

        String scenarioId = UUID.randomUUID().toString();
        persistScenario(scenarioId, portfolioId, label, currentNav, simNav, projReturn,
                volatility, maxDrift);

        return new ScenarioResult(scenarioId, portfolioId, label, currentNav, simNav,
                projReturn, volatility, maxDrift, holdingResults);
    }

    private int findHolding(List<HoldingRow> list, String instrumentId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).instrumentId().equals(instrumentId)) return i;
        }
        return -1;
    }

    /** Simplified volatility: use historical portfolio vol as proxy (new trades don't have history). */
    private BigDecimal computeSimulatedVolatility(String portfolioId,
                                                   List<HypotheticalTrade> trades) throws SQLException {
        String sql = """
                SELECT STDDEV(daily_return) * SQRT(252) AS annual_vol
                FROM (
                    SELECT (nav / LAG(nav) OVER (ORDER BY calc_date_ad) - 1) AS daily_return
                    FROM nav_history WHERE portfolio_id = ? ORDER BY calc_date_ad DESC LIMIT 90
                ) t WHERE daily_return IS NOT NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal v = rs.getBigDecimal("annual_vol");
                    return v != null ? v : BigDecimal.ZERO;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal computeMaxDrift(String portfolioId, List<HoldingRow> simHoldings,
                                        BigDecimal simNav) throws SQLException {
        if (simNav.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        String sql = "SELECT instrument_id, target_weight FROM target_allocations WHERE portfolio_id = ?";
        BigDecimal maxDrift = BigDecimal.ZERO;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String instrId = rs.getString("instrument_id");
                    BigDecimal target = rs.getBigDecimal("target_weight");
                    BigDecimal simWeight = simHoldings.stream()
                            .filter(h -> h.instrumentId().equals(instrId))
                            .map(h -> h.marketValue().divide(simNav, 6, RoundingMode.HALF_UP))
                            .findFirst().orElse(BigDecimal.ZERO);
                    BigDecimal drift = target.subtract(simWeight).abs();
                    if (drift.compareTo(maxDrift) > 0) maxDrift = drift;
                }
            }
        }
        return maxDrift;
    }

    private record HoldingRow(String instrumentId, BigDecimal qty, BigDecimal price, BigDecimal marketValue) {}

    private List<HoldingRow> loadCurrentHoldings(String portfolioId) throws SQLException {
        List<HoldingRow> list = new ArrayList<>();
        String sql = """
                SELECT h.instrument_id, h.quantity, p.price, h.quantity * p.price AS market_value
                FROM portfolio_holdings_latest h
                JOIN instrument_prices_latest p ON p.instrument_id = h.instrument_id
                WHERE h.portfolio_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HoldingRow(rs.getString("instrument_id"), rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("price"), rs.getBigDecimal("market_value")));
                }
            }
        }
        return list;
    }

    private void persistScenario(String scenarioId, String portfolioId, String label,
                                  BigDecimal currentNav, BigDecimal simNav, BigDecimal projReturn,
                                  BigDecimal volatility, BigDecimal maxDrift) throws SQLException {
        String sql = """
                INSERT INTO portfolio_scenarios
                    (scenario_id, portfolio_id, label, created_at,
                     current_nav, simulated_nav, projected_return_pct, volatility, max_drift)
                VALUES (?, ?, ?, NOW(), ?, ?, ?, ?, ?)
                ON CONFLICT (scenario_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scenarioId);
            ps.setString(2, portfolioId);
            ps.setString(3, label);
            ps.setBigDecimal(4, currentNav);
            ps.setBigDecimal(5, simNav);
            ps.setBigDecimal(6, projReturn);
            ps.setBigDecimal(7, volatility);
            ps.setBigDecimal(8, maxDrift);
            ps.executeUpdate();
        }
    }

    private List<ScenarioResult> loadScenarios(String portfolioId) throws SQLException {
        List<ScenarioResult> results = new ArrayList<>();
        String sql = """
                SELECT scenario_id, label, current_nav, simulated_nav,
                       projected_return_pct, volatility, max_drift
                FROM portfolio_scenarios WHERE portfolio_id = ?
                ORDER BY created_at DESC
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ScenarioResult(rs.getString("scenario_id"), portfolioId,
                            rs.getString("label"), rs.getBigDecimal("current_nav"),
                            rs.getBigDecimal("simulated_nav"), rs.getBigDecimal("projected_return_pct"),
                            rs.getBigDecimal("volatility"), rs.getBigDecimal("max_drift"),
                            List.of()));
                }
            }
        }
        return results;
    }
}
