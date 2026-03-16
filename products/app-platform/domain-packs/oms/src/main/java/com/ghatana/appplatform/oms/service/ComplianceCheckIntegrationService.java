package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

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
    private static final long   TIMEOUT_MS = 2_000;

    private final CompliancePort  compliancePort;
    private final Consumer<Object> eventPublisher;
    private final Counter complianceFails;
    private final Counter complianceReviews;
    private final Timer   complianceLatency;

    public ComplianceCheckIntegrationService(CompliancePort compliancePort,
                                              Consumer<Object> eventPublisher,
                                              MeterRegistry meterRegistry) {
        this.compliancePort    = compliancePort;
        this.eventPublisher    = eventPublisher;
        this.complianceFails   = meterRegistry.counter("oms.compliance.fails");
        this.complianceReviews = meterRegistry.counter("oms.compliance.reviews");
        this.complianceLatency = meterRegistry.timer("oms.compliance.latency");
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
                return compliancePort.check(clientId, instrumentId, side, quantity);
            } catch (Exception e) {
                log.warn("Compliance check timeout/error for orderId={}: {} — defaulting to REVIEW",
                        orderId, e.getMessage());
                return ComplianceOutcome.review(List.of("COMPLIANCE_TIMEOUT"));
            }
        });

        switch (outcome.decision()) {
            case FAIL -> {
                complianceFails.increment();
                log.info("Compliance FAIL orderId={} reasons={}", orderId, outcome.reasons());
                eventPublisher.accept(new OrderComplianceFailedEvent(orderId, clientId, outcome.reasons()));
            }
            case REVIEW -> {
                complianceReviews.increment();
                log.info("Compliance REVIEW orderId={} reasons={}", orderId, outcome.reasons());
                eventPublisher.accept(new OrderFlaggedForReviewEvent(orderId, clientId, outcome.reasons()));
            }
            case PASS -> log.debug("Compliance PASS orderId={}", orderId);
        }

        return outcome;
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

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
