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
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Manages regulatory report submissions to SEBON, NRB, and IRD via K-04 T3
 *              IRegulatorAdapter plugins. Lifecycle: PREPARED → SUBMITTED → ACKNOWLEDGED →
 *              ACCEPTED | REJECTED. Credentials via K-14 CredentialVaultPort.
 *              Tracks submission status and retry count. Satisfies STORY-D10-006.
 * @doc.layer   Domain
 * @doc.pattern K-04 T3 adapter plugin; K-14 credential vault; submission lifecycle; Counter.
 */
public class RegulatorSubmissionAdapterService {

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final AdapterRegistryPort adapterRegistry;
    private final CredentialVaultPort credentialVault;
    private final Counter             submittedCounter;
    private final Counter             failedCounter;

    public RegulatorSubmissionAdapterService(HikariDataSource dataSource, Executor executor,
                                              AdapterRegistryPort adapterRegistry,
                                              CredentialVaultPort credentialVault,
                                              MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.adapterRegistry   = adapterRegistry;
        this.credentialVault   = credentialVault;
        this.submittedCounter  = Counter.builder("reporting.submission.submitted_total").register(registry);
        this.failedCounter     = Counter.builder("reporting.submission.failed_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-04 T3 plugin registry for IRegulatorAdapter implementations. */
    public interface AdapterRegistryPort {
        RegulatorAdapter getAdapter(String regulator);
    }

    public interface RegulatorAdapter {
        SubmissionReceipt submit(String reportCode, byte[] payload, String mimeType,
                                  String bearerToken);
    }

    /** K-14 credential vault for regulator API keys / certificates. */
    public interface CredentialVaultPort {
        String getBearerToken(String regulator);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum SubmissionStatus { PREPARED, SUBMITTED, ACKNOWLEDGED, ACCEPTED, REJECTED }

    public record SubmissionReceipt(String receiptId, String submissionStatus, String regulatorRef) {}

    public record Submission(String submissionId, String reportId, String regulator,
                              String reportCode, SubmissionStatus status, String regulatorRef,
                              int retryCount, LocalDateTime submittedAt, LocalDateTime updatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<Submission> submit(String reportId, String regulator, String reportCode,
                                       byte[] payload, String mimeType) {
        return Promise.ofBlocking(executor, () -> {
            String token    = credentialVault.getBearerToken(regulator);
            RegulatorAdapter adapter = adapterRegistry.getAdapter(regulator);
            SubmissionReceipt receipt;
            try {
                receipt = adapter.submit(reportCode, payload, mimeType, token);
                Submission sub = persistSubmission(reportId, regulator, reportCode,
                        SubmissionStatus.SUBMITTED, receipt.regulatorRef(), 0);
                submittedCounter.increment();
                return sub;
            } catch (Exception e) {
                persistSubmission(reportId, regulator, reportCode,
                        SubmissionStatus.PREPARED, null, 0);
                failedCounter.increment();
                throw new RuntimeException("Submission failed: " + e.getMessage(), e);
            }
        });
    }

    public Promise<Submission> acknowledgeReceipt(String submissionId, String regulatorRef,
                                                   boolean accepted, String reasonIfRejected) {
        return Promise.ofBlocking(executor, () -> {
            SubmissionStatus newStatus = accepted ? SubmissionStatus.ACCEPTED : SubmissionStatus.REJECTED;
            updateStatus(submissionId, newStatus, regulatorRef, reasonIfRejected);
            return loadSubmission(submissionId);
        });
    }

    public Promise<List<Submission>> listPendingAcknowledgement() {
        return Promise.ofBlocking(executor, this::loadPendingSubmissions);
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private Submission persistSubmission(String reportId, String regulator, String reportCode,
                                          SubmissionStatus status, String regulatorRef,
                                          int retryCount) throws SQLException {
        String submissionId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO regulator_submissions
                    (submission_id, report_id, regulator, report_code, status, regulator_ref,
                     retry_count, submitted_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, submissionId);
            ps.setString(2, reportId);
            ps.setString(3, regulator);
            ps.setString(4, reportCode);
            ps.setString(5, status.name());
            ps.setString(6, regulatorRef);
            ps.setInt(7, retryCount);
            ps.executeUpdate();
        }
        return loadSubmission(submissionId);
    }

    private void updateStatus(String submissionId, SubmissionStatus status, String regulatorRef,
                               String reason) throws SQLException {
        String sql = """
                UPDATE regulator_submissions
                SET status=?, regulator_ref=COALESCE(?, regulator_ref),
                    rejection_reason=?, updated_at=NOW()
                WHERE submission_id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, regulatorRef);
            ps.setString(3, reason);
            ps.setString(4, submissionId);
            ps.executeUpdate();
        }
    }

    private Submission loadSubmission(String submissionId) throws SQLException {
        String sql = "SELECT * FROM regulator_submissions WHERE submission_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Submission not found: " + submissionId);
                return mapSubmission(rs);
            }
        }
    }

    private List<Submission> loadPendingSubmissions() throws SQLException {
        String sql = "SELECT * FROM regulator_submissions WHERE status='SUBMITTED'";
        List<Submission> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapSubmission(rs));
        }
        return result;
    }

    private Submission mapSubmission(ResultSet rs) throws SQLException {
        return new Submission(rs.getString("submission_id"), rs.getString("report_id"),
                rs.getString("regulator"), rs.getString("report_code"),
                SubmissionStatus.valueOf(rs.getString("status")),
                rs.getString("regulator_ref"), rs.getInt("retry_count"),
                rs.getObject("submitted_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }
}
