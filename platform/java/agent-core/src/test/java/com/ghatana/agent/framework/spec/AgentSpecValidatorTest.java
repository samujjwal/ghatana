/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.spec;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AgentSpecValidator}.
 *
 * <p>Covers: valid canonical spec, deprecated agentType warning, unknown
 * autonomyLevel rejection, unsupported specVersion, missing identity fields.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentSpecValidator — enum canonicalization and version validation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentSpecValidator")
class AgentSpecValidatorTest {

    private AgentSpecValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AgentSpecValidator();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper factory
    // ─────────────────────────────────────────────────────────────────────────

    private static AgentSpec minimalSpec(AgentType agentType,
                                         String autonomyLevel,
                                         DeterminismGuarantee determinism,
                                         StateMutability stateMutability) {
        return minimalSpec("1.0.0", agentType, autonomyLevel, determinism, stateMutability);
    }

    private static AgentSpec minimalSpec(String specVersion,
                                         AgentType agentType,
                                         String autonomyLevel,
                                         DeterminismGuarantee determinism,
                                         StateMutability stateMutability) {
        AgentSpec.SpecMetadata metadata = new AgentSpec.SpecMetadata(
                "test-agent", "Test Agent", "test", "1.0.0", "active",
                List.of(), List.of(), "A test agent", null);

        AgentSpec.SpecIdentity identity = new AgentSpec.SpecIdentity(
                agentType, null, List.of(), List.of(), "low",
                autonomyLevel, determinism, stateMutability, FailureMode.FAIL_FAST);

        return AgentSpec.builder()
                .agentSpecVersion(specVersion)
                .metadata(metadata)
                .identity(identity)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid specs")
    class ValidSpecs {

        @Test
        @DisplayName("canonical deterministic spec is valid")
        void canonicalDeterministicSpec() {
            AgentSpec spec = minimalSpec(AgentType.DETERMINISTIC, "SUPERVISED",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getIssues()).isEmpty();
        }

        @Test
        @DisplayName("canonical probabilistic spec with AUTONOMOUS level is valid")
        void canonicalProbabilisticSpec() {
            AgentSpec spec = minimalSpec(AgentType.PROBABILISTIC, "AUTONOMOUS",
                    DeterminismGuarantee.NONE, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("spec version 2.0.0 is valid")
        void specVersion200IsValid() {
            AgentSpec spec = minimalSpec("2.0.0", AgentType.DETERMINISTIC, "ADVISORY",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("spec version 1.0.0 is valid")
        void specVersion100IsValid() {
            AgentSpec spec = minimalSpec("1.0.0", AgentType.REACTIVE, "BOUNDED_AUTONOMOUS",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("legacy autonomyLevel aliases resolve to canonical — SUPERVISED")
        void legacyAutonomyAliasSemiAutonomous() {
            // The loader normalises "semi-autonomous" → "SUPERVISED" before the spec is built.
            // This test confirms that after normalisation the spec is valid.
            AgentSpec spec = minimalSpec(AgentType.HYBRID, "SUPERVISED",
                    DeterminismGuarantee.CONFIG_SCOPED, StateMutability.EXTERNAL_STATE);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("DRAFT autonomyLevel is canonical")
        void draftAutonomyLevel() {
            AgentSpec spec = minimalSpec(AgentType.PLANNING, "DRAFT",
                    DeterminismGuarantee.NONE, StateMutability.LOCAL_STATE);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deprecated AgentType
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deprecated AgentType")
    class DeprecatedAgentType {

        @Test
        @DisplayName("deprecated LLM type produces validation issue")
        @SuppressWarnings("deprecation")
        void deprecatedLlmType() {
            AgentSpec spec = minimalSpec(AgentType.LLM, "SUPERVISED",
                    DeterminismGuarantee.NONE, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getIssues()).hasSize(1);
            ValidationIssue issue = result.getIssues().get(0);
            assertThat(issue.field()).isEqualTo("identity.agentType");
            assertThat(issue.value()).isEqualTo("LLM");
            assertThat(issue.message()).containsIgnoringCase("deprecated")
                    .containsIgnoringCase("PROBABILISTIC");
        }

        @Test
        @DisplayName("throwIfInvalid throws AgentSpecValidationException for deprecated type")
        @SuppressWarnings("deprecation")
        void throwIfInvalidForDeprecated() {
            AgentSpec spec = minimalSpec(AgentType.LLM, "ADVISORY",
                    DeterminismGuarantee.NONE, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThatThrownBy(result::throwIfInvalid)
                    .isInstanceOf(AgentSpecValidationException.class)
                    .hasMessageContaining("validation failed")
                    .hasMessageContaining("deprecated");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unknown autonomyLevel
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown autonomyLevel")
    class UnknownAutonomyLevel {

        @Test
        @DisplayName("unknown autonomyLevel string produces validation issue")
        void unknownAutonomyLevel() {
            AgentSpec spec = minimalSpec(AgentType.DETERMINISTIC, "mission-critical",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getIssues()).anyMatch(i ->
                    i.field().equals("identity.autonomyLevel")
                    && "mission-critical".equals(i.value()));
        }

        @Test
        @DisplayName("null autonomyLevel produces required-field issue")
        void nullAutonomyLevel() {
            AgentSpec spec = minimalSpec(AgentType.DETERMINISTIC, null,
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getIssues()).anyMatch(i ->
                    i.field().equals("identity.autonomyLevel") && i.value() == null);
        }

        @Test
        @DisplayName("empty autonomyLevel produces required-field issue")
        void emptyAutonomyLevel() {
            AgentSpec spec = minimalSpec(AgentType.DETERMINISTIC, "",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unsupported spec version
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unsupported spec version")
    class UnsupportedSpecVersion {

        @Test
        @DisplayName("version 0.9.0 produces validation issue")
        void version090() {
            AgentSpec spec = minimalSpec("0.9.0", AgentType.DETERMINISTIC, "SUPERVISED",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getIssues()).anyMatch(i ->
                    i.field().equals("agentSpecVersion") && "0.9.0".equals(i.value()));
        }

        @Test
        @DisplayName("version 3.0.0 (future) produces validation issue")
        void version300() {
            AgentSpec spec = minimalSpec("3.0.0", AgentType.PLANNING, "BOUNDED_AUTONOMOUS",
                    DeterminismGuarantee.NONE, StateMutability.EXTERNAL_STATE);

            ValidationResult result = validator.validate(spec);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getIssues()).anyMatch(i ->
                    i.field().equals("agentSpecVersion") && "3.0.0".equals(i.value()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Guard on null spec
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null spec throws IllegalArgumentException")
    void nullSpecThrows() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spec must not be null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ValidationResult API
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidationResult API")
    class ValidationResultApi {

        @Test
        @DisplayName("throwIfInvalid is no-op for valid spec")
        void throwIfInvalidNoOpForValid() {
            AgentSpec spec = minimalSpec(AgentType.DETERMINISTIC, "SUPERVISED",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThatCode(result::throwIfInvalid).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getIssues returns unmodifiable list")
        void getIssuesIsUnmodifiable() {
            AgentSpec spec = minimalSpec(AgentType.DETERMINISTIC, "SUPERVISED",
                    DeterminismGuarantee.FULL, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);

            assertThatThrownBy(() -> result.getIssues().add(
                    new ValidationIssue("test", "val", "msg")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("AgentSpecValidationException carries the issues list")
        @SuppressWarnings("deprecation")
        void exceptionCarriesIssues() {
            AgentSpec spec = minimalSpec(AgentType.LLM, "SUPERVISED",
                    DeterminismGuarantee.NONE, StateMutability.STATELESS);

            ValidationResult result = validator.validate(spec);
            AgentSpecValidationException ex = catchThrowableOfType(
                    result::throwIfInvalid, AgentSpecValidationException.class);

            assertThat(ex).isNotNull();
            assertThat(ex.getIssues()).isNotEmpty();
        }
    }
}
