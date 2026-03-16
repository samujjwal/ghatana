package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

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
 * @doc.purpose Regulatory reporting operations dashboard. Shows upcoming and overdue submission
 *              deadlines, current ACK/NACK status counts, analyst workload, and dual-calendar
 *              (BS/AD) deadline views (K-15). Publishes dashboard snapshot via K-06 DashboardPort.
 *              Satisfies STORY-D10-012.
 * @doc.layer   Domain
 * @doc.pattern Dashboard aggregation; K-06 DashboardPort; K-15 dual calendar; Gauge metrics.
 */
public class ReportingDashboardService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final DashboardPort    dashboardPort;
    private final CalendarPort     calendarPort;
    private final AtomicLong       overdueCount = new AtomicLong();
    private final AtomicLong       pendingAckCount = new AtomicLong();

    public ReportingDashboardService(HikariDataSource dataSource, Executor executor,
                                      DashboardPort dashboardPort, CalendarPort calendarPort,
                                      MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.dashboardPort   = dashboardPort;
        this.calendarPort    = calendarPort;
        Gauge.builder("reporting.dashboard.overdue_count", overdueCount, AtomicLong::doubleValue).register(registry);
        Gauge.builder("reporting.dashboard.pending_ack_count", pendingAckCount, AtomicLong::doubleValue).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-06 dashboard publish. */
    public interface DashboardPort { void publish(String topic, Object payload); }

    /** K-15 Nepali calendar. */
    public interface CalendarPort { String toNepaliDate(LocalDate adDate); }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record DeadlineEntry(String reportCode, String regulator, LocalDate deadlineAd,
                                 String deadlineBs, int daysUntil, boolean overdue) {}

    public record SubmissionStatusSummary(long prepared, long submitted, long acknowledged,
                                           long accepted, long rejected) {}

    public record DashboardSnapshot(List<DeadlineEntry> upcoming, List<DeadlineEntry> overdue,
                                     SubmissionStatusSummary statusSummary, LocalDate asOf) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<DashboardSnapshot> getSnapshot(LocalDate today) {
        return Promise.ofBlocking(executor, () -> {
            List<DeadlineEntry> all      = loadDeadlines(today);
            List<DeadlineEntry> upcoming = all.stream().filter(d -> !d.overdue()).toList();
            List<DeadlineEntry> overdues = all.stream().filter(DeadlineEntry::overdue).toList();
            SubmissionStatusSummary status = loadSubmissionStatusSummary();

            overdueCount.set(overdues.size());
            pendingAckCount.set(status.submitted());

            DashboardSnapshot snap = new DashboardSnapshot(upcoming, overdues, status, today);
            dashboardPort.publish("reporting.dashboard.snapshot", snap);
            return snap;
        });
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    private List<DeadlineEntry> loadDeadlines(LocalDate today) throws SQLException {
        String sql = """
                SELECT report_code, regulator, next_deadline
                FROM report_definitions WHERE status='ACTIVE'
                ORDER BY next_deadline
                """;
        List<DeadlineEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDate deadline = rs.getObject("next_deadline", LocalDate.class);
                int days = (int) today.until(deadline).getDays();
                String bs = calendarPort.toNepaliDate(deadline);
                result.add(new DeadlineEntry(rs.getString("report_code"), rs.getString("regulator"),
                        deadline, bs, days, days < 0));
            }
        }
        return result;
    }

    private SubmissionStatusSummary loadSubmissionStatusSummary() throws SQLException {
        String sql = """
                SELECT
                  COUNT(CASE WHEN status='PREPARED'     THEN 1 END) AS prepared,
                  COUNT(CASE WHEN status='SUBMITTED'    THEN 1 END) AS submitted,
                  COUNT(CASE WHEN status='ACKNOWLEDGED' THEN 1 END) AS acknowledged,
                  COUNT(CASE WHEN status='ACCEPTED'     THEN 1 END) AS accepted,
                  COUNT(CASE WHEN status='REJECTED'     THEN 1 END) AS rejected
                FROM regulator_submissions
                WHERE submitted_at >= NOW() - INTERVAL '90 days'
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return new SubmissionStatusSummary(0, 0, 0, 0, 0);
            return new SubmissionStatusSummary(rs.getLong("prepared"), rs.getLong("submitted"),
                    rs.getLong("acknowledged"), rs.getLong("accepted"), rs.getLong("rejected"));
        }
    }
}
