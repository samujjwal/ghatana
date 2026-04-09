/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        validator = new PipelineSpecValidator();
    }

    /** Creates a minimal valid pipeline spec with one stage. */
    private PipelineSpec validSpec() {
        return PipelineSpecBuilder.create("pipe-1", "Valid Pipeline")
                .forTenant("tenant-alpha")
                .addStage(PipelineStageSpecBuilder.create("s1", "source")
                        .ofType("SOURCE")
                        .build())
                .build();
    }

    // ─── valid pipeline ───────────────────────────────────────────────────────

    @Test
    @DisplayName("valid minimal pipeline passes validation")
    void validate_validPipeline_noErrors() {
        PipelineSpecValidator.ValidationReport report = validator.validate(validSpec());

        assertThat(report.isValid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    // ─── pipeline-level errors ────────────────────────────────────────────────

    @Nested
    @DisplayName("pipeline-level validation")
    class PipelineLevelValidation {

        @Test
        @DisplayName("reports error when pipeline has no stages")
        void validate_noStages_error() {
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null,
                    java.util.List.of(),
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false),
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThat(report.isValid()).isFalse();
            assertThat(report.errors()).anyMatch(e -> e.contains("at least one stage"));
        }

        @Test
        @DisplayName("reports error when pipeline timeout is zero")
        void validate_zeroTimeout_error() {
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null,
                    java.util.List.of(PipelineStageSpecBuilder.create("s", "s").ofType("X").build()),
                    new PipelineSpec.PipelineConfiguration(3, 0L, "STREAMING", false),
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThat(report.isValid()).isFalse();
            assertThat(report.errors()).anyMatch(e -> e.contains("timeout"));
        }

        @Test
        @DisplayName("reports error when pipeline timeout is negative")
        void validate_negativeTimeout_error() {
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null,
                    java.util.List.of(PipelineStageSpecBuilder.create("s", "s").ofType("X").build()),
                    new PipelineSpec.PipelineConfiguration(3, -1L, "STREAMING", false),
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThat(report.isValid()).isFalse();
            assertThat(report.errors()).anyMatch(e -> e.contains("timeout"));
        }

        @Test
        @DisplayName("reports error when maxRetries is negative")
        void validate_negativeMaxRetries_error() {
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null,
                    java.util.List.of(PipelineStageSpecBuilder.create("s", "s").ofType("X").build()),
                    new PipelineSpec.PipelineConfiguration(-1, 5_000L, "STREAMING", false),
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThat(report.isValid()).isFalse();
            assertThat(report.errors()).anyMatch(e -> e.contains("maxRetries"));
        }
    }

    // ─── stage-level errors ───────────────────────────────────────────────────

    @Nested
    @DisplayName("stage-level validation")
    class StageLevelValidation {

        @Test
        @DisplayName("reports error when stage type is missing")
        void validate_missingStageType_error() {
            // Build a stage then manually create one without a type using the raw constructor
            PipelineStageSpec noType = new PipelineStageSpec("id", "name", "  ", null,
                    java.util.List.of(), null, true);

            PipelineSpec spec = new PipelineSpec("pipe", "Pipeline", "tenant", null,
                    java.util.List.of(noType),
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false),
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThat(report.isValid()).isFalse();
            assertThat(report.errors()).anyMatch(e -> e.contains("stageType"));
        }

        @Test
        @DisplayName("reports error when stage IDs are duplicated")
        void validate_duplicateStageIds_error() {
            PipelineStageSpec s1 = new PipelineStageSpec("same-id", "source", "SOURCE", null,
                    java.util.List.of(), null, true);
            PipelineStageSpec s2 = new PipelineStageSpec("same-id", "sink", "SINK", null,
                    java.util.List.of(), null, true);

            PipelineSpec spec = new PipelineSpec("pipe", "Pipeline", "tenant", null,
                    java.util.List.of(s1, s2),
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false),
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThat(report.isValid()).isFalse();
            assertThat(report.errors()).anyMatch(e -> e.contains("duplicate"));
        }

        @Test
        @DisplayName("reports error when stage parallelism is less than 1")
        void validate_parallelismZero_error() {
            PipelineStageSpec stage = new PipelineStageSpec("s", "stage", "FILTER", null,
                    java.util.List.of(),
                    new PipelineStageSpec.StageConfiguration(0, 5_000L, "AT_LEAST_ONCE", true),
                    true);

            PipelineSpec spec = new PipelineSpec("pipe", "Pipeline", "tenant", null,
                    java.util.List.of(stage),
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false),
                    true);

            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThat(report.isValid()).isFalse();
            assertThat(report.errors()).anyMatch(e -> e.contains("parallelism"));
        }
    }

    // ─── ValidationReport ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidationReport")
    class ValidationReportTest {

        @Test
        @DisplayName("throwIfInvalid() does not throw when valid")
        void throwIfInvalid_valid_doesNotThrow() {
            PipelineSpecValidator.ValidationReport report = validator.validate(validSpec());
            // Should not throw:
            report.throwIfInvalid();
        }

        @Test
        @DisplayName("throwIfInvalid() throws IllegalArgumentException with error list when invalid")
        void throwIfInvalid_invalid_throws() {
            PipelineSpec spec = new PipelineSpec("id", "name", "tenant", null,
                    java.util.List.of(),
                    new PipelineSpec.PipelineConfiguration(3, 5_000L, "STREAMING", false),
                    true);
            PipelineSpecValidator.ValidationReport report = validator.validate(spec);

            assertThatThrownBy(report::throwIfInvalid)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("validation failed");
        }

        @Test
        @DisplayName("errors list is unmodifiable")
        void errors_unmodifiable() {
            PipelineSpecValidator.ValidationReport report = validator.validate(validSpec());

            assertThatThrownBy(() -> report.errors().add("injected"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    @DisplayName("validate() throws NullPointerException for null spec")
    void validate_nullSpec_throws() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
