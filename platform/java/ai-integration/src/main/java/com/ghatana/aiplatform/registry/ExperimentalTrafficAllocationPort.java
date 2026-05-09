package com.ghatana.aiplatform.registry;

/**
 * Port interface for experimental traffic allocation (A/B testing).
 *
 * <p>Abstracts variant assignment, experiment management, and traffic splitting
 * for controlled model evaluation and rollout strategies.
 *
 * @doc.type interface
 * @doc.purpose Port abstraction for experimental traffic allocation and A/B testing
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface ExperimentalTrafficAllocationPort {

    /**
     * Register an experiment with a traffic split policy.
     *
     * @param tenantId the tenant identifier
     * @param experimentId unique experiment identifier
     * @param name display name for the experiment
     * @param trafficSplit traffic split policy (e.g., "80/20", "50/50")
     * @param baselineModel baseline model reference or name
     * @param variantModel variant model reference or name
     */
    void registerExperiment(String tenantId, String experimentId, String name, 
                          String trafficSplit, String baselineModel, String variantModel);

    /**
     * Assign a user/request to an experiment variant.
     *
     * @param tenantId the tenant identifier
     * @param experimentId the experiment identifier
     * @param requestContext the request context for deterministic assignment
     * @return the assigned variant name ("baseline" or "variant")
     */
    String assignVariant(String tenantId, String experimentId, String requestContext);

    /**
     * End an experiment and clear its assignment state.
     *
     * @param tenantId the tenant identifier
     * @param experimentId the experiment identifier
     */
    void endExperiment(String tenantId, String experimentId);
}
