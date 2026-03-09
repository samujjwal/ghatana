package com.ghatana.alerting;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;

/**
 * Alert manager interface.
 * 
 * @doc.type interface
 * @doc.purpose Alert management contract
 * @doc.layer product
 * @doc.pattern Manager
 */
public interface AlertManager {
    
    /**
     * Sends an alert.
     */
    Promise<Void> sendAlert(Alert alert);
    
    /**
     * Sends multiple alerts.
     */
    Promise<Void> sendAlerts(List<Alert> alerts);
    
    /**
     * Gets active alerts.
     */
    Promise<List<Alert>> getActiveAlerts();
    
    /**
     * Gets alerts by severity.
     */
    Promise<List<Alert>> getAlertsBySeverity(Alert.Severity severity);
    
    /**
     * Acknowledges an alert.
     */
    Promise<Void> acknowledgeAlert(String alertId);
    
    /**
     * Resolves an alert.
     */
    Promise<Void> resolveAlert(String alertId, String resolution);
    
    /**
     * Checks if alert manager is healthy.
     */
    boolean isHealthy();
    
    /**
     * Gets alert statistics.
     */
    Map<String, Object> getStatistics();
}
