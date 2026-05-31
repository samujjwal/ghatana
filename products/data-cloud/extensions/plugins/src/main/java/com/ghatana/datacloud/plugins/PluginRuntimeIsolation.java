/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

import java.util.Map;

/**
 * Plugin runtime isolation configuration (P8).
 *
 * <p>Defines the isolation requirements for a plugin's runtime environment.
 *
 * @doc.type record
 * @doc.purpose Plugin runtime isolation configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginRuntimeIsolation(
        IsolationLevel isolationLevel,
        boolean requiresDedicatedProcess,
        boolean requiresDedicatedMemory,
        long maxMemoryMb,
        boolean requiresNetworkIsolation,
        boolean requiresFilesystemIsolation,
        Map<String, String> environmentVariables,
        Map<String, Object> resourceLimits
) {
    public PluginRuntimeIsolation {
        if (isolationLevel == null) {
            isolationLevel = IsolationLevel.SHARED;
        }
        if (maxMemoryMb < 0) {
            maxMemoryMb = 512; // Default 512MB
        }
        if (environmentVariables == null) {
            environmentVariables = Map.of();
        }
        if (resourceLimits == null) {
            resourceLimits = Map.of();
        }
    }

    /**
     * Returns default isolation configuration.
     */
    public static PluginRuntimeIsolation defaultIsolation() {
        return new PluginRuntimeIsolation(IsolationLevel.SHARED, false, false, 512, false, false, Map.of(), Map.of());
    }

    /**
     * Returns true if the plugin requires a dedicated process.
     */
    public boolean requiresDedicatedProcess() {
        return requiresDedicatedProcess;
    }

    /**
     * Returns true if the plugin requires network isolation.
     */
    public boolean requiresNetworkIsolation() {
        return requiresNetworkIsolation;
    }

    /**
     * Isolation level enumeration.
     */
    public enum IsolationLevel {
        /**
         * Plugin runs in the same process as the host application.
         */
        SHARED,
        
        /**
         * Plugin runs in a separate thread but same process.
         */
        THREAD_ISOLATED,
        
        /**
         * Plugin runs in a separate process with shared resources.
         */
        PROCESS_ISOLATED,
        
        /**
         * Plugin runs in a completely isolated container.
         */
        CONTAINER_ISOLATED,
        
        /**
         * Plugin runs in a sandboxed environment with strict restrictions.
         */
        SANDBOXED
    }
}
