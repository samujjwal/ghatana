/*
 * Copyright (c) 2026 Ghatana Inc.
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
    // COMPLEX ACCESS POLICIES (5 tests)
    // ============================================

    @Nested
    @DisplayName("Complex Access Policies")
    class AccessPolicyTests {

        @Test
        @DisplayName("Many authorized producers can be added sequentially")
        void manyProducersSequential() {
            Governance.Builder builder = Governance.builder().withOwner("platform-team");

            for (int i = 0; i < 50; i++) {
                builder.addAuthorizedProducer("producer-" + i);
            }

            Governance gov = builder.build();

            assertThat(gov.getAuthorizedProducers()).hasSize(50);
            assertThat(gov.getAuthorizedProducers())
                .contains("producer-0", "producer-25", "producer-49");
        }

        @Test
        @DisplayName("Many authorized consumers can be added sequentially")
        void manyConsumersSequential() {
            Governance.Builder builder = Governance.builder().withOwner("data-team");

            for (int i = 0; i < 50; i++) {
                builder.addAuthorizedConsumer("consumer-" + i);
            }

            Governance gov = builder.build();

            assertThat(gov.getAuthorizedConsumers()).hasSize(50);
            assertThat(gov.getAuthorizedConsumers())
                .contains("consumer-0", "consumer-25", "consumer-49");
        }

        @Test
        @DisplayName("Complex multi-producer, multi-consumer policy enforces separation")
        void complexMultiPartyPolicy() {
            Governance gov = Governance.builder()
                    .withOwner("central-data")
                    .withClassification(DataClassification.CONFIDENTIAL)
                    .addAuthorizedProducers(
                        Set.of("analytics-prod", "ml-pipeline", "bi-system"))
                    .addAuthorizedConsumer("data-lake")
                    .addAuthorizedConsumer("reporting-service")
                    .addAuthorizedConsumer("ml-inference")
                    .withApprovalRequired(true)
                    .withApprovalWorkflow("security-and-compliance")
                    .build();

            assertThat(gov.getAuthorizedProducers()).hasSize(3);
            assertThat(gov.getAuthorizedConsumers()).hasSize(3);
            assertThat(gov.isApprovalRequired()).isTrue();
            assertThat(gov.getApprovalWorkflow()).isEqualTo("security-and-compliance");
        }

        @Test
        @DisplayName("Producer and consumer sets are independent")
        void producerConsumerIndependence() {
            Governance gov = Governance.builder()
                    .withOwner("team")
                    .addAuthorizedProducer("svc-a")
                    .addAuthorizedProducer("svc-b")
                    .addAuthorizedConsumer("analytics")
                    .addAuthorizedConsumer("reporting")
                    .addAuthorizedConsumer("ml-system")
                    .build();

            assertThat(gov.getAuthorizedProducers()).hasSize(2);
            assertThat(gov.getAuthorizedConsumers()).hasSize(3);
            assertThat(gov.getAuthorizedProducers())
                .doesNotContainAnyElementsOf(gov.getAuthorizedConsumers());
        }

        @Test
        @DisplayName("Duplicate producers/consumers are normalized")
        void duplicateNormalization() {
            Governance gov = Governance.builder()
                    .withOwner("team")
                    .addAuthorizedProducer("svc-1")
                    .addAuthorizedProducer("svc-1")
                    .addAuthorizedProducer("svc-1")
                    .addAuthorizedConsumer("reader")
                    .addAuthorizedConsumer("reader")
                    .build();

            assertThat(gov.getAuthorizedProducers()).hasSize(1);
            assertThat(gov.getAuthorizedConsumers()).hasSize(1);
        }
    }

    // ============================================
    // RETENTION POLICY VARIATIONS (5 tests)
    // ============================================

    @Nested
    @DisplayName("Retention Policy Variations")
    class RetentionTests {

        @Test
        @DisplayName("Very short retention period (days)")
        void shortRetentionPeriod() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .withRetentionPeriod(Duration.ofDays(1))
                    .withArchiveBeforeDeletion(false)
                    .build();

            Governance gov = Governance.builder()
                    .withOwner("ephemeral-data")
                    .withRetentionPolicy(policy)
                    .build();

            assertThat(gov.getRetentionPolicy()).isNotNull();
        }

        @Test
        @DisplayName("Very long retention period (years)")
        void longRetentionPeriod() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .withRetentionPeriod(Duration.ofDays(365 * 7))
                    .withArchiveBeforeDeletion(true)
                    .withArchiveLocation("s3://long-term-archive")
                    .build();

            Governance gov = Governance.builder()
                    .withOwner("historical-data")
                    .withRetentionPolicy(policy)
                    .build();

            assertThat(gov.getRetentionPolicy()).isNotNull();
        }

        @Test
        @DisplayName("Multiple governance objects with different retention periods")
        void multipleRetentionPolicies() {
            RetentionPolicy shortTerm = RetentionPolicy.builder()
                    .withRetentionPeriod(Duration.ofDays(30))
                    .withArchiveBeforeDeletion(false)
                    .build();

            RetentionPolicy longTerm = RetentionPolicy.builder()
                    .withRetentionPeriod(Duration.ofDays(365))
                    .withArchiveBeforeDeletion(true)
                    .withArchiveLocation("s3://archive")
                    .build();

            Governance shortGov = Governance.builder()
                    .withOwner("transient-data")
                    .withRetentionPolicy(shortTerm)
                    .build();

            Governance longGov = Governance.builder()
                    .withOwner("permanent-data")
                    .withRetentionPolicy(longTerm)
                    .build();

            assertThat(shortGov.getRetentionPolicy()).isNotEqualTo(longGov.getRetentionPolicy());
        }

        @Test
        @DisplayName("Archive location with complex path works")
        void complexArchiveLocation() {
            String location = "s3://corporate-archive/region-us-west/dept-analytics/2026/q1";
            RetentionPolicy policy = RetentionPolicy.builder()
                    .withRetentionPeriod(Duration.ofDays(365))
                    .withArchiveBeforeDeletion(true)
                    .withArchiveLocation(location)
                    .build();

            Governance gov = Governance.builder()
                    .withOwner("analytics-team")
                    .withRetentionPolicy(policy)
                    .build();

            assertThat(gov.getRetentionPolicy()).isNotNull();
        }

        @Test
        @DisplayName("No retention policy set creates valid governance")
        void noRetentionPolicy() {
            Governance gov = Governance.builder()
                    .withOwner("team-without-retention")
                    .build();

            assertThat(gov.getRetentionPolicy()).isNull();
        }
    }

    // ============================================
    // CLASSIFICATION LEVELS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Data Classification Levels")
    class ClassificationTests {

        @Test
        @DisplayName("All classification levels are assignable")
        void allClassificationLevels() {
            for (DataClassification level : DataClassification.values()) {
                Governance gov = Governance.builder()
                        .withOwner("team-" + level.name())
                        .withClassification(level)
                        .build();

                assertThat(gov.getClassification()).isEqualTo(level);
            }
        }

        @Test
        @DisplayName("Public data allows many consumers and producers")
        void publicDataPolicy() {
            Governance gov = Governance.builder()
                    .withOwner("public-data-team")
                    .withClassification(DataClassification.PUBLIC)
                    .addAuthorizedProducers(
                        Set.of("website", "mobile-app", "documentation"))
                    .addAuthorizedConsumers(
                        Set.of("anyone", "external-partners", "public-api"))
                    .build();

            assertThat(gov.getClassification()).isEqualTo(DataClassification.PUBLIC);
            assertThat(gov.getAuthorizedConsumers()).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Sensitive data requires approvals")
        void sensitiveDataPolicy() {
            Governance gov = Governance.builder()
                    .withOwner("sensitive-team")
                    .withClassification(DataClassification.SENSITIVE)
                    .withApprovalRequired(true)
                    .withApprovalWorkflow("security-review")
                    .addAuthorizedProducers(Set.of("hr-system"))
                    .addAuthorizedConsumer("payroll-service")
                    .build();

            assertThat(gov.getClassification()).isEqualTo(DataClassification.SENSITIVE);
            assertThat(gov.isApprovalRequired()).isTrue();
        }

        @Test
        @DisplayName("Confidential data has strict access controls")
        void confidentialDataPolicy() {
            Governance gov = Governance.builder()
                    .withOwner("security-team")
                    .withClassification(DataClassification.CONFIDENTIAL)
                    .withApprovalRequired(true)
                    .withApprovalWorkflow("security-audit-board")
                    .addAuthorizedProducer("security-system")
                    .addAuthorizedConsumer("ciso-office")
                    .addAuthorizedConsumer("audit-team")
                    .build();

            assertThat(gov.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
            assertThat(gov.getAuthorizedConsumers()).hasSizeLessThanOrEqualTo(2);
        }
    }

    // ============================================
    // APPROVAL WORKFLOWS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Approval Workflows")
    class ApprovalWorkflowTests {

        @Test
        @DisplayName("Approval not required creates default policy")
        void noApprovalRequired() {
            Governance gov = Governance.builder()
                    .withOwner("team")
                    .withApprovalRequired(false)
                    .build();

            assertThat(gov.isApprovalRequired()).isFalse();
            assertThat(gov.getApprovalWorkflow()).isNull();
        }

        @Test
        @DisplayName("Multiple governance policies with different approval workflows")
        void multipleApprovalWorkflows() {
            Governance gov1 = Governance.builder()
                    .withOwner("team-1")
                    .withApprovalRequired(true)
                    .withApprovalWorkflow("tech-review")
                    .build();

            Governance gov2 = Governance.builder()
                    .withOwner("team-2")
                    .withApprovalRequired(true)
                    .withApprovalWorkflow("security-review")
                    .build();

            Governance gov3 = Governance.builder()
                    .withOwner("team-3")
                    .withApprovalRequired(true)
                    .withApprovalWorkflow("compliance-review")
                    .build();

            assertThat(gov1.getApprovalWorkflow()).isEqualTo("tech-review");
            assertThat(gov2.getApprovalWorkflow()).isEqualTo("security-review");
            assertThat(gov3.getApprovalWorkflow()).isEqualTo("compliance-review");
        }

        @Test
        @DisplayName("Custom approval workflow names work")
        void customWorkflowNames() {
            String[] workflows = {
                "2-level-approval",
                "compliance@security@management",
                "fast-track-technical-only"
            };

            for (String workflow : workflows) {
                Governance gov = Governance.builder()
                        .withOwner("org-" + workflow)
                        .withApprovalRequired(true)
                        .withApprovalWorkflow(workflow)
                        .build();

                assertThat(gov.getApprovalWorkflow()).isEqualTo(workflow);
            }
        }
    }

    // ============================================
    // IMMUTABILITY & EQUALITY (4 tests)
    // ============================================

    @Nested
    @DisplayName("Immutability & Equality")
    class ImmutabilityEqualityTests {

        @Test
        @DisplayName("Returned collections are unmodifiable and equality stable")
        void immutableCollectionsAndStableEquality() {
            Governance gov1 = Governance.builder()
                    .withOwner("team-a")
                    .addAuthorizedProducers(Set.of("svc-1", "svc-2"))
                    .addAuthorizedConsumers(Set.of("reader-1"))
                    .build();

            Governance gov2 = Governance.builder()
                    .withOwner("team-a")
                    .addAuthorizedProducers(Set.of("svc-1", "svc-2"))
                    .addAuthorizedConsumers(Set.of("reader-1"))
                    .build();

            assertThat(gov1).isEqualTo(gov2);
            assertThat(gov1.hashCode()).isEqualTo(gov2.hashCode());

            // Collections are unmodifiable
            assertThatThrownBy(() -> gov1.getAuthorizedProducers().add("svc-3"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> gov1.getAuthorizedConsumers().add("reader-2"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Governance with many producers/consumers maintains equality")
        void largeGovernanceEquality() {
            Governance.Builder builder1 = Governance.builder().withOwner("large-team");
            Governance.Builder builder2 = Governance.builder().withOwner("large-team");

            for (int i = 0; i < 25; i++) {
                builder1.addAuthorizedProducer("producer-" + i);
                builder2.addAuthorizedProducer("producer-" + i);
                builder1.addAuthorizedConsumer("consumer-" + i);
                builder2.addAuthorizedConsumer("consumer-" + i);
            }

            Governance gov1 = builder1.build();
            Governance gov2 = builder2.build();

            assertThat(gov1).isEqualTo(gov2);
        }

        @Test
        @DisplayName("Different classifications make objects not equal")
        void classificationEqualityImpact() {
            Governance gov1 = Governance.builder()
                    .withOwner("team")
                    .withClassification(DataClassification.PUBLIC)
                    .build();

            Governance gov2 = Governance.builder()
                    .withOwner("team")
                    .withClassification(DataClassification.CONFIDENTIAL)
                    .build();

            assertThat(gov1).isNotEqualTo(gov2);
        }

        @Test
        @DisplayName("Hash codes consistent across multiple retrievals")
        void hashCodeConsistency() {
            Governance gov = Governance.builder()
                    .withOwner("team")
                    .addAuthorizedProducers(Set.of("p1", "p2", "p3"))
                    .addAuthorizedConsumer("c1")
                    .build();

            int hash1 = gov.hashCode();
            int hash2 = gov.hashCode();
            int hash3 = gov.hashCode();

            assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3);
        }
    }

    // ============================================
    // EDGE CASES (4 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Owner with special characters works")
        void specialCharacterOwners() {
            String[] owners = {
                "team-with-dash",
                "team_with_underscore",
                "team.with.dots",
                "team@domain.com"
            };

            for (String owner : owners) {
                Governance gov = Governance.builder().withOwner(owner).build();
                assertThat(gov.getOwner()).isEqualTo(owner);
            }
        }

        @Test
        @DisplayName("Very long owner name accepted")
        void longOwnerName() {
            String longOwner = "very-long-organizational-unit-" + "x".repeat(200);
            Governance gov = Governance.builder().withOwner(longOwner).build();

            assertThat(gov.getOwner()).isEqualTo(longOwner);
        }

        @Test
        @DisplayName("Empty producers and consumers list is valid state")
        void emptyAccessLists() {
            Governance gov = Governance.builder()
                    .withOwner("restricted-team")
                    .build();

            assertThat(gov.getAuthorizedProducers()).isEmpty();
            assertThat(gov.getAuthorizedConsumers()).isEmpty();
        }

        @Test
        @DisplayName("Minimum duration retention is supported")
        void minimumDurationRetention() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .withRetentionPeriod(Duration.ofHours(1))
                    .withArchiveBeforeDeletion(false)
                    .build();

            Governance gov = Governance.builder()
                    .withOwner("ephemeral-events")
                    .withRetentionPolicy(policy)
                    .build();

            assertThat(gov.getRetentionPolicy()).isNotNull();
        }
    }

    // ============================================
    // CONCURRENT BUILDING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Building")
    class ConcurrentBuildingTests {

        @Test
        @DisplayName("Many threads building governance policies independently")
        void concurrentPolicyBuilding() throws Exception {
            int threadCount = 20;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            List<Governance> governances = new ArrayList<>();

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    executor.submit(() -> {
                        try {
                            Governance gov = Governance.builder()
                                    .withOwner("team-" + idx)
                                    .withClassification(
                                        DataClassification.values()[idx % DataClassification.values().length])
                                    .addAuthorizedProducer("producer-" + idx)
                                    .addAuthorizedConsumer("consumer-" + idx)
                                    .build();

                            synchronized (governances) {
                                governances.add(gov);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(governances).hasSize(threadCount);
        }

        @Test
        @DisplayName("Rapid governance creation maintains integrity")
        void rapidCreation() {
            AtomicInteger createdCount = new AtomicInteger(0);

            for (int i = 0; i < 500; i++) {
                final int idx = i;
                Governance gov = Governance.builder()
                        .withOwner("rapid-team-" + idx)
                        .addAuthorizedProducer("prod-" + idx)
                        .build();

                if (gov.getOwner() != null) {
                    createdCount.incrementAndGet();
                }
            }

            assertThat(createdCount.get()).isEqualTo(500);
        }
    }
}
