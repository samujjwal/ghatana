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
 * @doc.purpose   Integrates D-06 risk engine into the OMS pre-trade pipeline.
 *                After compliance passes, performs margin sufficiency, position limit,
 *                and concentration limit checks. Must complete &lt; 5ms P99.
 * @doc.layer     Application
 * @doc.pattern   Anti-Corruption Layer — wraps D-06 risk calls behind an inner port
 *
 * Risk outcomes:
 *   APPROVE  → order proceeds to routing
 *   DENY     → order REJECTED with risk reason
 *
 * Story: D01-009
 */
public class RiskCheckIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(RiskCheckIntegrationService.class);

    private final RiskPort      riskPort;
    private final Consumer<Object> eventPublisher;
    private final Counter riskDenials;
    private final Timer   riskLatency;

    public RiskCheckIntegrationService(RiskPort riskPort,
                                       Consumer<Object> eventPublisher,
                                       MeterRegistry meterRegistry) {
        this.riskPort      = riskPort;
        this.eventPublisher = eventPublisher;
        this.riskDenials   = meterRegistry.counter("oms.risk.denials");
        this.riskLatency   = meterRegistry.timer("oms.risk.latency");
    }

    /**
     * Runs pre-trade risk check for an order.
     *
     * @param orderId      order being checked
     * @param clientId     client identifier
     * @param accountId    trading account
     * @param instrumentId instrument
     * @param marginType   "EQUITY", "BOND", "ETF", "MONEY_MARKET"
     * @param side         "BUY" or "SELL"
     * @param quantity     order quantity
     * @param price        order price
     * @param orderValue   total order value (quantity × price)
     * @return risk outcome (APPROVE/DENY with reason)
     */
    public RiskOutcome check(String orderId, String clientId, String accountId,
                              String instrumentId, String marginType, String side,
                              long quantity, BigDecimal price, BigDecimal orderValue) {

        RiskOutcome outcome = riskLatency.record(() ->
                riskPort.check(clientId, accountId, instrumentId, marginType, side, quantity, price, orderValue));

        if (!outcome.approved()) {
            riskDenials.increment();
            log.info("Risk DENY orderId={} reason={}", orderId, outcome.reason());
            eventPublisher.accept(new OrderRiskDeniedEvent(orderId, clientId, outcome.reason()));
        } else {
            log.debug("Risk APPROVE orderId={}", orderId);
        }

        return outcome;
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface RiskPort {
        RiskOutcome check(String clientId, String accountId, String instrumentId,
                          String marginType, String side, long quantity,
                          BigDecimal price, BigDecimal orderValue);
    }

    // ─── Return type ──────────────────────────────────────────────────────────

    public record RiskOutcome(boolean approved, String reason,
                               BigDecimal requiredMargin, BigDecimal availableMargin) {
        public static RiskOutcome approve(BigDecimal required, BigDecimal available) {
            return new RiskOutcome(true, null, required, available);
        }
        public static RiskOutcome deny(String reason) {
            return new RiskOutcome(false, reason, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record OrderRiskDeniedEvent(String orderId, String clientId, String reason) {}
}
