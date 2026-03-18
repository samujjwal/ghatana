package com.ghatana.softwareorg.devops.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for DevOps department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for deployment and infrastructure operations
 * (deployment tracking, incident management, infrastructure changes, health
 * monitoring).
 *
 * <p>
 * <b>Endpoints</b><br>
 * - POST /api/v1/devops/deployments/start - Begin deployment - POST
 * /api/v1/devops/deployments/complete - Record deployment result - POST
 * /api/v1/devops/incidents - Report infrastructure incident - GET
 * /api/v1/devops/health - Get platform health status
 *
 * @doc.type class
 * @doc.purpose DevOps domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class DevopsEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public DevopsEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Starts deployment process.
     *
     * @param tenantId tenant context
     * @param deploymentId deployment identifier
     * @param environment target environment (dev, staging, prod)
     * @param version version being deployed
     */
    public void startDeployment(
            String tenantId, String deploymentId, String environment, String version) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deploymentId", deploymentId);
        payload.put("environment", environment);
        payload.put("version", version);
        payload.put("status", "STARTED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("devops.deployment.started", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("devops.deployments.started", "environment", environment);
    }

    /**
     * Records deployment completion.
     *
     * @param tenantId tenant context
     * @param deploymentId deployment identifier
     * @param environment target environment
     * @param succeeded whether deployment succeeded
     * @param durationMs deployment duration in milliseconds
     */
    public void completeDeployment(
            String tenantId, String deploymentId, String environment, boolean succeeded,
            long durationMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deploymentId", deploymentId);
        payload.put("environment", environment);
        payload.put("succeeded", succeeded);
        payload.put("durationMs", durationMs);

        String eventType = succeeded ? "devops.deployment.succeeded" : "devops.deployment.failed";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }

        metrics.incrementCounter(
                "devops.deployments.completed", "succeeded", String.valueOf(succeeded), "environment", environment);
    }

    /**
     * Reports infrastructure incident.
     *
     * @param tenantId tenant context
     * @param incidentId incident identifier
     * @param severity incident severity (CRITICAL, HIGH, MEDIUM, LOW)
     * @param component affected component or service
     * @param description incident description
     */
    public void reportIncident(
            String tenantId, String incidentId, String severity, String component, String description) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("incidentId", incidentId);
        payload.put("severity", severity);
        payload.put("component", component);
        payload.put("description", description);
        payload.put("status", "OPENED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("devops.incident.detected", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("devops.incidents.detected", "severity", severity, "component", component);
    }

    /**
     * Records incident resolution.
     *
     * @param tenantId tenant context
     * @param incidentId incident identifier
     * @param durationMinutes time to resolution in minutes
     * @param rootCause root cause analysis
     */
    public void resolveIncident(
            String tenantId, String incidentId, long durationMinutes, String rootCause) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("incidentId", incidentId);
        payload.put("durationMinutes", durationMinutes);
        payload.put("rootCause", rootCause);
        payload.put("status", "RESOLVED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("devops.incident.resolved", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("devops.incident.mttr", durationMinutes * 60000);
    }

    /**
     * Records infrastructure configuration change.
     *
     * @param tenantId tenant context
     * @param changeId configuration change identifier
     * @param component component being changed
     * @param changeType type of change (CREATE, UPDATE, DELETE)
     * @param approverEmail email of person approving change
     */
    public void recordConfigChange(
            String tenantId, String changeId, String component, String changeType, String approverEmail) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("changeId", changeId);
        payload.put("component", component);
        payload.put("changeType", changeType);
        payload.put("approverEmail", approverEmail);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("devops.config.changed", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("devops.config_changes", "type", changeType, "component", component);
    }

    /**
     * Reports infrastructure health check result.
     *
     * @param tenantId tenant context
     * @param environment environment being monitored
     * @param healthy whether environment passed health check
     * @param cpuUsagePercent CPU utilization percentage
     * @param memoryUsagePercent memory utilization percentage
     * @param diskUsagePercent disk utilization percentage
     */
    public void reportHealthCheck(
            String tenantId,
            String environment,
            boolean healthy,
            double cpuUsagePercent,
            double memoryUsagePercent,
            double diskUsagePercent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("environment", environment);
        payload.put("healthy", healthy);
        payload.put("cpuUsagePercent", cpuUsagePercent);
        payload.put("memoryUsagePercent", memoryUsagePercent);
        payload.put("diskUsagePercent", diskUsagePercent);

        String eventType = healthy ? "devops.health.healthy" : "devops.health.degraded";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("devops.health.cpu", (long) cpuUsagePercent, "environment", environment);
    }
}
