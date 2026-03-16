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
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Generates execution reports for each fill: trade confirmation details,
 *              fees breakdown, net amount, and settlement date computed via K-15 T+n.
 *              Pushes the report to the D-09 post-trade module via an inner port and
 *              exposes a client-visible API response.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner PostTradeNotifyPort for D-09 integration;
 *              inner CalendarPort for K-15 settlement date calculation.
 */
public class ExecutionReportService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionReportService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private static final int DEFAULT_SETTLEMENT_DAYS = 2;  // T+2 equities

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final PostTradeNotifyPort postTradePort;
    private final CalendarPort        calendarPort;
    private final Counter             reportCounter;

    public ExecutionReportService(HikariDataSource dataSource, Executor executor,
                                  PostTradeNotifyPort postTradePort, CalendarPort calendarPort,
                                  MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.postTradePort = postTradePort;
        this.calendarPort  = calendarPort;
        this.reportCounter = registry.counter("ems.execution.report.generated");
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /**
     * Port to push execution reports to D-09 post-trade confirmation flow.
     */
    public interface PostTradeNotifyPort {
        void notifyFill(String fillId, ExecutionReport report);
    }

    /**
     * K-15: Calendar service to calculate settlement date from trade date.
     */
    public interface CalendarPort {
        String settlementDateBs(String tradeDateAd, int plusDays);  // returns BS date string
        String settlementDateAd(String tradeDateAd, int plusDays);  // returns AD date string
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ExecutionReport(
        String reportId,
        String orderId,
        String fillId,
        String clientId,
        String instrumentId,
        String side,
        double quantity,
        double price,
        double grossAmount,
        double commissionFee,
        double settlementFee,
        double totalFees,
        double netAmount,
        String tradeDateAd,
        String settlementDateAd,
        String settlementDateBs,
        String instrumentType
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Generate and persist an execution report for a fill, then push to D-09.
     */
    public Promise<ExecutionReport> generateForFill(String fillId) {
        return Promise.ofBlocking(executor, () -> {
            ExecutionReport report = buildReport(fillId);
            persistReport(report);
            postTradePort.notifyFill(fillId, report);
            reportCounter.increment();
            log.info("Execution report generated fillId={} reportId={}", fillId, report.reportId());
            return report;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private ExecutionReport buildReport(String fillId) {
        String sql = """
            SELECT f.fill_id, f.order_id, f.client_id, f.instrument_id, f.side,
                   f.quantity, f.fill_price, f.created_at::date AS trade_date,
                   COALESCE(i.instrument_type, 'EQUITY') AS instrument_type,
                   COALESCE(i.commission_rate, 0.004)    AS commission_rate,
                   COALESCE(i.settlement_fee_rate, 0.001) AS settlement_fee_rate,
                   COALESCE(i.settlement_days, ?)        AS settlement_days
            FROM order_fills f
            LEFT JOIN instruments i ON i.instrument_id = f.instrument_id
            WHERE f.fill_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, DEFAULT_SETTLEMENT_DAYS);
            ps.setString(2, fillId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double qty            = rs.getDouble("quantity");
                    double price          = rs.getDouble("fill_price");
                    double gross          = qty * price;
                    double commRate       = rs.getDouble("commission_rate");
                    double settlRate      = rs.getDouble("settlement_fee_rate");
                    int    settlDays      = rs.getInt("settlement_days");
                    double comm           = gross * commRate;
                    double settlFee       = gross * settlRate;
                    double totalFees      = comm + settlFee;
                    String side           = rs.getString("side");
                    double net            = "BUY".equals(side) ? -(gross + totalFees) : (gross - totalFees);
                    String tradeDateAd    = rs.getDate("trade_date").toString();
                    String settlAd        = calendarPort.settlementDateAd(tradeDateAd, settlDays);
                    String settlBs        = calendarPort.settlementDateBs(tradeDateAd, settlDays);

                    return new ExecutionReport(
                        UUID.randomUUID().toString(),
                        rs.getString("order_id"),
                        fillId,
                        rs.getString("client_id"),
                        rs.getString("instrument_id"),
                        side, qty, price, gross, comm, settlFee, totalFees, net,
                        tradeDateAd, settlAd, settlBs,
                        rs.getString("instrument_type")
                    );
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to build execution report for fillId={}", fillId, ex);
        }
        throw new IllegalStateException("Fill not found: " + fillId);
    }

    private void persistReport(ExecutionReport r) {
        String sql = """
            INSERT INTO execution_reports
                (report_id, order_id, fill_id, client_id, instrument_id, side,
                 quantity, price, gross_amount, commission_fee, settlement_fee,
                 total_fees, net_amount, trade_date_ad, settlement_date_ad,
                 settlement_date_bs, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (fill_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.reportId());
            ps.setString(2, r.orderId());
            ps.setString(3, r.fillId());
            ps.setString(4, r.clientId());
            ps.setString(5, r.instrumentId());
            ps.setString(6, r.side());
            ps.setDouble(7, r.quantity());
            ps.setDouble(8, r.price());
            ps.setDouble(9, r.grossAmount());
            ps.setDouble(10, r.commissionFee());
            ps.setDouble(11, r.settlementFee());
            ps.setDouble(12, r.totalFees());
            ps.setDouble(13, r.netAmount());
            ps.setString(14, r.tradeDateAd());
            ps.setString(15, r.settlementDateAd());
            ps.setString(16, r.settlementDateBs());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist execution report fillId={}", r.fillId(), ex);
        }
    }
}
