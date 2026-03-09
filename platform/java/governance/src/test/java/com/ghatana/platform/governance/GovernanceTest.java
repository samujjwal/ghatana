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

    private RetentionPolicy validRetention() {
        return RetentionPolicy.builder()
                .withRetentionPeriod(Duration.ofDays(90))
                .withArchiveBeforeDeletion(false)
                .build();
    }

    private RetentionPolicy retentionWithArchive() {
        return RetentionPolicy.builder()
                .withRetentionPeriod(Duration.ofDays(365))
                .withArchiveBeforeDeletion(true)
                .withArchiveLocation("s3://archive/events")
                .build();
    }

    // ---- Builder ----

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds governance with all fields set")
        void allFieldsSet() {
            RetentionPolicy retention = retentionWithArchive();

            Governance gov = Governance.builder()
                    .withOwner("platform-team")
                    .withClassification(DataClassification.CONFIDENTIAL)
                    .addAuthorizedProducer("service-a")
                    .addAuthorizedProducer("service-b")
                    .addAuthorizedConsumer("analytics")
                    .withApprovalRequired(true)
                    .withApprovalWorkflow("security-review")
                    .withRetentionPolicy(retention)
                    .build();

            assertThat(gov.getOwner()).isEqualTo("platform-team");
            assertThat(gov.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
            assertThat(gov.getAuthorizedProducers()).containsExactlyInAnyOrder("service-a", "service-b");
            assertThat(gov.getAuthorizedConsumers()).containsExactly("analytics");
            assertThat(gov.isApprovalRequired()).isTrue();
            assertThat(gov.getApprovalWorkflow()).isEqualTo("security-review");
            assertThat(gov.getRetentionPolicy()).isEqualTo(retention);
        }

        @Test
        @DisplayName("builds governance with minimal required fields")
        void minimalFields() {
            Governance gov = Governance.builder()
                    .withOwner("data-team")
                    .build();

            assertThat(gov.getOwner()).isEqualTo("data-team");
            assertThat(gov.getClassification()).isEqualTo(DataClassification.INTERNAL);
            assertThat(gov.isApprovalRequired()).isFalse();
            assertThat(gov.getAuthorizedProducers()).isEmpty();
            assertThat(gov.getAuthorizedConsumers()).isEmpty();
        }

        @Test
        @DisplayName("throws when owner is null")
        void nullOwner_throws() {
            assertThatThrownBy(() -> Governance.builder().build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Owner must be specified");
        }

        @Test
        @DisplayName("throws when owner is blank")
        void blankOwner_throws() {
            assertThatThrownBy(() -> Governance.builder().withOwner("   ").build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Owner must be specified");
        }

        @Test
        @DisplayName("throws when classification is null")
        void nullClassification_throws() {
            assertThatThrownBy(() -> Governance.builder()
                    .withOwner("team")
                    .withClassification(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("addAuthorizedProducers accepts iterable")
        void addProducersFromIterable() {
            Governance gov = Governance.builder()
                    .withOwner("team")
                    .addAuthorizedProducers(Set.of("svc-1", "svc-2", "svc-3"))
                    .build();

            assertThat(gov.getAuthorizedProducers()).hasSize(3);
        }
    }

    // ---- Immutability ----

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("authorized producers set is unmodifiable")
        void producersAreUnmodifiable() {
            Governance gov = Governance.builder()
                    .withOwner("team")
                    .addAuthorizedProducer("svc-a")
                    .build();

            assertThatThrownBy(() -> gov.getAuthorizedProducers().add("svc-b"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("authorized consumers set is unmodifiable")
        void consumersAreUnmodifiable() {
            Governance gov = Governance.builder()
                    .withOwner("team")
                    .addAuthorizedConsumer("reader")
                    .build();

            assertThatThrownBy(() -> gov.getAuthorizedConsumers().add("other"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ---- Equality ----

    @Nested
    @DisplayName("equals and hashCode")
    class EqualityTests {

        @Test
        @DisplayName("identical governance objects are equal")
        void identicalObjects_areEqual() {
            RetentionPolicy retention = validRetention();

            Governance gov1 = Governance.builder()
                    .withOwner("team-alpha")
                    .withClassification(DataClassification.SENSITIVE)
                    .addAuthorizedProducer("svc")
                    .withRetentionPolicy(retention)
                    .build();

            Governance gov2 = Governance.builder()
                    .withOwner("team-alpha")
                    .withClassification(DataClassification.SENSITIVE)
                    .addAuthorizedProducer("svc")
                    .withRetentionPolicy(retention)
                    .build();

            assertThat(gov1).isEqualTo(gov2);
            assertThat(gov1.hashCode()).isEqualTo(gov2.hashCode());
        }

        @Test
        @DisplayName("governance objects with different owners are not equal")
        void differentOwners_notEqual() {
            Governance gov1 = Governance.builder().withOwner("team-a").build();
            Governance gov2 = Governance.builder().withOwner("team-b").build();

            assertThat(gov1).isNotEqualTo(gov2);
        }

        @Test
        @DisplayName("governance objects with different classifications are not equal")
        void differentClassifications_notEqual() {
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
    }

    // ---- RetentionPolicy integration ----

    @Nested
    @DisplayName("RetentionPolicy")
    class RetentionPolicyTests {

        @Test
        @DisplayName("retention policy with archive location builds successfully")
        void withArchiveLocation() {
            RetentionPolicy policy = retentionWithArchive();

            assertThat(policy.getRetentionPeriod()).isEqualTo(Duration.ofDays(365));
            assertThat(policy.isArchiveBeforeDeletion()).isTrue();
            assertThat(policy.getArchiveLocation()).isEqualTo("s3://archive/events");
        }

        @Test
        @DisplayName("retainIndefinitely creates immutable indefinite policy")
        void retainIndefinitely() {
            RetentionPolicy policy = RetentionPolicy.retainIndefinitely();

            assertThat(policy.isRetainIndefinitely()).isTrue();
            assertThat(policy.isImmutable()).isTrue();
            assertThat(policy.isAllowExtension()).isFalse();
            assertThat(policy.getArchiveLocation()).isEqualTo("archive/indefinite");
        }

        @Test
        @DisplayName("archive without location throws")
        void archiveWithoutLocation_throws() {
            assertThatThrownBy(() -> RetentionPolicy.builder()
                    .withArchiveBeforeDeletion(true)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Archive location must be specified");
        }

        @Test
        @DisplayName("legal hold forces immutable indefinite retention")
        void legalHold_forcesImmutable() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .withLegalHoldId("HOLD-2026-001")
                    .build();

            assertThat(policy.isImmutable()).isTrue();
            assertThat(policy.isRetainIndefinitely()).isTrue();
            assertThat(policy.isAllowExtension()).isFalse();
            assertThat(policy.getLegalHoldId()).isEqualTo("HOLD-2026-001");
        }
    }

    // ---- toString ----

    @Test
    @DisplayName("toString includes key fields")
    void toStringContainsFields() {
        Governance gov = Governance.builder()
                .withOwner("my-team")
                .withClassification(DataClassification.INTERNAL)
                .build();

        assertThat(gov.toString())
                .contains("my-team")
                .contains("INTERNAL");
    }
}
