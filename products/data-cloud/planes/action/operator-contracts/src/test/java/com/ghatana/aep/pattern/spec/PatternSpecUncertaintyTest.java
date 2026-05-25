/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5: Uncertainty propagation tests for PatternSpec.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Uncertainty policy is required in semantics</li>
 *   <li>Uncertainty policy must be a valid object</li>
 *   <li>Uncertainty propagation rules are validated</li>
 * </ul>
 */
@DisplayName("PatternSpec Uncertainty Propagation Tests (Phase 5)")
class PatternSpecUncertaintyTest {

    // =========================================================================
    //  Uncertainty Policy Requirements
    // =========================================================================

    @Nested
    @DisplayName("Uncertainty Policy Requirements")
    class UncertaintyPolicyTests {

        @Test
        @DisplayName("semantics requires uncertaintyPolicy")
        void semanticsRequiresUncertaintyPolicy() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of("timePolicy", Map.of(), "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("uncertaintyPolicy"));
        }

        @Test
        @DisplayName("uncertaintyPolicy object is valid")
        void uncertaintyPolicyObjectIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of("mode", "propagate", "threshold", 0.95),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("empty uncertaintyPolicy is valid")
        void emptyUncertaintyPolicyIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            // Empty uncertaintyPolicy is still considered present, so it passes
            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("null uncertaintyPolicy is rejected")
        void nullUncertaintyPolicyIsRejected() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            Map<String, Object> semantics = new java.util.LinkedHashMap<>();
            semantics.put("timePolicy", Map.of());
            semantics.put("uncertaintyPolicy", null);
            semantics.put("replayPolicy", Map.of());
            spec.put("semantics", semantics);

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            // Null uncertaintyPolicy is treated as blank, so it fails the check
            assertThat(result.valid()).isFalse();
        }
    }

    // =========================================================================
    //  Uncertainty Propagation Modes
    // =========================================================================

    @Nested
    @DisplayName("Uncertainty Propagation Modes")
    class UncertaintyPropagationModeTests {

        @Test
        @DisplayName("propagate mode is valid")
        void propagateModeIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of("mode", "propagate"),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("drop mode is valid")
        void dropModeIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of("mode", "drop"),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("aggregate mode is valid")
        void aggregateModeIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of("mode", "aggregate"),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Uncertainty Threshold
    // =========================================================================

    @Nested
    @DisplayName("Uncertainty Threshold")
    class UncertaintyThresholdTests {

        @Test
        @DisplayName("threshold can be specified")
        void thresholdCanBeSpecified() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of("mode", "propagate", "threshold", 0.95),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("threshold can be omitted")
        void thresholdCanBeOmitted() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of("mode", "propagate"),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Helper
    // =========================================================================

    private static Map<String, Object> validSpec(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "test", "namespace", "test", "version", "1.0.0", "tenantId", "tenant-a", "owner", "sre"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true, "tracing", true)));
    }
}
