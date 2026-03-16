package com.ghatana.appplatform.surveillance.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose ML-based anomaly detection for trading patterns. Feature engineering:
 *              trade volume, price impact, order frequency, cancel ratio, time-of-day,
 *              instrument volatility. K-09 TIER_2 unsupervised Isolation Forest / Autoencoder.
 *              Anomaly score 0-1; &gt;0.8 = alert. SHAP explainability for governance.
 *              HITL for HIGH anomaly scores. Satisfies STORY-D08-009.
 * @doc.layer   Domain
 * @doc.pattern K-09 TIER_2 AI governance; Isolation Forest scoring; SHAP features;
 *              daily aggregated features; Counter for HIGH anomaly alerts.
 */
public class TradingAnomalyDetectionService {

    private static final double HIGH_ANOMALY_THRESHOLD = 0.80;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AiModelPort      aiModelPort;
    private final AlertPort        alertPort;
    private final Counter          anomalyCounter;

    public TradingAnomalyDetectionService(HikariDataSource dataSource, Executor executor,
                                           AiModelPort aiModelPort, AlertPort alertPort,
                                           MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.aiModelPort   = aiModelPort;
        this.alertPort     = alertPort;
        this.anomalyCounter = Counter.builder("surveillance.anomaly.high_alerts_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-09 TIER_2 supervised/unsupervised model port. */
    public interface AiModelPort {
        ScoringResult score(TradingFeatures features);
    }

    public interface AlertPort {
        String persistAlert(String clientId, String instrumentId, String alertType,
                            String description, String severity, LocalDate runDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    /** 6 features per K-09 TIER_2 governance. */
    public record TradingFeatures(String clientId, String instrumentId, LocalDate runDate,
                                   double tradeVolume, double priceImpact, double orderFrequency,
                                   double cancelRatio, double timeOfDayBias, double instrumentVolatility) {}

    public record ScoringResult(double anomalyScore, List<ShapFeature> shapFeatures) {}

    public record ShapFeature(String featureName, double shapValue) {}

    public record AnomalyAlert(String alertId, String clientId, String instrumentId,
                                LocalDate runDate, double anomalyScore,
                                List<ShapFeature> shapFeatures, String severity) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<AnomalyAlert>> runDailyScoring(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> scoreAll(runDate));
    }

    public Promise<ScoringResult> scoreClient(String clientId, String instrumentId, LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            TradingFeatures features = buildFeatures(clientId, instrumentId, runDate);
            return aiModelPort.score(features);
        });
    }

    // ─── Core scoring ────────────────────────────────────────────────────────

    private List<AnomalyAlert> scoreAll(LocalDate runDate) throws SQLException {
        List<AnomalyAlert> alerts = new ArrayList<>();
        List<TradingFeatures> allFeatures = loadAllFeatures(runDate);

        for (TradingFeatures features : allFeatures) {
            ScoringResult result = aiModelPort.score(features);
            String severity = result.anomalyScore() >= HIGH_ANOMALY_THRESHOLD ? "HIGH"
                    : result.anomalyScore() >= 0.60 ? "MEDIUM" : null;

            if (severity != null) {
                String topShap = result.shapFeatures().isEmpty() ? "unknown"
                        : result.shapFeatures().get(0).featureName();
                String desc = "Anomaly score=%.3f, top driver=%s".formatted(
                        result.anomalyScore(), topShap);
                String alertId = alertPort.persistAlert(features.clientId(), features.instrumentId(),
                        "ML_ANOMALY", desc, severity, runDate);
                persistPrediction(features.clientId(), features.instrumentId(), runDate,
                        result.anomalyScore(), topShap, alertId);

                if ("HIGH".equals(severity)) anomalyCounter.increment();
                alerts.add(new AnomalyAlert(alertId, features.clientId(), features.instrumentId(),
                        runDate, result.anomalyScore(), result.shapFeatures(), severity));
            }
        }
        return alerts;
    }

    private TradingFeatures buildFeatures(String clientId, String instrumentId, LocalDate runDate)
            throws SQLException {
        String sql = """
                SELECT SUM(t.quantity)::double precision AS trade_volume,
                       AVG(ABS(p_after.price - p_before.price) / NULLIF(p_before.price, 0)) AS price_impact,
                       COUNT(o.order_id)::double precision / NULLIF(SUM(t.quantity), 0) AS order_freq,
                       COUNT(CASE WHEN o.status='CANCELLED' THEN 1 END)::double precision /
                           NULLIF(COUNT(o.order_id), 0) AS cancel_ratio,
                       AVG(EXTRACT(HOUR FROM t.trade_at) + EXTRACT(MINUTE FROM t.trade_at)/60.0) AS tod,
                       COALESCE(iv.volatility, 0) AS instrument_vol
                FROM trades t
                LEFT JOIN orders o ON o.client_id = t.client_id AND o.instrument_id = t.instrument_id
                    AND DATE(o.submitted_at) = ?
                LEFT JOIN instrument_prices_intraday p_before ON p_before.instrument_id = t.instrument_id
                    AND p_before.price_at <= t.trade_at ORDER BY p_before.price_at DESC LIMIT 1
                LEFT JOIN instrument_prices_intraday p_after ON p_after.instrument_id = t.instrument_id
                    AND p_after.price_at > t.trade_at ORDER BY p_after.price_at ASC LIMIT 1
                LEFT JOIN instrument_volatility iv ON iv.instrument_id = t.instrument_id
                    AND iv.calc_date = ?
                WHERE t.client_id = ? AND t.instrument_id = ? AND t.trade_date = ?
                GROUP BY iv.volatility
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setObject(2, runDate);
            ps.setString(3, clientId);
            ps.setString(4, instrumentId);
            ps.setObject(5, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TradingFeatures(clientId, instrumentId, runDate,
                            rs.getDouble("trade_volume"), rs.getDouble("price_impact"),
                            rs.getDouble("order_freq"), rs.getDouble("cancel_ratio"),
                            rs.getDouble("tod"), rs.getDouble("instrument_vol"));
                }
            }
        }
        return new TradingFeatures(clientId, instrumentId, runDate, 0, 0, 0, 0, 0, 0);
    }

    private List<TradingFeatures> loadAllFeatures(LocalDate runDate) throws SQLException {
        List<String[]> pairs = new ArrayList<>();
        String sql = "SELECT DISTINCT client_id, instrument_id FROM trades WHERE trade_date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pairs.add(new String[]{rs.getString(1), rs.getString(2)});
            }
        }
        List<TradingFeatures> all = new ArrayList<>();
        for (String[] pair : pairs) {
            try { all.add(buildFeatures(pair[0], pair[1], runDate)); } catch (Exception ignored) {}
        }
        return all;
    }

    private void persistPrediction(String clientId, String instrumentId, LocalDate runDate,
                                    double score, String topShapFeature, String alertId)
            throws SQLException {
        String sql = """
                INSERT INTO trading_anomaly_scores
                    (score_id, client_id, instrument_id, run_date, anomaly_score, top_shap_feature, alert_id, scored_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (client_id, instrument_id, run_date) DO UPDATE
                SET anomaly_score = EXCLUDED.anomaly_score,
                    top_shap_feature = EXCLUDED.top_shap_feature,
                    alert_id = EXCLUDED.alert_id, scored_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, instrumentId);
            ps.setObject(3, runDate);
            ps.setDouble(4, score);
            ps.setString(5, topShapFeature);
            ps.setString(6, alertId);
            ps.executeUpdate();
        }
    }
}
