/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DC-BE-001: Tests for centralized Runtime Truth status taxonomy.
 *
 * <p>Ensures the RuntimeTruthStatus enum provides compile-time type safety,
 * prevents duplicate status enum drift, and correctly maps runtime state to
 * canonical status values.
 *
 * @doc.type class
 * @doc.purpose Unit tests for RuntimeTruthStatus enum — DC-BE-001
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-BE-001: RuntimeTruthStatus taxonomy tests")
class RuntimeTruthStatus_DC_BE_001_Test {

    // ─────────────────────────────────────────────────────────────────────────
    // Enum value completeness
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Enum value completeness")
    class EnumValueTests {

        @Test
        @DisplayName("all required status values are defined")
        void allRequiredStatusValuesDefined() {
            assertThat(RuntimeTruthStatus.values())
                .extracting(RuntimeTruthStatus::name)
                .containsExactlyInAnyOrder("LIVE", "DEGRADED", "DISABLED", "PREVIEW", "UNAVAILABLE", "MISCONFIGURED");
        }

        @Test
        @DisplayName("each enum has a unique JSON value")
        void eachEnumHasUniqueJsonValue() {
            assertThat(RuntimeTruthStatus.values())
                .extracting(RuntimeTruthStatus::toJsonValue)
                .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("legacy values follow the documented alias mapping")
        void legacyValuesFollowDocumentedAliasMapping() {
            assertThat(RuntimeTruthStatus.values())
                .extracting(RuntimeTruthStatus::toLegacyValue)
            .containsExactly("ACTIVE", "DEGRADED", "NOT_CONFIGURED", "ACTIVE", "NOT_CONFIGURED", "DEGRADED");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON value parsing
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromJsonValue() parsing")
    class FromJsonValueTests {

        @Test
        @DisplayName("parses lowercase JSON values correctly")
        void fromJsonValue_parsesLowercase() {
            assertThat(RuntimeTruthStatus.fromJsonValue("live")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromJsonValue("degraded")).isEqualTo(RuntimeTruthStatus.DEGRADED);
            assertThat(RuntimeTruthStatus.fromJsonValue("disabled")).isEqualTo(RuntimeTruthStatus.DISABLED);
            assertThat(RuntimeTruthStatus.fromJsonValue("preview")).isEqualTo(RuntimeTruthStatus.PREVIEW);
            assertThat(RuntimeTruthStatus.fromJsonValue("unavailable")).isEqualTo(RuntimeTruthStatus.UNAVAILABLE);
            assertThat(RuntimeTruthStatus.fromJsonValue("misconfigured")).isEqualTo(RuntimeTruthStatus.MISCONFIGURED);
        }

        @Test
        @DisplayName("is case-insensitive")
        void fromJsonValue_caseInsensitive() {
            assertThat(RuntimeTruthStatus.fromJsonValue("LIVE")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromJsonValue("Live")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromJsonValue("LIVE")).isEqualTo(RuntimeTruthStatus.LIVE);
        }

        @Test
        @DisplayName("throws exception for null value")
        void fromJsonValue_nullThrowsException() {
            assertThatThrownBy(() -> RuntimeTruthStatus.fromJsonValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("throws exception for unknown value")
        void fromJsonValue_unknownThrowsException() {
            assertThatThrownBy(() -> RuntimeTruthStatus.fromJsonValue("unknown_status"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown RuntimeTruthStatus value");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy value parsing
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromLegacyValue() parsing")
    class FromLegacyValueTests {

        @Test
        @DisplayName("parses uppercase legacy values correctly")
        void fromLegacyValue_parsesUppercase() {
            assertThat(RuntimeTruthStatus.fromLegacyValue("ACTIVE")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromLegacyValue("DEGRADED")).isEqualTo(RuntimeTruthStatus.DEGRADED);
            assertThat(RuntimeTruthStatus.fromLegacyValue("NOT_CONFIGURED")).isIn(RuntimeTruthStatus.DISABLED, RuntimeTruthStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("is case-insensitive")
        void fromLegacyValue_caseInsensitive() {
            assertThat(RuntimeTruthStatus.fromLegacyValue("active")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromLegacyValue("Active")).isEqualTo(RuntimeTruthStatus.LIVE);
        }

        @Test
        @DisplayName("throws exception for null value")
        void fromLegacyValue_nullThrowsException() {
            assertThatThrownBy(() -> RuntimeTruthStatus.fromLegacyValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("throws exception for unknown value")
        void fromLegacyValue_unknownThrowsException() {
            assertThatThrownBy(() -> RuntimeTruthStatus.fromLegacyValue("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown RuntimeTruthStatus legacy value");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Runtime state mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromRuntimeState() mapping")
    class FromRuntimeStateTests {

        @Test
        @DisplayName("not configured returns DISABLED")
        void fromRuntimeState_notConfigured_returnsDisabled() {
            assertThat(RuntimeTruthStatus.fromRuntimeState(false, null)).isEqualTo(RuntimeTruthStatus.DISABLED);
            assertThat(RuntimeTruthStatus.fromRuntimeState(false, "UP")).isEqualTo(RuntimeTruthStatus.DISABLED);
            assertThat(RuntimeTruthStatus.fromRuntimeState(false, "DOWN")).isEqualTo(RuntimeTruthStatus.DISABLED);
        }

        @Test
        @DisplayName("configured with UP or null returns LIVE")
        void fromRuntimeState_configuredWithUp_returnsLive() {
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, null)).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "UP")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "up")).isEqualTo(RuntimeTruthStatus.LIVE);
        }

        @Test
        @DisplayName("configured with DEGRADED returns DEGRADED")
        void fromRuntimeState_configuredWithDegraded_returnsDegraded() {
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "DEGRADED")).isEqualTo(RuntimeTruthStatus.DEGRADED);
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "degraded")).isEqualTo(RuntimeTruthStatus.DEGRADED);
        }

        @Test
        @DisplayName("configured with DOWN returns UNAVAILABLE")
        void fromRuntimeState_configuredWithDown_returnsUnavailable() {
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "DOWN")).isEqualTo(RuntimeTruthStatus.UNAVAILABLE);
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "down")).isEqualTo(RuntimeTruthStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("configured with NOT_CONFIGURED returns UNAVAILABLE")
        void fromRuntimeState_configuredWithNotConfigured_returnsUnavailable() {
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "NOT_CONFIGURED")).isEqualTo(RuntimeTruthStatus.UNAVAILABLE);
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "not_configured")).isEqualTo(RuntimeTruthStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("unknown health state defaults to LIVE")
        void fromRuntimeState_unknownHealth_defaultsToLive() {
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "UNKNOWN")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromRuntimeState(true, "MAINTENANCE")).isEqualTo(RuntimeTruthStatus.LIVE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Value consistency
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Value consistency")
    class ValueConsistencyTests {

        @Test
        @DisplayName("toJsonValue returns lowercase values")
        void toJsonValue_returnsLowercase() {
            assertThat(RuntimeTruthStatus.LIVE.toJsonValue()).isEqualTo("live");
            assertThat(RuntimeTruthStatus.DEGRADED.toJsonValue()).isEqualTo("degraded");
            assertThat(RuntimeTruthStatus.DISABLED.toJsonValue()).isEqualTo("disabled");
            assertThat(RuntimeTruthStatus.PREVIEW.toJsonValue()).isEqualTo("preview");
            assertThat(RuntimeTruthStatus.UNAVAILABLE.toJsonValue()).isEqualTo("unavailable");
            assertThat(RuntimeTruthStatus.MISCONFIGURED.toJsonValue()).isEqualTo("misconfigured");
        }

        @Test
        @DisplayName("toLegacyValue returns uppercase legacy values")
        void toLegacyValue_returnsUppercase() {
            assertThat(RuntimeTruthStatus.LIVE.toLegacyValue()).isEqualTo("ACTIVE");
            assertThat(RuntimeTruthStatus.DEGRADED.toLegacyValue()).isEqualTo("DEGRADED");
            assertThat(RuntimeTruthStatus.DISABLED.toLegacyValue()).isEqualTo("NOT_CONFIGURED");
            assertThat(RuntimeTruthStatus.PREVIEW.toLegacyValue()).isEqualTo("ACTIVE");
            assertThat(RuntimeTruthStatus.UNAVAILABLE.toLegacyValue()).isEqualTo("NOT_CONFIGURED");
            assertThat(RuntimeTruthStatus.MISCONFIGURED.toLegacyValue()).isEqualTo("DEGRADED");
        }

        @Test
        @DisplayName("round-trip parsing works for JSON values")
        void roundTrip_jsonValues() {
            for (RuntimeTruthStatus status : RuntimeTruthStatus.values()) {
                String jsonValue = status.toJsonValue();
                RuntimeTruthStatus parsed = RuntimeTruthStatus.fromJsonValue(jsonValue);
                assertThat(parsed).isEqualTo(status);
            }
        }

        @Test
        @DisplayName("round-trip parsing works for legacy values")
        void roundTrip_legacyValues() {
            assertThat(RuntimeTruthStatus.fromLegacyValue("ACTIVE")).isEqualTo(RuntimeTruthStatus.LIVE);
            assertThat(RuntimeTruthStatus.fromLegacyValue("DEGRADED")).isEqualTo(RuntimeTruthStatus.DEGRADED);
            assertThat(RuntimeTruthStatus.fromLegacyValue("NOT_CONFIGURED")).isEqualTo(RuntimeTruthStatus.DISABLED);
        }
    }
}
