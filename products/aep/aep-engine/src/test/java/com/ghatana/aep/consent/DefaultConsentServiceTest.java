package com.ghatana.aep.consent;

import com.ghatana.aep.AepEngine;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultConsentService}.
 *
 * <p>Validates that consent evaluation honours the {@link AepEngine.ConsentStatus}
 * on each event and correctly enforces the {@code "event_processing"} purpose gate.
 *
 * @doc.type class
 * @doc.purpose Verify consent evaluation logic in DefaultConsentService
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DefaultConsentService")
class DefaultConsentServiceTest extends EventloopTestBase {

    private static final String TENANT = "tenant-test";
    private DefaultConsentService service;

    @BeforeEach
    void setUp() { 
        service = new DefaultConsentService(); 
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Denied / Expired
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rejected consent states")
    class RejectedStates {

        @Test
        @DisplayName("DENIED consent produces deny decision")
        void shouldDenyWhenConsentDenied() { 
            AepEngine.Event event = eventWithStatus(AepEngine.ConsentStatus.DENIED); 
            ConsentService.ConsentDecision decision = runPromise( 
                () -> service.evaluateConsent(TENANT, event)); 
            assertThat(decision.allowed()).isFalse(); 
        }

        @Test
        @DisplayName("EXPIRED consent produces deny decision")
        void shouldDenyWhenConsentExpired() { 
            AepEngine.Event event = eventWithStatus(AepEngine.ConsentStatus.EXPIRED); 
            ConsentService.ConsentDecision decision = runPromise( 
                () -> service.evaluateConsent(TENANT, event)); 
            assertThat(decision.allowed()).isFalse(); 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Granted
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Granted consent states")
    class GrantedStates {

        @Test
        @DisplayName("GRANTED with no allowed-purposes list allows processing")
        void shouldAllowGrantedWithNoPurposes() { 
            AepEngine.Event event = eventWith(AepEngine.ConsentStatus.GRANTED, List.of()); 
            ConsentService.ConsentDecision decision = runPromise( 
                () -> service.evaluateConsent(TENANT, event)); 
            assertThat(decision.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("GRANTED with event_processing in allowed purposes allows processing")
        void shouldAllowGrantedWithEventProcessingPurpose() { 
            AepEngine.Event event = eventWith( 
                AepEngine.ConsentStatus.GRANTED, List.of("event_processing", "analytics")); 
            ConsentService.ConsentDecision decision = runPromise( 
                () -> service.evaluateConsent(TENANT, event)); 
            assertThat(decision.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("GRANTED without event_processing in non-empty allowed purposes denies")
        void shouldDenyGrantedWhenPurposeMissing() { 
            AepEngine.Event event = eventWith( 
                AepEngine.ConsentStatus.GRANTED, List.of("analytics", "reporting")); 
            ConsentService.ConsentDecision decision = runPromise( 
                () -> service.evaluateConsent(TENANT, event)); 
            assertThat(decision.allowed()).isFalse(); 
            assertThat(decision.reason()).contains("purpose");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Unknown
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown consent state")
    class UnknownState {

        @Test
        @DisplayName("UNKNOWN with no allowed-purposes allows processing (permissive default)")
        void shouldAllowUnknownWithNoPurposes() { 
            AepEngine.Event event = eventWith(AepEngine.ConsentStatus.UNKNOWN, List.of()); 
            ConsentService.ConsentDecision decision = runPromise( 
                () -> service.evaluateConsent(TENANT, event)); 
            assertThat(decision.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("UNKNOWN with non-empty purposes list that includes event_processing allows")
        void shouldAllowUnknownWithEventProcessingPurpose() { 
            AepEngine.Event event = eventWith( 
                AepEngine.ConsentStatus.UNKNOWN, List.of("event_processing"));
            ConsentService.ConsentDecision decision = runPromise( 
                () -> service.evaluateConsent(TENANT, event)); 
            assertThat(decision.allowed()).isTrue(); 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ConsentDecision factory methods
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ConsentDecision value type")
    class ConsentDecisionFactories {

        @Test
        @DisplayName("allow() produces an allowed decision with empty allowed-purposes")
        void allowFactoryShouldBeAllowed() { 
            ConsentService.ConsentDecision d = ConsentService.ConsentDecision.allow(); 
            assertThat(d.allowed()).isTrue(); 
            assertThat(d.allowedPurposes()).isEmpty(); 
        }

        @Test
        @DisplayName("allow(purposes) produces an allowed decision with purposes list")
        void allowWithPurposesShouldBeAllowed() { 
            ConsentService.ConsentDecision d = ConsentService.ConsentDecision.allow( 
                List.of("analytics", "event_processing")); 
            assertThat(d.allowed()).isTrue(); 
            assertThat(d.allowedPurposes()).contains("analytics", "event_processing"); 
        }

        @Test
        @DisplayName("deny(reason) produces a denied decision with a reason")
        void denyFactoryShouldBeDenied() { 
            ConsentService.ConsentDecision d = ConsentService.ConsentDecision.deny("GDPR opt-out");
            assertThat(d.allowed()).isFalse(); 
            assertThat(d.reason()).isEqualTo("GDPR opt-out");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static AepEngine.Event eventWithStatus(AepEngine.ConsentStatus status) { 
        return eventWith(status, List.of()); 
    }

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
