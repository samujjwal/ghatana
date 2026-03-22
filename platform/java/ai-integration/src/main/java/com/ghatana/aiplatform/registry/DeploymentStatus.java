package com.ghatana.aiplatform.registry;

/**
 * Deployment status lifecycle for ML models.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the stages a model goes through from development to retirement.
 * Enables controlled rollout, A/B testing, and graceful deprecation.
 *
 * <p>
 * <b>Lifecycle Flow</b><br>
 * DEVELOPMENT → STAGED → CANARY → ACTIVE → DEPRECATED → RETIRED (FAILED can
 * occur at any stage)
 *
 * @doc.type enum
 * @doc.purpose Model deployment lifecycle states
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum DeploymentStatus {
    /**
     * Model is under development, not ready for deployment.
     */
    DEVELOPMENT,
    /**
     * Model is staged for testing in non-production environment.
     */
    STAGED,
    /**
     * Model is in canary deployment (partial traffic for testing).
     */
    CANARY,
    /**
     * Model is deployed to production and actively serving traffic.
     */
    PRODUCTION,
    /**
     * Model is active and serving full production traffic (alias for
     * PRODUCTION).
     */
    ACTIVE,
    /**
     * Model deployment failed.
     */
    FAILED,
    /**
     * Model is deprecated and scheduled for removal.
     */
    DEPRECATED,
    /**
     * Model is retired and no longer available for inference.
     */
    RETIRED
}
