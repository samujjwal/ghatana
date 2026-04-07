package com.ghatana.phr.kernel.service;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Adds retry and circuit-breaker protection to patient-facing PHR notification delivery
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ResilientPhrNotificationSender implements PhrNotificationSender {

    private final Eventloop eventloop;
    private final PhrNotificationSender delegate;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker appointmentScheduleCircuitBreaker;
    private final CircuitBreaker appointmentCancelCircuitBreaker;
    private final CircuitBreaker consentCircuitBreaker;
    private final CircuitBreaker telemedicineCircuitBreaker;

    public ResilientPhrNotificationSender(Eventloop eventloop, PhrNotificationSender delegate) {
        this(
            eventloop,
            delegate,
            PhrNotificationDispatchResilienceUtils.createRetryPolicy(),
            PhrNotificationDispatchResilienceUtils.createCircuitBreaker("phr-appointment-reminder-schedule"),
            PhrNotificationDispatchResilienceUtils.createCircuitBreaker("phr-appointment-reminder-cancel"),
            PhrNotificationDispatchResilienceUtils.createCircuitBreaker("phr-consent-change-notify"),
            PhrNotificationDispatchResilienceUtils.createCircuitBreaker("phr-telemedicine-notify")
        );
    }

    ResilientPhrNotificationSender(
            Eventloop eventloop,
            PhrNotificationSender delegate,
            RetryPolicy retryPolicy,
            CircuitBreaker appointmentScheduleCircuitBreaker,
            CircuitBreaker appointmentCancelCircuitBreaker,
            CircuitBreaker consentCircuitBreaker,
            CircuitBreaker telemedicineCircuitBreaker) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.appointmentScheduleCircuitBreaker = Objects.requireNonNull(
            appointmentScheduleCircuitBreaker,
            "appointmentScheduleCircuitBreaker must not be null"
        );
        this.appointmentCancelCircuitBreaker = Objects.requireNonNull(
            appointmentCancelCircuitBreaker,
            "appointmentCancelCircuitBreaker must not be null"
        );
        this.consentCircuitBreaker = Objects.requireNonNull(
            consentCircuitBreaker,
            "consentCircuitBreaker must not be null"
        );
        this.telemedicineCircuitBreaker = Objects.requireNonNull(
            telemedicineCircuitBreaker,
            "telemedicineCircuitBreaker must not be null"
        );
    }

    @Override
    public Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification) {
        return appointmentScheduleCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.scheduleAppointmentReminder(notification))
        );
    }

    @Override
    public Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification) {
        return appointmentCancelCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.cancelAppointmentReminder(notification))
        );
    }

    @Override
    public Promise<Void> notifyConsentChange(ConsentChangeNotification notification) {
        return consentCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.notifyConsentChange(notification))
        );
    }

    @Override
    public Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification) {
        return telemedicineCircuitBreaker.execute(
            eventloop,
            () -> retryPolicy.execute(eventloop, () -> delegate.notifyTelemedicineSession(notification))
        );
    }
}