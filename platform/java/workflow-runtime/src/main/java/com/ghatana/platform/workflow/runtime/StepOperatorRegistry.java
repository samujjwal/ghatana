/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Resolves operator IDs to executable functions.
 *
 * <p>When a workflow step of kind {@link WorkflowStepKind#ACTION} is encountered,
 * the runtime asks this registry for the corresponding operator. Operators receive
 * the workflow context and step configuration, and return a promise of updated context.
 *
 * @doc.type interface
 * @doc.purpose Maps operator IDs to executable step logic
 * @doc.layer platform
 * @doc.pattern Registry
 */
public interface StepOperatorRegistry {

    /**
     * Looks up an operator by its ID.
     *
     * @param operatorId the unique operator identifier
     * @return the operator, or {@code null} if not found
     */
    StepOperator find(@NotNull String operatorId);

    /**
     * A single executable operator that transforms a workflow context.
     */
    @FunctionalInterface
    interface StepOperator {
        /**
         * Executes the operator logic.
         *
         * @param context the current workflow context as a mutable map
         * @param config  step-level configuration from the definition
         * @return a promise of the updated context
         */
        Promise<Map<String, Object>> execute(
            @NotNull Map<String, Object> context,
            @NotNull Map<String, Object> config);
    }
}
