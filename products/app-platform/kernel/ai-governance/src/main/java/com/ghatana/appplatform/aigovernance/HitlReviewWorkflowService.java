package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Human-In-The-Loop (HITL) review workflow for AI predictions that require
 *              human validation. Review cases move through: PENDING_REVIEW → APPROVED /
 *              OVERRIDDEN / ESCALATED. Each case surfaces the model's SHAP explanation
 *              to the reviewer. SLA is configurable per model tier via K-02. Satisfies
 *              STORY-K09-012.
 * @doc.layer   Kernel
 * @doc.pattern HITL state machine; SHAP explanation surfacing via ShapPort; K-02 SLA config;
 *              SLA escalation; reviewsCreated/approved/overridden/escalated Counters;
 *              pendingCount Gauge.
 */
public class HitlReviewWorkflowService {

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final ShapPort          shapPort;
    private final SlaConfigPort     slaConfigPort;
    private final NotificationPort  notificationPort;
    private final Counter           reviewsCreatedCounter;
    private final Counter           reviewsApprovedCounter;
    private final Counter           reviewsOverriddenCounter;
    private final Counter           reviewsEscalatedCounter;
    private final AtomicLong        pendingCount = new AtomicLong(0);

    public HitlReviewWorkflowService(HikariDataSource dataSource, Executor executor,
                                      ShapPort shapPort,
                                      SlaConfigPort slaConfigPort,
                                      NotificationPort notificationPort,
                                      MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.shapPort                = shapPort;
        this.slaConfigPort           = slaConfigPort;
        this.notificationPort        = notificationPort;
        this.reviewsCreatedCounter   = Counter.builder("aigovernance.hitl.created_total").register(registry);
        this.reviewsApprovedCounter  = Counter.builder("aigovernance.hitl.approved_total").register(registry);
        this.reviewsOverriddenCounter = Counter.builder("aigovernance.hitl.overridden_total").register(registry);
        this.reviewsEscalatedCounter = Counter.builder("aigovernance.hitl.escalated_total").register(registry);
        Gauge.builder("aigovernance.hitl.pending_count", pendingCount, AtomicLong::get).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Fetches SHAP explanation for a specific prediction. */
    public interface ShapPort {
        Map<String, Double> getShapValues(String predictionId);
    }

    /** K-02 SLA configuration: look up SLA hours for a model tier. */
    public interface SlaConfigPort {
        int getReviewSlaHours(String modelTier);
    }

    /** Reviewer assignment and notification. */
    public interface NotificationPort {
        void notifyReviewer(String reviewerId, String caseId, String message);
        void notifyEscalation(String supervisorId, String caseId, String reason);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum ReviewStatus { PENDING_REVIEW, APPROVED, OVERRIDDEN, ESCALATED }

    public record HitlCase(
        String caseId, String predictionId, String modelId, String modelVersion,
        String modelTier, ReviewStatus status,
        Map<String, Double> shapValues,
        String reviewerId, String reviewNotes,
        Instant createdAt, Instant slaDueAt, Instant completedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Create a new HITL review case for a prediction that exceeds the review threshold.
     * Fetches SHAP values for the prediction and calculates SLA deadline from model tier config.
     */
    public Promise<HitlCase> createCase(String predictionId, String modelId, String version,
                                         String modelTier, String assignedReviewerId) {
        return Promise.ofBlocking(executor, () -> {
            String caseId    = UUID.randomUUID().toString();
            Instant now      = Instant.now();
            int slaHours     = slaConfigPort.getReviewSlaHours(modelTier);
            Instant slaDue   = now.plusSeconds((long) slaHours * 3600);

            Map<String, Double> shapValues = shapPort.getShapValues(predictionId);

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO hitl_review_cases " +
                     "(case_id, prediction_id, model_id, model_version, model_tier, status, " +
                     "reviewer_id, created_at, sla_due_at) " +
                     "VALUES (?, ?, ?, ?, ?, 'PENDING_REVIEW', ?, NOW(), ?)")) {
                ps.setString(1, caseId);
                ps.setString(2, predictionId);
                ps.setString(3, modelId);
                ps.setString(4, version);
                ps.setString(5, modelTier);
                ps.setString(6, assignedReviewerId);
                ps.setTimestamp(7, Timestamp.from(slaDue));
                ps.executeUpdate();
            }

            notificationPort.notifyReviewer(assignedReviewerId, caseId,
                "New HITL review case for model " + modelId + " v" + version +
                ". SLA: " + slaHours + " hours.");

            reviewsCreatedCounter.increment();
            pendingCount.incrementAndGet();

            return new HitlCase(caseId, predictionId, modelId, version, modelTier,
                ReviewStatus.PENDING_REVIEW, shapValues, assignedReviewerId,
                null, now, slaDue, null);
        });
    }

    /**
     * Approve the model's prediction as-is. Closes the case as APPROVED.
     */
    public Promise<HitlCase> approve(String caseId, String reviewerId, String notes) {
        return Promise.ofBlocking(executor, () -> {
            transitionCase(caseId, ReviewStatus.APPROVED, reviewerId, notes);
            reviewsApprovedCounter.increment();
            pendingCount.decrementAndGet();
            return fetchCase(caseId);
        });
    }

    /**
     * Override the model's prediction with the reviewer's decision.
     * Records the override decision and closes as OVERRIDDEN.
     */
    public Promise<HitlCase> override(String caseId, String reviewerId,
                                       String overrideDecision, String notes) {
        return Promise.ofBlocking(executor, () -> {
            String fullNotes = "OVERRIDE: " + overrideDecision + " | " + notes;
            transitionCase(caseId, ReviewStatus.OVERRIDDEN, reviewerId, fullNotes);
            reviewsOverriddenCounter.increment();
            pendingCount.decrementAndGet();
            return fetchCase(caseId);
        });
    }

    /**
     * Escalate the case when reviewer cannot decide; notifies a supervisor.
     */
    public Promise<HitlCase> escalate(String caseId, String reviewerId, String reason) {
        return Promise.ofBlocking(executor, () -> {
            transitionCase(caseId, ReviewStatus.ESCALATED, reviewerId, "ESCALATED: " + reason);
            reviewsEscalatedCounter.increment();
            // Fetch supervisor from model tier ownership; simplified to system notification
            notificationPort.notifyEscalation("ai-governance-supervisor", caseId, reason);
            return fetchCase(caseId);
        });
    }

    /**
     * Check for SLA breaches and escalate overdue pending cases.
     */
    public Promise<Integer> escalateSlaBreachers() {
        return Promise.ofBlocking(executor, () -> {
            List<String> overdueCaseIds = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT case_id FROM hitl_review_cases " +
                     "WHERE status = 'PENDING_REVIEW' AND sla_due_at < NOW()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) overdueCaseIds.add(rs.getString("case_id"));
                }
            }

            for (String caseId : overdueCaseIds) {
                transitionCase(caseId, ReviewStatus.ESCALATED, "system", "SLA breach auto-escalation");
                notificationPort.notifyEscalation("ai-governance-supervisor", caseId, "SLA exceeded");
                reviewsEscalatedCounter.increment();
                pendingCount.decrementAndGet();
            }

            return overdueCaseIds.size();
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void transitionCase(String caseId, ReviewStatus status,
                                 String reviewerId, String notes) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE hitl_review_cases SET status = ?, reviewer_id = ?, " +
                 "review_notes = ?, completed_at = NOW() WHERE case_id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, reviewerId);
            ps.setString(3, notes);
            ps.setString(4, caseId);
            ps.executeUpdate();
        }
    }

    private HitlCase fetchCase(String caseId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT prediction_id, model_id, model_version, model_tier, status, " +
                 "reviewer_id, review_notes, created_at, sla_due_at, completed_at " +
                 "FROM hitl_review_cases WHERE case_id = ?")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Case not found: " + caseId);
                Timestamp ct = rs.getTimestamp("completed_at");
                return new HitlCase(caseId,
                    rs.getString("prediction_id"),
                    rs.getString("model_id"),
                    rs.getString("model_version"),
                    rs.getString("model_tier"),
                    ReviewStatus.valueOf(rs.getString("status")),
                    Map.of(), // SHAP values not re-fetched on status reads
                    rs.getString("reviewer_id"),
                    rs.getString("review_notes"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("sla_due_at").toInstant(),
                    ct != null ? ct.toInstant() : null);
            }
        }
    }
}
