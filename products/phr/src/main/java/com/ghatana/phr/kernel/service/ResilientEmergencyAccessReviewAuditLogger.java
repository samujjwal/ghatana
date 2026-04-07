package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Adds retry and circuit-breaker protection to emergency review audit logging
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ResilientEmergencyAccessReviewAuditLogger implements EmergencyAccessReviewAuditLogger {

    private final Eventloop eventloop;
    private final EmergencyAccessReviewAuditLogger delegate;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker queueCircuitBreaker;
    private final CircuitBreaker completeCircuitBreaker;

    public ResilientEmergencyAccessReviewAuditLogger(
            Eventloop eventloop,
            EmergencyAccessReviewAuditLogger delegate) {
        this(
            eventloop,
            delegate,
            EmergencyAccessWorkflowResilienceUtils.createRetryPolicy(),
            EmergencyAccessWorkflowResilienceUtils.createCircuitBreaker("phr-emergency-review-audit-queue"),
            EmergencyAccessWorkflowResilienceUtils.createCircuitBreaker("phr-emergency-review-audit-complete")
        );
    }

    ResilientEmergencyAccessReviewAuditLogger(
            Eventloop eventloop,
            EmergencyAccessReviewAuditLogger delegate,
            RetryPolicy retryPolicy,
            CircuitBreaker queueCircuitBreaker,
            CircuitBreaker completeCircuitBreaker) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.queueCircuitBreaker = Objects.requireNonNull(queueCircuitBreaker, "queueCircuitBreaker must not be null");
        this.completeCircuitBreaker = Objects.requireNonNull(
            completeCircuitBreaker,
            "completeCircuitBreaker must not be null"
        );
    }

    @Override
    public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
        return queueCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.logReviewQueued(reviewCase, event))
        );
    }

    @Override
    public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
        return completeCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.logReviewCompleted(reviewCase, event))
        );
    }
}