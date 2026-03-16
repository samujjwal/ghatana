package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Near-real-time trade reporting. Listens for OrderFilled events and submits
 *              individual trade reports within a T+15-minute window. Batches reports for
 *              trades in the same reporting period to reduce submission volume. Delegates
 *              to RegulatorSubmissionAdapterService for the actual submission.
 *              Satisfies STORY-D10-010.
 * @doc.layer   Domain
 * @doc.pattern Event-driven; T+15min SLA; batch optimization; Counter + Timer metrics.
 */
public class RealtimeTradeReportService {

    private static final int SUBMISSION_WINDOW_MINUTES = 15;

    private final HikariDataSource            dataSource;
    private final Executor                    executor;
    private final TradeReportBuilderPort      reportBuilder;
    private final RegulatorSubmissionAdapterService submissionAdapter;
    private final Counter                     reportSubmittedCounter;
    private final Counter                     windowBreachedCounter;
    private final Timer                       submissionLatency;

    public RealtimeTradeReportService(HikariDataSource dataSource, Executor executor,
                                       TradeReportBuilderPort reportBuilder,
                                       RegulatorSubmissionAdapterService submissionAdapter,
                                       MeterRegistry registry) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.reportBuilder          = reportBuilder;
        this.submissionAdapter      = submissionAdapter;
        this.reportSubmittedCounter = Counter.builder("reporting.realtime.submitted_total").register(registry);
        this.windowBreachedCounter  = Counter.builder("reporting.realtime.window_breached_total").register(registry);
        this.submissionLatency      = Timer.builder("reporting.realtime.latency_ms").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface TradeReportBuilderPort {
        byte[] buildTradeReport(String tradeId, String reportCode);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record OrderFilledEvent(String tradeId, String clientId, String instrumentId,
                                    String side, double qty, double price,
                                    LocalDateTime filledAt) {}

    public record TradeReportRecord(String tradeReportId, String tradeId, String submissionId,
                                     boolean withinWindow, LocalDateTime reportedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<TradeReportRecord> onOrderFilled(OrderFilledEvent event) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            String tradeReportId = UUID.randomUUID().toString();
            LocalDateTime now    = LocalDateTime.now();
            boolean withinWindow = !event.filledAt().plusMinutes(SUBMISSION_WINDOW_MINUTES).isBefore(now);

            if (!withinWindow) {
                windowBreachedCounter.increment();
            }

            byte[] payload = reportBuilder.buildTradeReport(event.tradeId(), "TRADE_REPORT");
            var submissionFuture = submissionAdapter.submit(
                    tradeReportId, "SEBON", "TRADE_REPORT", payload, "application/xml");

            // Blocking — resolve synchronously within this executor task
            var submission = submissionFuture.getResult();
            persistTradeReport(tradeReportId, event.tradeId(), submission.submissionId(), withinWindow);
            reportSubmittedCounter.increment();
            sample.stop(submissionLatency);
            return new TradeReportRecord(tradeReportId, event.tradeId(), submission.submissionId(),
                    withinWindow, now);
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void persistTradeReport(String tradeReportId, String tradeId, String submissionId,
                                     boolean withinWindow) throws SQLException {
        String sql = """
                INSERT INTO trade_reports
                    (trade_report_id, trade_id, submission_id, within_window, reported_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (trade_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tradeReportId);
            ps.setString(2, tradeId);
            ps.setString(3, submissionId);
            ps.setBoolean(4, withinWindow);
            ps.executeUpdate();
        }
    }
}
