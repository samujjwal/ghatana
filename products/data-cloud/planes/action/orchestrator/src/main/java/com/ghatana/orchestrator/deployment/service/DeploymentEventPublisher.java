package com.ghatana.orchestrator.deployment.service;

import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import io.activej.promise.Promise;

/**
 * Publisher for deployment events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Abstraction for publishing deployment events (deploy, update, undeploy
 * requests/responses) to EventCloud. Implementations handle event
 * serialization, routing, and observability.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * publisher.publishDeploymentEvent(
 *     "pipeline.deploy.requested",
 *     "deploy-123",
 *     request);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Deployment event publisher contract
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DeploymentEventPublisher {

    /**
     * Publish deployment event to EventCloud.
     *
     * @param eventType event type constant (e.g., PIPELINE_DEPLOY_REQUESTED)
     * @param deploymentId deployment identifier
     * @param request deployment request with context
     * @return Promise<Void> for async completion
     */
    Promise<Void> publishDeploymentEvent(String eventType, String deploymentId, DeploymentRequest request);
}
