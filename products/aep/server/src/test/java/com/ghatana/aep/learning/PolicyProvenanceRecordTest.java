/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PolicyProvenanceRecord} value object and factory methods.
 *
 * @doc.type class
 * @doc.purpose Tests for PolicyProvenanceRecord provenance lifecycle and immutability
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PolicyProvenanceRecord")
class PolicyProvenanceRecordTest {

    private static final String POLICY_ID  = "policy-001";
    private static final String TENANT_ID  = "tenant-alpha";
    private static final String SKILL_ID   = "skill-nav";
    private static final List<String> EPISODE_IDS = List.of("ep-1", "ep-2", "ep-3"); // GH-90000
    private static final Map<String, Double> METRICS = Map.of( // GH-90000
            "successRate", 0.85,
            "errorRate",   0.05,
            "avgLatencyMs", 120.0);

    // ─── pending() factory ──────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("pending() factory")
    class PendingFactory {

        @Test
        @DisplayName("creates record with SHADOW activation mode")
        void pending_setsModeShadow() { // GH-90000
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.80);

            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.SHADOW); // GH-90000
        }

        @Test
        @DisplayName("creates record with correct field values")
        void pending_setsAllFields() { // GH-90000
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.80);

            assertThat(record.policyId()).isEqualTo(POLICY_ID); // GH-90000
            assertThat(record.tenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(record.skillId()).isEqualTo(SKILL_ID); // GH-90000
            assertThat(record.version()).isEqualTo(1); // GH-90000
            assertThat(record.confidenceScore()).isEqualTo(0.80); // GH-90000
            assertThat(record.canaryFraction()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("creates record with null approver and promotedAt for pending state")
        void pending_nullsForPendingState() { // GH-90000
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.80);

            assertThat(record.approverId()).isNull(); // GH-90000
            assertThat(record.approverRationale()).isNull(); // GH-90000
            assertThat(record.promotedAt()).isNull(); // GH-90000
            assertThat(record.rollbackPointerId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("makes a defensive copy of episode IDs list")
        void pending_defensiveCopyOfEpisodeIds() { // GH-90000
            java.util.List<String> mutableIds = new java.util.ArrayList<>(EPISODE_IDS); // GH-90000
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, mutableIds, METRICS, 0.80);

            mutableIds.add("ep-injected");
            assertThat(record.sourceEpisodeIds()).hasSize(EPISODE_IDS.size()); // GH-90000
        }

        @Test
        @DisplayName("makes a defensive copy of metrics map")
        void pending_defensiveCopyOfMetrics() { // GH-90000
            java.util.Map<String, Double> mutableMetrics = new java.util.HashMap<>(METRICS); // GH-90000
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, mutableMetrics, 0.80);

            mutableMetrics.put("injected", 99.0); // GH-90000
            assertThat(record.evaluationMetrics()).doesNotContainKey("injected");
        }
    }

    // ─── compact constructor validation ───────────────────────────────────────

    @Nested
    @DisplayName("compact constructor validation")
    class Validation {

        @Test
        @DisplayName("throws for canaryFraction below 0")
        void constructor_negativeFraction_throws() { // GH-90000
            assertThatThrownBy(() -> new PolicyProvenanceRecord( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.SHADOW, -0.1, null))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("canaryFraction");
        }

        @Test
        @DisplayName("throws for canaryFraction above 1")
        void constructor_fractionAboveOne_throws() { // GH-90000
            assertThatThrownBy(() -> new PolicyProvenanceRecord( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.SHADOW, 1.5, null))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("canaryFraction");
        }

        @Test
        @DisplayName("accepts canaryFraction of exactly 0 and 1")
        void constructor_boundaryFractions_valid() { // GH-90000
            PolicyProvenanceRecord zero = new PolicyProvenanceRecord( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.SHADOW, 0.0, null);
            assertThat(zero.canaryFraction()).isZero(); // GH-90000

            PolicyProvenanceRecord full = new PolicyProvenanceRecord( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.CANARY, 1.0, null);
            assertThat(full.canaryFraction()).isEqualTo(1.0); // GH-90000
        }
    }

    // ─── withMode() transformation ──────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("withMode() transformation")
    class WithMode {

        @Test
        @DisplayName("returns new record with updated activation mode")
        void withMode_returnsNewRecordWithNewMode() { // GH-90000
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.85);

            PolicyProvenanceRecord canary = pending.withMode(PolicyActivationMode.CANARY); // GH-90000

            assertThat(canary.activationMode()).isEqualTo(PolicyActivationMode.CANARY); // GH-90000
            assertThat(pending.activationMode()).isEqualTo(PolicyActivationMode.SHADOW); // original unchanged // GH-90000
        }

        @Test
        @DisplayName("preserves all other fields in withMode()")
        void withMode_preservesOtherFields() { // GH-90000
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 2, EPISODE_IDS, METRICS, 0.75);

            PolicyProvenanceRecord active = pending.withMode(PolicyActivationMode.ACTIVE); // GH-90000

            assertThat(active.policyId()).isEqualTo(POLICY_ID); // GH-90000
            assertThat(active.tenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(active.skillId()).isEqualTo(SKILL_ID); // GH-90000
            assertThat(active.version()).isEqualTo(2); // GH-90000
            assertThat(active.confidenceScore()).isEqualTo(0.75); // GH-90000
        }
    }

    // ─── withPromotion() transformation ────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("withPromotion() transformation")
    class WithPromotion {

        @Test
        @DisplayName("sets approver identity, rationale, promotedAt and rollback pointer")
        void withPromotion_setsAllPromotionFields() { // GH-90000
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            Instant before = Instant.now(); // GH-90000
            PolicyProvenanceRecord promoted = pending.withPromotion( // GH-90000
                    "user-bob", "approved for prod", PolicyActivationMode.ACTIVE, "policy-000");
            Instant after = Instant.now(); // GH-90000

            assertThat(promoted.approverId()).isEqualTo("user-bob");
            assertThat(promoted.approverRationale()).isEqualTo("approved for prod");
            assertThat(promoted.activationMode()).isEqualTo(PolicyActivationMode.ACTIVE); // GH-90000
            assertThat(promoted.rollbackPointerId()).isEqualTo("policy-000");
            assertThat(promoted.promotedAt()).isBetween(before, after); // GH-90000
        }

        @Test
        @DisplayName("preserves original record after withPromotion()")
        void withPromotion_originalUnchanged() { // GH-90000
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            pending.withPromotion("user-bob", "reason", PolicyActivationMode.ACTIVE, null); // GH-90000

            assertThat(pending.approverId()).isNull(); // GH-90000
            assertThat(pending.promotedAt()).isNull(); // GH-90000
            assertThat(pending.activationMode()).isEqualTo(PolicyActivationMode.SHADOW); // GH-90000
        }

        @Test
        @DisplayName("accepts null rollback pointer for first version")
        void withPromotion_firstVersion_nullRollback() { // GH-90000
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            PolicyProvenanceRecord promoted = pending.withPromotion( // GH-90000
                    "auto-promote", "high confidence", PolicyActivationMode.ACTIVE, null);

            assertThat(promoted.rollbackPointerId()).isNull(); // GH-90000
        }
    }

    // ─── withCanaryFraction() transformation ────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("withCanaryFraction() transformation")
    class WithCanaryFraction {

        @Test
        @DisplayName("returns a CANARY-mode record with updated fraction")
        void withCanaryFraction_setsFraction() { // GH-90000
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.85);

            PolicyProvenanceRecord canary = pending.withCanaryFraction(0.1); // GH-90000

            assertThat(canary.activationMode()).isEqualTo(PolicyActivationMode.CANARY); // GH-90000
            assertThat(canary.canaryFraction()).isEqualTo(0.1); // GH-90000
        }

        @Test
        @DisplayName("preserves original record after withCanaryFraction()")
        void withCanaryFraction_originalUnchanged() { // GH-90000
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.85);

            pending.withCanaryFraction(0.25); // GH-90000

            assertThat(pending.canaryFraction()).isZero(); // GH-90000
            assertThat(pending.activationMode()).isEqualTo(PolicyActivationMode.SHADOW); // GH-90000
        }
    }

    // ─── PolicyActivationMode lifecycle ───────────────────────────────────────

    @Nested
    @DisplayName("PolicyActivationMode lifecycle")
    class ActivationModeLifecycle {

        @Test
        @DisplayName("enum contains all expected modes")
        void allModesPresent() { // GH-90000
            assertThat(PolicyActivationMode.values()).containsExactly( // GH-90000
                    PolicyActivationMode.SHADOW,
                    PolicyActivationMode.CANARY,
                    PolicyActivationMode.ACTIVE,
                    PolicyActivationMode.DEPRECATED);
        }

        @Test
        @DisplayName("full lifecycle chain via withMode()")
        void fullLifecycle() { // GH-90000
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending( // GH-90000
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.SHADOW); // GH-90000

            record = record.withCanaryFraction(0.10); // GH-90000
            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.CANARY); // GH-90000

            record = record.withMode(PolicyActivationMode.ACTIVE); // GH-90000
            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.ACTIVE); // GH-90000

            record = record.withMode(PolicyActivationMode.DEPRECATED); // GH-90000
            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.DEPRECATED); // GH-90000
        }
    }
}
