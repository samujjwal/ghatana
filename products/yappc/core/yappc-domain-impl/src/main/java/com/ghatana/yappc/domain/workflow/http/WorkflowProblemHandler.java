package com.ghatana.yappc.domain.workflow.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.products.yappc.domain.workflow.AiWorkflowService;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central RFC-7807 error handler for workflow HTTP operations.
 *
 * <p>Maps workflow-domain exceptions to typed {@link WorkflowProblemDetail} responses
 * with {@code Content-Type: application/problem+json} (F-Y045 / K-Y12).
 *
 * <p>Callers use {@link #handle(Exception, String, String)} directly or the
 * {@link #asPromiseHandler(String, String)} convenience form, which returns a
 * {@code Function<Exception, Promise<HttpResponse>>} compatible with ActiveJ
 * {@code .then(Promise::of, handler)}.
 *
 * <p>Example usage in a controller:
 * <pre>{@code
 * return workflowService.doSomething(id, tenantId)
 *     .map(result -> ResponseBuilder.ok().json(result).build())
 *     .then(Promise::of, problemHandler.asPromiseHandler(request.getPath(), correlationId));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Central RFC-7807 error handler for workflow HTTP errors (F-Y045 / K-Y12)
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class WorkflowProblemHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowProblemHandler.class);

    /** Content-Type for RFC-7807 problem responses. */
    static final String PROBLEM_JSON = "application/problem+json";

    private final ObjectMapper objectMapper;

    /**
     * Creates a handler backed by the given Jackson mapper.
     *
     * @param objectMapper must support standard JSON serialisation
     */
    public WorkflowProblemHandler(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Converts an exception into a typed RFC-7807 {@link HttpResponse}.
     *
     * @param e             the exception to handle
     * @param instance      request path for the {@code instance} field (may be null)
     * @param correlationId correlation ID for the {@code correlationId} field (may be null)
     * @return a completed {@link HttpResponse} with the appropriate status and problem body
     */
    @NotNull
    public HttpResponse handle(@NotNull Exception e, @Nullable String instance, @Nullable String correlationId) {
        WorkflowProblemDetail problem;
        int status;

        if (e instanceof AiWorkflowService.WorkflowNotFoundException notFound) {
            problem = WorkflowProblemDetail.workflowNotFound(
                notFound.getMessage() != null ? notFound.getMessage() : "unknown",
                instance,
                correlationId
            );
            status = 404;
        } else if (e instanceof AiWorkflowService.InvalidWorkflowStateException) {
            problem = WorkflowProblemDetail.invalidState(
                e.getMessage() != null ? e.getMessage() : "Invalid state transition",
                instance,
                correlationId
            );
            status = 409;
        } else if (e instanceof AiWorkflowService.WorkflowExecutionException) {
            problem = WorkflowProblemDetail.executionError(
                e.getMessage() != null ? e.getMessage() : "Execution failed",
                instance,
                correlationId
            );
            status = 500;
            LOG.error("Workflow execution error [correlationId={}] [instance={}]", correlationId, instance, e);
        } else {
            problem = WorkflowProblemDetail.unexpectedError(instance, correlationId);
            status = 500;
            LOG.error("Unexpected workflow error [correlationId={}] [instance={}]", correlationId, instance, e);
        }

        return buildProblemResponse(problem, status);
    }

    /**
     * Returns a function compatible with ActiveJ {@code .then(Promise::of, handler)}.
     *
     * <p>The function converts exceptions to RFC-7807 responses wrapped in a completed promise.
     *
     * @param instance      request path for the {@code instance} field (may be null)
     * @param correlationId correlation ID for the {@code correlationId} field (may be null)
     * @return a function mapping {@link Exception} to {@code Promise<HttpResponse>}
     */
    @NotNull
    public java.util.function.Function<Exception, Promise<HttpResponse>> asPromiseHandler(
        @Nullable String instance,
        @Nullable String correlationId
    ) {
        return e -> Promise.of(handle(e, instance, correlationId));
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    @NotNull
    private HttpResponse buildProblemResponse(@NotNull WorkflowProblemDetail problem, int status) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(problem);
            return ResponseBuilder.status(status)
                .bytes(body, PROBLEM_JSON)
                .build();
        } catch (Exception serializationError) {
            LOG.error("Failed to serialize WorkflowProblemDetail", serializationError);
            // Fallback: plain text to avoid infinite loops in error handling
            return ResponseBuilder.internalServerError()
                .text("Internal Server Error")
                .build();
        }
    }
}
