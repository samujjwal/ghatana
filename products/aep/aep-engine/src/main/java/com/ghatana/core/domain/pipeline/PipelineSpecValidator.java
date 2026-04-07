package com.ghatana.core.domain.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Static analysis validator for {@link PipelineSpec} definitions.
 *
 * <p>Performs structural and semantic validation on a pipeline before it is
 * deployed or executed — catching misconfigurations early without requiring a
 * running engine.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>Pipeline must have a non-blank ID, name, and tenantId.</li>
 *   <li>Pipeline must have at least one stage.</li>
 *   <li>Every stage must have a non-blank ID, name, and stageType.</li>
 *   <li>Stage IDs must be unique within the pipeline.</li>
 *   <li>Pipeline timeout must be positive.</li>
 *   <li>Stage parallelism must be at least 1.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelineSpecValidator validator = new PipelineSpecValidator();
 * ValidationReport report = validator.validate(spec);
 * if (!report.isValid()) {
 *     report.errors().forEach(System.out::println);
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Static analysis validator for pipeline specifications — catches misconfigurations early
 * @doc.layer core
 * @doc.pattern Validator
 */
public final class PipelineSpecValidator {

    /**
     * Validates the given pipeline specification.
     *
     * @param spec the pipeline specification to validate (must not be null)
     * @return a {@link ValidationReport} containing any errors found
     */
    public ValidationReport validate(PipelineSpec spec) {
        Objects.requireNonNull(spec, "Pipeline spec must not be null");

        List<String> errors = new ArrayList<>();

        // ── Pipeline-level checks ──────────────────────────────────────────────

        if (isBlank(spec.getId())) {
            errors.add("Pipeline ID must not be blank");
        }
        if (isBlank(spec.getName())) {
            errors.add("Pipeline name must not be blank");
        }
        if (isBlank(spec.getTenantId())) {
            errors.add("Pipeline tenantId must not be blank");
        }
        if (spec.getStages().isEmpty()) {
            errors.add("Pipeline must have at least one stage");
        }
        if (spec.getConfiguration() != null && spec.getConfiguration().getTimeoutMs() <= 0) {
            errors.add("Pipeline timeout must be positive, got: " + spec.getConfiguration().getTimeoutMs());
        }
        if (spec.getConfiguration() != null && spec.getConfiguration().getMaxRetries() < 0) {
            errors.add("Pipeline maxRetries must be >= 0, got: " + spec.getConfiguration().getMaxRetries());
        }

        // ── Stage-level checks ─────────────────────────────────────────────────

        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (int i = 0; i < spec.getStages().size(); i++) {
            PipelineStageSpec stage = spec.getStages().get(i);
            String prefix = "Stage[" + i + "]";

            if (stage == null) {
                errors.add(prefix + " must not be null");
                continue;
            }
            if (isBlank(stage.getId())) {
                errors.add(prefix + " ID must not be blank");
            } else if (!seenIds.add(stage.getId())) {
                errors.add(prefix + " has duplicate ID: " + stage.getId());
            }
            if (isBlank(stage.getName())) {
                errors.add(prefix + " name must not be blank (id=" + stage.getId() + ")");
            }
            if (isBlank(stage.getStageType())) {
                errors.add(prefix + " stageType must not be blank (id=" + stage.getId() + ")");
            }
            if (stage.getConfiguration() != null && stage.getConfiguration().getParallelism() < 1) {
                errors.add(prefix + " parallelism must be >= 1, got: "
                        + stage.getConfiguration().getParallelism() + " (id=" + stage.getId() + ")");
            }
        }

        return new ValidationReport(errors);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ─── Result type ──────────────────────────────────────────────────────────

    /**
     * Result of a pipeline validation run.
     *
     * @param errors unmodifiable list of validation error messages; empty when valid
     *
     * @doc.type record
     * @doc.purpose Immutable validation report from PipelineSpecValidator
     * @doc.layer core
     * @doc.pattern ValueObject
     */
    public record ValidationReport(List<String> errors) {

        /**
         * Compact constructor — wraps errors in an unmodifiable list.
         */
        public ValidationReport {
            errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        /**
         * Returns {@code true} when no validation errors were found.
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Throws an {@link IllegalArgumentException} when the report contains errors.
         *
         * @throws IllegalArgumentException containing all error messages
         */
        public void throwIfInvalid() {
            if (!isValid()) {
                throw new IllegalArgumentException(
                        "Pipeline validation failed:\n  - " + String.join("\n  - ", errors));
            }
        }
    }
}

