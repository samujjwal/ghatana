/*
 * Copyright (c) 2026 Ghatana Inc.
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
    private static final List<String> EPISODE_IDS = List.of("ep-1", "ep-2", "ep-3");
    private static final Map<String, Double> METRICS = Map.of(
            "successRate", 0.85,
            "errorRate",   0.05,
            "avgLatencyMs", 120.0);

    // ─── pending() factory ────────────────────────────────────────────────────

    @Nested
    @DisplayName("pending() factory")
    class PendingFactory {

        @Test
        @DisplayName("creates record with SHADOW activation mode")
        void pending_setsModeShadow() {
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.80);

            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.SHADOW);
        }

        @Test
        @DisplayName("creates record with correct field values")
        void pending_setsAllFields() {
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.80);

            assertThat(record.policyId()).isEqualTo(POLICY_ID);
            assertThat(record.tenantId()).isEqualTo(TENANT_ID);
            assertThat(record.skillId()).isEqualTo(SKILL_ID);
            assertThat(record.version()).isEqualTo(1);
            assertThat(record.confidenceScore()).isEqualTo(0.80);
            assertThat(record.canaryFraction()).isZero();
        }

        @Test
        @DisplayName("creates record with null approver and promotedAt for pending state")
        void pending_nullsForPendingState() {
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.80);

            assertThat(record.approverId()).isNull();
            assertThat(record.approverRationale()).isNull();
            assertThat(record.promotedAt()).isNull();
            assertThat(record.rollbackPointerId()).isNull();
        }

        @Test
        @DisplayName("makes a defensive copy of episode IDs list")
        void pending_defensiveCopyOfEpisodeIds() {
            java.util.List<String> mutableIds = new java.util.ArrayList<>(EPISODE_IDS);
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, mutableIds, METRICS, 0.80);

            mutableIds.add("ep-injected");
            assertThat(record.sourceEpisodeIds()).hasSize(EPISODE_IDS.size());
        }

        @Test
        @DisplayName("makes a defensive copy of metrics map")
        void pending_defensiveCopyOfMetrics() {
            java.util.Map<String, Double> mutableMetrics = new java.util.HashMap<>(METRICS);
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, mutableMetrics, 0.80);

            mutableMetrics.put("injected", 99.0);
            assertThat(record.evaluationMetrics()).doesNotContainKey("injected");
        }
    }

    // ─── compact constructor validation ───────────────────────────────────────

    @Nested
    @DisplayName("compact constructor validation")
    class Validation {

        @Test
        @DisplayName("throws for canaryFraction below 0")
        void constructor_negativeFraction_throws() {
            assertThatThrownBy(() -> new PolicyProvenanceRecord(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.SHADOW, -0.1, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("canaryFraction");
        }

        @Test
        @DisplayName("throws for canaryFraction above 1")
        void constructor_fractionAboveOne_throws() {
            assertThatThrownBy(() -> new PolicyProvenanceRecord(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.SHADOW, 1.5, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("canaryFraction");
        }

        @Test
        @DisplayName("accepts canaryFraction of exactly 0 and 1")
        void constructor_boundaryFractions_valid() {
            PolicyProvenanceRecord zero = new PolicyProvenanceRecord(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.SHADOW, 0.0, null);
            assertThat(zero.canaryFraction()).isZero();

            PolicyProvenanceRecord full = new PolicyProvenanceRecord(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1,
                    EPISODE_IDS, METRICS, 0.80,
                    null, null, null,
                    PolicyActivationMode.CANARY, 1.0, null);
            assertThat(full.canaryFraction()).isEqualTo(1.0);
        }
    }

    // ─── withMode() transformation ────────────────────────────────────────────

    @Nested
    @DisplayName("withMode() transformation")
    class WithMode {

        @Test
        @DisplayName("returns new record with updated activation mode")
        void withMode_returnsNewRecordWithNewMode() {
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.85);

            PolicyProvenanceRecord canary = pending.withMode(PolicyActivationMode.CANARY);

            assertThat(canary.activationMode()).isEqualTo(PolicyActivationMode.CANARY);
            assertThat(pending.activationMode()).isEqualTo(PolicyActivationMode.SHADOW); // original unchanged
        }

        @Test
        @DisplayName("preserves all other fields in withMode()")
        void withMode_preservesOtherFields() {
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 2, EPISODE_IDS, METRICS, 0.75);

            PolicyProvenanceRecord active = pending.withMode(PolicyActivationMode.ACTIVE);

            assertThat(active.policyId()).isEqualTo(POLICY_ID);
            assertThat(active.tenantId()).isEqualTo(TENANT_ID);
            assertThat(active.skillId()).isEqualTo(SKILL_ID);
            assertThat(active.version()).isEqualTo(2);
            assertThat(active.confidenceScore()).isEqualTo(0.75);
        }
    }

    // ─── withPromotion() transformation ──────────────────────────────────────

    @Nested
    @DisplayName("withPromotion() transformation")
    class WithPromotion {

        @Test
        @DisplayName("sets approver identity, rationale, promotedAt and rollback pointer")
        void withPromotion_setsAllPromotionFields() {
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            Instant before = Instant.now();
            PolicyProvenanceRecord promoted = pending.withPromotion(
                    "user-bob", "approved for prod", PolicyActivationMode.ACTIVE, "policy-000");
            Instant after = Instant.now();

            assertThat(promoted.approverId()).isEqualTo("user-bob");
            assertThat(promoted.approverRationale()).isEqualTo("approved for prod");
            assertThat(promoted.activationMode()).isEqualTo(PolicyActivationMode.ACTIVE);
            assertThat(promoted.rollbackPointerId()).isEqualTo("policy-000");
            assertThat(promoted.promotedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("preserves original record after withPromotion()")
        void withPromotion_originalUnchanged() {
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            pending.withPromotion("user-bob", "reason", PolicyActivationMode.ACTIVE, null);

            assertThat(pending.approverId()).isNull();
            assertThat(pending.promotedAt()).isNull();
            assertThat(pending.activationMode()).isEqualTo(PolicyActivationMode.SHADOW);
        }

        @Test
        @DisplayName("accepts null rollback pointer for first version")
        void withPromotion_firstVersion_nullRollback() {
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            PolicyProvenanceRecord promoted = pending.withPromotion(
                    "auto-promote", "high confidence", PolicyActivationMode.ACTIVE, null);

            assertThat(promoted.rollbackPointerId()).isNull();
        }
    }

    // ─── withCanaryFraction() transformation ──────────────────────────────────

    @Nested
    @DisplayName("withCanaryFraction() transformation")
    class WithCanaryFraction {

        @Test
        @DisplayName("returns a CANARY-mode record with updated fraction")
        void withCanaryFraction_setsFraction() {
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.85);

            PolicyProvenanceRecord canary = pending.withCanaryFraction(0.1);

            assertThat(canary.activationMode()).isEqualTo(PolicyActivationMode.CANARY);
            assertThat(canary.canaryFraction()).isEqualTo(0.1);
        }

        @Test
        @DisplayName("preserves original record after withCanaryFraction()")
        void withCanaryFraction_originalUnchanged() {
            PolicyProvenanceRecord pending = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.85);

            pending.withCanaryFraction(0.25);

            assertThat(pending.canaryFraction()).isZero();
            assertThat(pending.activationMode()).isEqualTo(PolicyActivationMode.SHADOW);
        }
    }

    // ─── PolicyActivationMode lifecycle ───────────────────────────────────────

    @Nested
    @DisplayName("PolicyActivationMode lifecycle")
    class ActivationModeLifecycle {

        @Test
        @DisplayName("enum contains all expected modes")
        void allModesPresent() {
            assertThat(PolicyActivationMode.values()).containsExactly(
                    PolicyActivationMode.SHADOW,
                    PolicyActivationMode.CANARY,
                    PolicyActivationMode.ACTIVE,
                    PolicyActivationMode.DEPRECATED);
        }

        @Test
        @DisplayName("full lifecycle chain via withMode()")
        void fullLifecycle() {
            PolicyProvenanceRecord record = PolicyProvenanceRecord.pending(
                    POLICY_ID, TENANT_ID, SKILL_ID, 1, EPISODE_IDS, METRICS, 0.90);

            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.SHADOW);

            record = record.withCanaryFraction(0.10);
            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.CANARY);

            record = record.withMode(PolicyActivationMode.ACTIVE);
            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.ACTIVE);

            record = record.withMode(PolicyActivationMode.DEPRECATED);
            assertThat(record.activationMode()).isEqualTo(PolicyActivationMode.DEPRECATED);
        }
    }
}

