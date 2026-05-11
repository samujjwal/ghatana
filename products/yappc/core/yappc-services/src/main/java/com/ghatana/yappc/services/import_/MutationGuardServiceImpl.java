/**
 * Mutation Guard Service Implementation
 * 
 * Production-grade implementation of mutation guard service.
 * Blocks direct mutations until validation passes.
 * 
 * @doc.type class
 * @doc.purpose Mutation guard implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade implementation of mutation guard service.
 */
public final class MutationGuardServiceImpl implements MutationGuardService {

    private static final Logger log = LoggerFactory.getLogger(MutationGuardServiceImpl.class);

    // In-memory storage for demonstration - replace with distributed cache in production
    private final Map<String, Boolean> mutationValidationStatus = new ConcurrentHashMap<>();
    private final Map<String, Long> mutationTimestamps = new ConcurrentHashMap<>();

    @Override
    public boolean isMutationAllowed(String mutationId) {
        Boolean validationPassed = mutationValidationStatus.get(mutationId);
        boolean allowed = validationPassed != null && validationPassed;

        log.debug("Mutation allowed check: mutationId={}, allowed={}", mutationId, allowed);
        return allowed;
    }

    @Override
    public void registerMutation(String mutationId, boolean validationPassed) {
        log.info("Registering mutation: mutationId={}, validationPassed={}", mutationId, validationPassed);

        mutationValidationStatus.put(mutationId, validationPassed);
        mutationTimestamps.put(mutationId, System.currentTimeMillis());

        if (!validationPassed) {
            log.warn("Mutation registered as invalid: mutationId={}", mutationId);
        }
    }

    @Override
    public void blockUntilValidated(String mutationId) {
        log.info("Blocking mutation until validated: mutationId={}", mutationId);

        if (!isMutationAllowed(mutationId)) {
            String message = String.format("Mutation %s is not allowed: validation has not passed", mutationId);
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Mutation validated and allowed: mutationId={}", mutationId);
    }

    @Override
    public void releaseMutationGuard(String mutationId) {
        log.info("Releasing mutation guard: mutationId={}", mutationId);

        mutationValidationStatus.remove(mutationId);
        mutationTimestamps.remove(mutationId);

        log.info("Mutation guard released: mutationId={}", mutationId);
    }

    /**
     * Gets the validation status of a mutation.
     * 
     * @param mutationId The mutation ID
     * @return true if validation passed, false if validation failed or not found
     */
    public boolean getValidationStatus(String mutationId) {
        return mutationValidationStatus.getOrDefault(mutationId, false);
    }

    /**
     * Clears old mutation records to prevent memory leaks.
     * 
     * @param maxAgeMillis Maximum age in milliseconds for a mutation record
     */
    public void cleanupOldMutations(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        int cleaned = 0;

        for (Map.Entry<String, Long> entry : mutationTimestamps.entrySet()) {
            if (now - entry.getValue() > maxAgeMillis) {
                mutationValidationStatus.remove(entry.getKey());
                mutationTimestamps.remove(entry.getKey());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up old mutation records: count={}", cleaned);
        }
    }
}
