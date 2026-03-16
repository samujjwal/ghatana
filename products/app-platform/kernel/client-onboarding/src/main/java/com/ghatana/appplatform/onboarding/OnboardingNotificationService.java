package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Send onboarding lifecycle notifications to clients via email (HTML templates),
 *              SMS (short-form), and in-app channels.
 *              Supported notification types:
 *                1. ONBOARDING_STARTED
 *                2. DOCUMENT_RECEIVED
 *                3. REVIEW_STARTED
 *                4. APPROVED (with welcome message)
 *                5. REJECTED (with reason code)
 *                6. ACCOUNT_CREATED
 *                7. FIRST_LOGIN_PROMPT
 *              Channel preferences are fetched per client from K-02.
 *              Dual-calendar dates (Gregorian + Hijri) formatted per client locale.
 * @doc.layer   Client Onboarding (W-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W02-010: Client onboarding notification suite
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS onboarding_notifications (
 *   notification_id   TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id       TEXT NOT NULL,
 *   client_id         TEXT NOT NULL,
 *   notification_type TEXT NOT NULL,
 *   channel           TEXT NOT NULL,   -- EMAIL | SMS | IN_APP
 *   status            TEXT NOT NULL DEFAULT 'QUEUED',
 *   external_ref      TEXT,
 *   sent_at           TIMESTAMPTZ,
 *   failure_reason    TEXT,
 *   created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class OnboardingNotificationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EmailPort {
        /** Send HTML email. Returns provider message ID. */
        String sendHtml(String toAddress, String subject, String htmlBody) throws Exception;
    }

    public interface SmsPort {
        /** Send SMS short message. Returns provider message ID. */
        String send(String toMobile, String message) throws Exception;
    }

    public interface InAppPort {
        /** Push in-app notification. Returns notification ref. */
        String push(String clientId, String title, String body) throws Exception;
    }

    public interface ClientPreferencesPort {
        /** Load preferred notification channels for a client (from K-02). */
        NotificationPreferences loadPreferences(String clientId) throws Exception;
    }

    public interface TemplateRenderPort {
        /** Render a named template with the given model variables. */
        String render(String templateId, Map<String, Object> model) throws Exception;
    }

    public interface CalendarPort {
        /** Format a date string in both Gregorian and Hijri per locale. */
        String formatDualCalendar(java.time.LocalDate date, String locale) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum NotificationType {
        ONBOARDING_STARTED, DOCUMENT_RECEIVED, REVIEW_STARTED,
        APPROVED, REJECTED, ACCOUNT_CREATED, FIRST_LOGIN_PROMPT
    }

    public enum Channel { EMAIL, SMS, IN_APP }

    public record NotificationPreferences(
        String clientId,
        String emailAddress,
        String mobileNumber,
        String locale,
        List<Channel> preferredChannels
    ) {}

    public record NotificationResult(
        String notificationId,
        NotificationType type,
        Channel channel,
        String status,
        String externalRef
    ) {}

    // ── Template ID constants ─────────────────────────────────────────────────

    private static final Map<NotificationType, String> EMAIL_TEMPLATE_IDS = Map.of(
        NotificationType.ONBOARDING_STARTED,  "onboarding/started-email",
        NotificationType.DOCUMENT_RECEIVED,   "onboarding/doc-received-email",
        NotificationType.REVIEW_STARTED,      "onboarding/review-started-email",
        NotificationType.APPROVED,            "onboarding/approved-email",
        NotificationType.REJECTED,            "onboarding/rejected-email",
        NotificationType.ACCOUNT_CREATED,     "onboarding/account-created-email",
        NotificationType.FIRST_LOGIN_PROMPT,  "onboarding/first-login-email"
    );

    private static final Map<NotificationType, String> SMS_TEMPLATE_IDS = Map.of(
        NotificationType.ONBOARDING_STARTED,  "onboarding/started-sms",
        NotificationType.DOCUMENT_RECEIVED,   "onboarding/doc-received-sms",
        NotificationType.REVIEW_STARTED,      "onboarding/review-started-sms",
        NotificationType.APPROVED,            "onboarding/approved-sms",
        NotificationType.REJECTED,            "onboarding/rejected-sms",
        NotificationType.ACCOUNT_CREATED,     "onboarding/account-created-sms",
        NotificationType.FIRST_LOGIN_PROMPT,  "onboarding/first-login-sms"
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final EmailPort email;
    private final SmsPort sms;
    private final InAppPort inApp;
    private final ClientPreferencesPort preferences;
    private final TemplateRenderPort templateRender;
    private final CalendarPort calendar;
    private final Executor executor;
    private final Counter sentCounter;
    private final Counter failedCounter;

    public OnboardingNotificationService(
        javax.sql.DataSource ds,
        EmailPort email,
        SmsPort sms,
        InAppPort inApp,
        ClientPreferencesPort preferences,
        TemplateRenderPort templateRender,
        CalendarPort calendar,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.email          = email;
        this.sms            = sms;
        this.inApp          = inApp;
        this.preferences    = preferences;
        this.templateRender = templateRender;
        this.calendar       = calendar;
        this.executor       = executor;
        this.sentCounter    = Counter.builder("onboarding.notification.sent").register(registry);
        this.failedCounter  = Counter.builder("onboarding.notification.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a lifecycle notification. Dispatches to all preferred channels.
     */
    public Promise<List<NotificationResult>> send(
        String instanceId, String clientId, NotificationType type, Map<String, Object> extraVars
    ) {
        return Promise.ofBlocking(executor, () -> {
            NotificationPreferences prefs = preferences.loadPreferences(clientId);

            // Enrich model with dual-calendar today date
            Map<String, Object> model = new HashMap<>(extraVars);
            model.put("clientId", clientId);
            model.put("instanceId", instanceId);
            model.put("today", calendar.formatDualCalendar(java.time.LocalDate.now(), prefs.locale()));

            List<NotificationResult> results = new ArrayList<>();
            for (Channel channel : prefs.preferredChannels()) {
                NotificationResult result = dispatch(instanceId, clientId, type, channel, prefs, model);
                results.add(result);
            }
            return results;
        });
    }

    // ── Private dispatch ──────────────────────────────────────────────────────

    private NotificationResult dispatch(
        String instanceId, String clientId, NotificationType type,
        Channel channel, NotificationPreferences prefs, Map<String, Object> model
    ) throws Exception {
        String notifId = insertPending(instanceId, clientId, type, channel);
        try {
            String externalRef = switch (channel) {
                case EMAIL -> {
                    String templateId = EMAIL_TEMPLATE_IDS.get(type);
                    String body = templateRender.render(templateId, model);
                    String subject = emailSubject(type);
                    yield email.sendHtml(prefs.emailAddress(), subject, body);
                }
                case SMS -> {
                    String templateId = SMS_TEMPLATE_IDS.get(type);
                    String message = templateRender.render(templateId, model);
                    yield sms.send(prefs.mobileNumber(), message);
                }
                case IN_APP -> {
                    String title = inAppTitle(type);
                    String body = templateRender.render("onboarding/in-app-" + type.name().toLowerCase(), model);
                    yield inApp.push(clientId, title, body);
                }
            };
            markSent(notifId, externalRef);
            sentCounter.increment();
            return new NotificationResult(notifId, type, channel, "SENT", externalRef);
        } catch (Exception e) {
            markFailed(notifId, e.getMessage());
            failedCounter.increment();
            return new NotificationResult(notifId, type, channel, "FAILED", null);
        }
    }

    private String emailSubject(NotificationType type) {
        return switch (type) {
            case ONBOARDING_STARTED -> "Your onboarding has started";
            case DOCUMENT_RECEIVED  -> "Document received – thank you";
            case REVIEW_STARTED     -> "Your application is under review";
            case APPROVED           -> "Congratulations – account approved";
            case REJECTED           -> "Application outcome";
            case ACCOUNT_CREATED    -> "Your account is ready";
            case FIRST_LOGIN_PROMPT -> "Complete your first login";
        };
    }

    private String inAppTitle(NotificationType type) {
        return switch (type) {
            case ONBOARDING_STARTED -> "Onboarding started";
            case DOCUMENT_RECEIVED  -> "Document received";
            case REVIEW_STARTED     -> "Under review";
            case APPROVED           -> "Account approved!";
            case REJECTED           -> "Application outcome";
            case ACCOUNT_CREATED    -> "Account created";
            case FIRST_LOGIN_PROMPT -> "Action required: first login";
        };
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private String insertPending(String instanceId, String clientId, NotificationType type, Channel channel)
        throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO onboarding_notifications (instance_id, client_id, notification_type, channel) " +
                 "VALUES (?,?,?,?) RETURNING notification_id"
             )) {
            ps.setString(1, instanceId); ps.setString(2, clientId);
            ps.setString(3, type.name()); ps.setString(4, channel.name());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markSent(String id, String externalRef) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE onboarding_notifications SET status = 'SENT', external_ref = ?, sent_at = NOW() WHERE notification_id = ?"
             )) {
            ps.setString(1, externalRef); ps.setString(2, id); ps.executeUpdate();
        }
    }

    private void markFailed(String id, String reason) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE onboarding_notifications SET status = 'FAILED', failure_reason = ? WHERE notification_id = ?"
             )) {
            ps.setString(1, reason); ps.setString(2, id); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
