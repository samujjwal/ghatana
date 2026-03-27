package com.ghatana.yappc.agent.util;

import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Reusable validation utilities for YAPPC workflow steps.
 *
 * <p>Eliminates duplicate {@code validateInput()} boilerplate that was previously
 * copy-pasted across {@code IntakeStep}, {@code ValidateStep}, {@code DeriveRequirementsStep},
 * {@code PolicyCheckStep} and others.
 *
 * <p>Usage:
 * <pre>{@code
 * // Single required field
 * return WorkflowStepValidator.validateRequiredFields(context, "source");
 *
 * // Multiple required fields
 * return WorkflowStepValidator.validateRequiredFields(context, "requirementId", "normalizedContent");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Composable input validation for workflow steps - eliminates duplication
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class WorkflowStepValidator {

    private WorkflowStepValidator() {
        // utility class
    }

    /**
     * Validates that {@code context.getData()} is non-null, non-empty, and contains
     * all {@code requiredFields}.
     *
     * <p>Returns a failed {@link Promise} with {@link IllegalArgumentException} on the
     * first violation found; otherwise returns {@code Promise.of(context)}.
     *
     * @param context        the workflow context to validate
     * @param requiredFields zero or more field keys that must be present in the data map
     * @return a succeeded {@code Promise<WorkflowContext>} or a failed one with the first error
     */
    public static Promise<WorkflowContext> validateRequiredFields(
            WorkflowContext context, String... requiredFields) {

        Map<String, Object> data = context.getData();

        if (data == null || data.isEmpty()) {
            return Promise.ofException(
                    new IllegalArgumentException("Input data is required"));
        }

        for (String field : requiredFields) {
            if (!data.containsKey(field)) {
                return Promise.ofException(
                        new IllegalArgumentException("Field '" + field + "' is required"));
            }
        }

        return Promise.of(context);
    }
}
