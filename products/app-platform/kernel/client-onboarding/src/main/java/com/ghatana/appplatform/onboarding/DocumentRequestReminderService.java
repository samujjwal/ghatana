package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Automates document request and reminder communications throughout the KYC
 *              onboarding lifecycle.
 *              - Sends initial document request (email + SMS) when KYC workflow starts.
 *              - Sends 3-day reminder if documents are still pending.
 *              - Escalates to KYC coordinator at 7-day mark.
 *              - Generates time-limited secure upload links (7-day expiry, single-use token).
 *              - Regenerates expired links on demand.
 *              Coordinates with W01 workflow triggers for scheduling reminders.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class DocumentRequestReminderService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface NotificationPort {
        /** Send an email notification. */
        Promise<Void> sendEmail(String toAddress, String templateId, String payloadJson);

        /** Send an SMS notification. */
        Promise<Void> sendSms(String toPhone, String templateId, String payloadJson);
    }

    public interface UploadLinkGeneratorPort {
        /**
         * Generate a secure, time-limited upload token for a specific document type.
         * Token is HMAC-signed and expires after {@code ttlSeconds}.
         */
        Promise<String> generateToken(String instanceId, String documentType, long ttlSeconds);

        /** Validate and decode an upload token. Returns null if invalid or expired. */
        Promise<UploadTokenClaims> validateToken(String token);
    }

    public interface SchedulerPort {
        /** Schedule a reminder job (cron or delay). Returns the scheduler job ID. */
        Promise<String> scheduleDelay(String name, String payloadJson, long delaySeconds);

        /** Cancel a scheduled job. */
        Promise<Void> cancel(String jobId);
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    public record UploadTokenClaims(String instanceId, String documentType, boolean expired) {}

    public record DocumentRequestRecord(
        String requestId,
        String instanceId,
        String documentType,
        String status,        // PENDING | UPLOADED | EXPIRED
        String uploadToken,
        String uploadLink,
        String expiresAt,
        String sentAt,
        String reminderSentAt,
        String escalationSentAt
    ) {}

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final String EMAIL_TEMPLATE_INITIAL_REQUEST = "kyc-document-request-v1";
    private static final String EMAIL_TEMPLATE_3DAY_REMINDER   = "kyc-document-reminder-3day-v1";
    private static final String EMAIL_TEMPLATE_7DAY_ESCALATION = "kyc-document-escalation-7day-v1";
    private static final String SMS_TEMPLATE_INITIAL_REQUEST    = "kyc-sms-request-v1";

    private static final long UPLOAD_LINK_TTL_SECONDS   = 7L * 24 * 3600;   // 7 days
    private static final long THREE_DAY_DELAY_SECONDS   = 3L * 24 * 3600;
    private static final long SEVEN_DAY_DELAY_SECONDS   = 7L * 24 * 3600;

    private static final String UPLOAD_BASE_URL = "https://onboard.ghatana.com/upload/";

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final NotificationPort notifier;
    private final UploadLinkGeneratorPort linkGenerator;
    private final SchedulerPort scheduler;
    private final AuditPort auditPort;

    private final Counter requestsSentTotal;
    private final Counter remindersSentTotal;
    private final Counter escalationsSentTotal;
    private final Counter linksRegeneratedTotal;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DocumentRequestReminderService(DataSource dataSource,
                                          Executor executor,
                                          MeterRegistry meterRegistry,
                                          NotificationPort notifier,
                                          UploadLinkGeneratorPort linkGenerator,
                                          SchedulerPort scheduler,
                                          AuditPort auditPort) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.notifier      = notifier;
        this.linkGenerator = linkGenerator;
        this.scheduler     = scheduler;
        this.auditPort     = auditPort;

        this.requestsSentTotal    = Counter.builder("kyc.document_request.sent_total")
                .description("Initial document requests sent")
                .register(meterRegistry);
        this.remindersSentTotal   = Counter.builder("kyc.document_request.reminder_sent_total")
                .description("3-day reminders sent")
                .register(meterRegistry);
        this.escalationsSentTotal = Counter.builder("kyc.document_request.escalation_sent_total")
                .description("7-day escalations sent to KYC coordinator")
                .register(meterRegistry);
        this.linksRegeneratedTotal = Counter.builder("kyc.document_request.link_regenerated_total")
                .description("Upload links regenerated after expiry")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Called when KYC onboarding starts. Generates secure upload link for each required document,
     * stores request records, sends the initial email + SMS, and schedules 3-day and 7-day follow-ups.
     */
    public Promise<Void> sendInitialDocumentRequests(String instanceId, String clientEmail,
                                                      String clientPhone, java.util.List<String> requiredDocs) {
        return Promise.all(requiredDocs.stream()
            .map(docType -> generateAndStoreDocumentRequest(instanceId, docType))
            .toList()
        ).then(__ -> {
            String uploadLinksJson = buildUploadLinksJson(instanceId, requiredDocs);
            return notifier.sendEmail(clientEmail, EMAIL_TEMPLATE_INITIAL_REQUEST, uploadLinksJson)
                .then(v -> notifier.sendSms(clientPhone, SMS_TEMPLATE_INITIAL_REQUEST,
                                            "{\"instanceId\":\"" + instanceId + "\"}"))
                .then(v -> scheduler.scheduleDelay("kyc-reminder-3day-" + instanceId,
                                                   "{\"instanceId\":\"" + instanceId + "\",\"clientEmail\":\"" + clientEmail + "\"}",
                                                   THREE_DAY_DELAY_SECONDS))
                .then(jobId3 -> scheduler.scheduleDelay("kyc-escalation-7day-" + instanceId,
                                                        "{\"instanceId\":\"" + instanceId + "\",\"clientEmail\":\"" + clientEmail + "\"}",
                                                        SEVEN_DAY_DELAY_SECONDS)
                    .then(jobId7 -> Promise.ofBlocking(executor, () -> {
                        storeJobIds(instanceId, jobId3, jobId7);
                        requestsSentTotal.increment(requiredDocs.size());
                        auditPort.log("DOCUMENT_REQUESTS_SENT", "system", instanceId,
                                      "KYC_INSTANCE", null,
                                      "{\"documentCount\":" + requiredDocs.size() + "}");
                        return null;
                    })));
        });
    }

    /**
     * Send the 3-day reminder. Called by the W-01 scheduler trigger.
     * Skips documents that are already UPLOADED or APPROVED.
     */
    public Promise<Void> sendThreeDayReminder(String instanceId, String clientEmail) {
        return Promise.ofBlocking(executor, () -> getPendingDocuments(instanceId))
            .then(pendingDocs -> {
                if (pendingDocs.isEmpty()) return Promise.of((Void) null); // all docs uploaded — no reminder needed
                String pendingJson = buildPendingDocJson(pendingDocs);
                return notifier.sendEmail(clientEmail, EMAIL_TEMPLATE_3DAY_REMINDER,
                                          "{\"instanceId\":\"" + instanceId + "\",\"pendingDocs\":" + pendingJson + "}")
                    .then(v -> Promise.ofBlocking(executor, () -> {
                        markReminderSent(instanceId);
                        remindersSentTotal.increment();
                        return null;
                    }));
            });
    }

    /**
     * Send the 7-day escalation email to the KYC coordinator.
     * Called by the W-01 scheduler trigger.
     */
    public Promise<Void> sendSevenDayEscalation(String instanceId, String clientEmail,
                                                  String coordinatorEmail) {
        return Promise.ofBlocking(executor, () -> getPendingDocuments(instanceId))
            .then(pendingDocs -> {
                if (pendingDocs.isEmpty()) return Promise.of((Void) null);
                String pendingJson = buildPendingDocJson(pendingDocs);
                return notifier.sendEmail(coordinatorEmail, EMAIL_TEMPLATE_7DAY_ESCALATION,
                                          "{\"instanceId\":\"" + instanceId + "\",\"clientEmail\":\"" + clientEmail + "\"," +
                                          "\"pendingDocs\":" + pendingJson + "}")
                    .then(v -> Promise.ofBlocking(executor, () -> {
                        markEscalationSent(instanceId);
                        escalationsSentTotal.increment();
                        return null;
                    }));
            });
    }

    /**
     * Regenerate the upload link for a specific document (e.g., after expiry).
     * Returns the new upload URL.
     */
    public Promise<String> regenerateUploadLink(String instanceId, String documentType) {
        return linkGenerator.generateToken(instanceId, documentType, UPLOAD_LINK_TTL_SECONDS)
            .then(token -> Promise.ofBlocking(executor, () -> {
                String uploadUrl = UPLOAD_BASE_URL + token;
                updateUploadToken(instanceId, documentType, token, uploadUrl);
                linksRegeneratedTotal.increment();
                auditPort.log("UPLOAD_LINK_REGENERATED", "system", instanceId, "KYC_DOCUMENT",
                              null, "{\"documentType\":\"" + documentType + "\"}");
                return uploadUrl;
            }));
    }

    /**
     * Validate an upload token presented by a client. Returns claims or null if invalid/expired.
     */
    public Promise<UploadTokenClaims> validateUploadToken(String token) {
        return linkGenerator.validateToken(token);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Promise<Void> generateAndStoreDocumentRequest(String instanceId, String documentType) {
        return linkGenerator.generateToken(instanceId, documentType, UPLOAD_LINK_TTL_SECONDS)
            .then(token -> Promise.ofBlocking(executor, () -> {
                String uploadUrl = UPLOAD_BASE_URL + token;
                String expiresAt = Instant.now().plusSeconds(UPLOAD_LINK_TTL_SECONDS).toString();
                insertDocumentRequest(instanceId, documentType, token, uploadUrl, expiresAt);
                return null;
            }));
    }

    private void insertDocumentRequest(String instanceId, String documentType,
                                        String token, String uploadUrl, String expiresAt) {
        String sql = """
            INSERT INTO kyc_document_requests
                (request_id, instance_id, document_type, status, upload_token, upload_link,
                 expires_at, sent_at)
            VALUES (gen_random_uuid()::text, ?, ?, 'PENDING', ?, ?, ?::timestamptz, now())
            ON CONFLICT (instance_id, document_type) DO UPDATE
               SET status = 'PENDING', upload_token = EXCLUDED.upload_token,
                   upload_link = EXCLUDED.upload_link, expires_at = EXCLUDED.expires_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setString(2, documentType);
            ps.setString(3, token);
            ps.setString(4, uploadUrl);
            ps.setString(5, expiresAt);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to store document request for " + documentType, e);
        }
    }

    private java.util.List<String> getPendingDocuments(String instanceId) {
        String sql = """
            SELECT document_type FROM kyc_document_requests
             WHERE instance_id = ? AND status = 'PENDING'
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<String> result = new java.util.ArrayList<>();
                while (rs.next()) result.add(rs.getString("document_type"));
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query pending documents for instance " + instanceId, e);
        }
    }

    private void markReminderSent(String instanceId) {
        String sql = "UPDATE kyc_document_requests SET reminder_sent_at = now() WHERE instance_id = ? AND status = 'PENDING'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to mark reminder sent for " + instanceId, e);
        }
    }

    private void markEscalationSent(String instanceId) {
        String sql = "UPDATE kyc_document_requests SET escalation_sent_at = now() WHERE instance_id = ? AND status = 'PENDING'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to mark escalation sent for " + instanceId, e);
        }
    }

    private void updateUploadToken(String instanceId, String documentType, String token, String uploadUrl) {
        String sql = """
            UPDATE kyc_document_requests
               SET upload_token = ?, upload_link = ?,
                   expires_at = (now() + INTERVAL '7 days')
             WHERE instance_id = ? AND document_type = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setString(2, uploadUrl);
            ps.setString(3, instanceId);
            ps.setString(4, documentType);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update upload token for " + documentType, e);
        }
    }

    private void storeJobIds(String instanceId, String jobId3day, String jobId7day) {
        String sql = """
            INSERT INTO kyc_reminder_jobs (instance_id, job_3day_id, job_7day_id, created_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (instance_id) DO UPDATE
               SET job_3day_id = EXCLUDED.job_3day_id, job_7day_id = EXCLUDED.job_7day_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setString(2, jobId3day);
            ps.setString(3, jobId7day);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to store reminder job IDs for " + instanceId, e);
        }
    }

    private String buildUploadLinksJson(String instanceId, java.util.List<String> docTypes) {
        return "{\"instanceId\":\"" + instanceId + "\",\"documentCount\":" + docTypes.size() + "}";
    }

    private String buildPendingDocJson(java.util.List<String> pendingDocs) {
        if (pendingDocs.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < pendingDocs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(pendingDocs.get(i)).append("\"");
        }
        return sb.append("]").toString();
    }
}
