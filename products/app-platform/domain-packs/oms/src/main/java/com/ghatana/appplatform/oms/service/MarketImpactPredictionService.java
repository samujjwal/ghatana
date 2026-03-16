package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   ML-powered pre-trade market impact prediction. Predicts expected market impact
 *                (basis points) before order placement using a gradient-boosted regressor
 *                governed by K-09 AI governance. High-impact orders trigger maker-checker escalation.
 * @doc.layer     Application
 * @doc.pattern   K-09 Advisory Model invocation via MlModelPort
 *
 * Features used by model: order_size÷ADV, instrument_volatility (σ), bid-ask spread,
 * L2 book depth, hour-of-day, recent participation rate.
 *
 * Escalation: if predicted_impact > configured threshold → trigger maker-checker review.
 * Model governance: trained nightly via K-09, SHAP explainability included.
 *
 * Story: D01-019
 */
public class MarketImpactPredictionService {

    private static final Logger log = LoggerFactory.getLogger(MarketImpactPredictionService.class);
    private static final double DEFAULT_IMPACT_THRESHOLD_BPS = 50.0;

    private final MlModelPort      modelPort;
    private final MarketInputPort  marketPort;
    private final Consumer<Object> eventPublisher;
    private final double           impactThresholdBps;
    private final Counter          escalations;
    private final Timer            predictionLatency;

    public MarketImpactPredictionService(MlModelPort modelPort,
                                          MarketInputPort marketPort,
                                          Consumer<Object> eventPublisher,
                                          double impactThresholdBps,
                                          MeterRegistry meterRegistry) {
        this.modelPort          = modelPort;
        this.marketPort         = marketPort;
        this.eventPublisher     = eventPublisher;
        this.impactThresholdBps = impactThresholdBps;
        this.escalations        = meterRegistry.counter("oms.impact.escalations");
        this.predictionLatency  = meterRegistry.timer("oms.impact.prediction.latency");
    }

    /**
     * Predicts market impact for an order and triggers escalation if above threshold.
     *
     * @param orderId      order being evaluated
     * @param clientId     client identifier
     * @param instrumentId instrument
     * @param side         "BUY" or "SELL"
     * @param quantity     order quantity
     * @param price        order price
     * @return prediction result with impact estimate and escalation flag
     */
    public ImpactPrediction predict(String orderId, String clientId, String instrumentId,
                                     String side, long quantity, BigDecimal price) {

        MarketContext ctx = marketPort.getMarketContext(instrumentId);

        ImpactPrediction prediction = predictionLatency.record(() ->
                modelPort.predictImpact(
                        instrumentId, side, quantity, price,
                        ctx.adv(), ctx.volatility(), ctx.bidAskSpread(),
                        ctx.l2Depth(), ctx.hourOfDay(), ctx.participationRate()));

        log.info("ImpactPrediction: orderId={} instrument={} impactBps={} confidence={}",
                orderId, instrumentId, prediction.impactBps(), prediction.confidence());

        if (prediction.impactBps() > impactThresholdBps) {
            escalations.increment();
            log.info("ImpactPrediction: escalating orderId={} impactBps={} > threshold={}",
                    orderId, prediction.impactBps(), impactThresholdBps);
            eventPublisher.accept(new HighImpactOrderEscalatedEvent(
                    orderId, clientId, instrumentId, prediction.impactBps(),
                    impactThresholdBps, prediction.shapExplanation()));
        }

        return prediction;
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface MlModelPort {
        ImpactPrediction predictImpact(String instrumentId, String side, long quantity,
                                       BigDecimal price, long adv, double volatility,
                                       double bidAskSpread, int l2Depth,
                                       int hourOfDay, double participationRate);
    }

    public interface MarketInputPort {
        MarketContext getMarketContext(String instrumentId);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record MarketContext(long adv, double volatility, double bidAskSpread,
                                 int l2Depth, int hourOfDay, double participationRate) {}

    public record ImpactPrediction(double impactBps, double confidenceLower,
                                    double confidenceUpper, String shapExplanation) {
        public double confidence() { return confidenceUpper - confidenceLower; }
        public boolean requiresEscalation(double threshold) { return impactBps > threshold; }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record HighImpactOrderEscalatedEvent(String orderId, String clientId, String instrumentId,
                                                double impactBps, double threshold, String shapExplanation) {}
}
