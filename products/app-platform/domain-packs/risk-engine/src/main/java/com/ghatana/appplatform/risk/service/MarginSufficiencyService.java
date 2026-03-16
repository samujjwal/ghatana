package com.ghatana.appplatform.risk.service;

import com.ghatana.appplatform.risk.domain.*;
import com.ghatana.appplatform.risk.domain.RiskCheckResult.RiskStatus;
import com.ghatana.appplatform.risk.port.MarginStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Evaluates whether a client has sufficient margin to place an order (D06-001).
 *              Margin rates by instrument type:
 *                EQUITY      = 50%
 *                BOND        = 10%
 *                ETF         = 30%
 *                MONEY_MARKET= 5%
 *              Uses {@link MarginStore#reserveAtomic} for an atomic Redis check-and-set, so
 *              concurrent order submissions do not double-spend the same margin capacity.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service; atomic Redis reservation
 */
public class MarginSufficiencyService {

    private static final Logger log = LoggerFactory.getLogger(MarginSufficiencyService.class);

    /** Margin rates by instrument classification (D06-001). */
    private static final Map<MarginType, BigDecimal> MARGIN_RATES = Map.of(
            MarginType.EQUITY,       new BigDecimal("0.50"),
            MarginType.BOND,         new BigDecimal("0.10"),
            MarginType.ETF,          new BigDecimal("0.30"),
            MarginType.MONEY_MARKET, new BigDecimal("0.05")
    );

    private final MarginStore marginStore;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;
    private final Counter insufficientMarginCounter;

    public MarginSufficiencyService(MarginStore marginStore,
                                     Executor executor,
                                     Consumer<Object> eventPublisher,
                                     MeterRegistry meterRegistry) {
        this.marginStore = marginStore;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.insufficientMarginCounter = meterRegistry.counter("risk.margin.insufficient");
    }

    /**
     * Check (and atomically reserve) margin for an order (D06-001).
     * On approval, the margin is held until the order is settled or cancelled.
     * On denial, no state changes are made.
     */
    public Promise<RiskCheckResult> check(RiskCheckRequest request) {
        return Promise.ofBlocking(executor, () -> {
            BigDecimal rate = MARGIN_RATES.getOrDefault(request.marginType(),
                    new BigDecimal("0.50"));
            BigDecimal required = request.orderValue().multiply(rate)
                    .setScale(2, RoundingMode.HALF_EVEN);

            var marginOpt = marginStore.find(request.clientId(), request.accountId());
            if (marginOpt.isEmpty()) {
                log.warn("No margin account: clientId={} accountId={}",
                        request.clientId(), request.accountId());
                insufficientMarginCounter.increment();
                return new RiskCheckResult(RiskStatus.DENY,
                        "No margin account found for client",
                        required, BigDecimal.ZERO, 1.0);
            }

            var margin = marginOpt.get();
            if (margin.available().compareTo(required) < 0) {
                log.warn("Insufficient margin: clientId={} available={} required={}",
                        request.clientId(), margin.available(), required);
                insufficientMarginCounter.increment();
                return new RiskCheckResult(RiskStatus.DENY,
                        "Insufficient margin: available=" + margin.available()
                                + " required=" + required,
                        required, margin.available(), margin.utilizationRatio());
            }

            // Atomic reservation — prevents concurrent over-spending
            var reserved = marginStore.reserveAtomic(
                    request.clientId(), request.accountId(), required);
            if (reserved.isEmpty()) {
                insufficientMarginCounter.increment();
                return new RiskCheckResult(RiskStatus.DENY,
                        "Margin reservation conflict — retry", required,
                        margin.available(), margin.utilizationRatio());
            }

            var updated = reserved.get();
            eventPublisher.accept(new MarginReservedEvent(
                    request.orderId(), request.clientId(), required));
            return new RiskCheckResult(RiskStatus.APPROVE, null,
                    required, updated.available(), updated.utilizationRatio());
        });
    }

    /** Release margin held for an order (called on fill, cancel, or rejection). */
    public Promise<Void> release(String clientId, String accountId,
                                  BigDecimal reservedAmount, String orderId) {
        return Promise.ofBlocking(executor, () -> {
            marginStore.release(clientId, accountId, reservedAmount);
            eventPublisher.accept(new MarginReleasedEvent(orderId, clientId, reservedAmount));
            return (Void) null;
        });
    }

    // ─── Events ──────────────────────────────────────────────────────────────

    public record MarginReservedEvent(String orderId, String clientId, BigDecimal amount) {}
    public record MarginReleasedEvent(String orderId, String clientId, BigDecimal amount) {}
}
