/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SurfaceRecord} and {@link DependencyProbeResult}.
 *
 * @doc.type class
 * @doc.purpose Verify typed surface record invariants and serialisation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SurfaceRecord — typed Runtime Truth surface record")
class SurfaceRecordTest {

    // =========================================================================
    // DependencyProbeResult
    // =========================================================================

    @Nested
    @DisplayName("DependencyProbeResult")
    class ProbeResultTests {

        @Test
        @DisplayName("pass() creates a passing UP probe")
        void pass_createsPassingProbe() {
            DependencyProbeResult probe = DependencyProbeResult.pass("audit-service");
            assertThat(probe.passed()).isTrue();
            assertThat(probe.status()).isEqualTo("UP");
            assertThat(probe.dependencyName()).isEqualTo("audit-service");
        }

        @Test
        @DisplayName("pass(name, detail) includes the detail as reason")
        void passWithDetail_includesReason() {
            DependencyProbeResult probe = DependencyProbeResult.pass("audit-service", "EventLogAuditService wired");
            assertThat(probe.reason()).isEqualTo("EventLogAuditService wired");
        }

        @Test
        @DisplayName("fail() creates a failing NOT_CONFIGURED probe")
        void fail_createsFailingProbe() {
            DependencyProbeResult probe = DependencyProbeResult.fail("audit-service", "DATACLOUD_AUDIT_ENABLED=false");
            assertThat(probe.passed()).isFalse();
            assertThat(probe.status()).isEqualTo("NOT_CONFIGURED");
            assertThat(probe.reason()).contains("AUDIT_ENABLED");
        }

        @Test
        @DisplayName("degraded() creates a DEGRADED probe")
        void degraded_createsDegradedProbe() {
            DependencyProbeResult probe = DependencyProbeResult.degraded("entity-store", "in-memory only");
            assertThat(probe.passed()).isFalse();
            assertThat(probe.status()).isEqualTo("DEGRADED");
        }

        @Test
        @DisplayName("toMap() includes all fields")
        void toMap_containsAllFields() {
            DependencyProbeResult probe = DependencyProbeResult.pass("svc", "ok");
            Map<String, Object> map = probe.toMap();
            assertThat(map).containsKey("dependencyName");
            assertThat(map).containsKey("passed");
            assertThat(map).containsKey("status");
            assertThat(map).containsKey("reason");
            assertThat(map).containsKey("probedAt");
        }

