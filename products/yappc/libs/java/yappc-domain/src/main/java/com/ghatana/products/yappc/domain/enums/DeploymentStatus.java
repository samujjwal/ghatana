package com.ghatana.products.yappc.domain.enums;

/**
 * Deployment status enumeration for deployment execution tracking.
 *
 * <p><b>Purpose</b><br>
 * Defines execution states for deployments from initiation to completion.
 *
 * <p><b>Status Flow</b><br>
 * PENDING → IN_PROGRESS → DEPLOYED → VERIFIED
 * Alternative: PENDING → IN_PROGRESS → FAILED
 * Rollback: DEPLOYED → ROLLED_BACK
 *
 * @see com.ghatana.products.yappc.domain.model.Deployment
 * @doc.type enum
 * @doc.purpose Deployment execution lifecycle
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum DeploymentStatus {
    /**
     * Pending - deployment queued, not started.
     */
    PENDING,

    /**
     * In progress - actively deploying.
     */
    IN_PROGRESS,

    /**
     * Deployed - deployment complete, not yet verified.
     */
    DEPLOYED,

    /**
     * Verified - deployment confirmed successful.
     */
    VERIFIED,

    /**
     * Failed - deployment failed with errors.
     */
    FAILED,

    /**
     * Rolled back - deployment reverted due to issues.
     */
    ROLLED_BACK
}
