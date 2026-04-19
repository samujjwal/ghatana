/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of service discovery service.
 * <p>
 * Provides rule-based service discovery using environment variables,
 * service registry lookups, and endpoint scanning.
 *
 * @doc.type class
 * @doc.purpose Rule-based service discovery service
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DefaultServiceDiscoveryService implements ServiceDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultServiceDiscoveryService.class);

    private final AtomicLong totalDiscovered = new AtomicLong(0);
    private final AtomicLong totalRegistered = new AtomicLong(0);
    private final AtomicLong totalSkipped = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);

    @Override
    public List<DiscoveredService> discoverServices(String scope, Map<String, Object> context) {
        logger.info("Discovering services with scope={}, context={}", scope, context);
        List<DiscoveredService> services = new ArrayList<>();

        // Discover services from environment variables
        services.addAll(discoverFromEnvironment(scope, context));

        // Discover services from service registry (placeholder)
        services.addAll(discoverFromRegistry(scope, context));

        // Discover services from endpoint scanning (placeholder)
        services.addAll(discoverFromEndpoints(scope, context));

        totalDiscovered.addAndGet(services.size());
        logger.info("Discovered {} services", services.size());

        return services;
    }

    @Override
    public RegistrationResult registerService(DiscoveredService service, boolean autoRegister) {
        if (!autoRegister) {
            logger.info("Auto-registration disabled for service: {}", service.serviceId());
            totalSkipped.incrementAndGet();
            return new RegistrationResult(false, null, List.of("auto_register_disabled"), "Auto-registration disabled");
        }

        // Validate service health
        if (service.health() == ServiceHealth.UNHEALTHY) {
            logger.warn("Skipping unhealthy service: {}", service.serviceId());
            totalFailed.incrementAndGet();
            return new RegistrationResult(false, null, List.of("unhealthy"), "Service is unhealthy");
        }

        // Validate required fields
        List<String> warnings = new ArrayList<>();
        if (service.endpoint() == null || service.endpoint().isEmpty()) {
            warnings.add("No endpoint provided");
        }

        // Generate agent ID
        String agentId = "agent-" + service.serviceId();
        totalRegistered.incrementAndGet();

        logger.info("Registered service as agent: serviceId={}, agentId={}", service.serviceId(), agentId);
        return new RegistrationResult(true, agentId, warnings, "Successfully registered");
    }

    @Override
    public DiscoveryStats getStats() {
        long discovered = totalDiscovered.get();
        long registered = totalRegistered.get();
        return new DiscoveryStats(
            discovered,
            registered,
            totalSkipped.get(),
            totalFailed.get(),
            discovered > 0 ? (double) registered / discovered : 0.0
        );
    }

    private List<DiscoveredService> discoverFromEnvironment(String scope, Map<String, Object> context) {
        List<DiscoveredService> services = new ArrayList<>();

        // Check for common service environment variables
        String[] serviceEnvVars = {
            "PAYMENT_SERVICE_URL",
            "USER_SERVICE_URL",
            "ORDER_SERVICE_URL",
            "INVENTORY_SERVICE_URL",
            "NOTIFICATION_SERVICE_URL"
        };

        for (String envVar : serviceEnvVars) {
            String endpoint = System.getenv(envVar);
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = System.getProperty(envVar);
            }
            if (endpoint != null && !endpoint.isEmpty()) {
                String serviceName = envVar.replace("_URL", "").toLowerCase();
                services.add(new DiscoveredService(
                    "env-" + serviceName,
                    serviceName,
                    "http",
                    endpoint,
                    Map.of("source", "environment"),
                    ServiceHealth.HEALTHY
                ));
            }
        }

        return services;
    }

    private List<DiscoveredService> discoverFromRegistry(String scope, Map<String, Object> context) {
        // Placeholder for service registry integration
        // In production, this would query a service registry (Consul, etcd, Kubernetes API)
        logger.debug("Service registry discovery not yet implemented");
        return List.of();
    }

    private List<DiscoveredService> discoverFromEndpoints(String scope, Map<String, Object> context) {
        // Placeholder for endpoint scanning
        // In production, this would scan common endpoint ranges
        logger.debug("Endpoint scanning not yet implemented");
        return List.of();
    }
}
