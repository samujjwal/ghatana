package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Tracks regulatory submission deadlines and issues T-5, T-2, T-1 day reminders
 *              via NotificationPort. Deadlines are expressed in both BS and AD calendar
 *              (K-15 CalendarPort). Submissions past deadline trigger escalation via
 *              EscalationPort. Nightly schedule runs from a scheduler.
 *              Satisfies STORY-D10-007.
 * @doc.layer   Domain
 * @doc.pattern Deadline tracking; K-15 BS calendar; T-5/T-2/T-1 reminders; Gauge overdue.
 */
public class SubmissionSchedulingService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final NotificationPort notificationPort;
    private final EscalationPort   escalationPort;
    private final Counter          reminderSentCounter;
    private final Counter          escalationCounter;
    private final AtomicLong       overdueCount = new AtomicLong();

    public SubmissionSchedulingService(HikariDataSource dataSource, Executor executor,
                                        CalendarPort calendarPort, NotificationPort notificationPort,
                                        EscalationPort escalationPort, MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.calendarPort        = calendarPort;
        this.notificationPort    = notificationPort;
        this.escalationPort      = escalationPort;
        this.reminderSentCounter = Counter.builder("reporting.schedule.reminders_sent_total").register(registry);
        this.escalationCounter   = Counter.builder("reporting.schedule.escalations_total").register(registry);
        Gauge.builder("reporting.schedule.overdue_count", overdueCount, AtomicLong::doubleValue)
             .register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface CalendarPort {
        String toNepaliDate(LocalDate adDate);
        boolean isBusinessDay(LocalDate date);
        LocalDate addBusinessDays(LocalDate from, int days);
    }

    public interface NotificationPort {
        void send(String recipient, String subject, String body);
    }

    public interface EscalationPort {
        void escalate(String reportCode, String regulator, LocalDate deadline, int daysOverdue);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ScheduledReport(String definitionId, String reportCode, String regulator,
                                   LocalDate deadline, String deadlineBs, int daysUntilDeadline,
                                   boolean overdue) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<ScheduledReport>> runDailyCheck(LocalDate today) {
        return Promise.ofBlocking(executor, () -> {
            List<ScheduledReport> upcoming = loadUpcomingReports(today);
            long overdue = 0;
            for (ScheduledReport report : upcoming) {
                if (report.overdue()) {
                    escalationPort.escalate(report.reportCode(), report.regulator(),
                            report.deadline(), Math.abs(report.daysUntilDeadline()));
                    escalationCounter.increment();
                    overdue++;
                } else {
                    int daysLeft = report.daysUntilDeadline();
                    if (daysLeft == 5 || daysLeft == 2 || daysLeft == 1) {
                        sendReminder(report, daysLeft);
                        reminderSentCounter.increment();
                    }
                }
            }
            overdueCount.set(overdue);
            return upcoming;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void sendReminder(ScheduledReport report, int daysLeft) {
        String subject = "Regulatory Reminder: " + report.reportCode() + " due in " + daysLeft + " day(s)";
        String body    = report.reportCode() + " for " + report.regulator()
                + " is due by " + report.deadline() + " (BS: " + report.deadlineBs() + ")."
                + "\nPlease ensure timely submission.";
        notificationPort.send("compliance-team@ghatana.com", subject, body);
    }

    private List<ScheduledReport> loadUpcomingReports(LocalDate today) throws SQLException {
        String sql = """
                SELECT definition_id, report_code, regulator, next_deadline, next_deadline_bs
                FROM report_definitions
                WHERE status = 'ACTIVE'
                ORDER BY next_deadline
                """;
        List<ScheduledReport> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDate deadline = rs.getObject("next_deadline", LocalDate.class);
                long daysLeft = today.until(deadline).getDays();
                result.add(new ScheduledReport(
                        rs.getString("definition_id"), rs.getString("report_code"),
                        rs.getString("regulator"), deadline, rs.getString("next_deadline_bs"),
                        (int) daysLeft, daysLeft < 0));
            }
        }
        return result;
    }
}
