package com.ghatana.orchestrator.store;

/**
 * Checkpoint status enumeration.
 */
public enum PipelineCheckpointStatus {
    CREATED,      // Initial state
    RUNNING,      // Execution in progress
    STEP_SUCCESS, // Individual step completed successfully
    STEP_FAILED,  // Individual step failed
    COMPLETED,    // Entire pipeline completed successfully
    FAILED,       // Entire pipeline failed
    CANCELLED     // Execution cancelled
}
