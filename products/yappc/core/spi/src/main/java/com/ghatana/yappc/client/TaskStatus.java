package com.ghatana.yappc.client;

/**
 * Status of a task execution.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type enum
 * @doc.purpose Enumerates task status values
 * @doc.layer core
 * @doc.pattern Enum
*/
public enum TaskStatus {
    /**
     * Task is pending execution.
     */
    PENDING,
    
    /**
     * Task is currently running.
     */
    RUNNING,
    
    /**
     * Task completed successfully.
     */
    SUCCESS,
    
    /**
     * Task failed with an error.
     */
    FAILED,
    
    /**
     * Task was cancelled.
     */
    CANCELLED
}
