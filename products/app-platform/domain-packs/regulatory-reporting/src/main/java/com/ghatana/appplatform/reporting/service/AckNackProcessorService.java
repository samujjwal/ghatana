package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Parses ACK (acknowledgement) and NACK (negative acknowledgement) responses
 *              from SEBON/NRB/IRD. ACK transitions submission to ACKNOWLEDGED. NACK extracts
 *              field-level error codes from regulator response and triggers a resubmission
 *              workflow (new submission row with incremented retry count). Notifications
 *              sent via NotificationPort for both outcomes. Satisfies STORY-D10-008.
 * @doc.layer   Domain
 * @doc.pattern ACK/NACK processing; field-level error extraction; resubmission workflow; Counter.
 */
public class AckNackProcessorService {

    private static final int MAX_RETRY_COUNT = 3;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final NotificationPort notificationPort;
    private final Counter          ackCounter;
    private final Counter          nackCounter;
    private final Counter          resubmitCounter;

    public AckNackProcessorService(HikariDataSource dataSource, Executor executor,
                                    NotificationPort notificationPort, MeterRegistry registry) {
        this.dataSource         = dataSource;
        this.executor           = executor;
        this.notificationPort   = notificationPort;
        this.ackCounter         = Counter.builder("reporting.acknack.ack_total").register(registry);
        this.nackCounter        = Counter.builder("reporting.acknack.nack_total").register(registry);
        this.resubmitCounter    = Counter.builder("reporting.acknack.resubmit_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface NotificationPort {
        void send(String recipient, String subject, String body);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record AckPayload(String submissionId, String regulatorRef, LocalDateTime acknowledgedAt) {}

    public record NackPayload(String submissionId, String regulatorRef, List<FieldError> fieldErrors,
                               LocalDateTime rejectedAt) {}

    public record FieldError(String fieldName, String errorCode, String message) {}

    public record ProcessResult(String submissionId, boolean accepted,
                                 List<FieldError> errors, String resubmitId) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ProcessResult> processAck(AckPayload ack) {
        return Promise.ofBlocking(executor, () -> {
            updateSubmissionStatus(ack.submissionId(), "ACKNOWLEDGED", ack.regulatorRef(), null);
            ackCounter.increment();
            Submission sub = loadSubmission(ack.submissionId());
            notificationPort.send("compliance-team@ghatana.com",
                    "Report Acknowledged: " + sub.reportCode(),
                    "Submission " + ack.submissionId() + " acknowledged by " + sub.regulator()
                            + ". Ref: " + ack.regulatorRef());
            return new ProcessResult(ack.submissionId(), true, List.of(), null);
        });
    }

    public Promise<ProcessResult> processNack(NackPayload nack) {
        return Promise.ofBlocking(executor, () -> {
            persistNackErrors(nack.submissionId(), nack.fieldErrors());
            updateSubmissionStatus(nack.submissionId(), "REJECTED", nack.regulatorRef(),
                    nack.fieldErrors().isEmpty() ? "NACK" : nack.fieldErrors().get(0).errorCode());
            nackCounter.increment();

            Submission sub = loadSubmission(nack.submissionId());
            String resubmitId = null;
            if (sub.retryCount() < MAX_RETRY_COUNT) {
                resubmitId = createResubmitPlaceholder(sub);
                resubmitCounter.increment();
            }
            notificationPort.send("compliance-team@ghatana.com",
                    "Report NACK: " + sub.reportCode(),
                    "Submission " + nack.submissionId() + " rejected by " + sub.regulator()
                            + ". Errors: " + nack.fieldErrors().size()
                            + (resubmitId != null ? ". Resubmission queued: " + resubmitId : ". Max retries reached."));
            return new ProcessResult(nack.submissionId(), false, nack.fieldErrors(), resubmitId);
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void updateSubmissionStatus(String submissionId, String status, String regulatorRef,
                                         String reason) throws SQLException {
        String sql = """
                UPDATE regulator_submissions
                SET status=?, regulator_ref=COALESCE(?,regulator_ref), rejection_reason=?, updated_at=NOW()
                WHERE submission_id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, regulatorRef);
            ps.setString(3, reason);
            ps.setString(4, submissionId);
            ps.executeUpdate();
        }
    }

    private void persistNackErrors(String submissionId, List<FieldError> errors) throws SQLException {
        if (errors.isEmpty()) return;
        String sql = """
                INSERT INTO submission_nack_errors (error_id, submission_id, field_name, error_code, message)
                VALUES (gen_random_uuid()::text, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (FieldError err : errors) {
                ps.setString(1, submissionId);
                ps.setString(2, err.fieldName());
                ps.setString(3, err.errorCode());
                ps.setString(4, err.message());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String createResubmitPlaceholder(Submission sub) throws SQLException {
        String newId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO regulator_submissions
                    (submission_id, report_id, regulator, report_code, status, retry_count, submitted_at, updated_at)
                VALUES (?, ?, ?, ?, 'PREPARED', ?, NOW(), NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newId);
            ps.setString(2, sub.reportId());
            ps.setString(3, sub.regulator());
            ps.setString(4, sub.reportCode());
            ps.setInt(5, sub.retryCount() + 1);
            ps.executeUpdate();
        }
        return newId;
    }

    record Submission(String submissionId, String reportId, String regulator,
                      String reportCode, int retryCount) {}

    private Submission loadSubmission(String submissionId) throws SQLException {
        String sql = "SELECT submission_id, report_id, regulator, report_code, retry_count FROM regulator_submissions WHERE submission_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Not found: " + submissionId);
                return new Submission(rs.getString("submission_id"), rs.getString("report_id"),
                        rs.getString("regulator"), rs.getString("report_code"), rs.getInt("retry_count"));
            }
        }
    }
}
