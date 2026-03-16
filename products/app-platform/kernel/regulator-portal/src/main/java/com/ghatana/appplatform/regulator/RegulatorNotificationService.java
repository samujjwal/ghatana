package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Real-time critical alerts and periodic digest notifications for regulators.
 *              Critical events (AML breach, compliance failure, forced revocation) trigger
 *              immediate push notification via the delivery channel registered by the regulator.
 *              Daily and weekly digest emails aggregate non-urgent activity.
 *              Regulators manage their own notification preferences per channel and event type.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-009: Regulator notification service — real-time + digest
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS regulator_notification_prefs (
 *   pref_id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   regulator_id    TEXT NOT NULL,
 *   event_type      TEXT NOT NULL,   -- CRITICAL | AML_BREACH | COMPLIANCE_FAIL | DAILY_DIGEST | WEEKLY_DIGEST
 *   channel         TEXT NOT NULL,   -- EMAIL | WEBHOOK | BOTH
 *   enabled         BOOLEAN NOT NULL DEFAULT TRUE,
 *   UNIQUE (regulator_id, event_type)
 * );
 * CREATE TABLE IF NOT EXISTS regulator_notification_log (
 *   notif_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   regulator_id    TEXT NOT NULL,
 *   event_type      TEXT NOT NULL,
 *   channel         TEXT NOT NULL,
 *   subject         TEXT NOT NULL,
 *   sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   delivered       BOOLEAN NOT NULL DEFAULT FALSE
 * );
 * </pre>
 */
