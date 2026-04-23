/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 Expansion tests for {@link Governance}.
 * Tests complex access policies, retention strategies, and data classification at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for platform data governance
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Governance - Phase 3 Expansion")
class GovernanceExpansionTest {

    // ============================================
    // COMPLEX ACCESS POLICIES (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Complex Access Policies")
    class AccessPolicyTests {

        @Test
        @DisplayName("Many authorized producers can be added sequentially")
        void manyProducersSequential() { // GH-90000
            Governance.Builder builder = Governance.builder().withOwner("platform-team");

            for (int i = 0; i < 50; i++) { // GH-90000
                builder.addAuthorizedProducer("producer-" + i); // GH-90000
            }

            Governance gov = builder.build(); // GH-90000

            assertThat(gov.getAuthorizedProducers()).hasSize(50); // GH-90000
            assertThat(gov.getAuthorizedProducers()) // GH-90000
                .contains("producer-0", "producer-25", "producer-49"); // GH-90000
        }

        @Test
        @DisplayName("Many authorized consumers can be added sequentially")
        void manyConsumersSequential() { // GH-90000
            Governance.Builder builder = Governance.builder().withOwner("data-team");

            for (int i = 0; i < 50; i++) { // GH-90000
                builder.addAuthorizedConsumer("consumer-" + i); // GH-90000
            }

            Governance gov = builder.build(); // GH-90000

            assertThat(gov.getAuthorizedConsumers()).hasSize(50); // GH-90000
            assertThat(gov.getAuthorizedConsumers()) // GH-90000
                .contains("consumer-0", "consumer-25", "consumer-49"); // GH-90000
        }

        @Test
        @DisplayName("Complex multi-producer, multi-consumer policy enforces separation")
        void complexMultiPartyPolicy() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("central-data")
                    .withClassification(DataClassification.CONFIDENTIAL) // GH-90000
                    .addAuthorizedProducers( // GH-90000
                        Set.of("analytics-prod", "ml-pipeline", "bi-system")) // GH-90000
                    .addAuthorizedConsumer("data-lake")
                    .addAuthorizedConsumer("reporting-service")
                    .addAuthorizedConsumer("ml-inference")
                    .withApprovalRequired(true) // GH-90000
                    .withApprovalWorkflow("security-and-compliance")
                    .build(); // GH-90000

