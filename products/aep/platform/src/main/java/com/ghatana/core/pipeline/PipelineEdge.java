package com.ghatana.core.pipeline;

import java.util.Objects;

/**
 * Immutable representation of a dependency edge between pipeline stages.
 *
 * <p><b>Purpose</b><br>
 * Represents data flow dependency in pipeline DAG. An edge from stage A to B
 * means "output from A feeds as input to B". Edges enable pipeline routing logic.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineEdge edge = new PipelineEdge(
 *     "filter",       // Source stage
 *     "enrich",       // Target stage
 *     "primary"       // Optional: edge label (e.g., "primary", "error", "fallback")
 * );
 * }</pre>
 *
 * <p><b>Edge Types</b><br>
 * - **Primary**: Normal data flow (default)
 * - **Error**: Error/exception handling flow
 * - **Fallback**: Alternative flow when primary produces no output
 * - **Broadcast**: Multiple targets from single source
 *
 * @param from Source stage ID (not null, non-empty)
 * @param to Target stage ID (not null, non-empty)
 * @param label Optional edge label for routing (default: "primary")
 *
 * @doc.type record
 * @doc.purpose Pipeline edge representing data flow dependency
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PipelineEdge(
    String from,
    String to,
    String label
) {
    /**
     * Standard edge labels.
     */
    public static final String LABEL_PRIMARY = "primary";
    public static final String LABEL_ERROR = "error";
    public static final String LABEL_FALLBACK = "fallback";
    public static final String LABEL_BROADCAST = "broadcast";

    /**
     * Constructor with validation.
     *
     * @param from source stage (not null, non-empty)
     * @param to target stage (not null, non-empty)
     * @param label edge label (not null, defaults to "primary")
     */
    public PipelineEdge {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        Objects.requireNonNull(label, "label cannot be null");
        
        if (from.isBlank()) {
            throw new IllegalArgumentException("from cannot be blank");
        }
        if (to.isBlank()) {
            throw new IllegalArgumentException("to cannot be blank");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label cannot be blank");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("Self-loops not allowed: " + from + " -> " + to);
        }
    }

    /**
     * Creates a primary flow edge (most common).
     *
     * @param from source stage
     * @param to target stage
     * @return edge with label "primary"
     */
    public static PipelineEdge primary(String from, String to) {
        return new PipelineEdge(from, to, LABEL_PRIMARY);
    }

    /**
     * Creates an error flow edge.
     *
     * @param from source stage
     * @param to error handler stage
     * @return edge with label "error"
     */
    public static PipelineEdge error(String from, String to) {
        return new PipelineEdge(from, to, LABEL_ERROR);
    }

    /**
     * Creates a fallback flow edge.
     *
     * @param from source stage
     * @param to fallback stage
     * @return edge with label "fallback"
     */
    public static PipelineEdge fallback(String from, String to) {
        return new PipelineEdge(from, to, LABEL_FALLBACK);
    }

    /**
     * Checks if this is a primary (normal) flow edge.
     *
     * @return true if label is "primary"
     */
    public boolean isPrimary() {
        return LABEL_PRIMARY.equals(label);
    }

    /**
     * Checks if this is an error handling edge.
     *
     * @return true if label is "error"
     */
    public boolean isError() {
        return LABEL_ERROR.equals(label);
    }
}
