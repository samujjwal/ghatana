/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.products.finance.domains.oms.service.ComplianceCheckIntegrationService.ComplianceDecision;
import com.ghatana.products.finance.domains.oms.service.ComplianceCheckIntegrationService.ComplianceOutcome;
import com.ghatana.products.finance.domains.oms.service.ComplianceCheckIntegrationService.CompliancePort;
import com.ghatana.products.finance.domains.oms.service.ComplianceCheckIntegrationService.OrderComplianceFailedEvent;
import com.ghatana.products.finance.domains.oms.service.ComplianceCheckIntegrationService.OrderFlaggedForReviewEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ComplianceCheckIntegrationService} covering:
 * <ul>
 *   <li>PASS / FAIL / REVIEW normal outcomes</li>
 *   <li>Timeout defaults to configurable fail-safe (ENH-F01)</li>
 *   <li>Execution error defaults to fail-safe</li>
 *   <li>Fail-safe FAIL vs REVIEW per environment</li>
 *   <li>Event publishing for FAIL and REVIEW outcomes</li>
 *   <li>Constructor overload defaults</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for compliance check integration with configurable fail-safe (D01-008, ENH-F01)
 * @doc.layer finance
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ComplianceCheckIntegrationService")
class ComplianceCheckIntegrationServiceTest {

    @Mock private EventBusPort eventBusPort;

    private SimpleMeterRegistry meterRegistry;

    private static final String ORDER_ID = "ORD-001";
    private static final String CLIENT_ID = "CLI-001";
    private static final String INSTRUMENT_ID = "NABIL";
    private static final String SIDE_BUY = "BUY";
    private static final long QUANTITY = 100L;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    // ── Normal outcomes ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Normal outcomes")
    class NormalOutcomes {

        @Test
        @DisplayName("Should return PASS when compliance port passes")
        void shouldReturnPassWhenPortPasses() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.pass();
            var service = new ComplianceCheckIntegrationService(port, eventBusPort, meterRegistry);

            ComplianceOutcome outcome = service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(outcome.isPassed()).isTrue();
            assertThat(outcome.reasons()).isEmpty();
            verifyNoInteractions(eventBusPort);
        }

        @Test
        @DisplayName("Should return FAIL and publish event when compliance port fails")
        void shouldReturnFailAndPublishEvent() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.fail(List.of("RESTRICTED_LIST"));
            var service = new ComplianceCheckIntegrationService(port, eventBusPort, meterRegistry);

            ComplianceOutcome outcome = service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(outcome.isFailed()).isTrue();
            assertThat(outcome.reasons()).containsExactly("RESTRICTED_LIST");

            ArgumentCaptor<OrderComplianceFailedEvent> captor =
                    ArgumentCaptor.forClass(OrderComplianceFailedEvent.class);
            verify(eventBusPort).publish(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(ORDER_ID);
            assertThat(captor.getValue().reasons()).containsExactly("RESTRICTED_LIST");
        }

        @Test
        @DisplayName("Should return REVIEW and publish event when compliance port returns review")
        void shouldReturnReviewAndPublishEvent() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.review(List.of("MANUAL_CHECK_REQUIRED"));
            var service = new ComplianceCheckIntegrationService(port, eventBusPort, meterRegistry);

            ComplianceOutcome outcome = service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(outcome.isReview()).isTrue();
            assertThat(outcome.reasons()).containsExactly("MANUAL_CHECK_REQUIRED");

