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
 * Phase 5: Replay semantics tests for PatternSpec.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Replay policy is required in semantics</li>
 *   <li>Replay policy must be a valid object</li>
 *   <li>Replay modes are validated</li>
 * </ul>
 */
@DisplayName("PatternSpec Replay Semantics Tests (Phase 5)")
class PatternSpecReplayTest {

    // =========================================================================
    //  Replay Policy Requirements
    // =========================================================================

    @Nested
    @DisplayName("Replay Policy Requirements")
    class ReplayPolicyTests {

        @Test
        @DisplayName("semantics requires replayPolicy")
        void semanticsRequiresReplayPolicy() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("replayPolicy"));
        }

        @Test
        @DisplayName("replayPolicy object is valid")
        void replayPolicyObjectIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of("mode", "replayable", "retention", "P30D")));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("empty replayPolicy is valid")
        void emptyReplayPolicyIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of()));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            // Empty replayPolicy is still considered present, so it passes
            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("null replayPolicy is rejected")
        void nullReplayPolicyIsRejected() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            Map<String, Object> semantics = new java.util.LinkedHashMap<>();
            semantics.put("timePolicy", Map.of());
            semantics.put("uncertaintyPolicy", Map.of());
            semantics.put("replayPolicy", null);
            spec.put("semantics", semantics);

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            // Null replayPolicy is treated as blank, so it fails the check
            assertThat(result.valid()).isFalse();
        }
    }

    // =========================================================================
    //  Replay Modes
    // =========================================================================

    @Nested
    @DisplayName("Replay Modes")
    class ReplayModeTests {

        @Test
        @DisplayName("replayable mode is valid")
        void replayableModeIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of("mode", "replayable")));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("non-replayable mode is valid")
        void nonReplayableModeIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of("mode", "non-replayable")));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("stateful mode is valid")
        void statefulModeIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of("mode", "stateful")));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Replay Retention
    // =========================================================================

    @Nested
    @DisplayName("Replay Retention")
    class ReplayRetentionTests {

        @Test
        @DisplayName("retention can be specified")
        void retentionCanBeSpecified() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of("mode", "replayable", "retention", "P30D")));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("retention can be omitted")
        void retentionCanBeOmitted() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("semantics", Map.of(
                "timePolicy", Map.of(),
                "uncertaintyPolicy", Map.of(),
                "replayPolicy", Map.of("mode", "replayable")));

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
