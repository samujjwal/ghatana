package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   ML model predicting total expected execution cost (implementation shortfall)
 *                before order placement: total_cost_bps = impact_bps + spread_cost + timing_risk + fee.
 *                Blocks orders where estimated cost exceeds client mandate cost limits.
 *                RMSE tracked vs actual TCA (D-02-F04) for continuous model improvement.
 * @doc.layer     Application
 * @doc.pattern   K-09 Supervised Model invocation + mandate limit enforcement
 *
 * Components:
 *   impact_bps    — from MarketImpactPredictionService (D01-019)
 *   spread_cost   — bid-ask spread from D-04 real-time data
 *   timing_risk   — σ × √T (volatility × square root of holding time)
 *   fee           — from T3 broker fee schedule
 *
 * Story: D01-021
 */
public class PreTradeCostEstimationService {

    private static final Logger log = LoggerFactory.getLogger(PreTradeCostEstimationService.class);

    private final CostModelPort        costModelPort;
    private final MandateLimitPort     mandatePort;
    private final Consumer<Object>     eventPublisher;
    private final AtomicReference<Double> runningRmse = new AtomicReference<>(0.0);
    private final Counter  mandateBreaches;
    private final Timer    estimationLatency;

    public PreTradeCostEstimationService(CostModelPort costModelPort,
                                          MandateLimitPort mandatePort,
                                          Consumer<Object> eventPublisher,
                                          MeterRegistry meterRegistry) {
        this.costModelPort     = costModelPort;
        this.mandatePort       = mandatePort;
        this.eventPublisher    = eventPublisher;
        this.mandateBreaches   = meterRegistry.counter("oms.cost.mandate.breaches");
        this.estimationLatency = meterRegistry.timer("oms.cost.estimation.latency");

        Gauge.builder("oms.cost.model.rmse", runningRmse, AtomicReference::get)
                .register(meterRegistry);
    }

    /**
     * Estimates pre-trade execution cost and enforces mandate limits.
     *
     * @param orderId         order being evaluated
     * @param clientId        client identifier
     * @param instrumentId    instrument
     * @param side            "BUY" or "SELL"
     * @param quantity        order quantity
     * @param price           order price
     * @param predImpactBps   impact prediction from D01-019
     * @return cost estimate with mandate check result
     */
    public CostEstimate estimate(String orderId, String clientId, String instrumentId,
                                  String side, long quantity, BigDecimal price,
                                  double predImpactBps) {

        CostComponents components = estimationLatency.record(() ->
                costModelPort.estimateCost(instrumentId, side, quantity, price, predImpactBps));

        double totalCostBps = predImpactBps
                + components.spreadCostBps()
                + components.timingRiskBps()
                + components.feeBps();

        CostEstimate estimate = new CostEstimate(
                predImpactBps, components.spreadCostBps(),
                components.timingRiskBps(), components.feeBps(),
                totalCostBps, components.confidenceInterval());

        // Mandate limit check
        double mandateLimit = mandatePort.getMandateCostLimitBps(clientId, instrumentId);
        if (totalCostBps > mandateLimit) {
            mandateBreaches.increment();
            log.info("PreTradeCost: mandate breach orderId={} totalCostBps={} limit={}",
                    orderId, totalCostBps, mandateLimit);
            eventPublisher.accept(new MandateCostLimitBreachedEvent(
                    orderId, clientId, instrumentId, totalCostBps, mandateLimit));
        }

        log.debug("PreTradeCost: orderId={} impact={}bps spread={}bps timing={}bps fee={}bps total={}bps",
                orderId, predImpactBps, components.spreadCostBps(),
                components.timingRiskBps(), components.feeBps(), totalCostBps);

        return estimate;
    }

    /**
     * Records the actual execution cost (from TCA) to compute model RMSE.
     * Called by the TCA reconciliation process after execution completion.
     *
     * @param estimatedBps predicted cost
     * @param actualBps    actual TCA cost
     */
    public void recordTcaActual(double estimatedBps, double actualBps) {
        double error = estimatedBps - actualBps;
        // Exponential moving average of RMSE
        double newRmse = Math.sqrt(0.9 * Math.pow(runningRmse.get(), 2) + 0.1 * Math.pow(error, 2));
        runningRmse.set(newRmse);
        log.debug("PreTradeCost: TCA reconciliation estimatedBps={} actualBps={} rmse={}",
                estimatedBps, actualBps, newRmse);

        if (newRmse > 20.0) { // RMSE > 20bps triggers K-09 drift alert
            eventPublisher.accept(new ModelDriftDetectedEvent("PRETRADE_COST", newRmse));
        }
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface CostModelPort {
        CostComponents estimateCost(String instrumentId, String side, long quantity,
                                    BigDecimal price, double predImpactBps);
    }

    public interface MandateLimitPort {
        double getMandateCostLimitBps(String clientId, String instrumentId);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record CostComponents(double spreadCostBps, double timingRiskBps,
                                  double feeBps, double[] confidenceInterval) {}

    public record CostEstimate(double impactBps, double spreadCostBps, double timingRiskBps,
                                double feeBps, double totalCostBps, double[] confidenceInterval) {
        public boolean exceedsLimit(double limitBps) { return totalCostBps > limitBps; }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record MandateCostLimitBreachedEvent(String orderId, String clientId,
                                                String instrumentId, double totalCostBps, double limitBps) {}
    public record ModelDriftDetectedEvent(String modelName, double rmse) {}
}
