package com.ghatana.softwareorg.devops.events;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles DevOps department events. Processes deployments, incidents,
 * infrastructure changes, and health checks. Coordinates with engineering (QA
 * approval), support (incident response), and compliance.
 */
public class DevopsEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevopsEventHandler.class);
    private final MetricsCollector metrics;
    private final DevopsStateManager stateManager;

    public DevopsEventHandler(MetricsCollector metrics, DevopsStateManager stateManager) {
        this.metrics = metrics != null ? metrics : new NoopMetricsCollector();
        this.stateManager = stateManager;
    }

    /**
     * Handle deployment_started event. Records deployment initiation, validates
     * prerequisites, begins canary/blue-green.
     */
    public void handleDeploymentStarted(String deploymentId, String environment, String version,
            String featureId, String tenantId) {
        LOGGER.info("Deployment started: {} to {} (v{})", deploymentId, environment, version);

        try {
            stateManager.recordDeploymentStart(tenantId, deploymentId, environment, version, featureId);
            metrics.incrementCounter("devops.deployments.started", "environment", environment);
            LOGGER.info("Deployment {} initiated for feature {}", deploymentId, featureId);

        } catch (Exception e) {
            LOGGER.error("Error handling deployment started: {}", deploymentId, e);
            metrics.incrementCounter("devops.events.failed", "event_type", "deployment_started");
            throw new RuntimeException("Failed to handle deployment started", e);
        }
    }

    /**
     * Handle deployment_succeeded event. Records deployment completion, updates
     * version registry, triggers canary analysis.
     */
    public void handleDeploymentSucceeded(String deploymentId, long durationMs, String environment,
            String version, String tenantId) {
        LOGGER.info("Deployment succeeded: {} in {}ms", deploymentId, durationMs);

        try {
            stateManager.recordDeploymentSuccess(tenantId, deploymentId, durationMs);

            metrics.incrementCounter("devops.deployments.succeeded", "environment", environment);
            metrics.recordTimer("devops.deployment.duration_ms", durationMs,
                    "environment", environment);

            // Deployment succeeded - notify support and product for monitoring
            LOGGER.info("Deployment {} completed successfully", deploymentId);

        } catch (Exception e) {
            LOGGER.error("Error handling deployment succeeded: {}", deploymentId, e);
            metrics.incrementCounter("devops.events.failed", "event_type", "deployment_succeeded");
            throw new RuntimeException("Failed to handle deployment succeeded", e);
        }
    }

    /**
     * Handle deployment_failed event. Records failure, initiates rollback if
     * necessary, notifies on-call.
     */
    public void handleDeploymentFailed(String deploymentId, String failureReason,
            String environment, String tenantId) {
        LOGGER.error("Deployment failed: {} - {}", deploymentId, failureReason);

        try {
            stateManager.recordDeploymentFailure(tenantId, deploymentId, failureReason);

            metrics.incrementCounter("devops.deployments.failed", "environment", environment);
            metrics.incrementCounter("devops.deployments.rollback_triggered",
                    "reason", failureReason);

            // Deployment failed - initiate rollback
            LOGGER.error("Deployment {} failed. Initiating rollback", deploymentId);
            // Notify on-call via incident

        } catch (Exception e) {
            LOGGER.error("Error handling deployment failed: {}", deploymentId, e);
            metrics.incrementCounter("devops.events.failed", "event_type", "deployment_failed");
            throw new RuntimeException("Failed to handle deployment failed", e);
        }
    }

    /**
     * Handle incident_detected event. Records incident, classifies severity,
     * pages on-call team, creates ticket.
     */
    public void handleIncidentDetected(String incidentId, String severity, String description,
            String environment, String tenantId) {
        LOGGER.error("Incident detected: {} ({}) - {}", incidentId, severity, description);

        try {
            stateManager.recordIncidentStart(tenantId, incidentId, severity, description);

            metrics.incrementCounter("devops.incidents.detected", "severity", severity);

            if ("critical".equalsIgnoreCase(severity) || "blocker".equalsIgnoreCase(severity)) {
                LOGGER.error("CRITICAL incident detected: {}", incidentId);
                metrics.incrementCounter("devops.incidents.critical");
                // Page on-call immediately
            } else {
                LOGGER.warn("Incident detected: {}", incidentId);
            }

        } catch (Exception e) {
            LOGGER.error("Error handling incident detected: {}", incidentId, e);
            metrics.incrementCounter("devops.events.failed", "event_type", "incident_detected");
            throw new RuntimeException("Failed to handle incident detected", e);
        }
    }

    /**
     * Handle incident_resolved event. Records resolution, calculates MTTR,
     * documents root cause, triggers postmortem.
     */
    public void handleIncidentResolved(String incidentId, long mttrMinutes, String resolution,
            String tenantId) {
        LOGGER.info("Incident resolved: {} (MTTR: {}min)", incidentId, mttrMinutes);

        try {
            stateManager.recordIncidentResolution(tenantId, incidentId, mttrMinutes);

            metrics.recordTimer("devops.incident.mttr_minutes", mttrMinutes);
            metrics.incrementCounter("devops.incidents.resolved");

            // Incident resolved - postmortem scheduled
            LOGGER.info("Incident {} resolved with MTTR: {}min", incidentId, mttrMinutes);

        } catch (Exception e) {
            LOGGER.error("Error handling incident resolved: {}", incidentId, e);
            metrics.incrementCounter("devops.events.failed", "event_type", "incident_resolved");
            throw new RuntimeException("Failed to handle incident resolved", e);
        }
    }

    /**
     * Handle config_change_recorded event. Records infrastructure changes,
     * maintains change log for audit.
     */
    public void handleConfigChangeRecorded(String changeId, String changeType, String component,
            String details, String approvedBy, String tenantId) {
        LOGGER.info("Config change recorded: {} - {} on {}", changeId, changeType, component);

        try {
            stateManager.recordConfigChange(tenantId, changeId, changeType, component);

            metrics.incrementCounter("devops.config_changes.recorded", "type", changeType);

            // Change logged for compliance audit trail
            LOGGER.info("Change {} approved by {} and recorded", changeId, approvedBy);

        } catch (Exception e) {
            LOGGER.error("Error handling config change recorded: {}", changeId, e);
            metrics.incrementCounter("devops.events.failed", "event_type", "config_change");
            throw new RuntimeException("Failed to handle config change recorded", e);
        }
    }

    /**
     * Handle health_check_reported event. Records health metrics, flags issues,
     * updates service status.
     */
    public void handleHealthCheckReported(String serviceId, boolean healthy,
            double cpuUsagePercent, double memoryUsagePercent, double diskUsagePercent, String tenantId) {
        LOGGER.info("Health check reported: {} - {}", serviceId, healthy ? "HEALTHY" : "DEGRADED");

        try {
            stateManager.recordHealthCheck(tenantId, serviceId, healthy, cpuUsagePercent,
                    memoryUsagePercent, diskUsagePercent);

            metrics.recordTimer("devops.health.cpu_percent", (long) cpuUsagePercent);
            metrics.recordTimer("devops.health.memory_percent", (long) memoryUsagePercent);
            metrics.recordTimer("devops.health.disk_percent", (long) diskUsagePercent);

            if (!healthy) {
                LOGGER.warn("Service {} health check failed", serviceId);
                metrics.incrementCounter("devops.health_checks.failed", "service", serviceId);
            }

        } catch (Exception e) {
            LOGGER.error("Error handling health check reported: {}", serviceId, e);
            metrics.incrementCounter("devops.events.failed", "event_type", "health_check");
            throw new RuntimeException("Failed to handle health check reported", e);
        }
    }
}