public class RegulatorNotificationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EmailDeliveryPort {
        void send(String regulatorId, String subject, String body) throws Exception;
    }

    public interface WebhookDeliveryPort {
        void post(String regulatorId, Map<String, String> payload) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final EmailDeliveryPort email;
    private final WebhookDeliveryPort webhook;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter criticalSent;
    private final Counter digestsSent;

    public RegulatorNotificationService(
        javax.sql.DataSource ds,
        EmailDeliveryPort email,
        WebhookDeliveryPort webhook,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds           = ds;
        this.email        = email;
        this.webhook      = webhook;
        this.audit        = audit;
        this.executor     = executor;
        this.criticalSent = Counter.builder("regulator.notification.critical_sent").register(registry);
        this.digestsSent  = Counter.builder("regulator.notification.digests_sent").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Broadcast a critical event to all regulators in the given jurisdiction. */
    public Promise<Void> broadcastCritical(String eventType, String jurisdiction,
                                            String subject, String body) {
        return Promise.ofBlocking(executor, () -> {
            List<String[]> regulators = getRegulatorsByJurisdiction(jurisdiction, eventType);
            for (String[] r : regulators) {
                deliverToRegulator(r[0], eventType, r[1], subject, body);
                criticalSent.increment();
            }
            audit.record("system", "CRITICAL_NOTIFICATION_BROADCAST",
                "eventType=" + eventType + " jurisdiction=" + jurisdiction + " recipients=" + regulators.size());
            return null;
        });
    }

    /** Send notification to a single regulator. */
    public Promise<Void> send(String regulatorId, String eventType, String subject, String body) {
        return Promise.ofBlocking(executor, () -> {
            String channel = getPreferredChannel(regulatorId, eventType);
            if (channel == null) return null; // disabled
            deliverToRegulator(regulatorId, eventType, channel, subject, body);
            return null;
        });
    }

    /** Upsert notification preference for a regulator. */
    public Promise<Void> setPreference(String regulatorId, String eventType, String channel, boolean enabled) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO regulator_notification_prefs (regulator_id, event_type, channel, enabled) VALUES (?,?,?,?) " +
                     "ON CONFLICT (regulator_id, event_type) DO UPDATE SET channel=EXCLUDED.channel, enabled=EXCLUDED.enabled"
                 )) {
                ps.setString(1, regulatorId); ps.setString(2, eventType);
                ps.setString(3, channel);     ps.setBoolean(4, enabled);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Generate and send daily digest to all regulators who have DAILY_DIGEST enabled.
     * The digest body is injected by caller (pre-aggregated by scheduler).
     */
    public Promise<Integer> sendDailyDigest(String digestBody) {
        return Promise.ofBlocking(executor, () -> {
            List<String> recipients = getDigestRecipients("DAILY_DIGEST");
            for (String regulatorId : recipients) {
                try {
                    email.send(regulatorId, "Daily Platform Activity Digest", digestBody);
                    logNotification(regulatorId, "DAILY_DIGEST", "EMAIL", "Daily Platform Activity Digest", true);
                    digestsSent.increment();
                } catch (Exception ignored) {
                    logNotification(regulatorId, "DAILY_DIGEST", "EMAIL", "Daily Platform Activity Digest", false);
                }
            }
            return recipients.size();
        });
    }

    /** Weekly digest variant. */
    public Promise<Integer> sendWeeklyDigest(String digestBody) {
        return Promise.ofBlocking(executor, () -> {
            List<String> recipients = getDigestRecipients("WEEKLY_DIGEST");
            for (String regulatorId : recipients) {
                try {
                    email.send(regulatorId, "Weekly Platform Activity Digest", digestBody);
                    logNotification(regulatorId, "WEEKLY_DIGEST", "EMAIL", "Weekly Platform Activity Digest", true);
                    digestsSent.increment();
                } catch (Exception ignored) {
                    logNotification(regulatorId, "WEEKLY_DIGEST", "EMAIL", "Weekly Platform Activity Digest", false);
                }
            }
            return recipients.size();
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void deliverToRegulator(String regulatorId, String eventType, String channel,
                                     String subject, String body) throws Exception {
        boolean delivered = false;
        try {
            if ("EMAIL".equals(channel) || "BOTH".equals(channel)) {
                email.send(regulatorId, subject, body);
                delivered = true;
            }
            if ("WEBHOOK".equals(channel) || "BOTH".equals(channel)) {
                webhook.post(regulatorId, Map.of("eventType", eventType, "subject", subject, "body", body));
                delivered = true;
            }
        } finally {
            logNotification(regulatorId, eventType, channel, subject, delivered);
        }
    }

    private String getPreferredChannel(String regulatorId, String eventType) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT channel FROM regulator_notification_prefs WHERE regulator_id=? AND event_type=? AND enabled=TRUE"
             )) {
            ps.setString(1, regulatorId); ps.setString(2, eventType);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString("channel") : "EMAIL"; }
        }
    }

    private List<String[]> getRegulatorsByJurisdiction(String jurisdiction, String eventType) throws SQLException {
        List<String[]> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT r.regulator_id, COALESCE(p.channel, 'EMAIL') " +
                 "FROM regulator_jurisdictions r " +
                 "LEFT JOIN regulator_notification_prefs p ON p.regulator_id=r.regulator_id AND p.event_type=? AND p.enabled=TRUE " +
                 "WHERE r.jurisdiction=?"
             )) {
            ps.setString(1, eventType); ps.setString(2, jurisdiction);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new String[]{rs.getString(1), rs.getString(2)});
            }
        }
        return result;
    }

    private List<String> getDigestRecipients(String digestType) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT regulator_id FROM regulator_notification_prefs WHERE event_type=? AND enabled=TRUE"
             )) {
            ps.setString(1, digestType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        }
        return result;
    }

    private void logNotification(String regulatorId, String eventType, String channel,
                                  String subject, boolean delivered) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO regulator_notification_log (regulator_id, event_type, channel, subject, delivered) VALUES (?,?,?,?,?)"
             )) {
            ps.setString(1, regulatorId); ps.setString(2, eventType);
            ps.setString(3, channel);     ps.setString(4, subject); ps.setBoolean(5, delivered);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }
}
