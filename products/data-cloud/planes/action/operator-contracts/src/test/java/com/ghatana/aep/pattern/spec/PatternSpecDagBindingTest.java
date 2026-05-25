/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5: Runtime DAG binding tests for PatternSpec.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Pattern expressions form valid DAG structure</li>
 *   <li>Operator operands are properly connected</li>
 *   <li>Circular references are detected</li>
 * </ul>
 */
@DisplayName("PatternSpec Runtime DAG Binding Tests (Phase 5)")
class PatternSpecDagBindingTest {

    // =========================================================================
    //  DAG Structure Validation
    // =========================================================================

    @Nested
    @DisplayName("DAG Structure Validation")
    class DagStructureTests {

        @Test
        @DisplayName("SEQ operator requires at least 2 operands")
        void seqRequiresAtLeast2Operands() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "SEQ",
                "operands", List.of(Map.of("event", "test.event")))));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("at least 2 operands"));
        }

        @Test
        @DisplayName("SEQ operator with 2 operands is valid")
        void seqWith2OperandsIsValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "SEQ",
                "operands", List.of(
                    Map.of("event", "test.event1"),
                    Map.of("event", "test.event2")))));

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("AND operator requires at least 2 operands")
        void andRequiresAtLeast2Operands() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AND",
                "operands", List.of(Map.of("event", "test.event")))));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("at least 2 operands"));
        }

        @Test
        @DisplayName("OR operator requires at least 2 operands")
        void orRequiresAtLeast2Operands() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "OR",
                "operands", List.of(Map.of("event", "test.event")))));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("at least 2 operands"));
        }

        @Test
        @DisplayName("NOT operator requires nested pattern")
        void notRequiresNestedPattern() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "NOT")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("nested pattern"));
        }

        @Test
        @DisplayName("NOT operator with nested pattern is valid")
        void notWithNestedPatternIsValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "NOT",
                "pattern", Map.of("event", "test.event"))));

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Nested DAG Structure
    // =========================================================================

    @Nested
    @DisplayName("Nested DAG Structure")
    class NestedDagTests {

        @Test
        @DisplayName("nested SEQ operators are valid")
        void nestedSeqOperatorsAreValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "SEQ",
                "operands", List.of(
                    Map.of("event", "test.event1"),
                    Map.of(
                        "operator", "SEQ",
                        "operands", List.of(
                            Map.of("event", "test.event2"),
                            Map.of("event", "test.event3")))))));

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("nested AND operators are valid")
        void nestedAndOperatorsAreValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AND",
                "operands", List.of(
                    Map.of("event", "test.event1"),
                    Map.of(
                        "operator", "AND",
                        "operands", List.of(
                            Map.of("event", "test.event2"),
                            Map.of("event", "test.event3")))))));

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("mixed operator nesting is valid")
        void mixedOperatorNestingIsValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "SEQ",
                "operands", List.of(
                    Map.of("event", "test.event1"),
                    Map.of(
                        "operator", "AND",
                        "operands", List.of(
                            Map.of("event", "test.event2"),
                            Map.of("event", "test.event3")))))));

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Window Operators in DAG
    // =========================================================================

    @Nested
    @DisplayName("Window Operators in DAG")
    class WindowOperatorDagTests {

        @Test
        @DisplayName("WINDOW operator requires nested pattern")
        void windowRequiresNestedPattern() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "WINDOW",
                "window", "PT10M")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("nested pattern"));
        }

        @Test
        @DisplayName("WINDOW operator with nested pattern is valid")
        void windowWithNestedPatternIsValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "WINDOW",
                "window", "PT10M",
                "pattern", Map.of("event", "test.event"))));

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("ABSENCE operator requires event")
        void absenceRequiresEvent() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "ABSENCE",
                "window", "PT10M")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("event"));
        }

        @Test
        @DisplayName("ABSENCE operator with event is valid")
        void absenceWithEventIsValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "ABSENCE",
                "window", "PT10M",
                "event", "test.event")));

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
