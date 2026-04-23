/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.data.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InMemoryConsentManager}.
 */
@DisplayName("InMemoryConsentManager")
class ConsentManagerTest extends EventloopTestBase {

    private InMemoryConsentManager consentManager;

    @BeforeEach
    void setUp() { // GH-90000
        consentManager = new InMemoryConsentManager(); // GH-90000
    }

    @Nested
    @DisplayName("recordConsent")
    class RecordConsentTests {

        @Test
        @DisplayName("records consent for a new subject+purpose pair")
        void recordsNewConsent() { // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); // GH-90000
            boolean result = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); // GH-90000
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("recording consent is idempotent")
        void recordConsentIdempotent() { // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); // GH-90000
            assertThat(consentManager.totalConsentedPairs()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("multiple purposes per subject are tracked independently")
        void multiplePurposesPerSubject() { // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "marketing")); // GH-90000

            boolean analytics = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); // GH-90000
            boolean marketing = runPromise(() -> consentManager.hasConsent("t1", "user1", "marketing")); // GH-90000

            assertThat(analytics).isTrue(); // GH-90000
            assertThat(marketing).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("different tenants are isolated")
        void tenantsAreIsolated() { // GH-90000
            runPromise(() -> consentManager.recordConsent("tenantA", "user1", "analytics")); // GH-90000
            boolean inA = runPromise(() -> consentManager.hasConsent("tenantA", "user1", "analytics")); // GH-90000
            boolean inB = runPromise(() -> consentManager.hasConsent("tenantB", "user1", "analytics")); // GH-90000

            assertThat(inA).isTrue(); // GH-90000
            assertThat(inB).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("withdrawConsent")
    class WithdrawConsentTests {

        @Test
        @DisplayName("withdrawing an existing consent removes it")
        void withdrawRemovesConsent() { // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); // GH-90000
            runPromise(() -> consentManager.withdrawConsent("t1", "user1", "analytics")); // GH-90000

            boolean hasConsent = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); // GH-90000
            assertThat(hasConsent).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("withdrawing a non-existing consent is a no-op")
        void withdrawNonExistingIsNoop() { // GH-90000
            runPromise(() -> consentManager.withdrawConsent("t1", "user1", "analytics")); // GH-90000
            assertThat(consentManager.totalConsentedPairs()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("withdrawing one purpose leaves other purposes intact")
        void withdrawOneDoesNotAffectOthers() { // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "marketing")); // GH-90000
            runPromise(() -> consentManager.withdrawConsent("t1", "user1", "analytics")); // GH-90000

            boolean analytics = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); // GH-90000
            boolean marketing = runPromise(() -> consentManager.hasConsent("t1", "user1", "marketing")); // GH-90000

            assertThat(analytics).isFalse(); // GH-90000
            assertThat(marketing).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("enforceConsent")
    class EnforceConsentTests {

        @Test
        @DisplayName("passes when consent exists")
        void passesWithConsent() { // GH-90000
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); // GH-90000
            runPromise(() -> consentManager.enforceConsent("t1", "user1", "analytics")); // GH-90000
        }

        @Test
        @DisplayName("throws ConsentRequiredException when consent is absent")
        void throwsWhenNoConsent() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> consentManager.enforceConsent("t1", "user1", "analytics")) // GH-90000
            ).isInstanceOf(ConsentRequiredException.class) // GH-90000
             .satisfies(ex -> { // GH-90000
                 ConsentRequiredException e = (ConsentRequiredException) ex; // GH-90000
                 assertThat(e.tenantId()).isEqualTo("t1");
                 assertThat(e.subjectId()).isEqualTo("user1");
                 assertThat(e.purpose()).isEqualTo("analytics");
             });
        }
    }
}
