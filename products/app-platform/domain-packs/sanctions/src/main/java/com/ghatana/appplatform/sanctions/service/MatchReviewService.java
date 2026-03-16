package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Manages the sanctions match review queue. Operates a CONFIRM/DISMISS/ESCALATE
 *                decision workflow with maker-checker controls for CONFIRM decisions to prevent
 *                unilateral blocking of legitimate clients.
 * @doc.layer     Application
 * @doc.pattern   Four-eyes / maker-checker on CONFIRM; event-driven state machine
 *
 * Story: D14-007
 */
public class MatchReviewService {

    private static final Logger log = LoggerFactory.getLogger(MatchReviewService.class);

    private final DataSource       dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter          matchesConfirmed;
    private final Counter          matchesDismissed;
    private final Counter          matchesEscalated;

    public MatchReviewService(DataSource dataSource,
                               Consumer<Object> eventPublisher,
                               MeterRegistry meterRegistry) {
        this.dataSource       = dataSource;
        this.eventPublisher   = eventPublisher;
        this.matchesConfirmed = meterRegistry.counter("sanctions.match.confirmed");
        this.matchesDismissed = meterRegistry.counter("sanctions.match.dismissed");
        this.matchesEscalated = meterRegistry.counter("sanctions.match.escalated");
    }

    /**
     * Returns the open match review queue, ordered by match score descending.
     *
     * @param limit   max items to return (capped at 100)
     */
    public List<MatchQueueItem> getQueue(int limit) {
        int safeLimit = Math.min(limit, 100);
        String sql = "SELECT review_id, client_id, entity_ref, match_score, match_algorithm, status, "
                   + "created_at, first_reviewer_id, second_reviewer_id "
                   + "FROM sanctions_match_reviews "
                   + "WHERE status IN ('PENDING','PENDING_SECOND_APPROVAL') "
                   + "ORDER BY match_score DESC LIMIT ?";
        List<MatchQueueItem> items = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new MatchQueueItem(rs.getString("review_id"), rs.getString("client_id"),
                            rs.getString("entity_ref"), rs.getDouble("match_score"),
                            rs.getString("match_algorithm"), rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            log.error("getQueue DB error", e);
        }
        return items;
    }

    /**
     * First reviewer action: CONFIRM (requires second approval), DISMISS, or ESCALATE.
     *
     * @param reviewId   match review identifier
     * @param reviewerId first reviewer
     * @param decision   CONFIRM | DISMISS | ESCALATE
     * @param notes      mandatory justification
     */
    public void review(String reviewId, String reviewerId, String decision, String notes) {
        String newStatus = switch (decision) {
            case "CONFIRM"  -> "PENDING_SECOND_APPROVAL";
            case "DISMISS"  -> "DISMISSED";
            case "ESCALATE" -> "ESCALATED";
            default         -> throw new IllegalArgumentException("Unknown decision: " + decision);
        };

        String sql = "UPDATE sanctions_match_reviews "
                   + "SET status=?, first_reviewer_id=?, first_reviewer_notes=?, first_reviewed_at=? "
                   + "WHERE review_id=? AND status='PENDING'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, reviewerId);
            ps.setString(3, notes);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setString(5, reviewId);
            int rows = ps.executeUpdate();
            if (rows == 0) { log.warn("review: no pending review id={}", reviewId); return; }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to review match " + reviewId, e);
        }

        if ("DISMISS".equals(decision))   matchesDismissed.increment();
        if ("ESCALATE".equals(decision))  matchesEscalated.increment();

        eventPublisher.accept(new MatchReviewedEvent(reviewId, reviewerId, decision, newStatus, Instant.now()));
        log.info("MatchReview id={} decision={} newStatus={}", reviewId, decision, newStatus);
    }

    /**
     * Second reviewer confirms a CONFIRM decision (maker-checker).
     * The second reviewer must be different from the first.
     */
    public void secondApprove(String reviewId, String secondReviewerId, String notes) {
        // Prevent same person approving twice
        String checkSql = "SELECT first_reviewer_id FROM sanctions_match_reviews "
                        + "WHERE review_id=? AND status='PENDING_SECOND_APPROVAL'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(checkSql)) {
            ps.setString(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { log.warn("secondApprove: review not in PENDING_SECOND_APPROVAL state id={}", reviewId); return; }
                String firstReviewer = rs.getString("first_reviewer_id");
                if (firstReviewer.equals(secondReviewerId)) {
                    throw new IllegalStateException("Maker-checker violation: same reviewer id=" + reviewId);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("secondApprove check failed " + reviewId, e);
        }

        String sql = "UPDATE sanctions_match_reviews "
                   + "SET status='CONFIRMED', second_reviewer_id=?, second_reviewer_notes=?, confirmed_at=? "
                   + "WHERE review_id=? AND status='PENDING_SECOND_APPROVAL'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, secondReviewerId);
            ps.setString(2, notes);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setString(4, reviewId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to second-approve match " + reviewId, e);
        }

        matchesConfirmed.increment();
        eventPublisher.accept(new MatchConfirmedEvent(reviewId, secondReviewerId, Instant.now()));
        log.warn("MatchReview CONFIRMED (dual approval) id={} approver2={}", reviewId, secondReviewerId);
    }

    /**
     * Creates a new match review record when a high-confidence hit is found during screening.
     *
     * @return the new reviewId
     */
    public String createReview(String clientId, String entityRef, double matchScore, String matchAlgorithm) {
        String reviewId = UUID.randomUUID().toString();
        String sql = "INSERT INTO sanctions_match_reviews"
                   + "(review_id, client_id, entity_ref, match_score, match_algorithm, status, created_at) "
                   + "VALUES(?,?,?,?,?,'PENDING',?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reviewId);
            ps.setString(2, clientId);
            ps.setString(3, entityRef);
            ps.setDouble(4, matchScore);
            ps.setString(5, matchAlgorithm);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create review for " + clientId, e);
        }
        eventPublisher.accept(new MatchReviewCreatedEvent(reviewId, clientId, entityRef, matchScore));
        return reviewId;
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record MatchQueueItem(String reviewId, String clientId, String entityRef,
                                  double matchScore, String matchAlgorithm, String status,
                                  Instant createdAt) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record MatchReviewCreatedEvent(String reviewId, String clientId,
                                          String entityRef, double matchScore) {}
    public record MatchReviewedEvent(String reviewId, String reviewerId, String decision,
                                     String newStatus, Instant reviewedAt) {}
    public record MatchConfirmedEvent(String reviewId, String secondReviewerId, Instant confirmedAt) {}
}
