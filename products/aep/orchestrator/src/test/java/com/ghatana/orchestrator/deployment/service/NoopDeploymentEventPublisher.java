package com.ghatana.orchestrator.deployment.service;

import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * No-op deployment event publisher for testing and development.
 *
 * <p><b>Purpose</b><br>
 * Test implementation of DeploymentEventPublisher that logs events instead of
 * publishing to EventCloud. Used for unit tests and dev environments.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DeploymentEventPublisher publisher = new NoopDeploymentEventPublisher(); // GH-90000
 * orchestrator = new DeploymentOrchestrator(publisher, metrics); // GH-90000
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose No-op deployment event publisher
 * @doc.layer testing
 * @doc.pattern Adapter
 */
@Slf4j
public class NoopDeploymentEventPublisher implements DeploymentEventPublisher {
    /**
     * Log deployment event without publishing.
     *
     * @param eventType event type
     * @param deploymentId deployment ID
     * @param request deployment request
     * @return Promise<Void> for async completion
     */
    public Promise<Void> publishDeploymentEvent(String eventType, String deploymentId, DeploymentRequest request) { // GH-90000
        String tenantId = request.getTenantId(); // GH-90000
        String pipelineId = request.getPipelineId(); // GH-90000

        MDC.put("eventType", eventType); // GH-90000
        MDC.put("deploymentId", deploymentId); // GH-90000

        try {
            log.info( // GH-90000
                    "Deployment event published: {} (deploymentId={}, pipelineId={}, tenantId={})", // GH-90000
                    eventType,
                    deploymentId,
                    pipelineId,
                    tenantId);
        } finally {
            MDC.remove("eventType");
            MDC.remove("deploymentId");
        }

        return Promise.of(null); // GH-90000
    }
}
