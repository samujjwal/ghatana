package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Plugin rating and review system backing the marketplace.
 *              Only tenants with an active installation can submit a review.
 *              Review lifecycle: PENDING → PUBLISHED (approved) or REJECTED (moderated).
 *              Developer response: plugin author can reply to a published review.
 *              Abuse reporting: any tenant can flag a review for re-moderation.
 *              Average rating recalculated on each publish/revoke.
 *              Review text anonymized on tenant offboarding.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-P01-007: Plugin rating and review system
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS plugin_reviews (
 *   review_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id      TEXT NOT NULL,
 *   tenant_id      TEXT NOT NULL,
 *   rating         SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
 *   review_text    TEXT NOT NULL,
 *   helpful_count  INT NOT NULL DEFAULT 0,
 *   status         TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED | REJECTED
 *   developer_response TEXT,
 *   abuse_flagged  BOOLEAN NOT NULL DEFAULT FALSE,
 *   created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   UNIQUE (plugin_id, tenant_id)
 * );
 * CREATE TABLE IF NOT EXISTS plugin_ratings_summary (
 *   plugin_id       TEXT PRIMARY KEY,
 *   total_reviews   INT NOT NULL DEFAULT 0,
 *   average_rating  NUMERIC(3,2) NOT NULL DEFAULT 0
 * );
 * </pre>
 */
public class PluginRatingReviewService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface InstallationCheckPort {
        /** Returns true if tenant has (or had) an active installation of the plugin. */
        boolean hasInstalled(String tenantId, String pluginId) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record Review(
        String reviewId, String pluginId, String tenantId, int rating,
        String reviewText, int helpfulCount, String status, String developerResponse
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final InstallationCheckPort installCheck;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter reviewsPublishedCounter;

    public PluginRatingReviewService(
        javax.sql.DataSource ds,
        InstallationCheckPort installCheck,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                       = ds;
        this.installCheck             = installCheck;
        this.audit                    = audit;
        this.executor                 = executor;
        this.reviewsPublishedCounter  = Counter.builder("certification.reviews.published").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Submit a review. Requires the tenant to have installed the plugin. Creates in PENDING. */
    public Promise<String> submit(String pluginId, String tenantId, int rating, String text) {
        return Promise.ofBlocking(executor, () -> {
            if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be 1-5");
            if (!installCheck.hasInstalled(tenantId, pluginId)) {
                throw new IllegalStateException("Only tenants with an installation may submit a review");
            }
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO plugin_reviews (plugin_id, tenant_id, rating, review_text) VALUES (?,?,?,?) RETURNING review_id"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, tenantId);
                ps.setInt(3, rating); ps.setString(4, text);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString("review_id"); }
            }
        });
    }

    /** Operator approves a review → PUBLISHED. Recalculates plugin average. */
    public Promise<Void> approve(String reviewId, String moderatorId) {
        return Promise.ofBlocking(executor, () -> {
            String pluginId = transitionStatus(reviewId, "PENDING", "PUBLISHED");
            recalculateRating(pluginId);
            reviewsPublishedCounter.increment();
            audit.record(moderatorId, "REVIEW_APPROVED", "reviewId=" + reviewId);
            return null;
        });
    }

    /** Operator rejects a review → REJECTED. */
    public Promise<Void> reject(String reviewId, String moderatorId) {
        return Promise.ofBlocking(executor, () -> {
            transitionStatus(reviewId, "PENDING", "REJECTED");
            audit.record(moderatorId, "REVIEW_REJECTED", "reviewId=" + reviewId);
            return null;
        });
    }

    /** Plugin developer responds to a PUBLISHED review. */
    public Promise<Void> developerRespond(String reviewId, String response, String developerId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE plugin_reviews SET developer_response=? WHERE review_id=? AND status='PUBLISHED'"
                 )) {
                ps.setString(1, response); ps.setString(2, reviewId); ps.executeUpdate();
            }
            audit.record(developerId, "REVIEW_DEVELOPER_RESPONSE", "reviewId=" + reviewId);
            return null;
        });
    }

    /** Flag a review for abuse → re-enters moderation queue. */
    public Promise<Void> reportAbuse(String reviewId, String reportedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE plugin_reviews SET abuse_flagged=TRUE, status='PENDING' WHERE review_id=? AND status='PUBLISHED'"
                 )) {
                ps.setString(1, reviewId); ps.executeUpdate();
            }
            audit.record(reportedBy, "REVIEW_ABUSE_REPORTED", "reviewId=" + reviewId);
            return null;
        });
    }

    /** List published reviews for a plugin with pagination. */
    public Promise<List<Review>> listPublished(String pluginId, int page, int pageSize) {
        return Promise.ofBlocking(executor, () -> {
            List<Review> reviews = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT review_id, plugin_id, tenant_id, rating, review_text, helpful_count, status, developer_response " +
                     "FROM plugin_reviews WHERE plugin_id=? AND status='PUBLISHED' " +
                     "ORDER BY helpful_count DESC, created_at DESC LIMIT ? OFFSET ?"
                 )) {
                ps.setString(1, pluginId); ps.setInt(2, pageSize); ps.setInt(3, page * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        reviews.add(new Review(rs.getString("review_id"), rs.getString("plugin_id"),
                            rs.getString("tenant_id"), rs.getInt("rating"), rs.getString("review_text"),
                            rs.getInt("helpful_count"), rs.getString("status"), rs.getString("developer_response")));
                    }
                }
            }
            return reviews;
        });
    }

    /** Return current average rating and review count for a plugin. */
    public Promise<Map<String, Object>> getRatingSummary(String pluginId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT total_reviews, average_rating FROM plugin_ratings_summary WHERE plugin_id=?"
                 )) {
                ps.setString(1, pluginId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Map.of("totalReviews", 0, "averageRating", 0.0);
                    return Map.of("totalReviews", rs.getInt("total_reviews"),
                        "averageRating", rs.getDouble("average_rating"));
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String transitionStatus(String reviewId, String from, String to) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE plugin_reviews SET status=? WHERE review_id=? AND status=? RETURNING plugin_id"
             )) {
            ps.setString(1, to); ps.setString(2, reviewId); ps.setString(3, from);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Review not in expected status " + from);
                return rs.getString("plugin_id");
            }
        }
    }

    private void recalculateRating(String pluginId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO plugin_ratings_summary (plugin_id, total_reviews, average_rating) " +
                 "SELECT plugin_id, COUNT(*)::int, ROUND(AVG(rating)::numeric, 2) FROM plugin_reviews " +
                 "WHERE plugin_id=? AND status='PUBLISHED' GROUP BY plugin_id " +
                 "ON CONFLICT (plugin_id) DO UPDATE SET total_reviews=EXCLUDED.total_reviews, average_rating=EXCLUDED.average_rating"
             )) {
            ps.setString(1, pluginId); ps.executeUpdate();
        }
    }
}
