package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose ML-based classification of dead-letter messages. Extracts features from
 *              message metadata (topic, error type, payload size, retry count, time of
 *              day) and calls an ML inference endpoint via ClassifierPort to predict:
 *              ROOT_CAUSE category, suggested ACTION (RETRY / TRANSFORM / DISCARD /
 *              INVESTIGATE), and a CONFIDENCE score. High-confidence classifications
 *              are applied automatically; low-confidence ones are flagged for human
 *              review. Satisfies STORY-K19-014.
 * @doc.layer   Kernel
 * @doc.pattern ML classification via port; auto-apply on high confidence; human review
 *              flag on low confidence; classified/autoApplied/humanFlagged Counters.
 */
public class MlDeadLetterClassifierService {

    private static final double AUTO_APPLY_THRESHOLD  = 0.90;
    private static final double HUMAN_REVIEW_THRESHOLD = 0.60;

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final ClassifierPort    classifierPort;
    private final AuditPort         auditPort;
    private final Counter           classifiedCounter;
    private final Counter           autoAppliedCounter;
    private final Counter           humanFlaggedCounter;

    public MlDeadLetterClassifierService(HikariDataSource dataSource, Executor executor,
                                          ClassifierPort classifierPort,
                                          AuditPort auditPort,
                                          MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.classifierPort      = classifierPort;
        this.auditPort           = auditPort;
        this.classifiedCounter   = Counter.builder("dlq.ml_classifier.classified_total").register(registry);
        this.autoAppliedCounter  = Counter.builder("dlq.ml_classifier.auto_applied_total").register(registry);
        this.humanFlaggedCounter = Counter.builder("dlq.ml_classifier.human_flagged_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** ML inference endpoint for DLQ classification. */
    public interface ClassifierPort {
        ClassificationResult classify(Map<String, Object> features);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum SuggestedAction { RETRY, TRANSFORM, DISCARD, INVESTIGATE }

    public record ClassificationResult(
        String rootCause, SuggestedAction suggestedAction,
        double confidence, Map<String, Double> featureImportance
    ) {}

    public record DlqClassification(
        String classificationId, String deadLetterId,
        ClassificationResult result,
        boolean autoApplied, boolean humanReviewRequired,
        Instant classifiedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Classify a dead-letter message using ML. If confidence ≥ AUTO_APPLY_THRESHOLD,
     * applies the suggested action automatically. If < HUMAN_REVIEW_THRESHOLD, flags
     * for human review.
     */
    public Promise<DlqClassification> classify(String deadLetterId) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Object> features = extractFeatures(deadLetterId);
            ClassificationResult result  = classifierPort.classify(features);

            String classificationId = UUID.randomUUID().toString();
            boolean autoApplied     = result.confidence() >= AUTO_APPLY_THRESHOLD;
            boolean humanRequired   = result.confidence() < HUMAN_REVIEW_THRESHOLD;

            if (autoApplied) {
                applyClassificationAction(deadLetterId, result.suggestedAction(), classificationId);
                autoAppliedCounter.increment();
            } else if (humanRequired) {
                flagForHumanReview(deadLetterId, classificationId);
                humanFlaggedCounter.increment();
            }

            persistClassification(classificationId, deadLetterId, result, autoApplied, humanRequired);
            classifiedCounter.increment();

            auditPort.log("DLQ_ML_CLASSIFIED", "DeadLetter", deadLetterId,
                Map.of("classificationId", classificationId,
                        "rootCause", result.rootCause(),
                        "action", result.suggestedAction().name(),
                        "confidence", result.confidence(),
                        "autoApplied", autoApplied));

            return new DlqClassification(classificationId, deadLetterId, result,
                autoApplied, humanRequired, Instant.now());
        });
    }

    /**
     * Batch classify all unclassified DEAD messages.
     */
    public Promise<Integer> classifyPendingBatch(int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<String> unclassified = fetchUnclassifiedIds(limit);
            int count = 0;
            for (String id : unclassified) {
                try {
                    // Delegate to single-message path via extracted logic
                    Map<String, Object> features = extractFeatures(id);
                    ClassificationResult result  = classifierPort.classify(features);
                    String cId = UUID.randomUUID().toString();
                    boolean auto   = result.confidence() >= AUTO_APPLY_THRESHOLD;
                    boolean human  = result.confidence() < HUMAN_REVIEW_THRESHOLD;
                    if (auto)  { applyClassificationAction(id, result.suggestedAction(), cId); autoAppliedCounter.increment(); }
                    if (human) { flagForHumanReview(id, cId); humanFlaggedCounter.increment(); }
                    persistClassification(cId, id, result, auto, human);
                    classifiedCounter.increment();
                    count++;
                } catch (Exception e) {
                    // Continue with other messages; don't fail batch on single error
                    auditPort.log("DLQ_ML_CLASSIFY_ERROR", "DeadLetter", id,
                        Map.of("error", e.getMessage()));
                }
            }
            return count;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Map<String, Object> extractFeatures(String deadLetterId) throws SQLException {
        Map<String, Object> features = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT topic_name, error_type, error_category, LENGTH(payload) AS payload_size, " +
                 "retry_count, priority, EXTRACT(HOUR FROM captured_at) AS hour_of_day " +
                 "FROM dead_letters WHERE dead_letter_id = ?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    features.put("topic_name",    rs.getString("topic_name"));
                    features.put("error_type",    rs.getString("error_type"));
                    features.put("error_category", rs.getString("error_category"));
                    features.put("payload_size",  rs.getLong("payload_size"));
                    features.put("retry_count",   rs.getInt("retry_count"));
                    features.put("priority",      rs.getString("priority"));
                    features.put("hour_of_day",   rs.getDouble("hour_of_day"));
                }
            }
        }
        return features;
    }

    private void applyClassificationAction(String deadLetterId, SuggestedAction action,
                                            String classificationId) throws SQLException {
        String newStatus = switch (action) {
            case RETRY       -> "DEAD";  // Will be picked up by ScheduledAutoRetryService
            case TRANSFORM   -> "DEAD";  // Operator handles transform + replay
            case DISCARD     -> "DISCARDED";
            case INVESTIGATE -> "INVESTIGATING";
        };
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET status = ?, ml_classification_id = ? " +
                 "WHERE dead_letter_id = ?")) {
            ps.setString(1, newStatus);
            ps.setString(2, classificationId);
            ps.setString(3, deadLetterId);
            ps.executeUpdate();
        }
    }

    private void flagForHumanReview(String deadLetterId, String classificationId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET ml_human_review_required = TRUE, " +
                 "ml_classification_id = ? WHERE dead_letter_id = ?")) {
            ps.setString(1, classificationId);
            ps.setString(2, deadLetterId);
            ps.executeUpdate();
        }
    }

    private void persistClassification(String classificationId, String deadLetterId,
                                        ClassificationResult result, boolean autoApplied,
                                        boolean humanRequired) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dlq_ml_classifications " +
                 "(classification_id, dead_letter_id, root_cause, suggested_action, " +
                 "confidence, auto_applied, human_review_required, classified_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, classificationId);
            ps.setString(2, deadLetterId);
            ps.setString(3, result.rootCause());
            ps.setString(4, result.suggestedAction().name());
            ps.setDouble(5, result.confidence());
            ps.setBoolean(6, autoApplied);
            ps.setBoolean(7, humanRequired);
            ps.executeUpdate();
        }
    }

    private List<String> fetchUnclassifiedIds(int limit) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dead_letter_id FROM dead_letters " +
                 "WHERE status = 'DEAD' AND ml_classification_id IS NULL " +
                 "ORDER BY captured_at ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("dead_letter_id"));
            }
        }
        return ids;
    }
}
