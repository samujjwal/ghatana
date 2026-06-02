/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import com.ghatana.data.governance.ConsentRequiredException;
import com.ghatana.data.governance.DefaultDataAccessBroker;
import com.ghatana.data.governance.DefaultPurposeLimitationEnforcer;
import com.ghatana.data.governance.InMemoryConsentManager;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
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
@DisplayName("AEP Compliance")
class ComplianceTest extends EventloopTestBase {

    private InMemoryConsentManager consent;
    private DefaultPurposeLimitationEnforcer purposeEnforcer;
    private InMemoryRetentionPolicyEnforcer retentionEnforcer;
    private ComplianceService complianceService;

    @BeforeEach
    void setUp() {
        consent = new InMemoryConsentManager();
        purposeEnforcer = new DefaultPurposeLimitationEnforcer();
        retentionEnforcer = new InMemoryRetentionPolicyEnforcer();
        ComplianceService.ComplianceEvidenceStore evidenceStore = new ComplianceService.ComplianceEvidenceStore() {
            @Override
            public Promise<Void> storeReviewEvidence(ComplianceService.ReviewEvidence evidence) {
                return Promise.complete();
            }
            @Override
            public Promise<Void> storeApprovalEvidence(ComplianceService.ApprovalEvidence evidence) {
                return Promise.complete();
            }
            @Override
            public Promise<Void> storeRollbackEvidence(ComplianceService.RollbackEvidence evidence) {
                return Promise.complete();
            }
            @Override
            public Promise<Void> storeLearningEvidence(ComplianceService.LearningEvidence evidence) {
                return Promise.complete();
            }
            @Override
            public Promise<java.util.List<ComplianceService.ComplianceEvidence>> retrieveEvidence(String tenantId, String entityId, ComplianceService.EntityType entityType) {
                return Promise.of(java.util.List.of());
            }
        };
        complianceService = new ComplianceService(
            new DefaultDataAccessBroker(consent, purposeEnforcer),
            retentionEnforcer,
            evidenceStore);
    }

    @Nested
    @DisplayName("RetentionPolicyEnforcer")
    class RetentionTests {

        @Test
        @DisplayName("unregistered data asset passes retention check (open policy)")
        void unregisteredDataPassesCheck() { 
            assertThatCode(() -> 
                runBlocking(() -> retentionEnforcer.checkRetention("t1", "unknown-data")) 
            ).doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("data within retention period passes check")
        void withinRetentionPasses() { 
            runBlocking(() -> retentionEnforcer.registerRetention("t1", "data1", Duration.ofDays(30))); 
            assertThatCode(() -> 
                runBlocking(() -> retentionEnforcer.checkRetention("t1", "data1")) 
            ).doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("data scheduled for deletion throws RetentionExpiredException")
        void scheduledForDeletionThrows() { 
            runBlocking(() -> retentionEnforcer.scheduleDeletion("t1", "data1")); 
            assertThatThrownBy(() -> 
                runPromise(() -> retentionEnforcer.checkRetention("t1", "data1")) 
            ).isInstanceOf(RetentionExpiredException.class) 
             .satisfies(ex -> { 
                 RetentionExpiredException e = (RetentionExpiredException) ex; 
                 assertThat(e.tenantId()).isEqualTo("t1");
                 assertThat(e.dataId()).isEqualTo("data1");
             });
        }

        @Test
        @DisplayName("expired retention throws RetentionExpiredException")
        void expiredRetentionThrows() { 
            // Register with negative duration — immediately expired
            runBlocking(() -> retentionEnforcer.registerRetention( 
                "t1", "old-data", Duration.ofMillis(-1))); 
            assertThatThrownBy(() -> 
                runPromise(() -> retentionEnforcer.checkRetention("t1", "old-data")) 
            ).isInstanceOf(RetentionExpiredException.class); 
        }
    }

    @Nested
    @DisplayName("ComplianceService.checkCompliance")
    class ComplianceServiceTests {

        @Test
        @DisplayName("passes when consent, purpose binding, and retention are all satisfied")
        void passesAllChecks() { 
            runBlocking(() -> consent.recordConsent("t1", "user1", "analytics")); 
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));
            runBlocking(() -> retentionEnforcer.registerRetention("t1", "email-data", Duration.ofDays(90))); 

            assertThatCode(() -> 
                runBlocking(() -> complianceService.checkCompliance("t1", "user1", "email-data", "analytics")) 
            ).doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("fails when consent is absent")
        void failsWithoutConsent() { 
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));
            assertThatThrownBy(() -> 
                runPromise(() -> complianceService.checkCompliance("t1", "user1", "email-data", "analytics")) 
            ).isInstanceOf(ConsentRequiredException.class); 
        }

        @Test
        @DisplayName("fails when data is scheduled for deletion")
        void failsWhenScheduledForDeletion() { 
            runBlocking(() -> consent.recordConsent("t1", "user1", "analytics")); 
            runBlocking(() -> purposeEnforcer.bindPurpose("t1", "email-data", Set.of("analytics")));
            runBlocking(() -> retentionEnforcer.scheduleDeletion("t1", "email-data")); 

            assertThatThrownBy(() -> 
                runPromise(() -> complianceService.checkCompliance("t1", "user1", "email-data", "analytics")) 
            ).isInstanceOf(RetentionExpiredException.class); 
        }
    }
}