            assertThat(gov.getAuthorizedProducers()).hasSize(3); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).hasSize(3); // GH-90000
            assertThat(gov.isApprovalRequired()).isTrue(); // GH-90000
            assertThat(gov.getApprovalWorkflow()).isEqualTo("security-and-compliance");
        }

        @Test
        @DisplayName("Producer and consumer sets are independent")
        void producerConsumerIndependence() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team")
                    .addAuthorizedProducer("svc-a")
                    .addAuthorizedProducer("svc-b")
                    .addAuthorizedConsumer("analytics")
                    .addAuthorizedConsumer("reporting")
                    .addAuthorizedConsumer("ml-system")
                    .build(); // GH-90000

            assertThat(gov.getAuthorizedProducers()).hasSize(2); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).hasSize(3); // GH-90000
            assertThat(gov.getAuthorizedProducers()) // GH-90000
                .doesNotContainAnyElementsOf(gov.getAuthorizedConsumers()); // GH-90000
        }

        @Test
        @DisplayName("Duplicate producers/consumers are normalized")
        void duplicateNormalization() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team")
                    .addAuthorizedProducer("svc-1")
                    .addAuthorizedProducer("svc-1")
                    .addAuthorizedProducer("svc-1")
                    .addAuthorizedConsumer("reader")
                    .addAuthorizedConsumer("reader")
                    .build(); // GH-90000

            assertThat(gov.getAuthorizedProducers()).hasSize(1); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).hasSize(1); // GH-90000
        }
    }

    // ============================================
    // RETENTION POLICY VARIATIONS (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Retention Policy Variations")
    class RetentionTests {

        @Test
        @DisplayName("Very short retention period (days)")
        void shortRetentionPeriod() { // GH-90000
            RetentionPolicy policy = RetentionPolicy.builder() // GH-90000
                    .withRetentionPeriod(Duration.ofDays(1)) // GH-90000
                    .withArchiveBeforeDeletion(false) // GH-90000
                    .build(); // GH-90000

            Governance gov = Governance.builder() // GH-90000
                    .withOwner("ephemeral-data")
                    .withRetentionPolicy(policy) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.getRetentionPolicy()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Very long retention period (years)")
        void longRetentionPeriod() { // GH-90000
            RetentionPolicy policy = RetentionPolicy.builder() // GH-90000
                    .withRetentionPeriod(Duration.ofDays(365 * 7)) // GH-90000
                    .withArchiveBeforeDeletion(true) // GH-90000
                    .withArchiveLocation("s3://long-term-archive")
                    .build(); // GH-90000

            Governance gov = Governance.builder() // GH-90000
                    .withOwner("historical-data")
                    .withRetentionPolicy(policy) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.getRetentionPolicy()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Multiple governance objects with different retention periods")
        void multipleRetentionPolicies() { // GH-90000
            RetentionPolicy shortTerm = RetentionPolicy.builder() // GH-90000
                    .withRetentionPeriod(Duration.ofDays(30)) // GH-90000
                    .withArchiveBeforeDeletion(false) // GH-90000
                    .build(); // GH-90000

            RetentionPolicy longTerm = RetentionPolicy.builder() // GH-90000
                    .withRetentionPeriod(Duration.ofDays(365)) // GH-90000
                    .withArchiveBeforeDeletion(true) // GH-90000
                    .withArchiveLocation("s3://archive")
                    .build(); // GH-90000

            Governance shortGov = Governance.builder() // GH-90000
                    .withOwner("transient-data")
                    .withRetentionPolicy(shortTerm) // GH-90000
                    .build(); // GH-90000

            Governance longGov = Governance.builder() // GH-90000
                    .withOwner("permanent-data")
                    .withRetentionPolicy(longTerm) // GH-90000
                    .build(); // GH-90000

            assertThat(shortGov.getRetentionPolicy()).isNotEqualTo(longGov.getRetentionPolicy()); // GH-90000
        }

        @Test
        @DisplayName("Archive location with complex path works")
        void complexArchiveLocation() { // GH-90000
            String location = "s3://corporate-archive/region-us-west/dept-analytics/2026/q1";
            RetentionPolicy policy = RetentionPolicy.builder() // GH-90000
                    .withRetentionPeriod(Duration.ofDays(365)) // GH-90000
                    .withArchiveBeforeDeletion(true) // GH-90000
                    .withArchiveLocation(location) // GH-90000
                    .build(); // GH-90000

            Governance gov = Governance.builder() // GH-90000
                    .withOwner("analytics-team")
                    .withRetentionPolicy(policy) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.getRetentionPolicy()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("No retention policy set creates valid governance")
        void noRetentionPolicy() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team-without-retention")
                    .build(); // GH-90000

            // Builder automatically sets default retention policy
            assertThat(gov.getRetentionPolicy()).isNotNull(); // GH-90000
            assertThat(gov.getRetentionPolicy()).isEqualTo(RetentionPolicy.defaults()); // GH-90000
        }
    }

    // ============================================
    // CLASSIFICATION LEVELS (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Data Classification Levels")
    class ClassificationTests {

        @Test
        @DisplayName("All classification levels are assignable")
        void allClassificationLevels() { // GH-90000
            for (DataClassification level : DataClassification.values()) { // GH-90000
                Governance gov = Governance.builder() // GH-90000
                        .withOwner("team-" + level.name()) // GH-90000
                        .withClassification(level) // GH-90000
                        .build(); // GH-90000

                assertThat(gov.getClassification()).isEqualTo(level); // GH-90000
            }
        }

        @Test
        @DisplayName("Public data allows many consumers and producers")
        void publicDataPolicy() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("public-data-team")
                    .withClassification(DataClassification.PUBLIC) // GH-90000
                    .addAuthorizedProducers( // GH-90000
                        Set.of("website", "mobile-app", "documentation")) // GH-90000
                    .addAuthorizedConsumers( // GH-90000
                        Set.of("anyone", "external-partners", "public-api")) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.getClassification()).isEqualTo(DataClassification.PUBLIC); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).hasSizeGreaterThanOrEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Sensitive data requires approvals")
        void sensitiveDataPolicy() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("sensitive-team")
                    .withClassification(DataClassification.SENSITIVE) // GH-90000
                    .withApprovalRequired(true) // GH-90000
                    .withApprovalWorkflow("security-review")
                    .addAuthorizedProducers(Set.of("hr-system"))
                    .addAuthorizedConsumer("payroll-service")
                    .build(); // GH-90000

            assertThat(gov.getClassification()).isEqualTo(DataClassification.SENSITIVE); // GH-90000
            assertThat(gov.isApprovalRequired()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Confidential data has strict access controls")
        void confidentialDataPolicy() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("security-team")
                    .withClassification(DataClassification.CONFIDENTIAL) // GH-90000
                    .withApprovalRequired(true) // GH-90000
                    .withApprovalWorkflow("security-audit-board")
                    .addAuthorizedProducer("security-system")
                    .addAuthorizedConsumer("ciso-office")
                    .addAuthorizedConsumer("audit-team")
                    .build(); // GH-90000

            assertThat(gov.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).hasSizeLessThanOrEqualTo(2); // GH-90000
        }
    }

    // ============================================
    // APPROVAL WORKFLOWS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Approval Workflows")
    class ApprovalWorkflowTests {

        @Test
        @DisplayName("Approval not required creates default policy")
        void noApprovalRequired() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team")
                    .withApprovalRequired(false) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.isApprovalRequired()).isFalse(); // GH-90000
            assertThat(gov.getApprovalWorkflow()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Multiple governance policies with different approval workflows")
        void multipleApprovalWorkflows() { // GH-90000
            Governance gov1 = Governance.builder() // GH-90000
                    .withOwner("team-1")
                    .withApprovalRequired(true) // GH-90000
                    .withApprovalWorkflow("tech-review")
                    .build(); // GH-90000

            Governance gov2 = Governance.builder() // GH-90000
                    .withOwner("team-2")
                    .withApprovalRequired(true) // GH-90000
                    .withApprovalWorkflow("security-review")
                    .build(); // GH-90000

            Governance gov3 = Governance.builder() // GH-90000
                    .withOwner("team-3")
                    .withApprovalRequired(true) // GH-90000
                    .withApprovalWorkflow("compliance-review")
                    .build(); // GH-90000

            assertThat(gov1.getApprovalWorkflow()).isEqualTo("tech-review");
            assertThat(gov2.getApprovalWorkflow()).isEqualTo("security-review");
            assertThat(gov3.getApprovalWorkflow()).isEqualTo("compliance-review");
        }

        @Test
        @DisplayName("Custom approval workflow names work")
        void customWorkflowNames() { // GH-90000
            String[] workflows = {
                "2-level-approval",
                "compliance@security@management",
                "fast-track-technical-only"
            };

            for (String workflow : workflows) { // GH-90000
                Governance gov = Governance.builder() // GH-90000
                        .withOwner("org-" + workflow) // GH-90000
                        .withApprovalRequired(true) // GH-90000
                        .withApprovalWorkflow(workflow) // GH-90000
                        .build(); // GH-90000

                assertThat(gov.getApprovalWorkflow()).isEqualTo(workflow); // GH-90000
            }
        }
    }

    // ============================================
    // IMMUTABILITY & EQUALITY (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Immutability & Equality")
    class ImmutabilityEqualityTests {

        @Test
        @DisplayName("Returned collections are unmodifiable and equality stable")
        void immutableCollectionsAndStableEquality() { // GH-90000
            Governance gov1 = Governance.builder() // GH-90000
                    .withOwner("team-a")
                    .addAuthorizedProducers(Set.of("svc-1", "svc-2")) // GH-90000
                    .addAuthorizedConsumers(Set.of("reader-1"))
                    .build(); // GH-90000

            Governance gov2 = Governance.builder() // GH-90000
                    .withOwner("team-a")
                    .addAuthorizedProducers(Set.of("svc-1", "svc-2")) // GH-90000
                    .addAuthorizedConsumers(Set.of("reader-1"))
                    .build(); // GH-90000

            assertThat(gov1).isEqualTo(gov2); // GH-90000
            assertThat(gov1.hashCode()).isEqualTo(gov2.hashCode()); // GH-90000

            // Collections are unmodifiable
            assertThatThrownBy(() -> gov1.getAuthorizedProducers().add("svc-3"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
            assertThatThrownBy(() -> gov1.getAuthorizedConsumers().add("reader-2"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("Governance with many producers/consumers maintains equality")
        void largeGovernanceEquality() { // GH-90000
            Governance.Builder builder1 = Governance.builder().withOwner("large-team");
            Governance.Builder builder2 = Governance.builder().withOwner("large-team");

            for (int i = 0; i < 25; i++) { // GH-90000
                builder1.addAuthorizedProducer("producer-" + i); // GH-90000
                builder2.addAuthorizedProducer("producer-" + i); // GH-90000
                builder1.addAuthorizedConsumer("consumer-" + i); // GH-90000
                builder2.addAuthorizedConsumer("consumer-" + i); // GH-90000
            }

            Governance gov1 = builder1.build(); // GH-90000
            Governance gov2 = builder2.build(); // GH-90000

            assertThat(gov1).isEqualTo(gov2); // GH-90000
        }

        @Test
        @DisplayName("Different classifications make objects not equal")
        void classificationEqualityImpact() { // GH-90000
            Governance gov1 = Governance.builder() // GH-90000
                    .withOwner("team")
                    .withClassification(DataClassification.PUBLIC) // GH-90000
                    .build(); // GH-90000

            Governance gov2 = Governance.builder() // GH-90000
                    .withOwner("team")
                    .withClassification(DataClassification.CONFIDENTIAL) // GH-90000
                    .build(); // GH-90000

            assertThat(gov1).isNotEqualTo(gov2); // GH-90000
        }

        @Test
        @DisplayName("Hash codes consistent across multiple retrievals")
        void hashCodeConsistency() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team")
                    .addAuthorizedProducers(Set.of("p1", "p2", "p3")) // GH-90000
                    .addAuthorizedConsumer("c1")
                    .build(); // GH-90000

            int hash1 = gov.hashCode(); // GH-90000
            int hash2 = gov.hashCode(); // GH-90000
            int hash3 = gov.hashCode(); // GH-90000

            assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Owner with special characters works")
        void specialCharacterOwners() { // GH-90000
            String[] owners = {
                "team-with-dash",
                "team_with_underscore",
                "team.with.dots",
                "team@domain.com"
            };

            for (String owner : owners) { // GH-90000
                Governance gov = Governance.builder().withOwner(owner).build(); // GH-90000
                assertThat(gov.getOwner()).isEqualTo(owner); // GH-90000
            }
        }

        @Test
        @DisplayName("Very long owner name accepted")
        void longOwnerName() { // GH-90000
            String longOwner = "very-long-organizational-unit-" + "x".repeat(200); // GH-90000
            Governance gov = Governance.builder().withOwner(longOwner).build(); // GH-90000

            assertThat(gov.getOwner()).isEqualTo(longOwner); // GH-90000
        }

        @Test
        @DisplayName("Empty producers and consumers list is valid state")
        void emptyAccessLists() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("restricted-team")
                    .build(); // GH-90000

            assertThat(gov.getAuthorizedProducers()).isEmpty(); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Minimum duration retention is supported")
        void minimumDurationRetention() { // GH-90000
            RetentionPolicy policy = RetentionPolicy.builder() // GH-90000
                    .withRetentionPeriod(Duration.ofHours(1)) // GH-90000
                    .withArchiveBeforeDeletion(false) // GH-90000
                    .build(); // GH-90000

            Governance gov = Governance.builder() // GH-90000
                    .withOwner("ephemeral-events")
                    .withRetentionPolicy(policy) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.getRetentionPolicy()).isNotNull(); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT BUILDING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Building")
    class ConcurrentBuildingTests {

        @Test
        @DisplayName("Many threads building governance policies independently")
        void concurrentPolicyBuilding() throws Exception { // GH-90000
            int threadCount = 20;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000
            List<Governance> governances = new ArrayList<>(); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    final int idx = i;
                    executor.submit(() -> { // GH-90000
                        try {
                            Governance gov = Governance.builder() // GH-90000
                                    .withOwner("team-" + idx) // GH-90000
                                    .withClassification( // GH-90000
                                        DataClassification.values()[idx % DataClassification.values().length]) // GH-90000
                                    .addAuthorizedProducer("producer-" + idx) // GH-90000
                                    .addAuthorizedConsumer("consumer-" + idx) // GH-90000
                                    .build(); // GH-90000

                            synchronized (governances) { // GH-90000
                                governances.add(gov); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(governances).hasSize(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Rapid governance creation maintains integrity")
        void rapidCreation() { // GH-90000
            AtomicInteger createdCount = new AtomicInteger(0); // GH-90000

            for (int i = 0; i < 500; i++) { // GH-90000
                final int idx = i;
                Governance gov = Governance.builder() // GH-90000
                        .withOwner("rapid-team-" + idx) // GH-90000
                        .addAuthorizedProducer("prod-" + idx) // GH-90000
                        .build(); // GH-90000

                if (gov.getOwner() != null) { // GH-90000
                    createdCount.incrementAndGet(); // GH-90000
                }
            }

            assertThat(createdCount.get()).isEqualTo(500); // GH-90000
        }
    }
}
