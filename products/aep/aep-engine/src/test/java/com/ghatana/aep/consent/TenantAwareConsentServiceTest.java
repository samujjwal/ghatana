/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.consent;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.consent.strategy.ConsentEvaluationStrategy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Verify TenantAwareConsentService per-tenant strategy dispatch and fallback
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TenantAwareConsentService")
class TenantAwareConsentServiceTest extends EventloopTestBase {

    private static final String TENANT_EU = "tenant-eu";
    private static final String TENANT_UNKNOWN = "tenant-unknown";

    private ConsentService fallback;
    private AepEngine.Event event;

    @BeforeEach
    void setUp() {
        fallback = mock(ConsentService.class);
        event = eventWith(AepEngine.ConsentStatus.GRANTED, List.of("event_processing"));
        when(fallback.evaluateConsent(any(), any()))
                .thenReturn(Promise.of(ConsentService.ConsentDecision.allow()));
        when(fallback.getAllowedPurposes(any(), any(), any()))
                .thenReturn(Promise.of(List.of("event_processing")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Builder validation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("null fallback throws NullPointerException")
        void shouldRejectNullFallback() {
            assertThatThrownBy(() -> TenantAwareConsentService.builder().build(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fallback");
        }

        @Test
        @DisplayName("null tenantId in withStrategy() throws NullPointerException")
        void shouldRejectNullTenantId() {
            assertThatThrownBy(() -> TenantAwareConsentService.builder()
                    .withStrategy(null, (tid, e) -> Promise.of(ConsentService.ConsentDecision.allow())))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("blank tenantId in withStrategy() throws IllegalArgumentException")
        void shouldRejectBlankTenantId() {
            assertThatThrownBy(() -> TenantAwareConsentService.builder()
                    .withStrategy("   ", (tid, e) -> Promise.of(ConsentService.ConsentDecision.allow())))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null strategy in withStrategy() throws NullPointerException")
        void shouldRejectNullStrategy() {
            assertThatThrownBy(() -> TenantAwareConsentService.builder()
                    .withStrategy(TENANT_EU, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // evaluateConsent — strategy dispatch
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("evaluateConsent() — strategy dispatch")
    class EvaluateConsentDispatch {

        @Test
        @DisplayName("registered tenant uses its strategy instead of the fallback")
        void shouldUseRegisteredStrategyForKnownTenant() {
            ConsentEvaluationStrategy strategy = mock(ConsentEvaluationStrategy.class);
            when(strategy.evaluate(eq(TENANT_EU), any()))
                    .thenReturn(Promise.of(ConsentService.ConsentDecision.deny("GDPR opt-out")));

            ConsentService service = TenantAwareConsentService.builder()
                    .withStrategy(TENANT_EU, strategy)
                    .build(fallback);

            ConsentService.ConsentDecision decision = runPromise(
                    () -> service.evaluateConsent(TENANT_EU, event));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).contains("GDPR");
            verify(strategy, times(1)).evaluate(eq(TENANT_EU), any());
            verify(fallback, never()).evaluateConsent(any(), any());
        }

        @Test
        @DisplayName("unregistered tenant delegates to fallback")
        void shouldUseFallbackForUnknownTenant() {
            ConsentService service = TenantAwareConsentService.builder()
                    .withStrategy(TENANT_EU, (tid, e) -> Promise.of(ConsentService.ConsentDecision.deny("EU rule")))
                    .build(fallback);

            ConsentService.ConsentDecision decision = runPromise(
                    () -> service.evaluateConsent(TENANT_UNKNOWN, event));

            assertThat(decision.allowed()).isTrue();
            verify(fallback, times(1)).evaluateConsent(eq(TENANT_UNKNOWN), any());
        }

        @Test
        @DisplayName("service with no strategies always uses fallback")
        void shouldAlwaysUseFallbackWhenNoStrategiesRegistered() {
            ConsentService service = TenantAwareConsentService.builder().build(fallback);

            runPromise(() -> service.evaluateConsent(TENANT_EU, event));

            verify(fallback, times(1)).evaluateConsent(eq(TENANT_EU), any());
        }

        @Test
        @DisplayName("strategy for tenant-A does not affect tenant-B evaluation")
        void shouldIsolateTenantStrategies() {
            ConsentEvaluationStrategy denyStrategy =
                    (tid, e) -> Promise.of(ConsentService.ConsentDecision.deny("policy"));

            ConsentService service = TenantAwareConsentService.builder()
                    .withStrategy(TENANT_EU, denyStrategy)
                    .build(fallback);

            // tenant-EU is denied by strategy
            ConsentService.ConsentDecision euDecision = runPromise(
                    () -> service.evaluateConsent(TENANT_EU, event));
            // tenant-unknown falls through to fallback (allow)
            ConsentService.ConsentDecision unknownDecision = runPromise(
                    () -> service.evaluateConsent(TENANT_UNKNOWN, event));

            assertThat(euDecision.allowed()).isFalse();
            assertThat(unknownDecision.allowed()).isTrue();
        }

        @Test
        @DisplayName("null tenantId in evaluateConsent() throws NullPointerException")
        void shouldRejectNullTenantIdOnEvaluate() {
            ConsentService service = TenantAwareConsentService.builder().build(fallback);

            assertThatThrownBy(() -> runPromise(() -> service.evaluateConsent(null, event)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null event in evaluateConsent() throws NullPointerException")
        void shouldRejectNullEventOnEvaluate() {
            ConsentService service = TenantAwareConsentService.builder().build(fallback);

            assertThatThrownBy(() -> runPromise(() -> service.evaluateConsent(TENANT_EU, null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getAllowedPurposes — always delegates to fallback
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllowedPurposes() — always delegates to fallback")
    class GetAllowedPurposes {

        @Test
        @DisplayName("delegates getAllowedPurposes to fallback regardless of registered strategies")
        void shouldDelegateToFallbackForAllowedPurposes() {
            ConsentService service = TenantAwareConsentService.builder()
                    .withStrategy(TENANT_EU, (tid, e) -> Promise.of(ConsentService.ConsentDecision.deny("EU")))
                    .build(fallback);

            List<String> purposes = runPromise(
                    () -> service.getAllowedPurposes(TENANT_EU, "user-1", "event_processing"));

            assertThat(purposes).containsExactly("event_processing");
            verify(fallback).getAllowedPurposes(TENANT_EU, "user-1", "event_processing");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static AepEngine.Event eventWith(AepEngine.ConsentStatus status,
                                              List<String> allowedPurposes) {
        return new AepEngine.Event(
                "test.event",
                Map.of(),
                Map.of(),
                java.time.Instant.now(),
                AepEngine.IdentityContext.empty(),
                new AepEngine.ConsentContext(status, AepEngine.RetentionPolicy.STANDARD, allowedPurposes),
                AepEngine.Event.DEFAULT_VERSION,
                Optional.empty()
        );
    }
}
