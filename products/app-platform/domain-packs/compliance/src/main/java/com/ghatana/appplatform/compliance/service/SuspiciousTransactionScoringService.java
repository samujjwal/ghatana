package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Scores each trade against a Suspicious Transaction Report (STR) model
 *                (XGBoost, K-09 TIER_3 supervised). Scores 0–1; trades scoring ≥ 0.7 are
 *                automatically escalated to the STR case queue. SHAP explanations are stored
 *                per transaction. False-positive flags feed back into the model training pipeline.
 * @doc.layer     Application
 * @doc.pattern   K-09 supervised ML with SHAP + human feedback loop
 *
 * Story: D07-016
 */
public class SuspiciousTransactionScoringService {

    private static final Logger log = LoggerFactory.getLogger(SuspiciousTransactionScoringService.class);
    private static final double STR_AUTO_ESCALATION_THRESHOLD = 0.70;
    private static final double STR_REVIEW_THRESHOLD          = 0.40;

    private final StrModelPort     strModelPort;
    private final DataSource       dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter          strAutoEscalated;
    private final Counter          strFalsePositiveFlagged;

    public SuspiciousTransactionScoringService(StrModelPort strModelPort,
                                                DataSource dataSource,
                                                Consumer<Object> eventPublisher,
                                                MeterRegistry meterRegistry) {
        this.strModelPort          = strModelPort;
        this.dataSource            = dataSource;
        this.eventPublisher        = eventPublisher;
        this.strAutoEscalated      = meterRegistry.counter("compliance.str.auto_escalated");
        this.strFalsePositiveFlagged = meterRegistry.counter("compliance.str.false_positive");
    }

    /**
     * Scores a completed transaction for suspicious activity.
     *
     * @param transactionId  unique trade/order execution identifier
     * @param clientId       trading client
     * @param instrumentId   instrument
     * @param features       feature vector (velocity, unusual_counterparty, round_amount,
     *                       time_of_day_score, peer_deviation, instrument_mismatch)
     * @return score record
     */
    public StrScore score(String transactionId, String clientId, String instrumentId,
                           Map<String, Double> features) {

        StrModelResult result = strModelPort.score(features);
        double score = result.score();
        Map<String, Double> shap = result.shapContributions();

        String caseId = null;
        if (score >= STR_AUTO_ESCALATION_THRESHOLD) {
            caseId = createStrCase(transactionId, clientId, instrumentId, score, shap);
            strAutoEscalated.increment();
            log.warn("STR auto-escalated txn={} client={} score={}", transactionId, clientId, score);
            eventPublisher.accept(new StrAutoEscalatedEvent(caseId, transactionId, clientId,
                    instrumentId, score, shap));
        } else if (score >= STR_REVIEW_THRESHOLD) {
            log.info("STR review-required txn={} client={} score={}", transactionId, clientId, score);
            eventPublisher.accept(new StrReviewRequiredEvent(transactionId, clientId, instrumentId, score));
        }

        saveScore(transactionId, clientId, instrumentId, score, shap, caseId);
        return new StrScore(transactionId, clientId, instrumentId, score, shap, caseId, Instant.now());
    }

    /**
     * Records a false-positive flag from the compliance officer. Feeds back to model training.
     *
     * @param transactionId   the scored transaction
     * @param reviewerId      officer flagging the false positive
     * @param justification   free-text justification
     */
    public void recordFalsePositive(String transactionId, String reviewerId, String justification) {
        String sql = "INSERT INTO str_feedback(transaction_id, reviewer_id, label, justification, flagged_at) "
                   + "VALUES(?,?,'FALSE_POSITIVE',?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, reviewerId);
            ps.setString(3, justification);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("recordFalsePositive DB error txn={}", transactionId, e);
        }
        strFalsePositiveFlagged.increment();
        log.info("STR false-positive recorded txn={} reviewer={}", transactionId, reviewerId);
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private String createStrCase(String transactionId, String clientId, String instrumentId,
                                  double score, Map<String, Double> shap) {
        String caseId = UUID.randomUUID().toString();
        String sql = "INSERT INTO suspicious_transaction_reports"
                   + "(case_id, transaction_id, client_id, instrument_id, score, shap_json, status, created_at) "
                   + "VALUES(?,?,?,?,?,?::jsonb,'OPEN',?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ps.setString(2, transactionId);
            ps.setString(3, clientId);
            ps.setString(4, instrumentId);
            ps.setDouble(5, score);
            ps.setString(6, shapToJson(shap));
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create STR case txn=" + transactionId, e);
        }
        return caseId;
    }

    private void saveScore(String transactionId, String clientId, String instrumentId,
                            double score, Map<String, Double> shap, String caseId) {
        String sql = "INSERT INTO str_scores(transaction_id, client_id, instrument_id, score, "
                   + "shap_json, str_case_id, scored_at) VALUES(?,?,?,?,?::jsonb,?,?) "
                   + "ON CONFLICT (transaction_id) DO NOTHING";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, clientId);
            ps.setString(3, instrumentId);
            ps.setDouble(4, score);
            ps.setString(5, shapToJson(shap));
            ps.setString(6, caseId);
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveScore DB error txn={}", transactionId, e);
        }
    }

    /** Minimal JSON serialisation for SHAP map — avoids pulling in full JSON library. */
    private String shapToJson(Map<String, Double> shap) {
        StringBuilder sb = new StringBuilder("{");
        shap.forEach((k, v) -> sb.append("\"").append(k).append("\":").append(v).append(","));
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface StrModelPort {
        StrModelResult score(Map<String, Double> features);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record StrModelResult(double score, Map<String, Double> shapContributions) {}

    public record StrScore(String transactionId, String clientId, String instrumentId,
                            double score, Map<String, Double> shapContributions,
                            String strCaseId, Instant scoredAt) {
        public boolean isAutoEscalated() { return strCaseId != null; }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record StrAutoEscalatedEvent(String caseId, String transactionId, String clientId,
                                         String instrumentId, double score,
                                         Map<String, Double> shapContributions) {}
    public record StrReviewRequiredEvent(String transactionId, String clientId,
                                          String instrumentId, double score) {}
}
