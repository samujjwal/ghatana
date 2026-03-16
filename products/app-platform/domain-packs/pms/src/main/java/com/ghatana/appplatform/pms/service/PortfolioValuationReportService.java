package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
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
 * @doc.purpose Produce holdings valuation report: per-instrument qty, avg cost, market price,
 *              market value, unrealized P&L, unrealized P&L %, and total portfolio NAV.
 *              K-15 CalendarPort for BS↔AD dual-calendar date display. Supports PDF, CSV,
 *              and JSON export via ExportPort. Satisfies STORY-D03-011.
 * @doc.layer   Domain
 * @doc.pattern Report generation; K-15 CalendarPort dual dates; ExportPort for formats;
 *              Timer for report generation latency.
 */
public class PortfolioValuationReportService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final ExportPort       exportPort;
    private final Timer            reportTimer;

    public PortfolioValuationReportService(HikariDataSource dataSource, Executor executor,
                                            CalendarPort calendarPort, ExportPort exportPort,
                                            MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.calendarPort = calendarPort;
        this.exportPort   = exportPort;
        this.reportTimer  = Timer.builder("pms.valuation_report.duration_ms").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-15: BS↔AD calendar conversion. */
    public interface CalendarPort {
        String toNepaliDate(LocalDate adDate);
    }

    public interface ExportPort {
        byte[] exportPdf(ValuationReport report);
        byte[] exportCsv(ValuationReport report);
        String exportJson(ValuationReport report);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record HoldingLine(String instrumentId, String instrumentName, String sector,
                              BigDecimal qty, BigDecimal avgCost, BigDecimal marketPrice,
                              BigDecimal marketValue, BigDecimal unrealizedPnl,
                              BigDecimal unrealizedPnlPct, BigDecimal weight) {}

    public record ValuationReport(String portfolioId, LocalDate reportDateAd, String reportDateBs,
                                  BigDecimal totalMarketValue, BigDecimal totalUnrealizedPnl,
                                  BigDecimal totalCostBasis, List<HoldingLine> holdings) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ValuationReport> generateReport(String portfolioId, LocalDate reportDate) {
        return Promise.ofBlocking(executor, () ->
                reportTimer.recordCallable(() -> buildReport(portfolioId, reportDate)));
    }

    public Promise<byte[]> exportPdf(String portfolioId, LocalDate reportDate) {
        return Promise.ofBlocking(executor, () ->
                exportPort.exportPdf(buildReport(portfolioId, reportDate)));
    }

    public Promise<byte[]> exportCsv(String portfolioId, LocalDate reportDate) {
        return Promise.ofBlocking(executor, () ->
                exportPort.exportCsv(buildReport(portfolioId, reportDate)));
    }

    public Promise<String> exportJson(String portfolioId, LocalDate reportDate) {
        return Promise.ofBlocking(executor, () ->
                exportPort.exportJson(buildReport(portfolioId, reportDate)));
    }

    // ─── Report builder ──────────────────────────────────────────────────────

    private ValuationReport buildReport(String portfolioId, LocalDate reportDate) throws SQLException {
        List<HoldingLine> holdings = loadHoldings(portfolioId, reportDate);

        BigDecimal totalMv  = holdings.stream().map(HoldingLine::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPnl = holdings.stream().map(HoldingLine::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = holdings.stream()
                .map(h -> h.avgCost().multiply(h.qty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Add weight field
        final BigDecimal nav = totalMv;
        List<HoldingLine> withWeight = holdings.stream()
                .map(h -> new HoldingLine(h.instrumentId(), h.instrumentName(), h.sector(),
                        h.qty(), h.avgCost(), h.marketPrice(), h.marketValue(), h.unrealizedPnl(),
                        h.unrealizedPnlPct(),
                        nav.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                                : h.marketValue().divide(nav, 6, java.math.RoundingMode.HALF_UP)))
                .toList();

        String reportDateBs = calendarPort.toNepaliDate(reportDate);
        return new ValuationReport(portfolioId, reportDate, reportDateBs,
                totalMv, totalPnl, totalCost, withWeight);
    }

    private List<HoldingLine> loadHoldings(String portfolioId, LocalDate reportDate) throws SQLException {
        List<HoldingLine> lines = new ArrayList<>();
        String sql = """
                SELECT h.instrument_id, r.name AS instrument_name, r.sector,
                       h.quantity AS qty, h.avg_cost,
                       p.price AS market_price,
                       h.quantity * p.price AS market_value,
                       (h.quantity * p.price) - (h.quantity * h.avg_cost) AS unrealized_pnl,
                       CASE WHEN h.avg_cost = 0 THEN 0
                            ELSE ((p.price - h.avg_cost) / h.avg_cost) END AS unrealized_pnl_pct
                FROM portfolio_holdings_snapshot h
                JOIN reference_data r ON r.instrument_id = h.instrument_id
                JOIN instrument_prices_eod p ON p.instrument_id = h.instrument_id
                    AND p.price_date = ?
                WHERE h.portfolio_id = ? AND h.snapshot_date = ?
                  AND h.quantity > 0
                ORDER BY h.quantity * p.price DESC
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, reportDate);
            ps.setString(2, portfolioId);
            ps.setObject(3, reportDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new HoldingLine(rs.getString("instrument_id"),
                            rs.getString("instrument_name"), rs.getString("sector"),
                            rs.getBigDecimal("qty"), rs.getBigDecimal("avg_cost"),
                            rs.getBigDecimal("market_price"), rs.getBigDecimal("market_value"),
                            rs.getBigDecimal("unrealized_pnl"), rs.getBigDecimal("unrealized_pnl_pct"),
                            BigDecimal.ZERO)); // weight populated above
                }
            }
        }
        return lines;
    }
}
