package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.ReviewStatus;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests retry and circuit-breaker behavior for emergency review notifications
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ResilientEmergencyAccessNotificationSender")
class ResilientEmergencyAccessNotificationSenderTest extends EventloopTestBase {

    @Test
    @DisplayName("retries transient compliance notification failures")
    void retriesTransientComplianceNotificationFailures() {
        AtomicInteger attempts = new AtomicInteger(0);
        EmergencyAccessNotificationSender delegate = new EmergencyAccessNotificationSender() {
            @Override
            public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                if (attempts.incrementAndGet() < 3) {
                    return Promise.ofException(new IllegalStateException("transient compliance outage"));
                }
                return Promise.complete();
            }

            @Override
            public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                return Promise.complete();
            }
        };

        ResilientEmergencyAccessNotificationSender sender = new ResilientEmergencyAccessNotificationSender(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(2).initialDelay(Duration.ofMillis(1)).build(),
            CircuitBreaker.builder("compliance").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("schedule").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("escalation").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build()
        );

        runPromise(() -> sender.notifyComplianceLead(reviewCase(), event()));

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("opens only the failing operation circuit")
    void opensOnlyTheFailingOperationCircuit() {
        AtomicInteger scheduleAttempts = new AtomicInteger(0);
        AtomicInteger escalationAttempts = new AtomicInteger(0);
        EmergencyAccessNotificationSender delegate = new EmergencyAccessNotificationSender() {
            @Override
            public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                scheduleAttempts.incrementAndGet();
                return Promise.ofException(new IllegalStateException("scheduler unavailable"));
            }

            @Override
            public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                escalationAttempts.incrementAndGet();
                return Promise.complete();
            }
        };

        ResilientEmergencyAccessNotificationSender sender = new ResilientEmergencyAccessNotificationSender(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(0).build(),
            CircuitBreaker.builder("compliance").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("schedule").failureThreshold(1).resetTimeout(Duration.ofSeconds(30)).build(),
            CircuitBreaker.builder("escalation").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build()
        );

        assertThatThrownBy(() -> runPromise(() -> sender.scheduleMandatoryReview(reviewCase(), event())))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runPromise(() -> sender.scheduleMandatoryReview(reviewCase(), event())))
            .matches(ResilientEmergencyAccessNotificationSenderTest::hasCircuitOpenCause);

        runPromise(() -> sender.notifyEscalation(reviewCase(), event()));

        assertThat(scheduleAttempts.get()).isEqualTo(1);
        assertThat(escalationAttempts.get()).isEqualTo(1);
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

    private static EmergencyAccessReviewCase reviewCase() {
        Instant now = Instant.now();
        return new EmergencyAccessReviewCase(
            "EMR-RESILIENCE",
            "event-1",
            "patient-1",
            "doctor-1",
            now,
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            now.plusSeconds(600),
            EmergencyAccessReviewCase.ReviewCaseStatus.QUEUED
        );
    }

    private static EmergencyAccessEvent event() {
        Instant accessedAt = Instant.now();
        return new EmergencyAccessEvent(
            "event-1",
            "patient-1",
            "doctor-1",
            "ER_PHYSICIAN",
            "Emergency care",
            Set.of("medications"),
            accessedAt,
            accessedAt.plusSeconds(3600),
            ReviewStatus.PENDING_REVIEW,
            accessedAt.plusSeconds(7200),
            null,
            null,
            null,
            "EMR-RESILIENCE"
        );
    }
}