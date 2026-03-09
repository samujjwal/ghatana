package com.ghatana.core.pipeline;

import com.ghatana.platform.domain.domain.event.Event;
import java.util.List;
import java.util.Objects;

/**
 * Result of pipeline execution for a single event.
 *
 * <p><b>Purpose</b><br>
 * Represents the outcome of routing an event through a complete pipeline.
 * Includes output events, processing time, and execution metadata.
 *
 * @param pipelineId pipeline identifier that was executed
 * @param inputEvent original input event
 * @param outputEvents events emitted by final stage (0-N events)
 * @param processingTimeMs milliseconds to complete pipeline execution
 * @param stagesExecuted count of stages that processed the event
 * @param isSuccess whether execution completed without errors
 * @param errorMessage error message if isSuccess=false
 *
 * @doc.type record
 * @doc.purpose Pipeline execution result with timing and output information
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PipelineExecutionResult(
    String pipelineId,
    Event inputEvent,
    List<Event> outputEvents,
    long processingTimeMs,
    int stagesExecuted,
    boolean isSuccess,
    String errorMessage
) {
    public PipelineExecutionResult {
        Objects.requireNonNull(pipelineId, "pipelineId cannot be null");
        Objects.requireNonNull(inputEvent, "inputEvent cannot be null");
        Objects.requireNonNull(outputEvents, "outputEvents cannot be null");
        
        if (processingTimeMs < 0) {
            throw new IllegalArgumentException("processingTimeMs cannot be negative");
        }
        if (stagesExecuted < 0) {
            throw new IllegalArgumentException("stagesExecuted cannot be negative");
        }
    }

    /**
     * Creates a successful execution result.
     *
     * @param pipelineId pipeline identifier
     * @param inputEvent input event
     * @param outputEvents output events (0-N)
     * @param processingTimeMs execution time in milliseconds
     * @param stagesExecuted count of stages executed
     * @return successful execution result
     */
    public static PipelineExecutionResult success(
        String pipelineId,
        Event inputEvent,
        List<Event> outputEvents,
        long processingTimeMs,
        int stagesExecuted
    ) {
        return new PipelineExecutionResult(
            pipelineId,
            inputEvent,
            outputEvents,
            processingTimeMs,
            stagesExecuted,
            true,
            null
        );
    }

    /**
     * Creates a failed execution result.
     *
     * @param pipelineId pipeline identifier
     * @param inputEvent input event
     * @param processingTimeMs execution time before failure
     * @param errorMessage error description
     * @return failed execution result with no output events
     */
    public static PipelineExecutionResult failure(
        String pipelineId,
        Event inputEvent,
        long processingTimeMs,
        String errorMessage
    ) {
        return new PipelineExecutionResult(
            pipelineId,
            inputEvent,
            List.of(),
            processingTimeMs,
            0,
            false,
            errorMessage
        );
    }

    /**
     * Checks if pipeline produced output events.
     *
     * @return true if outputEvents is non-empty
     */
    public boolean hasOutput() {
        return !outputEvents.isEmpty();
    }

    /**
     * Gets throughput (events per second).
     *
     * @return events/sec if processingTimeMs > 0, else 0
     */
    public double getThroughputEventsPerSec() {
        if (processingTimeMs == 0) return 0;
        return 1000.0 / processingTimeMs;
    }
}
