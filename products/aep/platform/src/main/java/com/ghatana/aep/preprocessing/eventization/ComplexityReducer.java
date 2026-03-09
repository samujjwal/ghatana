/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.eventization;

import java.util.Map;

/**
 * Reduces complexity of raw signals through threshold-based filtering.
 * 
 * <p><b>Purpose</b><br>
 * Implements signal-to-event mapping and significance testing to filter
 * out low-value signals. Part of the 10:1 compression strategy.
 * 
 * <p><b>Filtering Strategy</b><br>
 * <ul>
 *   <li>Magnitude: Signal value exceeds threshold</li>
 *   <li>Frequency: Signal repeats above threshold</li>
 *   <li>Variance: Signal changes significantly</li>
 *   <li>Impact: Signal affects business metrics</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Signal complexity reduction
 * @doc.layer product
 * @doc.pattern Service
 */
public class ComplexityReducer {
    
    private final double significanceThreshold;

    public ComplexityReducer(double significanceThreshold) {
        this.significanceThreshold = significanceThreshold;
    }

    /**
     * Tests if signal meets significance threshold.
     * 
     * @param signal Raw signal to test
     * @return true if signal is significant
     */
    public boolean meetsSignificanceThreshold(RawSignal signal) {
        double significance = calculateSignificance(signal);
        return significance >= significanceThreshold;
    }

    /**
     * Calculates signal significance score (0.0 to 1.0).
     */
    private double calculateSignificance(RawSignal signal) {
        Map<String, Object> payload = signal.payload();
        
        // Default significance based on signal type
        double baseSignificance = switch (signal.signalType()) {
            case "error", "exception", "failure" -> 1.0;
            case "warning", "alert" -> 0.8;
            case "info", "debug" -> 0.3;
            default -> 0.5;
        };

        // Adjust based on payload attributes
        if (payload.containsKey("severity")) {
            String severity = payload.get("severity").toString();
            baseSignificance = adjustForSeverity(baseSignificance, severity);
        }

        if (payload.containsKey("priority")) {
            String priority = payload.get("priority").toString();
            baseSignificance = adjustForPriority(baseSignificance, priority);
        }

        return Math.min(1.0, baseSignificance);
    }

    private double adjustForSeverity(double base, String severity) {
        return switch (severity.toLowerCase()) {
            case "critical", "fatal" -> Math.max(base, 1.0);
            case "high", "error" -> Math.max(base, 0.9);
            case "medium", "warning" -> Math.max(base, 0.7);
            case "low", "info" -> base;
            default -> base;
        };
    }

    private double adjustForPriority(double base, String priority) {
        return switch (priority.toLowerCase()) {
            case "urgent", "critical", "p0" -> Math.max(base, 1.0);
            case "high", "p1" -> Math.max(base, 0.8);
            case "medium", "p2" -> base;
            case "low", "p3" -> Math.min(base, 0.5);
            default -> base;
        };
    }

    /**
     * Maps raw signal type to semantic event type.
     */
    public String mapSignalToEventType(String signalType) {
        // Map technical signal types to business event types
        return switch (signalType) {
            case "http_request" -> "HttpActivityEvent";
            case "database_update" -> "DataChangeEvent";
            case "file_created" -> "FileSystemEvent";
            case "sensor_reading" -> "TelemetryEvent";
            case "error", "exception" -> "ErrorEvent";
            default -> "GenericEvent";
        };
    }
}
