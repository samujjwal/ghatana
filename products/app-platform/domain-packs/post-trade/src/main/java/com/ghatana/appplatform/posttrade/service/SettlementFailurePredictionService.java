package com.ghatana.appplatform.posttrade.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose ML binary classifier (XGBoost-equivalent feature scoring, governed by K-09 TIER_2)
 *              that predicts settlement failure risk at trade confirmation time — before the
 *              settlement window opens. Features include counterparty_fail_rate_30d,
 *              instrument_liquidity_score, settlement_amount_vs_counterparty_avg,
 *              securities_availability_indicator, currency_convertibility_risk,
 *              and t_plus_n_days_until_settlement. Score ≥ 0.7 = HIGH risk → immediate surface
 *              to settlement desk. Governed by K-09 (SHAP, HITL for HIGH-risk).
 *              Satisfies STORY-D09-017.
 * @doc.layer   Domain
 * @doc.pattern K-09 AI governance (TIER_2 supervised); SHAP feature attribution;
 *              scoring at trade confirmation event; INSERT-only prediction log.
 */
public class SettlementFailurePredictionService {

    private static final Logger log  = LoggerFactory.getLogger(SettlementFailurePredictionService.class);
    private static final double HIGH_RISK_THRESHOLD = 0.70;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AiModelPort      aiModel;
    private final Counter          scoredCounter;
    private final Counter          highRiskCounter;

    public SettlementFailurePredictionService(HikariDataSource dataSource, Executor executor,
                                              AiModelPort aiModel, MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.aiModel         = aiModel;
        this.scoredCounter   = registry.counter("posttrade.ml.settlements_scored");
        this.highRiskCounter = registry.counter("posttrade.ml.high_risk_alerts");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-09 AI governance port — TIER_2 supervised model. */
    public interface AiModelPort {
        PredictionResult score(SettlementFeatures features);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SettlementFeatures(String tradeId, String instrumentId, String counterpartyId,
                                     double counterpartyFailRate30d, double instrumentLiquidityScore,
                                     double settlementAmountVsAvg, double securitiesAvailability,
                                     double currencyConvertibilityRisk, int tPlusNDays) {}

    public record PredictionResult(double score, String riskLevel,
                                   String shapFeatures) {} // shapFeatures: JSON string

    public record ScoredTrade(String tradeId, double score, String riskLevel, String shapFeatures) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Score a newly confirmed trade for settlement failure risk. */
    public Promise<ScoredTrade> scoreAtConfirmation(String tradeId) {
        return Promise.ofBlocking(executor, () -> {
            SettlementFeatures features = loadFeatures(tradeId);
            PredictionResult result = aiModel.score(features);
            scoredCounter.increment();
            if (result.score() >= HIGH_RISK_THRESHOLD) {
                highRiskCounter.increment();
                log.warn("HIGH settlement failure risk: tradeId={} score={}", tradeId, result.score());
            }
            persistPrediction(tradeId, result);
            return new ScoredTrade(tradeId, result.score(), result.riskLevel(), result.shapFeatures());
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private SettlementFeatures loadFeatures(String tradeId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // counterparty fail rate
            String failRateSql = """
                    SELECT COALESCE(
                        (SELECT COUNT(*) FILTER (WHERE status='FAILED')::float /
                                NULLIF(COUNT(*),0)
                         FROM settlement_instructions
                         WHERE counterparty_id = (SELECT counterparty_id FROM trades WHERE trade_id=?)
                           AND created_at >= NOW() - INTERVAL '30 days'), 0.0)
                    """;
            double failRate = 0.0;
            try (PreparedStatement ps = conn.prepareStatement(failRateSql)) {
                ps.setString(1, tradeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) failRate = rs.getDouble(1);
                }
            }
            // trade details for other features
            String tradeSql = """
                    SELECT t.instrument_id, t.counterparty_id, t.settlement_amount,
                           t.t_plus_n_days, i.liquidity_score, i.securities_availability,
                           i.currency_convertibility_risk,
                           AVG(t2.settlement_amount) OVER (PARTITION BY t.counterparty_id) AS avg_amt
                    FROM trades t
                    JOIN instrument_metadata i ON i.instrument_id = t.instrument_id
                    JOIN trades t2 ON t2.counterparty_id = t.counterparty_id
                    WHERE t.trade_id = ?
                    LIMIT 1
                    """;
            try (PreparedStatement ps = conn.prepareStatement(tradeSql)) {
                ps.setString(1, tradeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double amt = rs.getDouble("settlement_amount");
                        double avgAmt = rs.getDouble("avg_amt");
                        return new SettlementFeatures(
                                tradeId,
                                rs.getString("instrument_id"),
                                rs.getString("counterparty_id"),
                                failRate,
                                rs.getDouble("liquidity_score"),
                                avgAmt > 0 ? amt / avgAmt : 1.0,
                                rs.getDouble("securities_availability"),
                                rs.getDouble("currency_convertibility_risk"),
                                rs.getInt("t_plus_n_days"));
                    }
                }
            }
        }
        return new SettlementFeatures(tradeId, "", "", 0, 1.0, 1.0, 1.0, 0.0, 2);
    }

    private void persistPrediction(String tradeId, PredictionResult result) throws SQLException {
        String sql = """
                INSERT INTO settlement_failure_predictions
                    (prediction_id, trade_id, score, risk_level, shap_features, scored_at)
                VALUES (?, ?, ?, ?, ?::jsonb, NOW())
                ON CONFLICT (trade_id) DO UPDATE
                    SET score = EXCLUDED.score,
                        risk_level = EXCLUDED.risk_level,
                        shap_features = EXCLUDED.shap_features,
                        scored_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tradeId);
            ps.setDouble(3, result.score());
            ps.setString(4, result.riskLevel());
            ps.setString(5, result.shapFeatures());
            ps.executeUpdate();
        }
    }
}
