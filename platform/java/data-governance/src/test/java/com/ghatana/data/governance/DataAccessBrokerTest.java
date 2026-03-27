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
    void setUp() {
        consent = new InMemoryConsentManager();
        purposeEnforcer = new DefaultPurposeLimitationEnforcer();
        broker = new DefaultDataAccessBroker(consent, purposeEnforcer);
    }

    @Nested
    @DisplayName("checkAccess — granted")
    class GrantedTests {

        @Test
        @DisplayName("grants access when both consent and purpose binding are satisfied")
        void grantsAccess() {
            runBlocking(() -> consent.recordConsent("t1", "user1", "analytics"));
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));

            assertThatCode(() ->
                runBlocking(() -> broker.checkAccess("t1", "user1", "email-data", "analytics"))
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("checkAccess — denied")
    class DeniedTests {

        @Test
        @DisplayName("denies when consent is missing (fails before checking purpose)")
        void deniesWithoutConsent() {
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));

            assertThatThrownBy(() ->
                runBlocking(() -> broker.checkAccess("t1", "user1", "email-data", "analytics"))
            ).isInstanceOf(ConsentRequiredException.class);
        }

        @Test
        @DisplayName("denies when purpose binding is missing even with consent")
        void deniesWithoutPurposeBinding() {
            runBlocking(() -> consent.recordConsent("t1", "user1", "analytics"));

            assertThatThrownBy(() ->
                runBlocking(() -> broker.checkAccess("t1", "user1", "email-data", "analytics"))
            ).isInstanceOf(PurposeViolationException.class);
        }

        @Test
        @DisplayName("denies when purpose is not in binding")
        void deniesForDisallowedPurpose() {
            runBlocking(() -> consent.recordConsent("t1", "user1", "marketing"));
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));

            assertThatThrownBy(() ->
                runBlocking(() -> broker.checkAccess("t1", "user1", "email-data", "marketing"))
            ).isInstanceOf(PurposeViolationException.class);
        }

        @Test
        @DisplayName("tenant isolation: consent from tenantA does not satisfy tenantB check")
        void tenantIsolation() {
            runBlocking(() -> consent.recordConsent("tenantA", "user1", "analytics"));
            runBlocking(() -> purposeEnforcer.bindPurpose("tenantA", "email-data", Set.of("analytics")));

            assertThatThrownBy(() ->
                runBlocking(() -> broker.checkAccess("tenantB", "user1", "email-data", "analytics"))
            ).isInstanceOf(ConsentRequiredException.class);
        }
    }
}
