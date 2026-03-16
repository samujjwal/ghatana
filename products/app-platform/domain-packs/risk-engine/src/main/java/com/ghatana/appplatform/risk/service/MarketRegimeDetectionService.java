package com.ghatana.appplatform.risk.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Detects current market regime (LOW_VOL, HIGH_VOL, CRISIS) using a Hidden Markov
 *              Model via inner port — governs tail-risk parameters in VaR (D06-020, K-09 advisory).
 * @doc.layer   Domain — risk analytics (AI advisory)
 * @doc.pattern K-09 AI Governance — model is advisory only; HITL override via recordOverride()
 */
public class MarketRegimeDetectionService {

    public enum MarketRegime { LOW_VOLATILITY, HIGH_VOLATILITY, CRISIS }

    /** Inner port: wraps the HMM model (deployed externally via K-09 AI registry). */
    public interface HmmModelPort {
        /**
         * Infer current regime probabilities from recent market features.
         * @param features map of feature names to values (volatility, returns, liquidity)
         * @return HmmResult with regime, transition probabilities, and SHAP factors
         */
        HmmResult infer(Map<String, Double> features);

        record HmmResult(MarketRegime regime, double[] transitionProbabilities,
                         Map<String, Double> shapFactors) {}
    }

    public record RegimeSnapshot(
        MarketRegime regime,
        double[] transitionProbabilities,
        Map<String, Double> shapFactors,
        String modelId,
        Instant detectedAt,
        boolean isAdvisory   // always true — human must confirm before use in regulatory stress reports
    ) {}

    public record RegimeChangedEvent(MarketRegime previous, MarketRegime current,
                                      Map<String, Double> shapFactors, Instant changedAt) {}

    private final HmmModelPort hmmModel;
    private final DataSource dataSource;
    private final Executor executor;
    private final Consumer<RegimeChangedEvent> eventPublisher;
    private final Counter overrideCounter;
    private final Timer inferenceTimer;
    private volatile MarketRegime lastRegime = null;

    public MarketRegimeDetectionService(HmmModelPort hmmModel, DataSource dataSource,
                                         Executor executor, Consumer<RegimeChangedEvent> eventPublisher,
                                         MeterRegistry registry) {
        this.hmmModel = hmmModel;
        this.dataSource = dataSource;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.overrideCounter = Counter.builder("risk.regime.overrides_total").register(registry);
        this.inferenceTimer = Timer.builder("risk.regime.inference_latency")
            .description("HMM regime inference latency").register(registry);
    }

    /**
     * Advisory: detect current market regime. Always marks result as advisory.
     * Emits RegimeChangedEvent if regime transitions from previous detection.
     */
    public Promise<RegimeSnapshot> detectRegime(String marketId) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample s = Timer.start();
            Map<String, Double> features = loadMarketFeatures(marketId);
            HmmModelPort.HmmResult result = hmmModel.infer(features);
            s.stop(inferenceTimer);

            RegimeSnapshot snapshot = new RegimeSnapshot(result.regime(),
                result.transitionProbabilities(), result.shapFactors(), "hmm-v1",
                Instant.now(), true);

            persistSnapshot(marketId, snapshot);

            if (lastRegime != null && lastRegime != result.regime()) {
                eventPublisher.accept(new RegimeChangedEvent(lastRegime, result.regime(),
                    result.shapFactors(), Instant.now()));
            }
            lastRegime = result.regime();
            return snapshot;
        });
    }

    /** HITL override: risk officer manually sets the regime with justification. */
    public Promise<Void> recordOverride(String marketId, MarketRegime overrideRegime,
                                         String reviewerId, String justification) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO regime_overrides(market_id, regime, reviewer_id, justification, overridden_at) " +
                     "VALUES(?,?,?,?,NOW())")) {
                ps.setString(1, marketId);
                ps.setString(2, overrideRegime.name());
                ps.setObject(3, UUID.fromString(reviewerId));
                ps.setString(4, justification);
                ps.executeUpdate();
            }
            lastRegime = overrideRegime;
            overrideCounter.increment();
            return null;
        });
    }

    private Map<String, Double> loadMarketFeatures(String marketId) throws Exception {
        // Load rolling 5-day volatility, returns, bid-ask spread, volume ratio from market data
        String sql = "SELECT avg_daily_vol, avg_return, avg_spread, volume_ratio " +
                     "FROM market_feature_snapshots WHERE market_id = ? ORDER BY snapshot_date DESC LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, marketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Map.of(
                        "volatility",    rs.getDouble("avg_daily_vol"),
                        "return",        rs.getDouble("avg_return"),
                        "spread",        rs.getDouble("avg_spread"),
                        "volume_ratio",  rs.getDouble("volume_ratio"));
                }
            }
        }
        return Map.of("volatility", 0.0, "return", 0.0, "spread", 0.0, "volume_ratio", 1.0);
    }

    private void persistSnapshot(String marketId, RegimeSnapshot snapshot) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO market_regime_snapshots(market_id, regime, model_id, detected_at) " +
                 "VALUES(?,?,?,?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, marketId);
            ps.setString(2, snapshot.regime().name());
            ps.setString(3, snapshot.modelId());
            ps.setObject(4, snapshot.detectedAt());
            ps.executeUpdate();
        }
    }
}
