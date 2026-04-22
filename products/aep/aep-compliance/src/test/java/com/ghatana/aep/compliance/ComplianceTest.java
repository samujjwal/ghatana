/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import com.ghatana.data.governance.ConsentRequiredException;
import com.ghatana.data.governance.DefaultDataAccessBroker;
import com.ghatana.data.governance.DefaultPurposeLimitationEnforcer;
import com.ghatana.data.governance.InMemoryConsentManager;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ComplianceService}, {@link InMemoryRetentionPolicyEnforcer}.
 */
@DisplayName("AEP Compliance [GH-90000]")
class ComplianceTest extends EventloopTestBase {

    private InMemoryConsentManager consent;
    private DefaultPurposeLimitationEnforcer purposeEnforcer;
    private InMemoryRetentionPolicyEnforcer retentionEnforcer;
    private ComplianceService complianceService;

    @BeforeEach
    void setUp() { // GH-90000
        consent = new InMemoryConsentManager(); // GH-90000
        purposeEnforcer = new DefaultPurposeLimitationEnforcer(); // GH-90000
        retentionEnforcer = new InMemoryRetentionPolicyEnforcer(); // GH-90000
        complianceService = new ComplianceService( // GH-90000
            new DefaultDataAccessBroker(consent, purposeEnforcer), // GH-90000
            retentionEnforcer);
    }

    @Nested
    @DisplayName("RetentionPolicyEnforcer [GH-90000]")
    class RetentionTests {

        @Test
        @DisplayName("unregistered data asset passes retention check (open policy) [GH-90000]")
        void unregisteredDataPassesCheck() { // GH-90000
            assertThatCode(() -> // GH-90000
                runBlocking(() -> retentionEnforcer.checkRetention("t1", "unknown-data")) // GH-90000
            ).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("data within retention period passes check [GH-90000]")
        void withinRetentionPasses() { // GH-90000
            runBlocking(() -> retentionEnforcer.registerRetention("t1", "data1", Duration.ofDays(30))); // GH-90000
            assertThatCode(() -> // GH-90000
                runBlocking(() -> retentionEnforcer.checkRetention("t1", "data1")) // GH-90000
            ).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("data scheduled for deletion throws RetentionExpiredException [GH-90000]")
        void scheduledForDeletionThrows() { // GH-90000
            runBlocking(() -> retentionEnforcer.scheduleDeletion("t1", "data1")); // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> retentionEnforcer.checkRetention("t1", "data1")) // GH-90000
            ).isInstanceOf(RetentionExpiredException.class) // GH-90000
             .satisfies(ex -> { // GH-90000
                 RetentionExpiredException e = (RetentionExpiredException) ex; // GH-90000
                 assertThat(e.tenantId()).isEqualTo("t1 [GH-90000]");
                 assertThat(e.dataId()).isEqualTo("data1 [GH-90000]");
             });
        }

        @Test
        @DisplayName("expired retention throws RetentionExpiredException [GH-90000]")
        void expiredRetentionThrows() { // GH-90000
            // Register with negative duration — immediately expired
            runBlocking(() -> retentionEnforcer.registerRetention( // GH-90000
                "t1", "old-data", Duration.ofMillis(-1))); // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> retentionEnforcer.checkRetention("t1", "old-data")) // GH-90000
            ).isInstanceOf(RetentionExpiredException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("ComplianceService.checkCompliance [GH-90000]")
    class ComplianceServiceTests {

        @Test
        @DisplayName("passes when consent, purpose binding, and retention are all satisfied [GH-90000]")
        void passesAllChecks() { // GH-90000
            runBlocking(() -> consent.recordConsent("t1", "user1", "analytics")); // GH-90000
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics [GH-90000]")));
            runBlocking(() -> retentionEnforcer.registerRetention("t1", "email-data", Duration.ofDays(90))); // GH-90000

            assertThatCode(() -> // GH-90000
                runBlocking(() -> complianceService.checkCompliance("t1", "user1", "email-data", "analytics")) // GH-90000
            ).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("fails when consent is absent [GH-90000]")
        void failsWithoutConsent() { // GH-90000
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics [GH-90000]")));
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> complianceService.checkCompliance("t1", "user1", "email-data", "analytics")) // GH-90000
            ).isInstanceOf(ConsentRequiredException.class); // GH-90000
        }

        @Test
        @DisplayName("fails when data is scheduled for deletion [GH-90000]")
        void failsWhenScheduledForDeletion() { // GH-90000
            runBlocking(() -> consent.recordConsent("t1", "user1", "analytics")); // GH-90000
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics [GH-90000]")));
            runBlocking(() -> retentionEnforcer.scheduleDeletion("t1", "email-data")); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> complianceService.checkCompliance("t1", "user1", "email-data", "analytics")) // GH-90000
            ).isInstanceOf(RetentionExpiredException.class); // GH-90000
        }
    }
}
