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
@DisplayName("DefaultConsentService [GH-90000]")
class DefaultConsentServiceTest extends EventloopTestBase {

    private static final String TENANT = "tenant-test";
    private DefaultConsentService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new DefaultConsentService(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Denied / Expired
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rejected consent states [GH-90000]")
    class RejectedStates {

        @Test
        @DisplayName("DENIED consent produces deny decision [GH-90000]")
        void shouldDenyWhenConsentDenied() { // GH-90000
            AepEngine.Event event = eventWithStatus(AepEngine.ConsentStatus.DENIED); // GH-90000
            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                () -> service.evaluateConsent(TENANT, event)); // GH-90000
            assertThat(decision.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("EXPIRED consent produces deny decision [GH-90000]")
        void shouldDenyWhenConsentExpired() { // GH-90000
            AepEngine.Event event = eventWithStatus(AepEngine.ConsentStatus.EXPIRED); // GH-90000
            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                () -> service.evaluateConsent(TENANT, event)); // GH-90000
            assertThat(decision.allowed()).isFalse(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Granted
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Granted consent states [GH-90000]")
    class GrantedStates {

        @Test
        @DisplayName("GRANTED with no allowed-purposes list allows processing [GH-90000]")
        void shouldAllowGrantedWithNoPurposes() { // GH-90000
            AepEngine.Event event = eventWith(AepEngine.ConsentStatus.GRANTED, List.of()); // GH-90000
            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                () -> service.evaluateConsent(TENANT, event)); // GH-90000
            assertThat(decision.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("GRANTED with event_processing in allowed purposes allows processing [GH-90000]")
        void shouldAllowGrantedWithEventProcessingPurpose() { // GH-90000
            AepEngine.Event event = eventWith( // GH-90000
                AepEngine.ConsentStatus.GRANTED, List.of("event_processing", "analytics")); // GH-90000
            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                () -> service.evaluateConsent(TENANT, event)); // GH-90000
            assertThat(decision.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("GRANTED without event_processing in non-empty allowed purposes denies [GH-90000]")
        void shouldDenyGrantedWhenPurposeMissing() { // GH-90000
            AepEngine.Event event = eventWith( // GH-90000
                AepEngine.ConsentStatus.GRANTED, List.of("analytics", "reporting")); // GH-90000
            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                () -> service.evaluateConsent(TENANT, event)); // GH-90000
            assertThat(decision.allowed()).isFalse(); // GH-90000
            assertThat(decision.reason()).contains("purpose [GH-90000]");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Unknown
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown consent state [GH-90000]")
    class UnknownState {

        @Test
        @DisplayName("UNKNOWN with no allowed-purposes allows processing (permissive default) [GH-90000]")
        void shouldAllowUnknownWithNoPurposes() { // GH-90000
            AepEngine.Event event = eventWith(AepEngine.ConsentStatus.UNKNOWN, List.of()); // GH-90000
            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                () -> service.evaluateConsent(TENANT, event)); // GH-90000
            assertThat(decision.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("UNKNOWN with non-empty purposes list that includes event_processing allows [GH-90000]")
        void shouldAllowUnknownWithEventProcessingPurpose() { // GH-90000
            AepEngine.Event event = eventWith( // GH-90000
                AepEngine.ConsentStatus.UNKNOWN, List.of("event_processing [GH-90000]"));
            ConsentService.ConsentDecision decision = runPromise( // GH-90000
                () -> service.evaluateConsent(TENANT, event)); // GH-90000
            assertThat(decision.allowed()).isTrue(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ConsentDecision factory methods
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ConsentDecision value type [GH-90000]")
    class ConsentDecisionFactories {

        @Test
        @DisplayName("allow() produces an allowed decision with empty allowed-purposes [GH-90000]")
        void allowFactoryShouldBeAllowed() { // GH-90000
            ConsentService.ConsentDecision d = ConsentService.ConsentDecision.allow(); // GH-90000
            assertThat(d.allowed()).isTrue(); // GH-90000
            assertThat(d.allowedPurposes()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("allow(purposes) produces an allowed decision with purposes list [GH-90000]")
        void allowWithPurposesShouldBeAllowed() { // GH-90000
            ConsentService.ConsentDecision d = ConsentService.ConsentDecision.allow( // GH-90000
                List.of("analytics", "event_processing")); // GH-90000
            assertThat(d.allowed()).isTrue(); // GH-90000
            assertThat(d.allowedPurposes()).contains("analytics", "event_processing"); // GH-90000
        }

        @Test
        @DisplayName("deny(reason) produces a denied decision with a reason [GH-90000]")
        void denyFactoryShouldBeDenied() { // GH-90000
            ConsentService.ConsentDecision d = ConsentService.ConsentDecision.deny("GDPR opt-out [GH-90000]");
            assertThat(d.allowed()).isFalse(); // GH-90000
            assertThat(d.reason()).isEqualTo("GDPR opt-out [GH-90000]");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static AepEngine.Event eventWithStatus(AepEngine.ConsentStatus status) { // GH-90000
        return eventWith(status, List.of()); // GH-90000
    }

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
