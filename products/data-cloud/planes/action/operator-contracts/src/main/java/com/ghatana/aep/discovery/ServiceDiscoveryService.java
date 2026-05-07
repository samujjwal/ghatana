/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.discovery;

import java.util.List;
import java.util.Map;

/**
 * Service for auto-discovery of existing services in the environment.
 * <p>
 * Scans the environment to discover services that could be registered as agents,
 * reducing manual configuration effort.
 *
 * @doc.type interface
 * @doc.purpose Auto-discover services for agent registration
 * @doc.layer core
 * @doc.pattern Service
 */
public interface ServiceDiscoveryService {

    /**
     * Discover services in the environment.
     *
     * @param scope discovery scope (e.g., "all", "tenant", "namespace")
     * @param context additional context for discovery
     * @return list of discovered services
     */
    List<DiscoveredService> discoverServices(String scope, Map<String, Object> context);

    /**
     * Register a discovered service as an agent.
     *
     * @param service the discovered service
     * @param autoRegister whether to automatically register the service
     * @return registration result
     */
    RegistrationResult registerService(DiscoveredService service, boolean autoRegister);

    /**
     * Get discovery statistics.
     *
     * @return discovery statistics
     */
    DiscoveryStats getStats();

    /**
     * Discovered service metadata.
     */
    record DiscoveredService(
        String serviceId,
        String serviceName,
        String serviceType,
        String endpoint,
        Map<String, String> metadata,
        ServiceHealth health
    ) {}

    /**
     * Service health status.
     */
    enum ServiceHealth {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

    /**
     * Registration result.
     */
    record RegistrationResult(
        boolean registered,
        String agentId,
        List<String> warnings,
        String reason
    ) {}

    /**
     * Discovery statistics.
     */
    record DiscoveryStats(
        long totalDiscovered,
        long totalRegistered,
        long totalSkipped,
        long totalFailed,
        double registrationRate
    ) {}
}
