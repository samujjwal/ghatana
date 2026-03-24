package com.ghatana.products.finance.domains.oms.service;


import com.ghatana.platform.core.event.EventBusPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @doc.type      Service
 * @doc.purpose   Integrates D-07 compliance engine into the OMS pre-trade pipeline.
 *                Calls compliance checks (restricted list, lock-in, KYC, beneficial ownership)
 *                before order routing. Routes to PENDING_APPROVAL on REVIEW decision.
 * @doc.layer     Application
 * @doc.pattern   Anti-Corruption Layer — wraps D-07 compliance calls behind an inner port
 *
 * Outcomes:
 *   PASS               → order proceeds to risk check (D01-009)
 *   FAIL(reasons)      → order REJECTED with compliance reasons
 *   REVIEW             → order to PENDING_APPROVAL state
 *
 * Circuit breaker: compliance service timeout defaults order to REVIEW (fail-safe).
 *
 * Story: D01-008
 */
public class ComplianceCheckIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceCheckIntegrationService.class);
    private static final long   DEFAULT_TIMEOUT_MS = 2_000;

    private final CompliancePort  compliancePort;
    private final EventBusPort eventBusPort;
    private final long timeoutMs;
    private final ComplianceDecision failSafeDefault;
    private final Counter complianceFails;
    private final Counter complianceReviews;
    private final Counter complianceTimeouts;
    private final Timer   complianceLatency;

    /**
     * Creates a compliance check service with configurable timeout and fail-safe default.
     *
     * @param compliancePort   compliance engine adapter
     * @param eventBusPort     event bus for publishing compliance events
     * @param meterRegistry    metrics registry
     * @param timeoutMs        maximum time (ms) to wait for a compliance check; must be &gt; 0
     * @param failSafeDefault  decision returned when the compliance check times out or errors
     *                         (typically {@link ComplianceDecision#REVIEW} for staging,
     *                         {@link ComplianceDecision#FAIL} for production); must not be null
     */
    public ComplianceCheckIntegrationService(CompliancePort compliancePort,
                                              EventBusPort eventBusPort,
                                              MeterRegistry meterRegistry,
                                              long timeoutMs,
                                              ComplianceDecision failSafeDefault) {
        this.compliancePort      = compliancePort;
        this.eventBusPort        = eventBusPort;
        this.timeoutMs           = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        this.failSafeDefault     = failSafeDefault != null ? failSafeDefault : ComplianceDecision.REVIEW;
        this.complianceFails     = meterRegistry.counter("oms.compliance.fails");
        this.complianceReviews   = meterRegistry.counter("oms.compliance.reviews");
        this.complianceTimeouts  = meterRegistry.counter("oms.compliance.timeouts");
        this.complianceLatency   = meterRegistry.timer("oms.compliance.latency");
    }

    /**
     * Creates a compliance check service with a configurable timeout and REVIEW as fail-safe.
     *
     * @param compliancePort  compliance engine adapter
     * @param eventBusPort    event bus for publishing compliance events
     * @param meterRegistry   metrics registry
     * @param timeoutMs       maximum time (ms) to wait for a compliance check; must be &gt; 0
     */
    public ComplianceCheckIntegrationService(CompliancePort compliancePort,
                                              EventBusPort eventBusPort,
                                              MeterRegistry meterRegistry,
                                              long timeoutMs) {
        this(compliancePort, eventBusPort, meterRegistry, timeoutMs, ComplianceDecision.REVIEW);
    }

    /** Creates a compliance check service with the default 2-second timeout and REVIEW fail-safe. */
    public ComplianceCheckIntegrationService(CompliancePort compliancePort,
                                              EventBusPort eventBusPort,
                                              MeterRegistry meterRegistry) {
        this(compliancePort, eventBusPort, meterRegistry, DEFAULT_TIMEOUT_MS, ComplianceDecision.REVIEW);
    }

    /**
     * Runs pre-trade compliance check for an order.
     *
     * @param orderId      order being checked
     * @param clientId     client identifier
     * @param instrumentId instrument being traded
     * @param side         "BUY" or "SELL"
     * @param quantity     order quantity
     * @return compliance outcome
     */
    public ComplianceOutcome check(String orderId, String clientId,
                                   String instrumentId, String side, long quantity) {
        ComplianceOutcome outcome = complianceLatency.record(() -> {
            try {
                Future<ComplianceOutcome> future = ForkJoinPool.commonPool().submit(
                        () -> compliancePort.check(clientId, instrumentId, side, quantity));
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                complianceTimeouts.increment();
                log.warn("Compliance check timed out for orderId={} ({}ms limit) — defaulting to {}",
                        orderId, timeoutMs, failSafeDefault);
                return failSafeOutcome(List.of("COMPLIANCE_TIMEOUT"));
            } catch (ExecutionException e) {
                log.warn("Compliance check error for orderId={}: {} — defaulting to {}",
                        orderId, e.getCause().getMessage(), failSafeDefault);
                return failSafeOutcome(List.of("COMPLIANCE_ERROR"));
            } catch (Exception e) {
                log.warn("Compliance check error for orderId={}: {} — defaulting to {}",
                        orderId, e.getMessage(), failSafeDefault);
                return failSafeOutcome(List.of("COMPLIANCE_TIMEOUT"));
            }
        });

        switch (outcome.decision()) {
            case FAIL -> {
                complianceFails.increment();
                log.info("Compliance FAIL orderId={} reasons={}", orderId, outcome.reasons());
                eventBusPort.publish(new OrderComplianceFailedEvent(orderId, clientId, outcome.reasons()));
            }
            case REVIEW -> {
                complianceReviews.increment();
                log.info("Compliance REVIEW orderId={} reasons={}", orderId, outcome.reasons());
                eventBusPort.publish(new OrderFlaggedForReviewEvent(orderId, clientId, outcome.reasons()));
            }
            case PASS -> log.debug("Compliance PASS orderId={}", orderId);
        }

        return outcome;
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    /** Returns the configured fail-safe decision used on timeout or error. */
    public ComplianceDecision getFailSafeDefault() {
        return failSafeDefault;
    }

    private ComplianceOutcome failSafeOutcome(List<String> reasons) {
        return switch (failSafeDefault) {
            case FAIL   -> ComplianceOutcome.fail(reasons);
            case REVIEW -> ComplianceOutcome.review(reasons);
            case PASS   -> ComplianceOutcome.pass();
        };
    }

    public interface CompliancePort {
        ComplianceOutcome check(String clientId, String instrumentId, String side, long quantity);
    }

    // ─── Return types ─────────────────────────────────────────────────────────

    public enum ComplianceDecision { PASS, FAIL, REVIEW }

    public record ComplianceOutcome(ComplianceDecision decision, List<String> reasons) {
        public static ComplianceOutcome pass()                     { return new ComplianceOutcome(ComplianceDecision.PASS, List.of()); }
        public static ComplianceOutcome fail(List<String> r)       { return new ComplianceOutcome(ComplianceDecision.FAIL, r); }
        public static ComplianceOutcome review(List<String> r)     { return new ComplianceOutcome(ComplianceDecision.REVIEW, r); }
        public boolean isPassed()   { return decision == ComplianceDecision.PASS; }
        public boolean isFailed()   { return decision == ComplianceDecision.FAIL; }
        public boolean isReview()   { return decision == ComplianceDecision.REVIEW; }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record OrderComplianceFailedEvent(String orderId, String clientId, List<String> reasons) {}
    public record OrderFlaggedForReviewEvent(String orderId, String clientId, List<String> reasons) {}
}
