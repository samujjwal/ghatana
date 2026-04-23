/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.core.domain.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PipelineSpecValidator}.
 *
 * @doc.type class
 * @doc.purpose Tests for PipelineSpecValidator static analysis rules
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("PipelineSpecValidator")
class PipelineSpecValidatorTest {

    private PipelineSpecValidator validator;

    @BeforeEach
    void setUp() { // GH-90000
        validator = new PipelineSpecValidator(); // GH-90000
    }

    /** Creates a minimal valid pipeline spec with one stage. */
    private PipelineSpec validSpec() { // GH-90000
        return PipelineSpecBuilder.create("pipe-1", "Valid Pipeline") // GH-90000
                .forTenant("tenant-alpha")
                .addStage(PipelineStageSpecBuilder.create("s1", "source") // GH-90000
                        .ofType("SOURCE")
                        .build()) // GH-90000
                .build(); // GH-90000
    }

    // ─── valid pipeline ───────────────────────────────────────────────────────

    @Test
    @DisplayName("valid minimal pipeline passes validation")
    void validate_validPipeline_noErrors() { // GH-90000
        PipelineSpecValidator.ValidationReport report = validator.validate(validSpec()); // GH-90000

        assertThat(report.isValid()).isTrue(); // GH-90000
        assertThat(report.errors()).isEmpty(); // GH-90000
    }

    // ─── pipeline-level errors ────────────────────────────────────────────────

    @Nested
    @DisplayName("pipeline-level validation")
    class PipelineLevelValidation {

        @Test
        @DisplayName("reports error when pipeline has no stages")
        void validate_noStages_error() { // GH-90000
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null, // GH-90000
                    java.util.List.of(), // GH-90000
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false), // GH-90000
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThat(report.isValid()).isFalse(); // GH-90000
            assertThat(report.errors()).anyMatch(e -> e.contains("at least one stage"));
        }

        @Test
        @DisplayName("reports error when pipeline timeout is zero")
        void validate_zeroTimeout_error() { // GH-90000
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null, // GH-90000
                    java.util.List.of(PipelineStageSpecBuilder.create("s", "s").ofType("X").build()),
                    new PipelineSpec.PipelineConfiguration(3, 0L, "STREAMING", false), // GH-90000
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThat(report.isValid()).isFalse(); // GH-90000
            assertThat(report.errors()).anyMatch(e -> e.contains("timeout"));
        }

        @Test
        @DisplayName("reports error when pipeline timeout is negative")
        void validate_negativeTimeout_error() { // GH-90000
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null, // GH-90000
                    java.util.List.of(PipelineStageSpecBuilder.create("s", "s").ofType("X").build()),
                    new PipelineSpec.PipelineConfiguration(3, -1L, "STREAMING", false), // GH-90000
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThat(report.isValid()).isFalse(); // GH-90000
            assertThat(report.errors()).anyMatch(e -> e.contains("timeout"));
        }

        @Test
        @DisplayName("reports error when maxRetries is negative")
        void validate_negativeMaxRetries_error() { // GH-90000
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null, // GH-90000
                    java.util.List.of(PipelineStageSpecBuilder.create("s", "s").ofType("X").build()),
                    new PipelineSpec.PipelineConfiguration(-1, 5_000L, "STREAMING", false), // GH-90000
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThat(report.isValid()).isFalse(); // GH-90000
            assertThat(report.errors()).anyMatch(e -> e.contains("maxRetries"));
        }
    }

    // ─── stage-level errors ───────────────────────────────────────────────────

    @Nested
    @DisplayName("stage-level validation")
    class StageLevelValidation {

        @Test
        @DisplayName("reports error when stage type is missing")
        void validate_missingStageType_error() { // GH-90000
            // Build a stage then manually create one without a type using the raw constructor
            PipelineStageSpec noType = new PipelineStageSpec("id", "name", "  ", null, // GH-90000
                    java.util.List.of(), null, true); // GH-90000

            PipelineSpec spec = new PipelineSpec("pipe", "Pipeline", "tenant", null, // GH-90000
                    java.util.List.of(noType), // GH-90000
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false), // GH-90000
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThat(report.isValid()).isFalse(); // GH-90000
            assertThat(report.errors()).anyMatch(e -> e.contains("stageType"));
        }

        @Test
        @DisplayName("reports error when stage IDs are duplicated")
        void validate_duplicateStageIds_error() { // GH-90000
            PipelineStageSpec s1 = new PipelineStageSpec("same-id", "source", "SOURCE", null, // GH-90000
                    java.util.List.of(), null, true); // GH-90000
            PipelineStageSpec s2 = new PipelineStageSpec("same-id", "sink", "SINK", null, // GH-90000
                    java.util.List.of(), null, true); // GH-90000

            PipelineSpec spec = new PipelineSpec("pipe", "Pipeline", "tenant", null, // GH-90000
                    java.util.List.of(s1, s2), // GH-90000
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false), // GH-90000
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThat(report.isValid()).isFalse(); // GH-90000
            assertThat(report.errors()).anyMatch(e -> e.contains("duplicate"));
        }

        @Test
        @DisplayName("reports error when stage parallelism is less than 1")
        void validate_parallelismZero_error() { // GH-90000
            PipelineStageSpec stage = new PipelineStageSpec("s", "stage", "FILTER", null, // GH-90000
                    java.util.List.of(), // GH-90000
                    new PipelineStageSpec.StageConfiguration(0, 5_000L, "AT_LEAST_ONCE", true), // GH-90000
                    true);

            PipelineSpec spec = new PipelineSpec("pipe", "Pipeline", "tenant", null, // GH-90000
                    java.util.List.of(stage), // GH-90000
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false), // GH-90000
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThat(report.isValid()).isFalse(); // GH-90000
            assertThat(report.errors()).anyMatch(e -> e.contains("parallelism"));
        }
    }

    // ─── ValidationReport ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidationReport")
    class ValidationReportTest {

        @Test
        @DisplayName("throwIfInvalid() does not throw when valid")
        void throwIfInvalid_valid_doesNotThrow() { // GH-90000
            PipelineSpecValidator.ValidationReport report = validator.validate(validSpec()); // GH-90000
            // Should not throw:
            report.throwIfInvalid(); // GH-90000
        }

        @Test
        @DisplayName("throwIfInvalid() throws IllegalArgumentException with error list when invalid")
        void throwIfInvalid_invalid_throws() { // GH-90000
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null, // GH-90000
                    java.util.List.of(), // GH-90000
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false), // GH-90000
                    true);
            PipelineSpecValidator.ValidationReport report = validator.validate(spec); // GH-90000

            assertThatThrownBy(report::throwIfInvalid) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("validation failed");
        }

        @Test
        @DisplayName("errors list is unmodifiable")
        void errors_unmodifiable() { // GH-90000
            PipelineSpecValidator.ValidationReport report = validator.validate(validSpec()); // GH-90000

            assertThatThrownBy(() -> report.errors().add("injected"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Test
    @DisplayName("validate() throws NullPointerException for null spec")
    void validate_nullSpec_throws() { // GH-90000
        assertThatThrownBy(() -> validator.validate(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
