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
 * @doc.purpose   Advisory AI model that recommends optimal order type and execution strategy.
 *                Suggests LIMIT, MARKET, ICEBERG, TWAP, or VWAP based on real-time market
 *                conditions. Advisory only — human makes final decision. Governed by K-09.
 * @doc.layer     Application
 * @doc.pattern   K-09 Advisory Model invocation — recommendation + audit log via K-07
 *
 * Features: urgency_flag, order_size_ratio vs ADV, realized_spread, current_volume_pace,
 * time_to_close, instrument_sector.
 *
 * All recommendations logged via K-07 for audit. Human override captured and logged.
 *
 * Story: D01-020
 */
public class OrderStrategyAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(OrderStrategyAdvisorService.class);

    private final StrategyModelPort modelPort;
    private final AuditPort         auditPort;
    private final Consumer<Object>  eventPublisher;
    private final Counter           recommendations;
    private final Counter           overrides;
    private final Timer             predictionLatency;

    public OrderStrategyAdvisorService(StrategyModelPort modelPort,
                                        AuditPort auditPort,
                                        Consumer<Object> eventPublisher,
                                        MeterRegistry meterRegistry) {
        this.modelPort        = modelPort;
        this.auditPort        = auditPort;
        this.eventPublisher   = eventPublisher;
        this.recommendations  = meterRegistry.counter("oms.strategy.recommendations");
        this.overrides        = meterRegistry.counter("oms.strategy.overrides");
        this.predictionLatency = meterRegistry.timer("oms.strategy.latency");
    }

    /**
     * Generates an order execution strategy recommendation.
     *
     * @param orderId       order being evaluated
     * @param clientId      client
     * @param instrumentId  instrument
     * @param side          "BUY" or "SELL"
     * @param quantity      order quantity
     * @param urgencyFlag   true if client requests immediate execution
     * @return advisory recommendation (not enforced)
     */
    public StrategyRecommendation recommend(String orderId, String clientId, String instrumentId,
                                             String side, long quantity, boolean urgencyFlag) {

        MarketConditions conditions = modelPort.getMarketConditions(instrumentId);

        StrategyRecommendation rec = predictionLatency.record(() ->
                modelPort.recommend(instrumentId, side, quantity, urgencyFlag,
                        conditions.orderSizeRatio(), conditions.realizedSpread(),
                        conditions.volumePace(), conditions.minutesToClose(), conditions.sector()));

        recommendations.increment();
        log.info("StrategyAdvisor: orderId={} suggestion={} confidence={}",
                orderId, rec.suggestedStrategy(), rec.confidence());

        // Audit log via K-07
        auditPort.log("STRATEGY_RECOMMENDATION", orderId, clientId,
                "strategy=" + rec.suggestedStrategy() + " confidence=" + rec.confidence()
                + " rationale=" + rec.shapRationale());

        eventPublisher.accept(new StrategyRecommendationEvent(orderId, clientId, rec));
        return rec;
    }

    /**
     * Records when a trader overrides the AI recommendation. Critical for HITL tracking.
     *
     * @param orderId          order ID
     * @param clientId         client
     * @param recommendedStrategy what the AI suggested
     * @param chosenStrategy   what the trader chose
     * @param overrideReason   trader's reason (optional)
     */
    public void recordOverride(String orderId, String clientId,
                                String recommendedStrategy, String chosenStrategy,
                                String overrideReason) {
        overrides.increment();
        auditPort.log("STRATEGY_OVERRIDE", orderId, clientId,
                "recommended=" + recommendedStrategy + " chosen=" + chosenStrategy
                + " reason=" + overrideReason);
        log.info("StrategyAdvisor: override orderId={} recommended={} chosen={}",
                orderId, recommendedStrategy, chosenStrategy);
        eventPublisher.accept(new StrategyOverrideEvent(orderId, clientId,
                recommendedStrategy, chosenStrategy, overrideReason));
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface StrategyModelPort {
        MarketConditions getMarketConditions(String instrumentId);
        StrategyRecommendation recommend(String instrumentId, String side, long quantity,
                                          boolean urgencyFlag, double orderSizeRatio,
                                          double realizedSpread, double volumePace,
                                          int minutesToClose, String sector);
    }

    public interface AuditPort {
        void log(String eventType, String entityId, String actorId, String detail);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public enum OrderStrategy { LIMIT, MARKET, ICEBERG, TWAP, VWAP }

    public record MarketConditions(double orderSizeRatio, double realizedSpread,
                                    double volumePace, int minutesToClose, String sector) {}

    public record StrategyRecommendation(OrderStrategy suggestedStrategy,
                                          BigDecimal suggestedPrice, String suggestedTif,
                                          double confidence, String shapRationale) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record StrategyRecommendationEvent(String orderId, String clientId,
                                               StrategyRecommendation recommendation) {}
    public record StrategyOverrideEvent(String orderId, String clientId,
                                         String recommendedStrategy, String chosenStrategy,
                                         String overrideReason) {}
}
