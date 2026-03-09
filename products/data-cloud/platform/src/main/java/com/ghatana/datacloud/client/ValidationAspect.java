/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.client;

import com.ghatana.datacloud.record.Record;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * AI aspect for validating data using AI-powered quality checks.
 *
 * <p>This aspect runs in PRE phase to validate incoming data
 * before it's persisted. Can reject or flag invalid data.
 *
 * <h2>Validation Types</h2>
 * <ul>
 *   <li><b>Format</b> - Data format validation</li>
 *   <li><b>Completeness</b> - Required field checks</li>
 *   <li><b>Consistency</b> - Cross-field consistency</li>
 *   <li><b>Quality</b> - AI-powered quality scoring</li>
 *   <li><b>Semantic</b> - Content semantic validation</li>
 * </ul>
 *
 * <h2>Context Attributes</h2>
 * <ul>
 *   <li>{@code validation.passed} - Whether validation passed</li>
 *   <li>{@code validation.errors} - List of validation errors</li>
 *   <li>{@code validation.warnings} - List of warnings</li>
 *   <li>{@code validation.score} - Quality score</li>
 * </ul>
 *
 * @see AIAspect
 * @doc.type class
 * @doc.purpose Data validation aspect
 * @doc.layer core
 * @doc.pattern Aspect, Validator
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class ValidationAspect implements AIAspect<Record, Record> {

    /** Context key for validation passed flag */
    public static final String ATTR_PASSED = "validation.passed";
    /** Context key for errors list */
    public static final String ATTR_ERRORS = "validation.errors";
    /** Context key for warnings list */
    public static final String ATTR_WARNINGS = "validation.warnings";
    /** Context key for quality score */
    public static final String ATTR_SCORE = "validation.score";

    private final List<Validator> validators;
    private final boolean failFast;
    private final int priority;

    private ValidationAspect(Builder builder) {
        this.validators = List.copyOf(builder.validators);
        this.failFast = builder.failFast;
        this.priority = builder.priority;
    }

    @Override
    public String name() {
        return "validation";
    }

    @Override
    public Phase phase() {
        return Phase.PRE;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean isApplicable(OperationType operation, AIAspectContext context) {
        return operation == OperationType.CREATE || operation == OperationType.UPDATE;
    }

    @Override
    public Promise<Record> process(Record input, AIAspectContext context) {
        List<ValidationError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        return runValidators(input, context, errors, warnings, 0)
                .map(score -> {
                    boolean passed = errors.isEmpty();
                    context.setAttribute(ATTR_PASSED, passed);
                    context.setAttribute(ATTR_ERRORS, errors);
                    context.setAttribute(ATTR_WARNINGS, warnings);
                    context.setAttribute(ATTR_SCORE, score);

                    if (!passed && failFast) {
                        throw new ValidationException(errors);
                    }

                    return input;
                });
    }

    private Promise<Double> runValidators(
            Record input,
            AIAspectContext context,
            List<ValidationError> errors,
            List<String> warnings,
            int index
    ) {
        if (index >= validators.size()) {
            // Calculate average score (default 1.0 if no errors)
            double score = errors.isEmpty() ? 1.0 : Math.max(0.0, 1.0 - (errors.size() * 0.2));
            return Promise.of(score);
        }

        Validator validator = validators.get(index);
        
        return validator.validate(input, context)
                .then(result -> {
                    errors.addAll(result.errors());
                    warnings.addAll(result.warnings());

                    if (failFast && !result.errors().isEmpty()) {
                        return Promise.of(0.0); // Stop early
                    }

                    return runValidators(input, context, errors, warnings, index + 1);
                });
    }

    /**
     * Creates a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ValidationAspect.
     */
    public static final class Builder {
        private final List<Validator> validators = new ArrayList<>();
        private boolean failFast = true;
        private int priority = 10; // Early in PRE phase

        private Builder() {
        }

        /**
         * Adds a validator.
         *
         * @param validator the validator
         * @return this builder
         */
        public Builder add(Validator validator) {
            validators.add(validator);
            return this;
        }

        /**
         * Adds a simple predicate validator.
         *
         * @param name validator name
         * @param predicate validation predicate
         * @param errorMessage error message if failed
         * @return this builder
         */
        public Builder add(String name, Predicate<Record> predicate, String errorMessage) {
            validators.add(new PredicateValidator(name, predicate, errorMessage));
            return this;
        }

        /**
         * Sets fail-fast behavior.
         *
         * @param failFast true to stop on first error
         * @return this builder
         */
        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        /**
         * Sets the priority.
         *
         * @param priority priority value
         * @return this builder
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Builds the aspect.
         *
         * @return configured aspect
         */
        public ValidationAspect build() {
            return new ValidationAspect(this);
        }
    }

    /**
     * Validator interface.
     */
    public interface Validator {

        /**
         * Returns the validator name.
         *
         * @return name
         */
        String name();

        /**
         * Validates the record.
         *
         * @param record the record
         * @param context the context
         * @return validation result
         */
        Promise<ValidationResult> validate(Record record, AIAspectContext context);
    }

    /**
     * Validation result.
     *
     * @param errors list of errors
     * @param warnings list of warnings
     */
    public record ValidationResult(
            List<ValidationError> errors,
            List<String> warnings
    ) {
        public static ValidationResult success() {
            return new ValidationResult(List.of(), List.of());
        }

        public static ValidationResult error(String field, String message) {
            return new ValidationResult(
                    List.of(new ValidationError(field, message, ErrorSeverity.ERROR)),
                    List.of()
            );
        }

        public static ValidationResult warning(String message) {
            return new ValidationResult(List.of(), List.of(message));
        }
    }

    /**
     * Validation error.
     *
     * @param field the field with error
     * @param message error message
     * @param severity error severity
     */
    public record ValidationError(
            String field,
            String message,
            ErrorSeverity severity
    ) {
    }

    /**
     * Error severity levels.
     */
    public enum ErrorSeverity {
        WARNING,
        ERROR,
        CRITICAL
    }

    /**
     * Exception thrown on validation failure.
     */
    public static class ValidationException extends RuntimeException {
        private final List<ValidationError> errors;

        public ValidationException(List<ValidationError> errors) {
            super("Validation failed: " + errors.size() + " error(s)");
            this.errors = List.copyOf(errors);
        }

        public List<ValidationError> errors() {
            return errors;
        }
    }

    /**
     * Simple predicate-based validator.
     */
    private record PredicateValidator(
            String name,
            Predicate<Record> predicate,
            String errorMessage
    ) implements Validator {

        @Override
        public Promise<ValidationResult> validate(Record record, AIAspectContext context) {
            if (predicate.test(record)) {
                return Promise.of(ValidationResult.success());
            }
            return Promise.of(ValidationResult.error("record", errorMessage));
        }
    }
}
