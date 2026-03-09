package com.ghatana.platform.security.rbac;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing policies.
 
 *
 * @doc.type interface
 * @doc.purpose Policy repository
 * @doc.layer core
 * @doc.pattern Repository
*/
public interface PolicyRepository {

    /**
     * Finds a policy by its ID.
     *
     * @param id The policy ID
     * @return The policy, or empty if not found
     */
    Optional<Policy> findById(String id);

    /**
     * Finds all policies for the specified role.
     *
     * @param role The role
     * @return The policies
     */
    List<Policy> findByRole(String role);

    /**
     * Finds all policies for the specified resource.
     *
     * @param resource The resource
     * @return The policies
     */
    List<Policy> findByResource(String resource);

    /**
     * Finds all policies.
     *
     * @return All policies
     */
    List<Policy> findAll();

    /**
     * Saves a policy.
     *
     * @param policy The policy to save
     * @return The saved policy
     */
    Policy save(Policy policy);

    /**
     * Deletes a policy by its ID.
     *
     * @param id The policy ID
     * @return true if the policy was deleted, false otherwise
     */
    boolean deleteById(String id);
}
