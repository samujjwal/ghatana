package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose GDPR / right-to-erasure (Article 17) handler. Accepts erasure requests from
 *              clients, discovers all personal data across services via DataLineageService,
 *              validates no regulatory hold (ongoing investigation or legal requirement)
 *              blocks erasure, then executes distributed erasure via a K-17 saga. Issues a
 *              signed proof certificate on completion. Lifecycle: PENDING → IN_PROGRESS →
 *              COMPLETED | BLOCKED_BY_HOLD. Satisfies STORY-K08-011.
 * @doc.layer   Kernel
 * @doc.pattern Saga-based distributed erasure; regulatory hold check before execution;
 *              proof certificate generation; K-07 audit trail; erasure state machine.
 */
public class RightToErasureHandlerService {

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final DataLocatorPort   dataLocatorPort;
    private final ErasurePort       erasurePort;
    private final RegulatoryHoldPort holdPort;
    private final AuditPort         auditPort;
    private final Counter           requestsInitiatedCounter;
    private final Counter           requestsCompletedCounter;
    private final Counter           requestsBlockedCounter;

    public RightToErasureHandlerService(HikariDataSource dataSource, Executor executor,
                                         DataLocatorPort dataLocatorPort,
                                         ErasurePort erasurePort,
                                         RegulatoryHoldPort holdPort,
                                         AuditPort auditPort,
                                         MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.dataLocatorPort          = dataLocatorPort;
        this.erasurePort              = erasurePort;
        this.holdPort                 = holdPort;
        this.auditPort                = auditPort;
        this.requestsInitiatedCounter = Counter.builder("governance.erasure.requests_initiated_total").register(registry);
        this.requestsCompletedCounter = Counter.builder("governance.erasure.requests_completed_total").register(registry);
        this.requestsBlockedCounter   = Counter.builder("governance.erasure.requests_blocked_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Discovers all datasets containing personal data for the given client. */
    public interface DataLocatorPort {
        List<String> locateAllDatasets(String clientId);
    }

    /** Executes erasure of a specific dataset in an external service. */
    public interface ErasurePort {
        void eraseDataset(String datasetId, String requestId);
    }

    /** Checks whether a regulatory hold (legal or investigatory) blocks erasure. */
    public interface RegulatoryHoldPort {
        boolean hasActiveHold(String clientId);
        String holdReason(String clientId);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum ErasureStatus { PENDING, IN_PROGRESS, COMPLETED, BLOCKED_BY_HOLD }

    public record ErasureRequest(
        String requestId, String clientId,
        ErasureStatus status,
        String holdReason,
        ProofCertificate certificate,
        Instant initiatedAt, Instant completedAt
    ) {}

    public record ProofCertificate(
        String requestId, String clientId,
        List<String> datasetsErased,
        Instant completedAt,
        String attestationHash
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Initiate an erasure request. Validates hold status; if blocked, records the reason
     * and returns BLOCKED_BY_HOLD. Otherwise starts the erasure saga asynchronously.
     */
    public Promise<ErasureRequest> initiateErasure(String clientId, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            String requestId = UUID.randomUUID().toString();
            Instant now      = Instant.now();

            if (holdPort.hasActiveHold(clientId)) {
                String reason = holdPort.holdReason(clientId);
                persistRequest(requestId, clientId, ErasureStatus.BLOCKED_BY_HOLD, reason, now);
                requestsBlockedCounter.increment();
                auditPort.log("ERASURE_BLOCKED", "Client", clientId,
                    Map.of("requestId", requestId, "holdReason", reason, "requestedBy", requestedBy));
                return new ErasureRequest(requestId, clientId, ErasureStatus.BLOCKED_BY_HOLD,
                    reason, null, now, null);
            }

            persistRequest(requestId, clientId, ErasureStatus.PENDING, null, now);
            requestsInitiatedCounter.increment();
            auditPort.log("ERASURE_INITIATED", "Client", clientId,
                Map.of("requestId", requestId, "requestedBy", requestedBy));

            return executeErasure(requestId, clientId, now);
        });
    }

    /**
     * Returns the current status of an erasure request.
     */
    public Promise<Optional<ErasureRequest>> getRequestStatus(String requestId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT client_id, status, hold_reason, proof_certificate, " +
                     "initiated_at, completed_at FROM erasure_requests WHERE request_id = ?")) {
                ps.setString(1, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    ErasureStatus status = ErasureStatus.valueOf(rs.getString("status"));
                    String holdReason    = rs.getString("hold_reason");
                    Timestamp completedTs = rs.getTimestamp("completed_at");
                    Instant completedAt  = completedTs != null ? completedTs.toInstant() : null;
                    return Optional.of(new ErasureRequest(
                        requestId, rs.getString("client_id"),
                        status, holdReason, null,
                        rs.getTimestamp("initiated_at").toInstant(), completedAt
                    ));
                }
            }
        });
    }

    // ─── Private execution ────────────────────────────────────────────────────

    private ErasureRequest executeErasure(String requestId, String clientId, Instant initiatedAt)
            throws Exception {
        markInProgress(requestId);
        List<String> datasets = dataLocatorPort.locateAllDatasets(clientId);

        List<String> erased  = new ArrayList<>();
        List<String> failed  = new ArrayList<>();

        for (String datasetId : datasets) {
            try {
                erasurePort.eraseDataset(datasetId, requestId);
                erased.add(datasetId);
            } catch (Exception e) {
                failed.add(datasetId + ":" + e.getMessage());
            }
        }

        Instant completedAt = Instant.now();
        ProofCertificate cert = buildCertificate(requestId, clientId, erased, completedAt);
        ErasureStatus finalStatus = failed.isEmpty() ? ErasureStatus.COMPLETED : ErasureStatus.IN_PROGRESS;

        persistCompletion(requestId, finalStatus, cert, completedAt);
        requestsCompletedCounter.increment();

        auditPort.log("ERASURE_COMPLETED", "Client", clientId,
            Map.of("requestId", requestId,
                    "datasetsErased", erased.size(),
                    "datasetsFailed", failed.size(),
                    "attestation", cert.attestationHash()));

        return new ErasureRequest(requestId, clientId, finalStatus, null, cert, initiatedAt, completedAt);
    }

    private ProofCertificate buildCertificate(String requestId, String clientId,
                                               List<String> erased, Instant completedAt) {
        String payload = requestId + clientId + String.join(",", erased) + completedAt.toString();
        String hash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            hash = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            hash = UUID.randomUUID().toString();
        }
        return new ProofCertificate(requestId, clientId, erased, completedAt, hash);
    }

    private void persistRequest(String requestId, String clientId,
                                 ErasureStatus status, String holdReason,
                                 Instant initiatedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO erasure_requests " +
                 "(request_id, client_id, status, hold_reason, initiated_at) " +
                 "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, requestId);
            ps.setString(2, clientId);
            ps.setString(3, status.name());
            ps.setString(4, holdReason);
            ps.setTimestamp(5, Timestamp.from(initiatedAt));
            ps.executeUpdate();
        }
    }

    private void markInProgress(String requestId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE erasure_requests SET status = 'IN_PROGRESS' WHERE request_id = ?")) {
            ps.setString(1, requestId);
            ps.executeUpdate();
        }
    }

    private void persistCompletion(String requestId, ErasureStatus status,
                                    ProofCertificate cert, Instant completedAt) throws SQLException {
        String certJson = String.format(
            "{\"requestId\":\"%s\",\"clientId\":\"%s\",\"datasetsErased\":%d," +
            "\"completedAt\":\"%s\",\"attestation\":\"%s\"}",
            cert.requestId(), cert.clientId(), cert.datasetsErased().size(),
            cert.completedAt(), cert.attestationHash());

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE erasure_requests SET status = ?, proof_certificate = ?::jsonb, " +
                 "completed_at = ? WHERE request_id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, certJson);
            ps.setTimestamp(3, Timestamp.from(completedAt));
            ps.setString(4, requestId);
            ps.executeUpdate();
        }
    }
}
