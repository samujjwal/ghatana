package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Manages the air-gap bundle import workflow with maker-checker controls.
 *                An operator uploads a signed bundle; a second operator approves it before
 *                it is committed to the live sanctions list store.
 *                Uses {@link AirGapBundleSigningService} to verify the cryptographic signature
 *                before any approval step.
 * @doc.layer     Application
 * @doc.pattern   Maker-checker workflow; cryptographic validation before approval
 *
 * Story: D14-014
 */
public class BundleImportWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(BundleImportWorkflowService.class);

    private final AirGapBundleSigningService signingService;
    private final SanctionsListIngestionService ingestionService;
    private final DataSource               dataSource;
    private final Consumer<Object>         eventPublisher;
    private final Counter                  bundlesImported;
    private final Counter                  bundlesRejected;

    public BundleImportWorkflowService(AirGapBundleSigningService signingService,
                                        SanctionsListIngestionService ingestionService,
                                        DataSource dataSource,
                                        Consumer<Object> eventPublisher,
                                        MeterRegistry meterRegistry) {
        this.signingService    = signingService;
        this.ingestionService  = ingestionService;
        this.dataSource        = dataSource;
        this.eventPublisher    = eventPublisher;
        this.bundlesImported   = meterRegistry.counter("sanctions.bundle.imported");
        this.bundlesRejected   = meterRegistry.counter("sanctions.bundle.rejected");
    }

    /**
     * First step: operator uploads a signed bundle for review.
     *
     * @param bundle     the signed bundle from the air-gap medium
     * @param uploaderId first operator (uploader / maker)
     * @return import request identifier
     * @throws BundleSignatureException if the Ed25519 signature is invalid
     */
    public String submitForApproval(AirGapBundleSigningService.SignedBundle bundle, String uploaderId) {
        if (!signingService.verify(bundle)) {
            bundlesRejected.increment();
            throw new BundleSignatureException("Invalid bundle signature bundleId=" + bundle.bundleId());
        }

        String importId = UUID.randomUUID().toString();
        String sql = "INSERT INTO bundle_import_requests"
                   + "(import_id, bundle_id, uploader_id, status, payload_size, signature_b64, submitted_at) "
                   + "VALUES(?,?,?,'PENDING_APPROVAL',?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, importId);
            ps.setString(2, bundle.bundleId());
            ps.setString(3, uploaderId);
            ps.setInt(4, bundle.payload().length);
            ps.setString(5, bundle.signatureB64());
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to submit bundle for approval", e);
        }

        eventPublisher.accept(new BundleSubmittedEvent(importId, bundle.bundleId(), uploaderId, Instant.now()));
        log.info("BundleImport submitted for approval importId={} bundleId={}", importId, bundle.bundleId());
        return importId;
    }

    /**
     * Second step: a different operator approves the import. The bundle is then committed to
     * the live sanctions list store via the existing ingestion service.
     *
     * @param importId   the import request to approve
     * @param approverId second operator (approver / checker) — must differ from uploader
     */
    public void approve(String importId, String approverId) {
        ImportRequest req = loadRequest(importId);
        if (req == null) {
            throw new IllegalArgumentException("Import request not found: " + importId);
        }
        if (!"PENDING_APPROVAL".equals(req.status())) {
            throw new IllegalStateException("Import not in PENDING_APPROVAL state: " + importId);
        }
        if (req.uploaderId().equals(approverId)) {
            throw new IllegalStateException("Maker-checker violation: same operator id=" + approverId);
        }

        updateStatus(importId, "APPROVED", approverId);

        // Delegate actual ingestion to the existing D14 ingestion service
        ingestionService.ingestFromBytes(req.bundleId(), req.payload());

        bundlesImported.increment();
        eventPublisher.accept(new BundleApprovedEvent(importId, req.bundleId(), approverId, Instant.now()));
        log.info("BundleImport approved importId={} bundleId={} approver={}", importId, req.bundleId(), approverId);
    }

    /**
     * Rejects a pending import request.
     */
    public void reject(String importId, String reviewerId, String reason) {
        updateStatus(importId, "REJECTED", reviewerId);
        bundlesRejected.increment();
        eventPublisher.accept(new BundleRejectedEvent(importId, reviewerId, reason, Instant.now()));
        log.warn("BundleImport rejected importId={} reviewer={} reason={}", importId, reviewerId, reason);
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private ImportRequest loadRequest(String importId) {
        String sql = "SELECT import_id, bundle_id, uploader_id, status, payload_bytes "
                   + "FROM bundle_import_requests WHERE import_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, importId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ImportRequest(rs.getString("import_id"), rs.getString("bundle_id"),
                            rs.getString("uploader_id"), rs.getString("status"),
                            rs.getBytes("payload_bytes"));
                }
            }
        } catch (SQLException e) {
            log.error("loadRequest DB error importId={}", importId, e);
        }
        return null;
    }

    private void updateStatus(String importId, String status, String actorId) {
        String sql = "UPDATE bundle_import_requests SET status=?, reviewed_by=?, reviewed_at=? WHERE import_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, actorId);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setString(4, importId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateStatus DB error importId={}", importId, e);
        }
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    private record ImportRequest(String importId, String bundleId, String uploaderId,
                                  String status, byte[] payload) {}

    public static class BundleSignatureException extends RuntimeException {
        public BundleSignatureException(String message) { super(message); }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record BundleSubmittedEvent(String importId, String bundleId,
                                        String uploaderId, Instant submittedAt) {}
    public record BundleApprovedEvent(String importId, String bundleId,
                                       String approverId, Instant approvedAt) {}
    public record BundleRejectedEvent(String importId, String reviewerId,
                                       String reason, Instant rejectedAt) {}
}
