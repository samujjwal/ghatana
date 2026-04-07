package com.ghatana.phr.kernel.service;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests retry and circuit-breaker behavior for PHR notification delivery
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ResilientPhrNotificationSender")
class ResilientPhrNotificationSenderTest extends EventloopTestBase {

    @Test
    @DisplayName("retries transient consent notification failures")
    void retriesTransientConsentNotificationFailures() {
        AtomicInteger attempts = new AtomicInteger(0);
        PhrNotificationSender delegate = new PhrNotificationSender() {
            @Override
            public Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> notifyConsentChange(ConsentChangeNotification notification) {
                if (attempts.incrementAndGet() < 3) {
                    return Promise.ofException(new IllegalStateException("transient consent notification failure"));
                }
                return Promise.complete();
            }

            @Override
            public Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification) {
                return Promise.complete();
            }
        };

        ResilientPhrNotificationSender sender = new ResilientPhrNotificationSender(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(2).initialDelay(Duration.ofMillis(1)).build(),
            CircuitBreaker.builder("appointment-schedule").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("appointment-cancel").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("consent").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("telemedicine").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build()
        );

        runPromise(() -> sender.notifyConsentChange(consentNotification()));

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("opens only the failing telemedicine circuit")
    void opensOnlyTheFailingTelemedicineCircuit() {
        AtomicInteger telemedicineAttempts = new AtomicInteger(0);
        AtomicInteger appointmentAttempts = new AtomicInteger(0);
        PhrNotificationSender delegate = new PhrNotificationSender() {
            @Override
            public Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification) {
                appointmentAttempts.incrementAndGet();
                return Promise.complete();
            }

            @Override
            public Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> notifyConsentChange(ConsentChangeNotification notification) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification) {
                telemedicineAttempts.incrementAndGet();
                return Promise.ofException(new IllegalStateException("telemedicine notifications unavailable"));
            }
        };

        ResilientPhrNotificationSender sender = new ResilientPhrNotificationSender(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(0).build(),
            CircuitBreaker.builder("appointment-schedule").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("appointment-cancel").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("consent").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("telemedicine").failureThreshold(1).resetTimeout(Duration.ofSeconds(30)).build()
        );

        assertThatThrownBy(() -> runPromise(() -> sender.notifyTelemedicineSession(telemedicineNotification())))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runPromise(() -> sender.notifyTelemedicineSession(telemedicineNotification())))
            .matches(ResilientPhrNotificationSenderTest::hasCircuitOpenCause);

        runPromise(() -> sender.scheduleAppointmentReminder(appointmentNotification()));

        assertThat(telemedicineAttempts.get()).isEqualTo(1);
        assertThat(appointmentAttempts.get()).isEqualTo(1);
    }

    private static boolean hasCircuitOpenCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CircuitBreaker.CircuitBreakerOpenException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static PhrNotificationSender.AppointmentReminderNotification appointmentNotification() {
        return new PhrNotificationSender.AppointmentReminderNotification(
            "appointment-1",
            "patient-1",
            "provider-1",
            Instant.now().plusSeconds(3600),
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-appointment-1",
            "phr_appointment_reminder_schedule"
        );
    }

    private static PhrNotificationSender.ConsentChangeNotification consentNotification() {
        return new PhrNotificationSender.ConsentChangeNotification(
            "patient-1",
            "doctor-1",
            "grant-1",
            PhrNotificationSender.ConsentChangeType.GRANT_CREATED,
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-consent-1",
            "phr_consent_change"
        );
    }

    private static PhrNotificationSender.TelemedicineSessionNotification telemedicineNotification() {
        return new PhrNotificationSender.TelemedicineSessionNotification(
            "tele-1",
            "patient-1",
            "provider-1",
            Instant.now().plusSeconds(7200),
            PhrNotificationSender.TelemedicineNotificationType.SESSION_SCHEDULED,
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-tele-1",
            "phr_telemedicine_schedule"
        );
    }
}