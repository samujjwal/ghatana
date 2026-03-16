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
 * @doc.purpose AI-powered recommendations for resolving dead-letter messages. Analyses
 *              historical resolution patterns, ML classification results, and current
 *              message metadata to generate ranked resolution recommendations. Each
 *              recommendation includes: action type, estimated success probability,
 *              rationale, and confidence. Used by operators to make informed replay /
 *              transform / discard decisions. Satisfies STORY-K19-015.
 * @doc.layer   Kernel
 * @doc.pattern Historical pattern analysis; similarity scoring; ranked recommendations;
 *              RecommendationEnginePort for LLM/ML; recommendationsGenerated Counter.
 */
public class AiDlqRecommendationService {

    private final HikariDataSource            dataSource;
    private final Executor                    executor;
    private final RecommendationEnginePort    recommendationEnginePort;
    private final Counter                     recommendationsGeneratedCounter;

    public AiDlqRecommendationService(HikariDataSource dataSource, Executor executor,
                                       RecommendationEnginePort recommendationEnginePort,
                                       MeterRegistry registry) {
        this.dataSource                     = dataSource;
        this.executor                       = executor;
        this.recommendationEnginePort       = recommendationEnginePort;
        this.recommendationsGeneratedCounter = Counter.builder("dlq.ai_recommendations.generated_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** ML/LLM-based recommendation engine. */
    public interface RecommendationEnginePort {
        List<Recommendation> generateRecommendations(Map<String, Object> context);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum RecommendedAction { REPLAY_AS_IS, TRANSFORM_AND_REPLAY, DISCARD, ROUTE_TO_TEAM, INVESTIGATE }

    public record Recommendation(
        RecommendedAction action, double successProbability,
        double confidence, String rationale, String configHint
    ) {}

    public record MessageRecommendations(
        String recommendationSetId, String deadLetterId,
        List<Recommendation> ranked,
        Map<String, Object> historicalContext,
        Instant generatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Generate ranked recommendations for a specific dead-letter message.
     * Enriches context with historical resolution data for similar messages.
     */
    public Promise<MessageRecommendations> recommend(String deadLetterId) {
        return Promise.ofBlocking(executor, () -> {
            String recommendationSetId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            Map<String, Object> messageContext   = fetchMessageContext(deadLetterId);
            Map<String, Object> historicalContext = fetchHistoricalContext(
                (String) messageContext.get("topic_name"),
                (String) messageContext.get("error_type")
            );

            Map<String, Object> fullContext = new LinkedHashMap<>(messageContext);
            fullContext.putAll(historicalContext);

            List<Recommendation> recommendations = recommendationEnginePort.generateRecommendations(fullContext);

            // Sort by success probability descending
            recommendations.sort(Comparator.comparingDouble(Recommendation::successProbability).reversed());

            persistRecommendations(recommendationSetId, deadLetterId, recommendations, now);
            recommendationsGeneratedCounter.increment();

            return new MessageRecommendations(recommendationSetId, deadLetterId,
                recommendations, historicalContext, now);
        });
    }

    /**
     * Retrieve stored recommendations for a message (e.g. to show in the operations UI).
     */
    public Promise<List<Map<String, Object>>> getStoredRecommendations(String deadLetterId) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT recommendation_set_id, recommended_action, success_probability, " +
                     "confidence, rationale, config_hint, rank, generated_at " +
                     "FROM dlq_ai_recommendations WHERE dead_letter_id = ? " +
                     "ORDER BY generated_at DESC, rank ASC LIMIT 20")) {
                ps.setString(1, deadLetterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("setId",               rs.getString("recommendation_set_id"));
                        row.put("action",              rs.getString("recommended_action"));
                        row.put("successProbability",  rs.getDouble("success_probability"));
                        row.put("confidence",          rs.getDouble("confidence"));
                        row.put("rationale",           rs.getString("rationale"));
                        row.put("configHint",          rs.getString("config_hint"));
                        row.put("rank",                rs.getInt("rank"));
                        row.put("generatedAt",         rs.getTimestamp("generated_at").toInstant().toString());
                        results.add(row);
                    }
                }
            }
            return results;
        });
    }

    /**
     * Record operator feedback on a recommendation to improve future models.
     */
    public Promise<Void> recordFeedback(String recommendationSetId, String deadLetterId,
                                         int chosenRank, boolean wasSuccessful,
                                         String operatorNotes) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dlq_recommendation_feedback " +
                     "(recommendation_set_id, dead_letter_id, chosen_rank, was_successful, " +
                     "operator_notes, recorded_at) VALUES (?, ?, ?, ?, ?, NOW()) " +
                     "ON CONFLICT (recommendation_set_id) DO UPDATE SET " +
                     "chosen_rank = EXCLUDED.chosen_rank, was_successful = EXCLUDED.was_successful")) {
                ps.setString(1, recommendationSetId);
                ps.setString(2, deadLetterId);
                ps.setInt(3, chosenRank);
                ps.setBoolean(4, wasSuccessful);
                ps.setString(5, operatorNotes);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Map<String, Object> fetchMessageContext(String deadLetterId) throws SQLException {
        Map<String, Object> ctx = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT topic_name, error_type, error_category, priority, retry_count, " +
                 "LENGTH(payload) AS payload_size, ml_classification_id, is_poison_pill " +
                 "FROM dead_letters WHERE dead_letter_id = ?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ctx.put("dead_letter_id",       deadLetterId);
                    ctx.put("topic_name",            rs.getString("topic_name"));
                    ctx.put("error_type",            rs.getString("error_type"));
                    ctx.put("error_category",        rs.getString("error_category"));
                    ctx.put("priority",              rs.getString("priority"));
                    ctx.put("retry_count",           rs.getInt("retry_count"));
                    ctx.put("payload_size",          rs.getLong("payload_size"));
                    ctx.put("ml_classification_id",  rs.getString("ml_classification_id"));
                    ctx.put("is_poison_pill",        rs.getBoolean("is_poison_pill"));
                }
            }
        }
        return ctx;
    }

    private Map<String, Object> fetchHistoricalContext(String topicName,
                                                        String errorType) throws SQLException {
        Map<String, Object> ctx = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT " +
                 "COUNT(*) AS total_similar, " +
                 "COUNT(*) FILTER (WHERE status = 'RESOLVED') AS resolved_count, " +
                 "AVG(retry_count) FILTER (WHERE status = 'RESOLVED') AS avg_retries_to_resolve, " +
                 "MODE() WITHIN GROUP (ORDER BY discard_reason) FILTER (WHERE status = 'DISCARDED') AS common_discard_reason " +
                 "FROM dead_letters WHERE topic_name = ? AND error_type = ? " +
                 "AND captured_at > NOW() - INTERVAL '30 days'")) {
            ps.setString(1, topicName);
            ps.setString(2, errorType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ctx.put("total_similar",             rs.getLong("total_similar"));
                    ctx.put("resolved_count",            rs.getLong("resolved_count"));
                    ctx.put("avg_retries_to_resolve",    rs.getDouble("avg_retries_to_resolve"));
                    ctx.put("common_discard_reason",     rs.getString("common_discard_reason"));
                    long total = rs.getLong("total_similar");
                    long resolved = rs.getLong("resolved_count");
                    ctx.put("historical_resolution_rate", total > 0 ? 100.0 * resolved / total : 0.0);
                }
            }
        }
        return ctx;
    }

    private void persistRecommendations(String setId, String deadLetterId,
                                         List<Recommendation> recs, Instant generatedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dlq_ai_recommendations " +
                 "(recommendation_set_id, dead_letter_id, recommended_action, success_probability, " +
                 "confidence, rationale, config_hint, rank, generated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < recs.size(); i++) {
                Recommendation r = recs.get(i);
                ps.setString(1, setId);
                ps.setString(2, deadLetterId);
                ps.setString(3, r.action().name());
                ps.setDouble(4, r.successProbability());
                ps.setDouble(5, r.confidence());
                ps.setString(6, r.rationale());
                ps.setString(7, r.configHint());
                ps.setInt(8, i + 1);
                ps.setTimestamp(9, Timestamp.from(generatedAt));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
