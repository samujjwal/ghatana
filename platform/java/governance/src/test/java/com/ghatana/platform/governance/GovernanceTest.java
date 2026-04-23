package com.ghatana.platform.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Governance} builder, value semantics, and defaults.
 */
@DisplayName("Governance")
class GovernanceTest {

    private RetentionPolicy validRetention() { // GH-90000
        return RetentionPolicy.builder() // GH-90000
                .withRetentionPeriod(Duration.ofDays(90)) // GH-90000
                .withArchiveBeforeDeletion(false) // GH-90000
                .build(); // GH-90000
    }

    private RetentionPolicy retentionWithArchive() { // GH-90000
        return RetentionPolicy.builder() // GH-90000
                .withRetentionPeriod(Duration.ofDays(365)) // GH-90000
                .withArchiveBeforeDeletion(true) // GH-90000
                .withArchiveLocation("s3://archive/events")
                .build(); // GH-90000
    }

    // ---- Builder ----

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds governance with all fields set")
        void allFieldsSet() { // GH-90000
            RetentionPolicy retention = retentionWithArchive(); // GH-90000

            Governance gov = Governance.builder() // GH-90000
                    .withOwner("platform-team")
                    .withClassification(DataClassification.CONFIDENTIAL) // GH-90000
                    .addAuthorizedProducer("service-a")
                    .addAuthorizedProducer("service-b")
                    .addAuthorizedConsumer("analytics")
                    .withApprovalRequired(true) // GH-90000
                    .withApprovalWorkflow("security-review")
                    .withRetentionPolicy(retention) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.getOwner()).isEqualTo("platform-team");
            assertThat(gov.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL); // GH-90000
            assertThat(gov.getAuthorizedProducers()).containsExactlyInAnyOrder("service-a", "service-b"); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).containsExactly("analytics");
            assertThat(gov.isApprovalRequired()).isTrue(); // GH-90000
            assertThat(gov.getApprovalWorkflow()).isEqualTo("security-review");
            assertThat(gov.getRetentionPolicy()).isEqualTo(retention); // GH-90000
        }

        @Test
        @DisplayName("builds governance with minimal required fields")
        void minimalFields() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("data-team")
                    .build(); // GH-90000

            assertThat(gov.getOwner()).isEqualTo("data-team");
            assertThat(gov.getClassification()).isEqualTo(DataClassification.INTERNAL); // GH-90000
            assertThat(gov.isApprovalRequired()).isFalse(); // GH-90000
            assertThat(gov.getAuthorizedProducers()).isEmpty(); // GH-90000
            assertThat(gov.getAuthorizedConsumers()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("throws when owner is null")
        void nullOwner_throws() { // GH-90000
            assertThatThrownBy(() -> Governance.builder().build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("Owner must be specified");
        }

        @Test
        @DisplayName("throws when owner is blank")
        void blankOwner_throws() { // GH-90000
            assertThatThrownBy(() -> Governance.builder().withOwner("   ").build())
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("Owner must be specified");
        }

        @Test
        @DisplayName("throws when classification is null")
        void nullClassification_throws() { // GH-90000
            assertThatThrownBy(() -> Governance.builder() // GH-90000
                    .withOwner("team")
                    .withClassification(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("addAuthorizedProducers accepts iterable")
        void addProducersFromIterable() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team")
                    .addAuthorizedProducers(Set.of("svc-1", "svc-2", "svc-3")) // GH-90000
                    .build(); // GH-90000

            assertThat(gov.getAuthorizedProducers()).hasSize(3); // GH-90000
        }
    }

    // ---- Immutability ----

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("authorized producers set is unmodifiable")
        void producersAreUnmodifiable() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team")
                    .addAuthorizedProducer("svc-a")
                    .build(); // GH-90000

            assertThatThrownBy(() -> gov.getAuthorizedProducers().add("svc-b"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("authorized consumers set is unmodifiable")
        void consumersAreUnmodifiable() { // GH-90000
            Governance gov = Governance.builder() // GH-90000
                    .withOwner("team")
                    .addAuthorizedConsumer("reader")
                    .build(); // GH-90000

            assertThatThrownBy(() -> gov.getAuthorizedConsumers().add("other"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ---- Equality ----

    @Nested
    @DisplayName("equals and hashCode")
    class EqualityTests {

        @Test
        @DisplayName("identical governance objects are equal")
        void identicalObjects_areEqual() { // GH-90000
            RetentionPolicy retention = validRetention(); // GH-90000

            Governance gov1 = Governance.builder() // GH-90000
                    .withOwner("team-alpha")
                    .withClassification(DataClassification.SENSITIVE) // GH-90000
                    .addAuthorizedProducer("svc")
                    .withRetentionPolicy(retention) // GH-90000
                    .build(); // GH-90000

            Governance gov2 = Governance.builder() // GH-90000
                    .withOwner("team-alpha")
                    .withClassification(DataClassification.SENSITIVE) // GH-90000
                    .addAuthorizedProducer("svc")
                    .withRetentionPolicy(retention) // GH-90000
                    .build(); // GH-90000

            assertThat(gov1).isEqualTo(gov2); // GH-90000
            assertThat(gov1.hashCode()).isEqualTo(gov2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("governance objects with different owners are not equal")
        void differentOwners_notEqual() { // GH-90000
            Governance gov1 = Governance.builder().withOwner("team-a").build();
            Governance gov2 = Governance.builder().withOwner("team-b").build();

            assertThat(gov1).isNotEqualTo(gov2); // GH-90000
        }

        @Test
        @DisplayName("governance objects with different classifications are not equal")
        void differentClassifications_notEqual() { // GH-90000
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
    }

    // ---- RetentionPolicy integration ----

    @Nested
    @DisplayName("RetentionPolicy")
    class RetentionPolicyTests {

        @Test
        @DisplayName("retention policy with archive location builds successfully")
        void withArchiveLocation() { // GH-90000
            RetentionPolicy policy = retentionWithArchive(); // GH-90000

            assertThat(policy.getRetentionPeriod()).isEqualTo(Duration.ofDays(365)); // GH-90000
            assertThat(policy.isArchiveBeforeDeletion()).isTrue(); // GH-90000
            assertThat(policy.getArchiveLocation()).isEqualTo("s3://archive/events");
        }

        @Test
        @DisplayName("retainIndefinitely creates immutable indefinite policy")
        void retainIndefinitely() { // GH-90000
            RetentionPolicy policy = RetentionPolicy.retainIndefinitely(); // GH-90000

            assertThat(policy.isRetainIndefinitely()).isTrue(); // GH-90000
            assertThat(policy.isImmutable()).isTrue(); // GH-90000
            assertThat(policy.isAllowExtension()).isFalse(); // GH-90000
            assertThat(policy.getArchiveLocation()).isEqualTo("archive/indefinite");
        }

        @Test
        @DisplayName("archive without location throws")
        void archiveWithoutLocation_throws() { // GH-90000
            assertThatThrownBy(() -> RetentionPolicy.builder() // GH-90000
                    .withArchiveBeforeDeletion(true) // GH-90000
                    .build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("Archive location must be specified");
        }

        @Test
        @DisplayName("legal hold forces immutable indefinite retention")
        void legalHold_forcesImmutable() { // GH-90000
            RetentionPolicy policy = RetentionPolicy.builder() // GH-90000
                    .withLegalHoldId("HOLD-2026-001")
                    .build(); // GH-90000

            assertThat(policy.isImmutable()).isTrue(); // GH-90000
            assertThat(policy.isRetainIndefinitely()).isTrue(); // GH-90000
            assertThat(policy.isAllowExtension()).isFalse(); // GH-90000
            assertThat(policy.getLegalHoldId()).isEqualTo("HOLD-2026-001");
        }
    }

    // ---- toString ----

    @Test
    @DisplayName("toString includes key fields")
    void toStringContainsFields() { // GH-90000
        Governance gov = Governance.builder() // GH-90000
                .withOwner("my-team")
                .withClassification(DataClassification.INTERNAL) // GH-90000
                .build(); // GH-90000

        assertThat(gov.toString()) // GH-90000
                .contains("my-team")
                .contains("INTERNAL");
    }
}
