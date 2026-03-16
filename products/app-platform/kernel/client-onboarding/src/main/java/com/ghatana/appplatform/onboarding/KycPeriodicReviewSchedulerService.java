package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    Service
 * @doc.purpose Schedule periodic KYC reviews by risk tier and send reminders.
 *              Review cycle per tier: LOW = 3 years, MEDIUM = 2 years, HIGH = 1 year.
 *              Reminder schedule: T-60 days (email), T-30 (email + SMS), T-7 (urgent email + SMS).
 *              Date arithmetic uses a business calendar (skips weekends and public holidays).
 *              On review becoming due: triggers full KYC re-verification workflow (W02-001 re-run).
 * @doc.layer   Client Onboarding (W-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W02-012: KYC periodic review scheduler
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS kyc_review_schedules (
 *   schedule_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   client_id       TEXT NOT NULL,
 *   instance_id     TEXT NOT NULL,
 *   risk_tier       TEXT NOT NULL,
 *   review_due_date DATE NOT NULL,
 *   next_reminder   DATE,
 *   status          TEXT NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | TRIGGERED | COMPLETED | CANCELLED
 *   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   triggered_at    TIMESTAMPTZ
 * );
 * CREATE TABLE IF NOT EXISTS kyc_review_reminders (
 *   reminder_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   schedule_id     TEXT NOT NULL REFERENCES kyc_review_schedules(schedule_id),
 *   reminder_type   TEXT NOT NULL,   -- T60 | T30 | T7
 *   sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class KycPeriodicReviewSchedulerService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface BusinessCalendarPort {
        /** Calculate the next business date that is {@code calendarDays} after {@code from}. */
        LocalDate addBusinessDays(LocalDate from, int calendarDays) throws Exception;
        /** Return today's business date (may differ from calendar date on holidays). */
        LocalDate today() throws Exception;
    }

    public interface ReminderDispatchPort {
        void sendReminder(String clientId, String reminderType, LocalDate reviewDueDate, List<String> channels) throws Exception;
    }

    public interface KycWorkflowTriggerPort {
        /** Re-trigger the KYC verification workflow for a client due for periodic review. */
        String triggerReview(String clientId, String existingInstanceId, String reason) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum RiskTier { LOW, MEDIUM, HIGH }
    public enum ReminderType { T60, T30, T7 }

    private static final Map<RiskTier, Integer> REVIEW_YEARS = Map.of(
        RiskTier.LOW, 3, RiskTier.MEDIUM, 2, RiskTier.HIGH, 1
    );

    public record ReviewSchedule(
        String scheduleId,
        String clientId,
        String instanceId,
        RiskTier riskTier,
        LocalDate reviewDueDate,
        String status
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final BusinessCalendarPort businessCalendar;
    private final ReminderDispatchPort reminderDispatch;
    private final KycWorkflowTriggerPort kycTrigger;
    private final Executor executor;
    private final Counter scheduledCounter;
    private final Counter overdueTriggeredCounter;
    private final Counter remindersCounter;
    private final AtomicInteger activeSchedulesGauge = new AtomicInteger(0);

    public KycPeriodicReviewSchedulerService(
        javax.sql.DataSource ds,
        BusinessCalendarPort businessCalendar,
        ReminderDispatchPort reminderDispatch,
        KycWorkflowTriggerPort kycTrigger,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                   = ds;
        this.businessCalendar     = businessCalendar;
        this.reminderDispatch     = reminderDispatch;
        this.kycTrigger           = kycTrigger;
        this.executor             = executor;
        this.scheduledCounter     = Counter.builder("kyc.review.scheduled").register(registry);
        this.overdueTriggeredCounter = Counter.builder("kyc.review.triggered").register(registry);
        this.remindersCounter     = Counter.builder("kyc.review.reminders.sent").register(registry);
        Gauge.builder("kyc.review.active", activeSchedulesGauge, AtomicInteger::get).register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create a periodic review schedule after successful account provisioning.
     */
    public Promise<ReviewSchedule> schedule(String clientId, String instanceId, RiskTier riskTier) {
        return Promise.ofBlocking(executor, () -> {
            int years = REVIEW_YEARS.get(riskTier);
            LocalDate today = businessCalendar.today();
            LocalDate dueDate = today.plusYears(years);
            LocalDate firstReminder = dueDate.minusDays(60);

            String scheduleId = insertSchedule(clientId, instanceId, riskTier, dueDate, firstReminder);
            activeSchedulesGauge.incrementAndGet();
            scheduledCounter.increment();
            return new ReviewSchedule(scheduleId, clientId, instanceId, riskTier, dueDate, "ACTIVE");
        });
    }

    /**
     * Process due reminders and trigger overdue reviews.
     * Call this daily via the scheduler (e.g. D-13 cron trigger).
     */
    public Promise<Void> processDue() {
        return Promise.ofBlocking(executor, () -> {
            LocalDate today = businessCalendar.today();
            processReminders(today);
            triggerOverdueReviews(today);
            return null;
        });
    }

    /**
     * Mark a review schedule as completed (called after re-verification workflow finishes).
     */
    public Promise<Void> markCompleted(String scheduleId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE kyc_review_schedules SET status = 'COMPLETED' WHERE schedule_id = ?"
                 )) {
                ps.setString(1, scheduleId);
                ps.executeUpdate();
                activeSchedulesGauge.decrementAndGet();
            }
            return null;
        });
    }

    // ── Private processing ────────────────────────────────────────────────────

    private void processReminders(LocalDate today) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT schedule_id, client_id, review_due_date FROM kyc_review_schedules " +
                 "WHERE status = 'ACTIVE' AND next_reminder <= ?"
             )) {
            ps.setObject(1, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String scheduleId = rs.getString("schedule_id");
                    String clientId   = rs.getString("client_id");
                    LocalDate dueDate = rs.getObject("review_due_date", LocalDate.class);
                    long daysUntilDue = today.until(dueDate, java.time.temporal.ChronoUnit.DAYS);

                    ReminderType type;
                    List<String> channels;
                    LocalDate nextReminder;

                    if (daysUntilDue >= 55) {
                        type = ReminderType.T60; channels = List.of("EMAIL");
                        nextReminder = dueDate.minusDays(30);
                    } else if (daysUntilDue >= 25) {
                        type = ReminderType.T30; channels = List.of("EMAIL", "SMS");
                        nextReminder = dueDate.minusDays(7);
                    } else {
                        type = ReminderType.T7; channels = List.of("EMAIL", "SMS");
                        nextReminder = null; // no further reminders before due
                    }

                    reminderDispatch.sendReminder(clientId, type.name(), dueDate, channels);
                    insertReminderRecord(c, scheduleId, type);
                    updateNextReminder(c, scheduleId, nextReminder);
                    remindersCounter.increment();
                }
            }
        }
    }

    private void triggerOverdueReviews(LocalDate today) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT schedule_id, client_id, instance_id FROM kyc_review_schedules " +
                 "WHERE status = 'ACTIVE' AND review_due_date <= ?"
             )) {
            ps.setObject(1, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String scheduleId  = rs.getString("schedule_id");
                    String clientId    = rs.getString("client_id");
                    String instanceId  = rs.getString("instance_id");
                    kycTrigger.triggerReview(clientId, instanceId, "PERIODIC_REVIEW_DUE");
                    markTriggered(c, scheduleId);
                    overdueTriggeredCounter.increment();
                    activeSchedulesGauge.decrementAndGet();
                }
            }
        }
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private String insertSchedule(String clientId, String instanceId, RiskTier tier,
                                  LocalDate dueDate, LocalDate firstReminder) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO kyc_review_schedules (client_id, instance_id, risk_tier, review_due_date, next_reminder) " +
                 "VALUES (?,?,?,?,?) RETURNING schedule_id"
             )) {
            ps.setString(1, clientId); ps.setString(2, instanceId);
            ps.setString(3, tier.name());
            ps.setObject(4, dueDate); ps.setObject(5, firstReminder);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void insertReminderRecord(Connection c, String scheduleId, ReminderType type) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO kyc_review_reminders (schedule_id, reminder_type) VALUES (?,?)"
        )) {
            ps.setString(1, scheduleId); ps.setString(2, type.name()); ps.executeUpdate();
        }
    }

    private void updateNextReminder(Connection c, String scheduleId, LocalDate next) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "UPDATE kyc_review_schedules SET next_reminder = ? WHERE schedule_id = ?"
        )) {
            ps.setObject(1, next); ps.setString(2, scheduleId); ps.executeUpdate();
        }
    }

    private void markTriggered(Connection c, String scheduleId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "UPDATE kyc_review_schedules SET status = 'TRIGGERED', triggered_at = NOW() WHERE schedule_id = ?"
        )) {
            ps.setString(1, scheduleId); ps.executeUpdate();
        }
    }
}
