package com.ghatana.yappc.domain.workflow.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * RFC-7807 Problem Detail record for YAPPC workflow HTTP errors.
 *
 * <p>Every workflow error response carries a structured JSON body conforming to
 * <a href="https://www.rfc-editor.org/rfc/rfc7807">RFC 7807 Problem Details for HTTP APIs</a>.
 * The {@code Content-Type} is set to {@code application/problem+json}.
 *
 * <p>Use {@link WorkflowProblemHandler} to map workflow exceptions to typed problem responses
 * instead of building ad-hoc {@code Map.of("error", ...)} payloads.
 *
 * <p>Example response body:
 * <pre>{@code
 * {
 *   "type": "https://yappc.ghatana.com/problems/workflow-not-found",
 *   "title": "Workflow Not Found",
 *   "status": 404,
 *   "detail": "Workflow wf-abc123 was not found for tenant tenant-xyz",
 *   "instance": "/api/v1/workflows/wf-abc123",
 *   "correlationId": "3f2e1a00-…",
 *   "timestamp": "2026-04-27T14:00:00.000Z"
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose RFC-7807 Problem Details envelope for workflow HTTP errors (F-Y045 / K-Y12)
 * @doc.layer product
 * @doc.pattern DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowProblemDetail(
    /**
     * A URI reference that identifies the problem type.
     * Dereferencing this URI should provide human-readable documentation.
     */
    @JsonProperty("type")
    @NotNull String type,

    /**
     * A short, human-readable summary of the problem type.
     * MUST NOT change across occurrences of the same problem type.
     */
    @JsonProperty("title")
    @NotNull String title,

    /**
     * The HTTP status code applicable to this problem.
     */
    @JsonProperty("status")
    int status,

    /**
     * A human-readable explanation specific to this occurrence.
     */
    @JsonProperty("detail")
    @Nullable String detail,

    /**
     * A URI reference identifying the specific occurrence (typically the request path).
     */
    @JsonProperty("instance")
    @Nullable String instance,

    /**
     * Correlation ID for distributed tracing. Propagated from X-Correlation-ID.
     */
    @JsonProperty("correlationId")
    @Nullable String correlationId,

    /**
     * Server-side timestamp in ISO-8601 format when the problem occurred.
     */
    @JsonProperty("timestamp")
    @NotNull String timestamp
) {
    /** Base URI for YAPPC problem type documentation. */
    static final String PROBLEM_BASE_URI = "https://yappc.ghatana.com/problems/";

    // ── Factory methods ────────────────────────────────────────────────────────

    /**
     * Creates a 404 workflow-not-found problem detail.
     *
     * @param workflowId   the missing workflow ID
     * @param instance     the request path
     * @param correlationId correlation ID from the request
     * @return RFC-7807 problem detail
     */
    @NotNull
    public static WorkflowProblemDetail workflowNotFound(
        @NotNull String workflowId,
        @Nullable String instance,
        @Nullable String correlationId
    ) {
        return new WorkflowProblemDetail(
            PROBLEM_BASE_URI + "workflow-not-found",
            "Workflow Not Found",
            404,
            "Workflow " + workflowId + " was not found",
            instance,
            correlationId,
            Instant.now().toString()
        );
    }

    /**
     * Creates a 409 invalid-workflow-state problem detail.
     *
     * @param detail       description of the invalid state transition
     * @param instance     the request path
     * @param correlationId correlation ID from the request
     * @return RFC-7807 problem detail
     */
    @NotNull
    public static WorkflowProblemDetail invalidState(
        @NotNull String detail,
        @Nullable String instance,
        @Nullable String correlationId
    ) {
        return new WorkflowProblemDetail(
            PROBLEM_BASE_URI + "invalid-workflow-state",
            "Invalid Workflow State",
            409,
            detail,
            instance,
            correlationId,
            Instant.now().toString()
        );
    }

    /**
     * Creates a 500 workflow-execution-error problem detail.
     *
     * @param detail       short description of the failure
     * @param instance     the request path
     * @param correlationId correlation ID from the request
     * @return RFC-7807 problem detail
     */
    @NotNull
    public static WorkflowProblemDetail executionError(
        @NotNull String detail,
        @Nullable String instance,
        @Nullable String correlationId
    ) {
        return new WorkflowProblemDetail(
            PROBLEM_BASE_URI + "workflow-execution-error",
            "Workflow Execution Error",
            500,
            detail,
            instance,
            correlationId,
            Instant.now().toString()
        );
    }

    /**
     * Creates a 400 bad-request problem detail.
     *
     * @param detail       description of the validation failure
     * @param instance     the request path
     * @param correlationId correlation ID from the request
     * @return RFC-7807 problem detail
     */
    @NotNull
    public static WorkflowProblemDetail badRequest(
        @NotNull String detail,
        @Nullable String instance,
        @Nullable String correlationId
    ) {
        return new WorkflowProblemDetail(
            PROBLEM_BASE_URI + "bad-request",
            "Bad Request",
            400,
            detail,
            instance,
            correlationId,
            Instant.now().toString()
        );
    }

    /**
     * Creates a 500 unexpected-error problem detail with a generated reference ID.
     *
     * @param instance      the request path
     * @param correlationId correlation ID from the request
     * @return RFC-7807 problem detail
     */
    @NotNull
    public static WorkflowProblemDetail unexpectedError(
        @Nullable String instance,
        @Nullable String correlationId
    ) {
        return new WorkflowProblemDetail(
            PROBLEM_BASE_URI + "unexpected-error",
            "Internal Server Error",
            500,
            "An unexpected error occurred. Reference: " + UUID.randomUUID(),
            instance,
            correlationId,
            Instant.now().toString()
        );
    }
}
