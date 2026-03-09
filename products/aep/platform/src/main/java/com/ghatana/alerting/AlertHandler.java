package com.ghatana.alerting;

/**
 * Handler for alerts.
 * 
 * @doc.type interface
 * @doc.purpose Alert handling contract
 * @doc.layer product
 * @doc.pattern Handler
 */
public interface AlertHandler {
    
    /**
     * Handles an alert.
     */
    void handle(Alert alert);
}
