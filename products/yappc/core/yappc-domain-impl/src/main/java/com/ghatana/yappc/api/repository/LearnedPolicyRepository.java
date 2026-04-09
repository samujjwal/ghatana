package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.LearnedPolicy;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing learned policies.
 * Provides async CRUD operations for policy storage and retrieval.
 *
 * @doc.type interface
 * @doc.purpose Repository for learned policy persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface LearnedPolicyRepository {

    /**
     * Save a learned policy.
     *
     * @param policy the policy to save
     * @return Promise of the saved policy
     */
    Promise<LearnedPolicy> save(LearnedPolicy policy);

    /**
     * Find a policy by its ID.
     *
     * @param id the policy ID
     * @return Promise of optional containing the policy if found
     */
    Promise<Optional<LearnedPolicy>> findById(String id);

    /**
     * Find all policies for a tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise of list of policies
     */
    Promise<List<LearnedPolicy>> findByTenantId(String tenantId);

    /**
     * Find policies by agent type.
     *
     * @param tenantId the tenant ID
     * @param agentType the agent type
     * @return Promise of list of matching policies
     */
    Promise<List<LearnedPolicy>> findByTenantIdAndAgentType(String tenantId, String agentType);

    /**
     * Find high-confidence policies above a threshold.
     *
     * @param tenantId the tenant ID
     * @param minConfidence minimum confidence threshold
     * @return Promise of list of high-confidence policies
     */
    Promise<List<LearnedPolicy>> findByTenantIdAndConfidenceGreaterThan(String tenantId, double minConfidence);

    /**
     * Find policies by agent ID.
     *
     * @param tenantId the tenant ID
     * @param agentId the agent ID
     * @return Promise of list of policies for the agent
     */
    Promise<List<LearnedPolicy>> findByAgent(String tenantId, String agentId);

    /**
     * Find policies above a confidence threshold.
     *
     * @param tenantId the tenant ID
     * @param minConfidence minimum confidence threshold
     * @return Promise of list of high-confidence policies
     */
    Promise<List<LearnedPolicy>> findAboveConfidence(String tenantId, double minConfidence);

    /**
     * Delete a policy by ID.
     *
     * @param id the policy ID
     * @return Promise of true if deleted, false otherwise
     */
    Promise<Boolean> deleteById(String id);

    /**
     * Find all active policies for a tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise of list of active policies
     */
    default Promise<List<LearnedPolicy>> findActiveByTenantId(String tenantId) {
        return findByTenantId(tenantId)
            .map(policies -> policies.stream()
                .filter(LearnedPolicy::isActive)
                .toList());
    }
}
