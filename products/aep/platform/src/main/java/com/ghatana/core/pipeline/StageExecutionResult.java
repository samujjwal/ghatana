package com.ghatana.core.pipeline;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.domain.domain.event.Event;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of executing a single pipeline stage.
 *
 * <p><b>Purpose</b><br>
 * Captures the complete outcome of one operator's execution within a pipeline run:
 * which stage ran, which operator handled it, the input/output events, timing, and
 * any error that occurred.
 *
 * <p><b>Architecture Role</b><br>
 * Produced by {@link PipelineExecutionEngine} for each stage in the topological
 * execution order. Collected into the final {@link PipelineExecutionResult} to
 * provide per-stage observability.
 *
 * <p><b>Thread Safety</b><br>
 * Fully immutable record — safe for concurrent access.
 *
 * @param stageId      the pipeline stage identifier that was executed
 * @param operatorId   the operator that processed the event(s)
 * @param inputEvents  the event(s) fed into this stage
 * @param result       the {@link OperatorResult} from the operator, or null on lookup failure
 * @param duration     wall-clock time for this stage's execution
 * @param success      true if the stage completed without error
 * @param errorMessage human-readable error description, or null on success
 * @param routeLabel   the edge label used to reach this stage (primary/error/fallback/broadcast)
 *
 * @see PipelineExecutionEngine
 * @see PipelineExecutionResult
 *
 * @doc.type record
 * @doc.purpose Per-stage execution outcome
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record StageExecutionResult(
        String stageId,
        OperatorId operatorId,
        List<Event> inputEvents,
        OperatorResult result,
        Duration duration,
        boolean success,
        String errorMessage,
        String routeLabel
) {
    public StageExecutionResult {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(operatorId, "operatorId");
        inputEvents = inputEvents != null ? List.copyOf(inputEvents) : List.of();
        routeLabel = routeLabel != null ? routeLabel : PipelineEdge.LABEL_PRIMARY;
    }

    /**
     * Creates a successful stage result.
     */
    public static StageExecutionResult success(String stageId, OperatorId operatorId,
                                                List<Event> inputEvents, OperatorResult result,
                                                Duration duration) {
        return new StageExecutionResult(stageId, operatorId, inputEvents, result, duration,
                true, null, PipelineEdge.LABEL_PRIMARY);
    }

    /**
     * Creates a successful stage result reached via a specific route.
     */
    public static StageExecutionResult success(String stageId, OperatorId operatorId,
                                                List<Event> inputEvents, OperatorResult result,
                                                Duration duration, String routeLabel) {
        return new StageExecutionResult(stageId, operatorId, inputEvents, result, duration,
                true, null, routeLabel);
    }

    /**
     * Creates a failed stage result.
     */
    public static StageExecutionResult failure(String stageId, OperatorId operatorId,
                                                List<Event> inputEvents, Duration duration,
                                                String errorMessage) {
        return new StageExecutionResult(stageId, operatorId, inputEvents, null, duration,
                false, errorMessage, PipelineEdge.LABEL_PRIMARY);
    }

    /**
     * Returns the output events from this stage, or an empty list if the stage failed
     * or produced no output.
     */
    public List<Event> getOutputEvents() {
        if (result == null || !success) {
            return List.of();
        }
        return result.getOutputEvents() != null ? result.getOutputEvents() : List.of();
    }

    /**
     * Returns true if this stage produced at least one output event.
     */
    public boolean hasOutput() {
        return !getOutputEvents().isEmpty();
    }

    /**
     * Returns the processing time in milliseconds.
     */
    public long getDurationMs() {
        return duration != null ? duration.toMillis() : 0;
    }
}
