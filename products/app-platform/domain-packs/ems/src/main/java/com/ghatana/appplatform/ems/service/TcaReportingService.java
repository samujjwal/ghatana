package com.ghatana.appplatform.ems.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Produces three levels of TCA reporting: per-order analysis,
 *              daily aggregated summary, and monthly broker scorecard. Supports
 *              CSV and PDF export (PDF handed to a render port to avoid binary
 *              dependencies in domain layer).
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; reads tca_metrics and tca_records; inner ReportRenderPort
 *              for PDF generation; CSV produced as plain string.
 */
public class TcaReportingService {

    private static final Logger log = LoggerFactory.getLogger(TcaReportingService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ReportRenderPort renderPort;
    private final Counter          reportGeneratedCounter;

    public TcaReportingService(HikariDataSource dataSource, Executor executor,
                               ReportRenderPort renderPort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.renderPort              = renderPort;
        this.reportGeneratedCounter  = registry.counter("ems.tca.report.generated");
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /**
     * Hands structured report data to an external PDF renderer.
     */
    public interface ReportRenderPort {
        byte[] renderToPdf(String reportTitle, String csvContent);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record OrderTcaReport(
        String orderId,
        String side,
        String instrumentId,
        double quantity,
        double isBps,
        double marketImpactBps,
        double timingCostBps,
        String algoType
    ) {}

    public record DailySummaryReport(
        String tradeDate,
        int    orderCount,
        double avgIsBps,
        double avgMarketImpactBps,
        double totalNotional
    ) {}

    public record BrokerScorecardRow(
        String brokerVenueId,
        double avgIsBps,
        double avgMarketImpactBps,
        double fillRatePct,
        int    tradeCount
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Per-order TCA analysis report.
     * Exposed as {@code GET /execution/tca/:orderId}.
     */
    public Promise<OrderTcaReport> getOrderReport(String orderId) {
        return Promise.ofBlocking(executor, () -> {
            reportGeneratedCounter.increment();
            return loadOrderReport(orderId);
        });
    }

    /**
     * Daily TCA summary.
     * Exposed as {@code GET /execution/tca/report?type=daily&date=YYYY-MM-DD}.
     */
    public Promise<DailySummaryReport> getDailySummary(String date) {
        return Promise.ofBlocking(executor, () -> {
            reportGeneratedCounter.increment();
            return loadDailySummary(date);
        });
    }

    /**
     * Monthly broker scorecard for a year-month (format: YYYY-MM).
     */
    public Promise<List<BrokerScorecardRow>> getMonthlyBrokerScorecard(String yearMonth) {
        return Promise.ofBlocking(executor, () -> {
            reportGeneratedCounter.increment();
            return loadBrokerScorecard(yearMonth);
        });
    }

    /**
     * Export daily summary as CSV string.
     */
    public Promise<String> exportDailyCsv(String date) {
        return getDailySummary(date).map(s ->
            "date,order_count,avg_is_bps,avg_market_impact_bps,total_notional\n" +
            String.format("%s,%d,%.2f,%.2f,%.2f%n",
                s.tradeDate(), s.orderCount(), s.avgIsBps(),
                s.avgMarketImpactBps(), s.totalNotional())
        );
    }

    /**
     * Export daily summary as PDF bytes via the render port.
     */
    public Promise<byte[]> exportDailyPdf(String date) {
        return getDailySummary(date).then(s -> Promise.ofBlocking(executor, () -> {
            String csv = String.format("date,order_count,avg_is_bps\n%s,%d,%.2f",
                s.tradeDate(), s.orderCount(), s.avgIsBps());
            return renderPort.renderToPdf("Daily TCA Summary – " + date, csv);
        }));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private OrderTcaReport loadOrderReport(String orderId) {
        String sql = """
            SELECT t.order_id, t.side, t.instrument_id, t.quantity,
                   m.is_bps, m.market_impact_bps, m.timing_cost_bps,
                   COALESCE(o.algo_type, 'UNKNOWN') AS algo_type
            FROM tca_records t
            JOIN tca_metrics m ON m.fill_id = t.fill_id
            LEFT JOIN orders o ON o.order_id = t.order_id
            WHERE t.order_id = ?
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderTcaReport(
                        rs.getString("order_id"),
                        rs.getString("side"),
                        rs.getString("instrument_id"),
                        rs.getDouble("quantity"),
                        rs.getDouble("is_bps"),
                        rs.getDouble("market_impact_bps"),
                        rs.getDouble("timing_cost_bps"),
                        rs.getString("algo_type")
                    );
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load order TCA report orderId={}", orderId, ex);
        }
        return new OrderTcaReport(orderId, "", "", 0, 0, 0, 0, "");
    }

    private DailySummaryReport loadDailySummary(String date) {
        String sql = """
            SELECT COUNT(DISTINCT t.order_id)   AS order_count,
                   AVG(m.is_bps)                AS avg_is_bps,
                   AVG(m.market_impact_bps)     AS avg_market_impact_bps,
                   SUM(t.quantity * t.exec_price) AS total_notional
            FROM tca_records t
            JOIN tca_metrics m ON m.fill_id = t.fill_id
            WHERE t.order_received_at::date = ?::date
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DailySummaryReport(
                        date,
                        rs.getInt("order_count"),
                        rs.getDouble("avg_is_bps"),
                        rs.getDouble("avg_market_impact_bps"),
                        rs.getDouble("total_notional")
                    );
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load daily TCA summary date={}", date, ex);
        }
        return new DailySummaryReport(date, 0, 0, 0, 0);
    }

    private List<BrokerScorecardRow> loadBrokerScorecard(String yearMonth) {
        String sql = """
            SELECT t.venue_id AS broker_venue_id,
                   AVG(m.is_bps)            AS avg_is_bps,
                   AVG(m.market_impact_bps) AS avg_market_impact_bps,
                   100.0 * COUNT(t.fill_id) / NULLIF(COUNT(t.order_id), 0) AS fill_rate_pct,
                   COUNT(t.fill_id)         AS trade_count
            FROM tca_records t
            JOIN tca_metrics m ON m.fill_id = t.fill_id
            WHERE to_char(t.order_received_at, 'YYYY-MM') = ?
            GROUP BY t.venue_id
            ORDER BY avg_is_bps
            """;
        List<BrokerScorecardRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, yearMonth);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BrokerScorecardRow(
                        rs.getString("broker_venue_id"),
                        rs.getDouble("avg_is_bps"),
                        rs.getDouble("avg_market_impact_bps"),
                        rs.getDouble("fill_rate_pct"),
                        rs.getInt("trade_count")
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load broker scorecard yearMonth={}", yearMonth, ex);
        }
        return result;
    }
}
