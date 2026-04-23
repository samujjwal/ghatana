/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        fallback = mock(ConsentService.class); // GH-90000
        event = eventWith(AepEngine.ConsentStatus.GRANTED, List.of("event_processing"));
        when(fallback.evaluateConsent(any(), any())) // GH-90000
                .thenReturn(Promise.of(ConsentService.ConsentDecision.allow())); // GH-90000
        when(fallback.getAllowedPurposes(any(), any(), any())) // GH-90000
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
        void shouldRejectNullFallback() { // GH-90000
            assertThatThrownBy(() -> TenantAwareConsentService.builder().build(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("fallback");
        }

        @Test
        @DisplayName("null tenantId in withStrategy() throws NullPointerException")
        void shouldRejectNullTenantId() { // GH-90000
            assertThatThrownBy(() -> TenantAwareConsentService.builder() // GH-90000
                    .withStrategy(null, (tid, e) -> Promise.of(ConsentService.ConsentDecision.allow()))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("blank tenantId in withStrategy() throws IllegalArgumentException")
        void shouldRejectBlankTenantId() { // GH-90000
            assertThatThrownBy(() -> TenantAwareConsentService.builder() // GH-90000
                    .withStrategy("   ", (tid, e) -> Promise.of(ConsentService.ConsentDecision.allow()))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("null strategy in withStrategy() throws NullPointerException")
        void shouldRejectNullStrategy() { // GH-90000
            assertThatThrownBy(() -> TenantAwareConsentService.builder() // GH-90000
                    .withStrategy(TENANT_EU, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
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
        void shouldUseRegisteredStrategyForKnownTenant() { // GH-90000
            ConsentEvaluationStrategy strategy = mock(ConsentEvaluationStrategy.class); // GH-90000
            when(strategy.evaluate(eq(TENANT_EU), any())) // GH-90000
                    .thenReturn(Promise.of(ConsentService.ConsentDecision.deny("GDPR opt-out")));

            ConsentService service = TenantAwareConsentService.builder() // GH-90000
                    .withStrategy(TENANT_EU, strategy) // GH-90000
                    .build(fallback); // GH-90000

            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                    () -> service.evaluateConsent(TENANT_EU, event)); // GH-90000

            assertThat(decision.allowed()).isFalse(); // GH-90000
            assertThat(decision.reason()).contains("GDPR");
            verify(strategy, times(1)).evaluate(eq(TENANT_EU), any()); // GH-90000
            verify(fallback, never()).evaluateConsent(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("unregistered tenant delegates to fallback")
        void shouldUseFallbackForUnknownTenant() { // GH-90000
            ConsentService service = TenantAwareConsentService.builder() // GH-90000
                    .withStrategy(TENANT_EU, (tid, e) -> Promise.of(ConsentService.ConsentDecision.deny("EU rule")))
                    .build(fallback); // GH-90000

            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                    () -> service.evaluateConsent(TENANT_UNKNOWN, event)); // GH-90000

            assertThat(decision.allowed()).isTrue(); // GH-90000
            verify(fallback, times(1)).evaluateConsent(eq(TENANT_UNKNOWN), any()); // GH-90000
        }

        @Test
        @DisplayName("service with no strategies always uses fallback")
        void shouldAlwaysUseFallbackWhenNoStrategiesRegistered() { // GH-90000
            ConsentService service = TenantAwareConsentService.builder().build(fallback); // GH-90000

            runPromise(() -> service.evaluateConsent(TENANT_EU, event)); // GH-90000

            verify(fallback, times(1)).evaluateConsent(eq(TENANT_EU), any()); // GH-90000
        }

        @Test
        @DisplayName("strategy for tenant-A does not affect tenant-B evaluation")
        void shouldIsolateTenantStrategies() { // GH-90000
            ConsentEvaluationStrategy denyStrategy =
                    (tid, e) -> Promise.of(ConsentService.ConsentDecision.deny("policy"));

            ConsentService service = TenantAwareConsentService.builder() // GH-90000
                    .withStrategy(TENANT_EU, denyStrategy) // GH-90000
                    .build(fallback); // GH-90000

            // tenant-EU is denied by strategy
            ConsentService.ConsentDecision euDecision = runPromise( // GH-90000
                    () -> service.evaluateConsent(TENANT_EU, event)); // GH-90000
            // tenant-unknown falls through to fallback (allow) // GH-90000
            ConsentService.ConsentDecision unknownDecision = runPromise( // GH-90000
                    () -> service.evaluateConsent(TENANT_UNKNOWN, event)); // GH-90000

            assertThat(euDecision.allowed()).isFalse(); // GH-90000
            assertThat(unknownDecision.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("null tenantId in evaluateConsent() throws NullPointerException")
        void shouldRejectNullTenantIdOnEvaluate() { // GH-90000
            ConsentService service = TenantAwareConsentService.builder().build(fallback); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> service.evaluateConsent(null, event))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null event in evaluateConsent() throws NullPointerException")
        void shouldRejectNullEventOnEvaluate() { // GH-90000
            ConsentService service = TenantAwareConsentService.builder().build(fallback); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> service.evaluateConsent(TENANT_EU, null))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
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
        void shouldDelegateToFallbackForAllowedPurposes() { // GH-90000
            ConsentService service = TenantAwareConsentService.builder() // GH-90000
                    .withStrategy(TENANT_EU, (tid, e) -> Promise.of(ConsentService.ConsentDecision.deny("EU")))
                    .build(fallback); // GH-90000

            List<String> purposes = runPromise( // GH-90000
                    () -> service.getAllowedPurposes(TENANT_EU, "user-1", "event_processing")); // GH-90000

            assertThat(purposes).containsExactly("event_processing");
            verify(fallback).getAllowedPurposes(TENANT_EU, "user-1", "event_processing"); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static AepEngine.Event eventWith(AepEngine.ConsentStatus status, // GH-90000
                                              List<String> allowedPurposes) {
        return new AepEngine.Event( // GH-90000
                "test.event",
                Map.of(), // GH-90000
                Map.of(), // GH-90000
                java.time.Instant.now(), // GH-90000
                AepEngine.IdentityContext.empty(), // GH-90000
                new AepEngine.ConsentContext(status, AepEngine.RetentionPolicy.STANDARD, allowedPurposes), // GH-90000
                AepEngine.Event.DEFAULT_VERSION,
                Optional.empty() // GH-90000
        );
    }
}
