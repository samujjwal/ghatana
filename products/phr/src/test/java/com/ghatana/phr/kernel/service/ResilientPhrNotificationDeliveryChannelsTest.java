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
 * @doc.purpose Tests retry and circuit-breaker protection for PHR notification delivery channels
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ResilientPhrNotificationDeliveryChannels")
class ResilientPhrNotificationDeliveryChannelsTest extends EventloopTestBase {

    @Test
    @DisplayName("retries transient email delivery failures")
    void retriesTransientEmailDeliveryFailures() {
        AtomicInteger attempts = new AtomicInteger();
        PhrNotificationDeliveryChannels delegate = new PhrNotificationDeliveryChannels() {
            @Override
            public Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification) {
                if (attempts.incrementAndGet() < 3) {
                    return Promise.ofException(new IllegalStateException("temporary email provider outage"));
                }
                return Promise.of(new DeliveryReceipt("email-123", Instant.now()));
            }

            @Override
            public Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification) {
                return Promise.of(new DeliveryReceipt("sms-1", Instant.now()));
            }

            @Override
            public Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification) {
                return Promise.of(new DeliveryReceipt("push-1", Instant.now()));
            }
        };

        ResilientPhrNotificationDeliveryChannels channels = new ResilientPhrNotificationDeliveryChannels(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(2).initialDelay(Duration.ofMillis(1)).build(),
            CircuitBreaker.builder("email").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("sms").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("push").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build()
        );

        runPromise(() -> channels.sendEmail(envelope(PhrNotificationSender.NotificationChannel.EMAIL)));

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("opens only the failing sms circuit")
    void opensOnlyFailingSmsCircuit() {
        AtomicInteger smsAttempts = new AtomicInteger();
        AtomicInteger pushAttempts = new AtomicInteger();
        PhrNotificationDeliveryChannels delegate = new PhrNotificationDeliveryChannels() {
            @Override
            public Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification) {
                return Promise.of(new DeliveryReceipt("email-1", Instant.now()));
            }

            @Override
            public Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification) {
                smsAttempts.incrementAndGet();
                return Promise.ofException(new IllegalStateException("sms unavailable"));
            }

            @Override
            public Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification) {
                pushAttempts.incrementAndGet();
                return Promise.of(new DeliveryReceipt("push-1", Instant.now()));
            }
        };

        ResilientPhrNotificationDeliveryChannels channels = new ResilientPhrNotificationDeliveryChannels(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(0).build(),
            CircuitBreaker.builder("email").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("sms").failureThreshold(1).resetTimeout(Duration.ofSeconds(30)).build(),
            CircuitBreaker.builder("push").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build()
        );

        assertThatThrownBy(() -> runPromise(() -> channels.sendSms(envelope(PhrNotificationSender.NotificationChannel.SMS))))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runPromise(() -> channels.sendSms(envelope(PhrNotificationSender.NotificationChannel.SMS))))
            .matches(ResilientPhrNotificationDeliveryChannelsTest::hasCircuitOpenCause);

        runPromise(() -> channels.sendPush(envelope(PhrNotificationSender.NotificationChannel.PUSH)));

        assertThat(smsAttempts.get()).isEqualTo(1);
        assertThat(pushAttempts.get()).isEqualTo(1);
    }

    private static PhrNotificationDeliveryChannels.NotificationEnvelope envelope(PhrNotificationSender.NotificationChannel channel) {
        return new PhrNotificationDeliveryChannels.NotificationEnvelope(
            "notif-1",
            "patient-1",
            "patient-1",
            "provider-1",
            "ref-1",
            "appointment",
            "APPOINTMENT_REMINDER_SCHEDULED",
            channel,
            Instant.now().plusSeconds(3600),
            Instant.now(),
            "corr-envelope-1",
            "phr_appointment_reminder_schedule"
        );
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
}