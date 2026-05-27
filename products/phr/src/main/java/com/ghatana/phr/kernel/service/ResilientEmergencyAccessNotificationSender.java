package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Adds retry and circuit-breaker protection to emergency review notification delivery
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ResilientEmergencyAccessNotificationSender implements EmergencyAccessNotificationSender {

    private final Eventloop eventloop;
    private final EmergencyAccessNotificationSender delegate;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker complianceCircuitBreaker;
    private final CircuitBreaker scheduleCircuitBreaker;
    private final CircuitBreaker escalationCircuitBreaker;
    private final CircuitBreaker patientCircuitBreaker;

    public ResilientEmergencyAccessNotificationSender(
            Eventloop eventloop,
            EmergencyAccessNotificationSender delegate) {
        this(
            eventloop,
            delegate,
            EmergencyAccessWorkflowResilienceUtils.createRetryPolicy(),
            EmergencyAccessWorkflowResilienceUtils.createCircuitBreaker("phr-emergency-review-notify-compliance"),
            EmergencyAccessWorkflowResilienceUtils.createCircuitBreaker("phr-emergency-review-schedule-review"),
            EmergencyAccessWorkflowResilienceUtils.createCircuitBreaker("phr-emergency-review-escalation"),
            EmergencyAccessWorkflowResilienceUtils.createCircuitBreaker("phr-emergency-review-notify-patient")
        );
    }

    ResilientEmergencyAccessNotificationSender(
            Eventloop eventloop,
            EmergencyAccessNotificationSender delegate,
            RetryPolicy retryPolicy,
            CircuitBreaker complianceCircuitBreaker,
            CircuitBreaker scheduleCircuitBreaker,
            CircuitBreaker escalationCircuitBreaker,
            CircuitBreaker patientCircuitBreaker) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.complianceCircuitBreaker = Objects.requireNonNull(
            complianceCircuitBreaker,
            "complianceCircuitBreaker must not be null"
        );
        this.scheduleCircuitBreaker = Objects.requireNonNull(
            scheduleCircuitBreaker,
            "scheduleCircuitBreaker must not be null"
        );
        this.escalationCircuitBreaker = Objects.requireNonNull(
            escalationCircuitBreaker,
            "escalationCircuitBreaker must not be null"
        );
        this.patientCircuitBreaker = Objects.requireNonNull(
            patientCircuitBreaker,
            "patientCircuitBreaker must not be null"
        );
    }

    @Override
    public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
        return complianceCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.notifyComplianceLead(reviewCase, event))
        );
    }

    @Override
    public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
        return scheduleCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.scheduleMandatoryReview(reviewCase, event))
        );
    }

    @Override
    public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
        return escalationCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.notifyEscalation(reviewCase, event))
        );
    }

    @Override
    public Promise<Void> notifyPatient(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
        return patientCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.notifyPatient(reviewCase, event))
        );
    }
}
