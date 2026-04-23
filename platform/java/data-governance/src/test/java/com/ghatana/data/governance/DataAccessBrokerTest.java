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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link DefaultDataAccessBroker}.
 */
@DisplayName("DefaultDataAccessBroker")
class DataAccessBrokerTest extends EventloopTestBase {

    private InMemoryConsentManager consent;
    private DefaultPurposeLimitationEnforcer purposeEnforcer;
    private DefaultDataAccessBroker broker;

    @BeforeEach
    void setUp() { // GH-90000
        consent = new InMemoryConsentManager(); // GH-90000
        purposeEnforcer = new DefaultPurposeLimitationEnforcer(); // GH-90000
        broker = new DefaultDataAccessBroker(consent, purposeEnforcer); // GH-90000
    }

    @Nested
    @DisplayName("checkAccess — granted")
    class GrantedTests {

        @Test
        @DisplayName("grants access when both consent and purpose binding are satisfied")
        void grantsAccess() { // GH-90000
            runPromise(() -> consent.recordConsent("t1", "user1", "analytics")); // GH-90000
            runPromise(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));

            assertThatCode(() -> // GH-90000
                runPromise(() -> broker.checkAccess("t1", "user1", "email-data", "analytics")) // GH-90000
            ).doesNotThrowAnyException(); // GH-90000
        }
    }

    @Nested
    @DisplayName("checkAccess — denied")
    class DeniedTests {

        @Test
        @DisplayName("denies when consent is missing (fails before checking purpose)")
        void deniesWithoutConsent() { // GH-90000
            runPromise(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> broker.checkAccess("t1", "user1", "email-data", "analytics")) // GH-90000
            ).isInstanceOf(ConsentRequiredException.class); // GH-90000
        }

        @Test
        @DisplayName("denies when purpose binding is missing even with consent")
        void deniesWithoutPurposeBinding() { // GH-90000
            runPromise(() -> consent.recordConsent("t1", "user1", "analytics")); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> broker.checkAccess("t1", "user1", "email-data", "analytics")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class); // GH-90000
        }

        @Test
        @DisplayName("denies when purpose is not in binding")
        void deniesForDisallowedPurpose() { // GH-90000
            runPromise(() -> consent.recordConsent("t1", "user1", "marketing")); // GH-90000
            runPromise(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> broker.checkAccess("t1", "user1", "email-data", "marketing")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class); // GH-90000
        }

        @Test
        @DisplayName("tenant isolation: consent from tenantA does not satisfy tenantB check")
        void tenantIsolation() { // GH-90000
            runPromise(() -> consent.recordConsent("tenantA", "user1", "analytics")); // GH-90000
            runPromise(() -> purposeEnforcer.bindPurpose("tenantA", "email-data", Set.of("analytics")));

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> broker.checkAccess("tenantB", "user1", "email-data", "analytics")) // GH-90000
            ).isInstanceOf(ConsentRequiredException.class); // GH-90000
        }
    }
}
