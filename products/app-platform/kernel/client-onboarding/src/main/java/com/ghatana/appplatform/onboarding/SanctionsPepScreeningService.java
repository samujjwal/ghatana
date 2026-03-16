package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Screen onboarding clients against sanctions and PEP lists via D-14 Sanctions Engine.
 *              Fuzzy name matching. BLOCK workflow on hit → notify compliance → require manual review/override.
 *              Clear → proceed. Enroll post-approval in continuous monitoring.
 * @doc.layer   Client Onboarding (W-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W02-005: Sanctions and PEP screening at onboarding
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS onboarding_sanctions_screenings (
 *   screening_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id       TEXT NOT NULL,
 *   client_id         TEXT NOT NULL,
 *   full_name         TEXT NOT NULL,
 *   date_of_birth     DATE,
 *   nationality       TEXT,
 *   outcome           TEXT NOT NULL,  -- CLEAR | HIT | PEP_MATCH | REVIEW_REQUIRED
 *   match_score       INT,
 *   matched_list      TEXT,
 *   matched_entity    TEXT,
 *   override_by       TEXT,
 *   override_at       TIMESTAMPTZ,
 *   override_reason   TEXT,
 *   screened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class SanctionsPepScreeningService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface SanctionsEnginePort {
        SanctionsHit screen(ScreeningRequest request) throws Exception;
    }

    public interface ComplianceNotificationPort {
        void notifyHit(String instanceId, String clientId, SanctionsHit hit) throws Exception;
    }

    public interface MonitoringEnrollmentPort {
        void enroll(String clientId, String enrollmentType) throws Exception; // ONGOING_SANCTIONS
    }

    public interface WorkflowBlockPort {
        void blockInstance(String instanceId, String reason) throws Exception;
        void unblockInstance(String instanceId) throws Exception;
    }

    public interface AuditPort {
        void log(String eventType, String instanceId, String clientId, Map<String, Object> detail) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum ScreeningOutcome { CLEAR, HIT, PEP_MATCH, REVIEW_REQUIRED }

    public record ScreeningRequest(
        String clientId, String fullName, String dateOfBirth, String nationality, List<String> aliases
    ) {}

    public record SanctionsHit(
        boolean hit, boolean pepMatch, String matchedList, String matchedEntity,
        int matchScore, String matchType
    ) {}

    public record ScreeningResult(
        String screeningId,
        String instanceId,
        ScreeningOutcome outcome,
        SanctionsHit hit,
        boolean workflowBlocked
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final SanctionsEnginePort sanctionsEngine;
    private final ComplianceNotificationPort complianceNotification;
    private final MonitoringEnrollmentPort monitoringEnrollment;
    private final WorkflowBlockPort workflowBlock;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter screenedCounter;
    private final Counter hitCounter;
    private final Counter clearCounter;
    private final Counter overrideCounter;

    private static final int FUZZY_MATCH_BLOCK_THRESHOLD = 85; // ≥85% = HIT

    public SanctionsPepScreeningService(
        javax.sql.DataSource ds,
        SanctionsEnginePort sanctionsEngine,
        ComplianceNotificationPort complianceNotification,
        MonitoringEnrollmentPort monitoringEnrollment,
        WorkflowBlockPort workflowBlock,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                     = ds;
        this.sanctionsEngine        = sanctionsEngine;
        this.complianceNotification = complianceNotification;
        this.monitoringEnrollment   = monitoringEnrollment;
        this.workflowBlock          = workflowBlock;
        this.audit                  = audit;
        this.executor               = executor;
        this.screenedCounter = Counter.builder("onboarding.sanctions.screened").register(registry);
        this.hitCounter      = Counter.builder("onboarding.sanctions.hits").register(registry);
        this.clearCounter    = Counter.builder("onboarding.sanctions.clear").register(registry);
        this.overrideCounter = Counter.builder("onboarding.sanctions.overrides").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Screen a client during onboarding. Blocks the workflow instance on a hit
     * and notifies compliance for manual review.
     */
    public Promise<ScreeningResult> screen(String instanceId, ScreeningRequest request) {
        return Promise.ofBlocking(executor, () -> {
            SanctionsHit hit = sanctionsEngine.screen(request);
            screenedCounter.increment();

            ScreeningOutcome outcome;
            if (hit.hit() && hit.matchScore() >= FUZZY_MATCH_BLOCK_THRESHOLD) {
                outcome = ScreeningOutcome.HIT;
            } else if (hit.pepMatch()) {
                outcome = ScreeningOutcome.PEP_MATCH;
            } else if (hit.hit() && hit.matchScore() > 0) {
                outcome = ScreeningOutcome.REVIEW_REQUIRED;
            } else {
                outcome = ScreeningOutcome.CLEAR;
            }

            // Persist
            String screeningId = persistScreening(instanceId, request, hit, outcome);

            boolean blocked = false;
            if (outcome == ScreeningOutcome.HIT || outcome == ScreeningOutcome.PEP_MATCH) {
                workflowBlock.blockInstance(instanceId, outcome.name() + ": " + hit.matchedEntity());
                complianceNotification.notifyHit(instanceId, request.clientId(), hit);
                hitCounter.increment();
                blocked = true;
            } else {
                clearCounter.increment();
            }

            audit.log("SANCTIONS_SCREENING", instanceId, request.clientId(),
                Map.of("outcome", outcome.name(), "matchScore", hit.matchScore()));

            return new ScreeningResult(screeningId, instanceId, outcome, hit, blocked);
        });
    }

    /**
     * Compliance officer overrides a HIT/PEP_MATCH result, unblocking the workflow.
     */
    public Promise<Void> override(String screeningId, String complianceOfficerId, String reason) {
        return Promise.ofBlocking(executor, () -> {
            String instanceId = null;
            String clientId = null;

            try (Connection c = ds.getConnection();
                 PreparedStatement sel = c.prepareStatement(
                     "UPDATE onboarding_sanctions_screenings SET override_by=?, override_at=NOW(), override_reason=? WHERE screening_id=? RETURNING instance_id, client_id"
                 )) {
                sel.setString(1, complianceOfficerId);
                sel.setString(2, reason);
                sel.setString(3, screeningId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        instanceId = rs.getString("instance_id");
                        clientId   = rs.getString("client_id");
                    }
                }
            }

            if (instanceId != null) {
                workflowBlock.unblockInstance(instanceId);
                overrideCounter.increment();
                audit.log("SANCTIONS_OVERRIDE", instanceId, clientId,
                    Map.of("by", complianceOfficerId, "reason", reason));
            }
            return null;
        });
    }

    /**
     * Enroll an approved client in continuous sanctions monitoring post-onboarding.
     */
    public Promise<Void> enrollInContinuousMonitoring(String clientId) {
        return Promise.ofBlocking(executor, () -> {
            monitoringEnrollment.enroll(clientId, "ONGOING_SANCTIONS");
            audit.log("MONITORING_ENROLLED", null, clientId, Map.of("type", "ONGOING_SANCTIONS"));
            return null;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String persistScreening(String instanceId, ScreeningRequest req, SanctionsHit hit, ScreeningOutcome outcome) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO onboarding_sanctions_screenings " +
                 "(instance_id, client_id, full_name, date_of_birth, nationality, outcome, match_score, matched_list, matched_entity) " +
                 "VALUES (?,?,?,?::date,?,?,?,?,?) RETURNING screening_id"
             )) {
            ps.setString(1, instanceId);
            ps.setString(2, req.clientId());
            ps.setString(3, req.fullName());
            ps.setString(4, req.dateOfBirth());
            ps.setString(5, req.nationality());
            ps.setString(6, outcome.name());
            ps.setInt(7, hit.matchScore());
            ps.setString(8, hit.matchedList());
            ps.setString(9, hit.matchedEntity());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }
}