        @Test
        @DisplayName("null dependencyName throws IllegalArgumentException")
        void nullDependencyName_throws() {
            assertThatThrownBy(() -> DependencyProbeResult.pass(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("blank dependencyName throws IllegalArgumentException")
        void blankDependencyName_throws() {
            assertThatThrownBy(() ->
                new DependencyProbeResult("  ", true, "UP", "ok", null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // SurfaceRecord invariants
    // =========================================================================

    @Nested
    @DisplayName("SurfaceRecord invariants")
    class InvariantTests {

        @Test
        @DisplayName("LIVE state requires at least one probe result")
        void liveState_requiresProbe() {
            assertThatThrownBy(() ->
                SurfaceRecord.builder("authentication.apiKey")
                    .state(RuntimeTruthStatus.LIVE)
                    .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIVE")
                .hasMessageContaining("DependencyProbeResult");
        }

        @Test
        @DisplayName("DISABLED state does not require a probe")
        void disabledState_doesNotRequireProbe() {
            SurfaceRecord record = SurfaceRecord.builder("authentication.apiKey")
                .state(RuntimeTruthStatus.DISABLED)
                .build();
            assertThat(record.state()).isEqualTo(RuntimeTruthStatus.DISABLED);
            assertThat(record.dependencyProbes()).isEmpty();
        }

        @Test
        @DisplayName("LIVE state with passing probe succeeds")
        void liveState_withPassingProbe_succeeds() {
            SurfaceRecord record = SurfaceRecord.builder("governance.audit")
                .ownerPlane("governance")
                .state(RuntimeTruthStatus.LIVE)
                .probe(DependencyProbeResult.pass("audit-service"))
                .tenantScope("tenant")
                .runtimeProfile("production")
                .actionsAllowed(List.of("emit-audit-event"))
                .build();

            assertThat(record.state()).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(record.dependencyProbes()).hasSize(1);
            assertThat(record.dependencyProbes().get(0).passed()).isTrue();
        }

        @Test
        @DisplayName("null surfaceId throws NullPointerException")
        void nullSurfaceId_throws() {
            assertThatThrownBy(() ->
                SurfaceRecord.builder(null).state(RuntimeTruthStatus.DISABLED).build())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("blank surfaceId throws IllegalArgumentException")
        void blankSurfaceId_throws() {
            assertThatThrownBy(() ->
                SurfaceRecord.builder("  ").state(RuntimeTruthStatus.DISABLED).build())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("defaults are applied for null optional fields")
        void defaults_appliedForNullFields() {
            SurfaceRecord record = SurfaceRecord.builder("test.surface")
                .state(RuntimeTruthStatus.DISABLED)
                .build();

            assertThat(record.tenantScope()).isEqualTo("global");
            assertThat(record.runtimeProfile()).isEqualTo("local");
            assertThat(record.lastCheckedAt()).isNotNull();
            assertThat(record.evidence()).isEmpty();
            assertThat(record.actionsAllowed()).isEmpty();
            assertThat(record.requiredDependencies()).isEmpty();
        }
    }

    // =========================================================================
    // toMap serialisation
    // =========================================================================

    @Nested
    @DisplayName("toMap serialisation")
    class ToMapTests {

        @Test
        @DisplayName("toMap includes state as jsonValue and status as legacyValue")
        void toMap_includesStateAndStatus() {
            SurfaceRecord record = SurfaceRecord.builder("intelligence.aiCompletion")
                .ownerPlane("intelligence")
                .state(RuntimeTruthStatus.LIVE)
                .probe(DependencyProbeResult.pass("completion-service"))
                .tenantScope("tenant")
                .runtimeProfile("production")
                .actionsAllowed(List.of("ai-assist"))
                .build();

            Map<String, Object> map = record.toMap();
            assertThat(map.get("state")).isEqualTo("live");
            assertThat(map.get("status")).isEqualTo("ACTIVE");
            assertThat(map.get("surfaceId")).isEqualTo("intelligence.aiCompletion");
            assertThat(map.get("ownerPlane")).isEqualTo("intelligence");
            assertThat(map.get("tenantScope")).isEqualTo("tenant");
            assertThat(map.get("runtimeProfile")).isEqualTo("production");
        }

        @Test
        @DisplayName("toMap serialises dependency probes as list of maps")
        void toMap_serialisesProbes() {
            SurfaceRecord record = SurfaceRecord.builder("data.entityStore")
                .ownerPlane("data")
                .state(RuntimeTruthStatus.DEGRADED)
                .probe(DependencyProbeResult.degraded("entity-store", "in-memory only"))
                .build();

            Map<String, Object> map = record.toMap();
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> probes = (java.util.List<Map<String, Object>>) map.get("dependencyProbes");
            assertThat(probes).hasSize(1);
            assertThat(probes.get(0).get("dependencyName")).isEqualTo("entity-store");
            assertThat(probes.get(0).get("status")).isEqualTo("DEGRADED");
        }

        @Test
        @DisplayName("toMap includes limitations when set")
        void toMap_includesLimitations() {
            SurfaceRecord record = SurfaceRecord.builder("event.store")
                .ownerPlane("event")
                .state(RuntimeTruthStatus.DEGRADED)
                .probe(DependencyProbeResult.degraded("event-log", "in-memory"))
                .limitations("Events are non-durable — lost on process restart")
                .build();

            Map<String, Object> map = record.toMap();
            assertThat(map.get("limitations")).isEqualTo("Events are non-durable — lost on process restart");
        }
    }

    // =========================================================================
    // Immutability
    // =========================================================================

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("dependencyProbes list is unmodifiable")
        void dependencyProbesList_isUnmodifiable() {
            SurfaceRecord record = SurfaceRecord.builder("test")
                .state(RuntimeTruthStatus.DISABLED)
                .build();

            assertThatThrownBy(() -> record.dependencyProbes().add(DependencyProbeResult.pass("x")))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("actionsAllowed list is unmodifiable")
        void actionsAllowedList_isUnmodifiable() {
            SurfaceRecord record = SurfaceRecord.builder("test")
                .state(RuntimeTruthStatus.DISABLED)
                .actionsAllowed(List.of("read"))
                .build();

            assertThatThrownBy(() -> record.actionsAllowed().add("write"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