            ArgumentCaptor<OrderFlaggedForReviewEvent> captor =
                    ArgumentCaptor.forClass(OrderFlaggedForReviewEvent.class);
            verify(eventBusPort).publish(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(ORDER_ID);
        }
    }

    // ── Timeout / error fail-safe ──────────────────────────────────────────────

    @Nested
    @DisplayName("Fail-safe on timeout/error")
    class FailSafe {

        @Test
        @DisplayName("Should default to REVIEW on timeout when failSafeDefault is REVIEW")
        void shouldDefaultToReviewOnTimeout() {
            // Port that sleeps longer than the timeout
            CompliancePort slowPort = (c, i, s, q) -> {
                try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ComplianceOutcome.pass();
            };
            var service = new ComplianceCheckIntegrationService(
                    slowPort, eventBusPort, meterRegistry, 50, ComplianceDecision.REVIEW);

            ComplianceOutcome outcome = service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(outcome.isReview()).isTrue();
            assertThat(outcome.reasons()).contains("COMPLIANCE_TIMEOUT");
        }

        @Test
        @DisplayName("Should default to FAIL on timeout when failSafeDefault is FAIL")
        void shouldDefaultToFailOnTimeout() {
            CompliancePort slowPort = (c, i, s, q) -> {
                try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ComplianceOutcome.pass();
            };
            var service = new ComplianceCheckIntegrationService(
                    slowPort, eventBusPort, meterRegistry, 50, ComplianceDecision.FAIL);

            ComplianceOutcome outcome = service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(outcome.isFailed()).isTrue();
            assertThat(outcome.reasons()).contains("COMPLIANCE_TIMEOUT");
        }

        @Test
        @DisplayName("Should default to REVIEW on execution error when failSafeDefault is REVIEW")
        void shouldDefaultToReviewOnExecutionError() {
            CompliancePort errorPort = (c, i, s, q) -> { throw new RuntimeException("Connection refused"); };
            var service = new ComplianceCheckIntegrationService(
                    errorPort, eventBusPort, meterRegistry, 5_000, ComplianceDecision.REVIEW);

            ComplianceOutcome outcome = service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(outcome.isReview()).isTrue();
            assertThat(outcome.reasons()).contains("COMPLIANCE_ERROR");
        }

        @Test
        @DisplayName("Should default to FAIL on execution error when failSafeDefault is FAIL")
        void shouldDefaultToFailOnExecutionError() {
            CompliancePort errorPort = (c, i, s, q) -> { throw new RuntimeException("Connection refused"); };
            var service = new ComplianceCheckIntegrationService(
                    errorPort, eventBusPort, meterRegistry, 5_000, ComplianceDecision.FAIL);

            ComplianceOutcome outcome = service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(outcome.isFailed()).isTrue();
            assertThat(outcome.reasons()).contains("COMPLIANCE_ERROR");
        }
    }

    // ── Constructor defaults ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Constructor and configuration")
    class ConstructorDefaults {

        @Test
        @DisplayName("Default constructor should use REVIEW fail-safe")
        void defaultConstructorShouldUseReviewFailSafe() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.pass();
            var service = new ComplianceCheckIntegrationService(port, eventBusPort, meterRegistry);

            assertThat(service.getFailSafeDefault()).isEqualTo(ComplianceDecision.REVIEW);
        }

        @Test
        @DisplayName("Four-arg constructor should use REVIEW fail-safe")
        void fourArgConstructorShouldUseReviewFailSafe() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.pass();
            var service = new ComplianceCheckIntegrationService(port, eventBusPort, meterRegistry, 1_000);

            assertThat(service.getFailSafeDefault()).isEqualTo(ComplianceDecision.REVIEW);
        }

        @Test
        @DisplayName("Five-arg constructor should accept FAIL as fail-safe")
        void fiveArgConstructorShouldAcceptFail() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.pass();
            var service = new ComplianceCheckIntegrationService(
                    port, eventBusPort, meterRegistry, 1_000, ComplianceDecision.FAIL);

            assertThat(service.getFailSafeDefault()).isEqualTo(ComplianceDecision.FAIL);
        }

        @Test
        @DisplayName("Null failSafeDefault should default to REVIEW")
        void nullFailSafeDefaultShouldDefaultToReview() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.pass();
            var service = new ComplianceCheckIntegrationService(
                    port, eventBusPort, meterRegistry, 1_000, null);

            assertThat(service.getFailSafeDefault()).isEqualTo(ComplianceDecision.REVIEW);
        }
    }

    // ── Metrics ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("Should increment fail counter on FAIL outcome")
        void shouldIncrementFailCounter() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.fail(List.of("RESTRICTED"));
            var service = new ComplianceCheckIntegrationService(port, eventBusPort, meterRegistry);

            service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(meterRegistry.counter("oms.compliance.fails").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should increment review counter on REVIEW outcome")
        void shouldIncrementReviewCounter() {
            CompliancePort port = (c, i, s, q) -> ComplianceOutcome.review(List.of("CHECK"));
            var service = new ComplianceCheckIntegrationService(port, eventBusPort, meterRegistry);

            service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(meterRegistry.counter("oms.compliance.reviews").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should increment timeout counter on timeout")
        void shouldIncrementTimeoutCounter() {
            CompliancePort slowPort = (c, i, s, q) -> {
                try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ComplianceOutcome.pass();
            };
            var service = new ComplianceCheckIntegrationService(
                    slowPort, eventBusPort, meterRegistry, 50, ComplianceDecision.REVIEW);

            service.check(ORDER_ID, CLIENT_ID, INSTRUMENT_ID, SIDE_BUY, QUANTITY);

            assertThat(meterRegistry.counter("oms.compliance.timeouts").count()).isEqualTo(1.0);
        }
    }

    // ── ComplianceOutcome record ───────────────────────────────────────────────

    @Nested
    @DisplayName("ComplianceOutcome")
    class OutcomeRecord {

        @Test
        @DisplayName("pass() should create PASS outcome with no reasons")
        void passShouldCreatePassOutcome() {
            ComplianceOutcome outcome = ComplianceOutcome.pass();
            assertThat(outcome.isPassed()).isTrue();
            assertThat(outcome.isFailed()).isFalse();
            assertThat(outcome.isReview()).isFalse();
            assertThat(outcome.reasons()).isEmpty();
        }

        @Test
        @DisplayName("fail() should create FAIL outcome with reasons")
        void failShouldCreateFailOutcome() {
            ComplianceOutcome outcome = ComplianceOutcome.fail(List.of("R1", "R2"));
            assertThat(outcome.isFailed()).isTrue();
            assertThat(outcome.reasons()).containsExactly("R1", "R2");
        }

        @Test
        @DisplayName("review() should create REVIEW outcome with reasons")
        void reviewShouldCreateReviewOutcome() {
            ComplianceOutcome outcome = ComplianceOutcome.review(List.of("MANUAL"));
            assertThat(outcome.isReview()).isTrue();
            assertThat(outcome.reasons()).containsExactly("MANUAL");
        }
    }
}
