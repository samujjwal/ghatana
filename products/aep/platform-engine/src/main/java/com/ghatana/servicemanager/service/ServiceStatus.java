package com.ghatana.servicemanager.service;

/**
 * Service status enumeration.
 * 
 * @doc.type enum
 * @doc.purpose Service status representation
 * @doc.layer orchestration
 * @doc.pattern Enumeration
 */
public enum ServiceStatus {
    /**
     * Service is currently running and healthy.
     */
    RUNNING,
    
    /**
     * Service is stopped but enabled.
     */
    STOPPED,
    
    /**
     * Service is disabled in configuration.
     */
    DISABLED,
    
    /**
     * Service failed to start or crashed.
     */
    FAILED,
    
    /**
     * Service status is unknown (configuration missing).
     */
    UNKNOWN
}
