/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Individual step in a Saga transaction.
 * Represents a service operation with optional compensation action.
 *
 * @doc.type interface
 * @doc.purpose Saga step interface for individual service operations
 * @doc.layer core
 * @doc.pattern Command
 */
public interface SagaStep {

    /**
     * Gets the step name.
     *
     * @return step name
     */
    String name();

    /**
     * Executes the step's primary action.
     *
     * @return promise resolving to the step result
     */
    Promise<SagaStepResult> execute();

    /**
     * Executes the compensation action (undo the primary action).
     *
     * @return promise resolving to the compensation result
     */
    Promise<SagaStepResult> compensate();

    /**
     * Default compensation that does nothing (no-op).
     *
     * @return promise of successful no-op result
     */
    default Promise<SagaStepResult> noOpCompensate() {
        return Promise.of(new SagaStepResult(name(), true, null, Map.of(), "No-op compensation"));
    }

    /**
     * Result of a saga step execution.
     *
     * @param stepName step name
     * @param success whether the step succeeded
     * @param error error message if failed
     * @param output output data from the step
     * @param message human-readable message
     */
    record SagaStepResult(
        String stepName,
        boolean success,
        String error,
        Map<String, Object> output,
        String message
    ) {
        public SagaStepResult {
            output = output != null ? Map.copyOf(output) : Map.of();
        }
    }

    /**
     * Builder for creating saga steps.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for saga steps.
     */
    class Builder {
        private String name;
        private io.activej.promise.Promise<SagaStepResult> executePromise;
        private io.activej.promise.Promise<SagaStepResult> compensatePromise;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder execute(io.activej.promise.Promise<SagaStepResult> executePromise) {
            this.executePromise = executePromise;
            return this;
        }

        public Builder compensate(io.activej.promise.Promise<SagaStepResult> compensatePromise) {
            this.compensatePromise = compensatePromise;
            return this;
        }

        public SagaStep build() {
            final String stepName = name;
            final io.activej.promise.Promise<SagaStepResult> exec = executePromise;
            final io.activej.promise.Promise<SagaStepResult> comp = compensatePromise;

            return new SagaStep() {
                @Override
                public String name() {
                    return stepName;
                }

                @Override
                public Promise<SagaStepResult> execute() {
                    return exec != null ? exec : Promise.of(new SagaStepResult(stepName, false, 
                        "No execute action defined", Map.of(), "No execute action"));
                }

                @Override
                public Promise<SagaStepResult> compensate() {
                    return comp != null ? comp : noOpCompensate();
                }
            };
        }
    }
}
