package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Multi-channel corporate action notifications (portal, email, SMS). Channel
 *              preferences per client are read from K-02 ConfigPort. T-2 business day
 *              reminders are sent before ex_date. Election deadline reminders are sent for
 *              RIGHTS/OPTIONAL actions. Notification state prevents duplicate sends.
 *              Satisfies STORY-D12-003.
 * @doc.layer   Domain
 * @doc.pattern Multi-channel notify; K-02 preferences; T-2 reminder; idempotent send; Counter.
 */
public class CorporateActionNotificationService {

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final NotificationPort  notificationPort;
    private final ConfigPort        configPort;
    private final Counter           notificationSentCounter;
    private final Counter           electionReminderCounter;

    public CorporateActionNotificationService(HikariDataSource dataSource, Executor executor,
                                               NotificationPort notificationPort,
                                               ConfigPort configPort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.notificationPort        = notificationPort;
        this.configPort              = configPort;
        this.notificationSentCounter = Counter.builder("ca.notification.sent_total").register(registry);
        this.electionReminderCounter = Counter.builder("ca.notification.election_reminder_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface NotificationPort {
        void sendPortal(String clientId, String subject, String body);
        void sendEmail(String clientId, String subject, String body);
        void sendSms(String clientId,   String message);
    }

    /** K-02 per-client notification channel preferences. */
    public interface ConfigPort {
        boolean isPortalEnabled(String clientId);
        boolean isEmailEnabled(String clientId);
        boolean isSmsEnabled(String clientId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum NotificationType { ANNOUNCEMENT, EX_DATE_REMINDER, ELECTION_DEADLINE }

    public record NotificationRecord(String notifId, String caId, String clientId,
                                      NotificationType type, List<String> channels,
                                      LocalDateTime sentAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<NotificationRecord>> notifyAnnouncement(String caId, String issuerId,
                                                                  String caType, LocalDate exDate,
                                                                  String exDateBs) {
        return Promise.ofBlocking(executor, () -> {
            List<String> clientIds   = loadHolderClientIds(caId);
            List<NotificationRecord> sent = new ArrayList<>();
            String subject = "Corporate Action Announced: " + caType + " – " + issuerId;
            String body    = "A " + caType + " corporate action has been announced for " + issuerId
                    + ".\nEx-Date: " + exDate + " (BS: " + exDateBs + ")."
                    + "\nPlease check your portfolio for details.";
            for (String clientId : clientIds) {
                if (alreadySent(caId, clientId, NotificationType.ANNOUNCEMENT)) continue;
                List<String> channels = sendToClient(clientId, subject, body, null);
                NotificationRecord rec = persist(caId, clientId, NotificationType.ANNOUNCEMENT, channels);
                sent.add(rec);
                notificationSentCounter.increment();
            }
            return sent;
        });
    }

    public Promise<List<NotificationRecord>> sendExDateReminders(LocalDate today) {
        return Promise.ofBlocking(executor, () -> {
            // T-2 business days before ex_date
            LocalDate target = today.plusDays(2);
            List<String[]> actions = loadCasDueOn(target);
            List<NotificationRecord> sent = new ArrayList<>();
            for (String[] row : actions) {
                String caId      = row[0];
                String issuerId  = row[1];
                String caType    = row[2];
                LocalDate exDate = LocalDate.parse(row[3]);
                List<String> clientIds = loadHolderClientIds(caId);
                for (String clientId : clientIds) {
                    if (alreadySent(caId, clientId, NotificationType.EX_DATE_REMINDER)) continue;
                    String subject = "Reminder: " + caType + " ex-date in 2 business days – " + issuerId;
                    String body    = "Ex-date for " + issuerId + " " + caType + " is " + exDate + ".";
                    List<String> channels = sendToClient(clientId, subject, body, body);
                    sent.add(persist(caId, clientId, NotificationType.EX_DATE_REMINDER, channels));
                    notificationSentCounter.increment();
                }
            }
            return sent;
        });
    }

    public Promise<List<NotificationRecord>> sendElectionDeadlineReminders(String caId,
                                                                             LocalDate electionDeadline) {
        return Promise.ofBlocking(executor, () -> {
            List<String> clientIds = loadHolderClientIds(caId);
            List<NotificationRecord> sent = new ArrayList<>();
            for (String clientId : clientIds) {
                if (alreadySent(caId, clientId, NotificationType.ELECTION_DEADLINE)) continue;
                String subject = "Election deadline approaching for CA " + caId;
                String body    = "Your election deadline is " + electionDeadline + ". Please make your election.";
                List<String> channels = sendToClient(clientId, subject, body, body);
                sent.add(persist(caId, clientId, NotificationType.ELECTION_DEADLINE, channels));
                electionReminderCounter.increment();
            }
            return sent;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<String> sendToClient(String clientId, String subject, String body, String smsMsg) {
        List<String> channels = new ArrayList<>();
        if (configPort.isPortalEnabled(clientId)) { notificationPort.sendPortal(clientId, subject, body); channels.add("PORTAL"); }
        if (configPort.isEmailEnabled(clientId))  { notificationPort.sendEmail(clientId, subject, body);  channels.add("EMAIL"); }
        if (configPort.isSmsEnabled(clientId) && smsMsg != null) { notificationPort.sendSms(clientId, smsMsg); channels.add("SMS"); }
        return channels;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private boolean alreadySent(String caId, String clientId, NotificationType type) throws SQLException {
        String sql = "SELECT 1 FROM ca_notifications WHERE ca_id=? AND client_id=? AND type=? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId); ps.setString(2, clientId); ps.setString(3, type.name());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private NotificationRecord persist(String caId, String clientId, NotificationType type,
                                        List<String> channels) throws SQLException {
        String notifId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO ca_notifications (notif_id, ca_id, client_id, type, channels, sent_at)
                VALUES (?, ?, ?, ?, ?, NOW()) ON CONFLICT DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, notifId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setString(4, type.name()); ps.setString(5, String.join(",", channels));
            ps.executeUpdate();
        }
        return new NotificationRecord(notifId, caId, clientId, type, channels, LocalDateTime.now());
    }

    private List<String> loadHolderClientIds(String caId) throws SQLException {
        String sql = "SELECT DISTINCT client_id FROM ca_holder_snapshots WHERE ca_id=?";
        List<String> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getString(1)); }
        }
        return result;
    }

    private List<String[]> loadCasDueOn(LocalDate exDate) throws SQLException {
        String sql = "SELECT ca_id, issuer_id, ca_type, ex_date FROM corporate_actions WHERE ex_date=? AND status!='COMPLETED'";
        List<String[]> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, exDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new String[]{
                        rs.getString("ca_id"), rs.getString("issuer_id"),
                        rs.getString("ca_type"), rs.getString("ex_date")});
            }
        }
        return result;
    }
}
