package com.ghatana.softwareorg.devops.events;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages DevOps department state (deployments, incidents, configuration,
 * health). Thread-safe storage with tenant isolation.
 */
public class DevopsStateManager {

    private final Map<String, Map<String, Object>> deploymentsByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> incidentsByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> configChangesByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> healthChecksByTenant
            = new ConcurrentHashMap<>();

    public void recordDeploymentStart(String tenantId, String deploymentId, String environment,
            String version, String featureId) {
        Map<String, Object> deploymentData = new ConcurrentHashMap<>();
        deploymentData.put("deploymentId", deploymentId);
        deploymentData.put("environment", environment);
        deploymentData.put("version", version);
        deploymentData.put("featureId", featureId);
        deploymentData.put("startTime", System.currentTimeMillis());

        deploymentsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(deploymentId, deploymentData);
    }

    public void recordDeploymentSuccess(String tenantId, String deploymentId, long durationMs) {
        @SuppressWarnings("unchecked")
        Map<String, Object> deploymentData = (Map<String, Object>) deploymentsByTenant
                .getOrDefault(tenantId, new ConcurrentHashMap<>()).get(deploymentId);

        if (deploymentData != null) {
            deploymentData.put("status", "succeeded");
            deploymentData.put("durationMs", durationMs);
            deploymentData.put("completedTime", System.currentTimeMillis());
        }
    }

    public void recordDeploymentFailure(String tenantId, String deploymentId, String failureReason) {
        @SuppressWarnings("unchecked")
        Map<String, Object> deploymentData = (Map<String, Object>) deploymentsByTenant
                .getOrDefault(tenantId, new ConcurrentHashMap<>()).get(deploymentId);

        if (deploymentData != null) {
            deploymentData.put("status", "failed");
            deploymentData.put("failureReason", failureReason);
            deploymentData.put("failedTime", System.currentTimeMillis());
        }
    }

    public void recordIncidentStart(String tenantId, String incidentId, String severity,
            String description) {
        Map<String, Object> incidentData = new ConcurrentHashMap<>();
        incidentData.put("incidentId", incidentId);
        incidentData.put("severity", severity);
        incidentData.put("description", description);
        incidentData.put("startTime", System.currentTimeMillis());

        incidentsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(incidentId, incidentData);
    }

    public void recordIncidentResolution(String tenantId, String incidentId, long mttrMinutes) {
        @SuppressWarnings("unchecked")
        Map<String, Object> incidentData = (Map<String, Object>) incidentsByTenant
                .getOrDefault(tenantId, new ConcurrentHashMap<>()).get(incidentId);

        if (incidentData != null) {
            incidentData.put("status", "resolved");
            incidentData.put("mttrMinutes", mttrMinutes);
            incidentData.put("resolvedTime", System.currentTimeMillis());
        }
    }

    public void recordConfigChange(String tenantId, String changeId, String changeType,
            String component) {
        Map<String, Object> changeData = new ConcurrentHashMap<>();
        changeData.put("changeId", changeId);
        changeData.put("changeType", changeType);
        changeData.put("component", component);
        changeData.put("timestamp", System.currentTimeMillis());

        configChangesByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(changeId, changeData);
    }

    public void recordHealthCheck(String tenantId, String serviceId, boolean healthy,
            double cpuUsagePercent, double memoryUsagePercent, double diskUsagePercent) {
        Map<String, Object> healthData = new ConcurrentHashMap<>();
        healthData.put("serviceId", serviceId);
        healthData.put("healthy", healthy);
        healthData.put("cpuUsagePercent", cpuUsagePercent);
        healthData.put("memoryUsagePercent", memoryUsagePercent);
        healthData.put("diskUsagePercent", diskUsagePercent);
        healthData.put("timestamp", System.currentTimeMillis());

        healthChecksByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(serviceId, healthData);
    }
}
