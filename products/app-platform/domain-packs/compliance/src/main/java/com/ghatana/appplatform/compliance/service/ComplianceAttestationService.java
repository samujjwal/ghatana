package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Manages periodic compliance attestations (annual, quarterly). Staff must
 *                attest having read the compliance policy. Lifecycle:
 *                PENDING → SIGNED → REVIEWED → EXPIRED. Escalation for overdue attestations.
 *                Triggered by K-05 event scheduler.
 * @doc.layer     Application
 * @doc.pattern   Workflow lifecycle with escalation
 *
 * Story: D07-013
 */
public class ComplianceAttestationService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAttestationService.class);

    private static final int  ESCALATION_DAYS    = 3;
    private final AtomicInteger pendingCount = new AtomicInteger(0);

    private final DataSource       dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter          attestationsSigned;
    private final Counter          attestationsExpired;

    public ComplianceAttestationService(DataSource dataSource,
                                         Consumer<Object> eventPublisher,
                                         MeterRegistry meterRegistry) {
        this.dataSource          = dataSource;
        this.eventPublisher      = eventPublisher;
        this.attestationsSigned  = meterRegistry.counter("compliance.attestations.signed");
        this.attestationsExpired = meterRegistry.counter("compliance.attestations.expired");
        Gauge.builder("compliance.attestations.pending", pendingCount, AtomicInteger::get)
             .register(meterRegistry);
    }

    /**
     * Creates an attestation obligation for a user. Called by K-05 scheduler.
     *
     * @param userId         employee / advisor identifier
     * @param policyVersion  compliance policy version to attest
     * @param frequency      "ANNUAL" | "QUARTERLY"
     * @param dueDays        calendar days until deadline
     */
    public String createAttestation(String userId, String policyVersion,
                                     String frequency, int dueDays) {
        String attestationId = UUID.randomUUID().toString();
        Instant dueAt = Instant.now().plus(dueDays, ChronoUnit.DAYS);

        String sql = "INSERT INTO compliance_attestations"
                   + "(attestation_id, user_id, policy_version, frequency, status, due_at, created_at) "
                   + "VALUES(?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, attestationId);
            ps.setString(2, userId);
            ps.setString(3, policyVersion);
            ps.setString(4, frequency);
            ps.setString(5, "PENDING");
            ps.setTimestamp(6, Timestamp.from(dueAt));
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create attestation for " + userId, e);
        }
        pendingCount.incrementAndGet();
        log.info("Attestation created={} user={} dueAt={}", attestationId, userId, dueAt);
        eventPublisher.accept(new AttestationCreatedEvent(attestationId, userId, policyVersion, dueAt));
        return attestationId;
    }

    /**
     * Records a user's signature on the attestation.
     *
     * @param attestationId  obligation to sign
     * @param userId         signing user
     * @param ipAddress      client IP for audit trail (validated at API layer)
     */
    public void sign(String attestationId, String userId, String ipAddress) {
        String sql = "UPDATE compliance_attestations "
                   + "SET status='SIGNED', signed_at=?, signed_ip=? "
                   + "WHERE attestation_id=? AND user_id=? AND status='PENDING'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, ipAddress);
            ps.setString(3, attestationId);
            ps.setString(4, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                log.warn("sign: no pending attestation id={} user={}", attestationId, userId);
                return;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sign attestation " + attestationId, e);
        }
        pendingCount.decrementAndGet();
        attestationsSigned.increment();
        log.info("Attestation signed={} user={}", attestationId, userId);
        eventPublisher.accept(new AttestationSignedEvent(attestationId, userId, Instant.now()));
    }

    /**
     * Compliance officer reviews and approves a signed attestation.
     */
    public void review(String attestationId, String reviewerId) {
        String sql = "UPDATE compliance_attestations SET status='REVIEWED', reviewer_id=?, reviewed_at=? "
                   + "WHERE attestation_id=? AND status='SIGNED'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reviewerId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, attestationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to review attestation " + attestationId, e);
        }
        eventPublisher.accept(new AttestationReviewedEvent(attestationId, reviewerId, Instant.now()));
    }

    /**
     * Scheduled daily: escalates attestations past their deadline, expires very overdue ones.
     */
    public void processOverdue() {
        Instant now = Instant.now();
        Instant escalateAfter = now.minus(ESCALATION_DAYS, ChronoUnit.DAYS);

        String escalateSql = "SELECT attestation_id, user_id, due_at FROM compliance_attestations "
                           + "WHERE status='PENDING' AND due_at < ? AND due_at >= ?";
        String expireSql   = "UPDATE compliance_attestations SET status='EXPIRED', expired_at=? "
                           + "WHERE status='PENDING' AND due_at < ?";

        try (Connection c = dataSource.getConnection()) {
            // Escalate those 0–ESCALATION_DAYS overdue
            try (PreparedStatement ps = c.prepareStatement(escalateSql)) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setTimestamp(2, Timestamp.from(escalateAfter));
                List<OverdueEntry> entries = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new OverdueEntry(rs.getString("attestation_id"),
                                rs.getString("user_id"),
                                rs.getTimestamp("due_at").toInstant()));
                    }
                }
                for (OverdueEntry e : entries) {
                    log.warn("Attestation overdue escalation id={} user={}", e.id(), e.userId());
                    eventPublisher.accept(new AttestationOverdueEvent(e.id(), e.userId(), e.dueAt()));
                }
            }
            // Expire those more than ESCALATION_DAYS overdue
            try (PreparedStatement ps = c.prepareStatement(expireSql)) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setTimestamp(2, Timestamp.from(escalateAfter));
                int expired = ps.executeUpdate();
                if (expired > 0) {
                    attestationsExpired.increment(expired);
                    log.warn("Expired {} overdue attestations", expired);
                }
            }
        } catch (SQLException e) {
            log.error("processOverdue: DB error", e);
        }
    }

    // ─── private record ───────────────────────────────────────────────────────

    private record OverdueEntry(String id, String userId, Instant dueAt) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record AttestationCreatedEvent(String attestationId, String userId,
                                          String policyVersion, Instant dueAt) {}
    public record AttestationSignedEvent(String attestationId, String userId, Instant signedAt) {}
    public record AttestationReviewedEvent(String attestationId, String reviewerId, Instant reviewedAt) {}
    public record AttestationOverdueEvent(String attestationId, String userId, Instant dueAt) {}
}
