package com.ghatana.datacloud.launcher.connectors;

/**
 * P0-08: Durable job state for connector operations.
 * 
 * <p>This enum represents the lifecycle state of connector jobs (sync, test, schema discovery).
 * Jobs transition through these states as they are processed by the connector job runtime.
 *
 * @doc.type enum
 * @doc.purpose Durable job state for connector operations
 * @doc.layer product
 * @doc.pattern State Machine
 */
public enum ConnectorJobState {
    /**
     * Job has been created but not yet started processing.
     */
    PENDING,
    
    /**
     * Job is being validated (credentials, permissions, connectivity).
     */
    VALIDATING,
    
    /**
     * Job is actively executing.
     */
    RUNNING,
    
    /**
     * Job completed with some operations succeeded but others failed.
     */
    PARTIAL_SUCCESS,
    
    /**
     * Job failed to complete successfully.
     */
    FAILED,
    
    /**
     * Job completed successfully.
     */
    COMPLETED,
    
    /**
     * Job was cancelled by user or system.
     */
    CANCELLED,
    
    /**
     * Job is being retried after a transient failure.
     */
    RETRYING,
    
    /**
     * Job has exhausted retry attempts and is marked as dead letter.
     */
    DEAD_LETTERED
}
