package com.ghatana.yappc.agent.util;

import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.yappc.agent.EventPublisher;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error handling utilities for YAPPC workflow steps.
 *
 * <p>Eliminates duplicate error-event building patterns that were previously scattered across
 * workflow step {@code handleError()} methods with inconsistent event structures.
 *
 * <p>Usage:
 * <pre>{@code
 * .whenException(error ->
 *     WorkflowErrorHandler.publishStepError("requirements.intake", error, context, eventClient));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Standardized error handling and event publishing for workflow steps
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class WorkflowErrorHandler {

    private WorkflowErrorHandler() {
        // utility class
    }

    /**
     * Publishes a standardized step-failure event to the event bus and re-propagates the error.
     *
     * <p>The event contains: stepId, workflowId, tenantId, errorType, errorMessage, and timestamp.
     * The event key is {@code <stepId>.failed}.
     *
     * @param stepId       the step identifier (e.g. {@code "requirements.intake"})
     * @param error        the exception that caused the failure
     * @param context      the workflow context at the time of failure
     * @param publisher    the event publisher to use
     * @return a failed {@link Promise} re-propagating {@code error} after publishing
     */
    public static Promise<WorkflowContext> handleStepError(
            String stepId,
            Throwable error,
            WorkflowContext context,
            EventPublisher publisher) {

        Map<String, Object> errorEvent = Map.of(
                "stepId", stepId,
                "workflowId", context.getWorkflowId(),
                "tenantId", context.getTenantId(),
                "errorType", error.getClass().getSimpleName(),
                "errorMessage", error.getMessage() != null ? error.getMessage() : "(no message)",
                "timestamp", Instant.now().toString()
        );

        return publisher.publish(stepId + ".failed", errorEvent)
                .then($ -> Promise.<WorkflowContext>ofException(
                        error instanceof Exception ? (Exception) error : new RuntimeException(error)));
    }

    /**
     * Fire-and-forget variant that publishes a step error event without
     * re-propagating the exception.  Use inside {@code .whenException(...)} callbacks.
     *
     * @param stepId    the step identifier
     * @param error     the exception that occurred
     * @param context   the workflow context
     * @param publisher the event publisher
     */
    public static void publishStepError(
            String stepId,
            Throwable error,
            WorkflowContext context,
            EventPublisher publisher) {

        Map<String, Object> errorEvent = Map.of(
                "stepId", stepId,
                "workflowId", context.getWorkflowId(),
                "tenantId", context.getTenantId(),
                "errorType", error.getClass().getSimpleName(),
                "errorMessage", error.getMessage() != null ? error.getMessage() : "(no message)",
                "timestamp", Instant.now().toString()
        );

        publisher.publish(stepId + ".failed", errorEvent);
    }
}
