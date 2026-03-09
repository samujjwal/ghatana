package com.ghatana.orchestrator.deployment.contract;

/**
 * Contract for deployment event types.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines canonical event types emitted by orchestrator during deployment
 * lifecycle (deploy, update, undeploy operations). Events are published to
 * EventCloud and consumed by event-processing instances for runtime deployment.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * String eventType = DeploymentEventType.PIPELINE_DEPLOY_REQUESTED;
 * // Emit event to EventCloud with this type
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Deployment event type constants
 * @doc.layer contracts
 * @doc.pattern Constants
 */
public final class DeploymentEventType {

    private DeploymentEventType() {
        // Utility class - no instantiation
    }

    /**
     * Event: Pipeline deployment requested. Emitted when orchestrator receives
     * valid deployment request. Payload includes DeploymentRequest details +
     * deploymentId.
     */
    public static final String PIPELINE_DEPLOY_REQUESTED = "pipeline.deploy.requested";

    /**
     * Event: Pipeline deployment succeeded. Emitted after event-processing
     * instance successfully deploys pipeline. Payload includes deploymentId,
     * pipelineId, tenantId, runtime details.
     */
    public static final String PIPELINE_DEPLOY_SUCCEEDED = "pipeline.deploy.succeeded";

    /**
     * Event: Pipeline deployment failed. Emitted if deployment fails (invalid
     * spec, connector issues, etc). Payload includes deploymentId, pipelineId,
     * tenantId, error reason.
     */
    public static final String PIPELINE_DEPLOY_FAILED = "pipeline.deploy.failed";

    /**
     * Event: Pipeline update requested. Emitted when orchestrator receives
     * update request for existing deployment. Payload includes deploymentId,
     * pipelineId, tenantId, new spec.
     */
    public static final String PIPELINE_UPDATE_REQUESTED = "pipeline.update.requested";

    /**
     * Event: Pipeline update succeeded. Emitted after event-processing instance
     * successfully updates pipeline. Payload includes deploymentId, pipelineId,
     * tenantId, new spec version.
     */
    public static final String PIPELINE_UPDATE_SUCCEEDED = "pipeline.update.succeeded";

    /**
     * Event: Pipeline update failed. Emitted if update fails (invalid spec,
     * rolling update issues, etc). Payload includes deploymentId, pipelineId,
     * tenantId, error reason.
     */
    public static final String PIPELINE_UPDATE_FAILED = "pipeline.update.failed";

    /**
     * Event: Pipeline undeploy requested. Emitted when orchestrator receives
     * undeploy request. Payload includes deploymentId, pipelineId, tenantId.
     */
    public static final String PIPELINE_UNDEPLOY_REQUESTED = "pipeline.undeploy.requested";

    /**
     * Event: Pipeline undeploy succeeded. Emitted after event-processing
     * instance successfully undeployed pipeline. Payload includes deploymentId,
     * pipelineId, tenantId.
     */
    public static final String PIPELINE_UNDEPLOY_SUCCEEDED = "pipeline.undeploy.succeeded";

    /**
     * Event: Pipeline undeploy failed. Emitted if undeploy fails. Payload
     * includes deploymentId, pipelineId, tenantId, error reason.
     */
    public static final String PIPELINE_UNDEPLOY_FAILED = "pipeline.undeploy.failed";
}
