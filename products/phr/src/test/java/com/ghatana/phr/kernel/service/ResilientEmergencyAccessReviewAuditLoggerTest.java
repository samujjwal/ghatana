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
 * @doc.purpose Tests retry and circuit-breaker behavior for emergency review audit logging
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ResilientEmergencyAccessReviewAuditLogger")
class ResilientEmergencyAccessReviewAuditLoggerTest extends EventloopTestBase {

    @Test
    @DisplayName("retries transient queue audit failures")
    void retriesTransientQueueAuditFailures() {
        AtomicInteger attempts = new AtomicInteger(0);
        EmergencyAccessReviewAuditLogger delegate = new EmergencyAccessReviewAuditLogger() {
            @Override
            public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                if (attempts.incrementAndGet() < 3) {
                    return Promise.ofException(new IllegalStateException("audit store unavailable"));
                }
                return Promise.complete();
            }

            @Override
            public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                return Promise.complete();
            }
        };

        ResilientEmergencyAccessReviewAuditLogger logger = new ResilientEmergencyAccessReviewAuditLogger(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(2).initialDelay(Duration.ofMillis(1)).build(),
            CircuitBreaker.builder("queue").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("complete").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build()
        );

        runPromise(() -> logger.logReviewQueued(reviewCase(), event()));

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("opens only the completion audit circuit")
    void opensOnlyCompletionAuditCircuit() {
        AtomicInteger completedAttempts = new AtomicInteger(0);
        AtomicInteger queuedAttempts = new AtomicInteger(0);
        EmergencyAccessReviewAuditLogger delegate = new EmergencyAccessReviewAuditLogger() {
            @Override
            public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                queuedAttempts.incrementAndGet();
                return Promise.complete();
            }

            @Override
            public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                completedAttempts.incrementAndGet();
                return Promise.ofException(new IllegalStateException("audit completion unavailable"));
            }
        };

        ResilientEmergencyAccessReviewAuditLogger logger = new ResilientEmergencyAccessReviewAuditLogger(
            eventloop(),
            delegate,
            RetryPolicy.builder().maxRetries(0).build(),
            CircuitBreaker.builder("queue").failureThreshold(2).resetTimeout(Duration.ofSeconds(1)).build(),
            CircuitBreaker.builder("complete").failureThreshold(1).resetTimeout(Duration.ofSeconds(30)).build()
        );

        assertThatThrownBy(() -> runPromise(() -> logger.logReviewCompleted(reviewCase(), event())))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runPromise(() -> logger.logReviewCompleted(reviewCase(), event())))
            .matches(ResilientEmergencyAccessReviewAuditLoggerTest::hasCircuitOpenCause);

        runPromise(() -> logger.logReviewQueued(reviewCase(), event()));

        assertThat(completedAttempts.get()).isEqualTo(1);
        assertThat(queuedAttempts.get()).isEqualTo(1);
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
            "EMR-AUDIT",
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
            "EMR-AUDIT"
        );
    }
}
