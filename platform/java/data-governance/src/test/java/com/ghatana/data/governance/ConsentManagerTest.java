/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        consentManager = new InMemoryConsentManager(); 
    }

    @Nested
    @DisplayName("recordConsent")
    class RecordConsentTests {

        @Test
        @DisplayName("records consent for a new subject+purpose pair")
        void recordsNewConsent() { 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); 
            boolean result = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); 
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("recording consent is idempotent")
        void recordConsentIdempotent() { 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); 
            assertThat(consentManager.totalConsentedPairs()).isEqualTo(1); 
        }

        @Test
        @DisplayName("multiple purposes per subject are tracked independently")
        void multiplePurposesPerSubject() { 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "marketing")); 

            boolean analytics = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); 
            boolean marketing = runPromise(() -> consentManager.hasConsent("t1", "user1", "marketing")); 

            assertThat(analytics).isTrue(); 
            assertThat(marketing).isTrue(); 
        }

        @Test
        @DisplayName("different tenants are isolated")
        void tenantsAreIsolated() { 
            runPromise(() -> consentManager.recordConsent("tenantA", "user1", "analytics")); 
            boolean inA = runPromise(() -> consentManager.hasConsent("tenantA", "user1", "analytics")); 
            boolean inB = runPromise(() -> consentManager.hasConsent("tenantB", "user1", "analytics")); 

            assertThat(inA).isTrue(); 
            assertThat(inB).isFalse(); 
        }
    }

    @Nested
    @DisplayName("withdrawConsent")
    class WithdrawConsentTests {

        @Test
        @DisplayName("withdrawing an existing consent removes it")
        void withdrawRemovesConsent() { 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); 
            runPromise(() -> consentManager.withdrawConsent("t1", "user1", "analytics")); 

            boolean hasConsent = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); 
            assertThat(hasConsent).isFalse(); 
        }

        @Test
        @DisplayName("withdrawing a non-existing consent is a no-op")
        void withdrawNonExistingIsNoop() { 
            runPromise(() -> consentManager.withdrawConsent("t1", "user1", "analytics")); 
            assertThat(consentManager.totalConsentedPairs()).isEqualTo(0); 
        }

        @Test
        @DisplayName("withdrawing one purpose leaves other purposes intact")
        void withdrawOneDoesNotAffectOthers() { 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "marketing")); 
            runPromise(() -> consentManager.withdrawConsent("t1", "user1", "analytics")); 

            boolean analytics = runPromise(() -> consentManager.hasConsent("t1", "user1", "analytics")); 
            boolean marketing = runPromise(() -> consentManager.hasConsent("t1", "user1", "marketing")); 

            assertThat(analytics).isFalse(); 
            assertThat(marketing).isTrue(); 
        }
    }

    @Nested
    @DisplayName("enforceConsent")
    class EnforceConsentTests {

        @Test
        @DisplayName("passes when consent exists")
        void passesWithConsent() { 
            runPromise(() -> consentManager.recordConsent("t1", "user1", "analytics")); 
            runPromise(() -> consentManager.enforceConsent("t1", "user1", "analytics")); 
        }

        @Test
        @DisplayName("throws ConsentRequiredException when consent is absent")
        void throwsWhenNoConsent() { 
            assertThatThrownBy(() -> 
                runPromise(() -> consentManager.enforceConsent("t1", "user1", "analytics")) 
            ).isInstanceOf(ConsentRequiredException.class) 
             .satisfies(ex -> { 
                 ConsentRequiredException e = (ConsentRequiredException) ex; 
                 assertThat(e.tenantId()).isEqualTo("t1");
                 assertThat(e.subjectId()).isEqualTo("user1");
                 assertThat(e.purpose()).isEqualTo("analytics");
             });
        }
    }
}
