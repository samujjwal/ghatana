/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

import java.time.Instant;
import java.util.Map;

/**
 * Plugin health information (P8).
 *
 * <p>Defines the health status of a plugin including last check time,
 * metrics, and any error information.
 *
 * @doc.type record
 * @doc.purpose Plugin health status information
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginHealth(
        HealthStatus status,
        String message,
        Instant lastHealthCheck,
        long uptimeMs,
        Map<String, Object> metrics,
        String lastError,
        Instant lastErrorTime
) {
    public PluginHealth {
        if (status == null) {
            status = HealthStatus.UNKNOWN;
        }
        if (message == null) {
            message = "";
        }
        if (lastHealthCheck == null) {
            lastHealthCheck = Instant.now();
        }
        if (metrics == null) {
            metrics = Map.of();
        }
    }

    /**
     * Returns unknown health status.
     */
    public static PluginHealth unknown() {
        return new PluginHealth(HealthStatus.UNKNOWN, "Health status unknown", Instant.now(), 0, Map.of(), null, null);
    }

    /**
     * Returns healthy status.
     */
    public static PluginHealth healthy(String message) {
        return new PluginHealth(HealthStatus.HEALTHY, message, Instant.now(), 0, Map.of(), null, null);
    }

    /**
     * Returns degraded status.
     */
    public static PluginHealth degraded(String message) {
        return new PluginHealth(HealthStatus.DEGRADED, message, Instant.now(), 0, Map.of(), null, null);
    }

    /**
     * Returns unhealthy status.
     */
    public static PluginHealth unhealthy(String message, String error) {
        return new PluginHealth(HealthStatus.UNHEALTHY, message, Instant.now(), 0, Map.of(), error, Instant.now());
    }

    /**
     * Returns true if the plugin is healthy.
     */
    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY;
    }

    /**
     * Returns true if the plugin is in a degraded or unhealthy state.
     */
    public boolean isUnhealthy() {
        return status == HealthStatus.DEGRADED || status == HealthStatus.UNHEALTHY;
    }

    /**
     * Health status enumeration.
     */
    public enum HealthStatus {
        /**
         * Health status is unknown.
         */
        UNKNOWN,
        
        /**
         * Plugin is healthy and functioning normally.
         */
        HEALTHY,
        
        /**
         * Plugin is functioning but with degraded performance.
         */
        DEGRADED,
        
        /**
         * Plugin is not functioning properly.
         */
        UNHEALTHY,
        
        /**
         * Plugin is not running.
         */
        STOPPED
    }
}
