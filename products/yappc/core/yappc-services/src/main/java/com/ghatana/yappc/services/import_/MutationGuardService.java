/**
 * Mutation Guard Service
 * 
 * Blocks direct mutations until validation passes.
 * Ensures all mutations are validated before being applied.
 * 
 * @doc.type interface
 * @doc.purpose Mutation guard
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

/**
 * Service interface for guarding mutations.
 */
public interface MutationGuardService {

    /**
     * Checks if a mutation is allowed based on validation status.
     * 
     * @param mutationId The mutation ID
     * @return true if the mutation is allowed, false otherwise
     */
    boolean isMutationAllowed(String mutationId);

    /**
     * Registers a mutation with its validation status.
     * 
     * @param mutationId The mutation ID
     * @param validationPassed Whether validation passed
     */
    void registerMutation(String mutationId, boolean validationPassed);

    /**
     * Blocks a mutation until validation passes.
     * 
     * @param mutationId The mutation ID
     * @throws IllegalStateException if the mutation is not allowed
     */
    void blockUntilValidated(String mutationId);

    /**
     * Releases a mutation guard after validation passes.
     * 
     * @param mutationId The mutation ID
     */
    void releaseMutationGuard(String mutationId);
}
