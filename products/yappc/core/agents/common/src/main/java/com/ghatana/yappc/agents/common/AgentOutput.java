package com.ghatana.yappc.agents.common;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Base class for all agent output types.
 * Provides common fields and functionality for agent outputs.
 */
@Data
@SuperBuilder
public abstract class AgentOutput {
    /**
     * Unique identifier for this output response.
     */
    private String responseId;
    
    /**
     * Status of the agent execution.
     */
    private ExecutionStatus status;
    
    /**
     * Timestamp when this output was created.
     */
    private Long timestamp;
    
    /**
     * Optional error message if execution failed.
     */
    private String errorMessage;
    
    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE,
        PENDING
    }
}
