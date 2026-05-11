/**
 * Preview Session Enforcement Service
 * 
 * Enforces preview sessions and trust policies.
 * Ensures preview sessions are properly managed and trust policies are enforced.
 * 
 * @doc.type interface
 * @doc.purpose Preview session enforcement
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

/**
 * Service interface for enforcing preview sessions and trust policies.
 */
public interface PreviewSessionEnforcementService {

    /**
     * Creates a preview session.
     * 
     * @param projectId The project ID
     * @param importJobId The import job ID
     * @param userId The user ID
     * @return Preview session ID
     */
    String createPreviewSession(String projectId, String importJobId, String userId);

    /**
     * Validates a preview session.
     * 
     * @param sessionId The preview session ID
     * @return true if the session is valid, false otherwise
     */
    boolean validateSession(String sessionId);

    /**
     * Enforces trust policy for a preview session.
     * 
     * @param sessionId The preview session ID
     * @param action The action to check
     * @return true if the action is allowed, false otherwise
     */
    boolean enforceTrustPolicy(String sessionId, String action);

    /**
     * Blocks an action if trust policy is not satisfied.
     * 
     * @param sessionId The preview session ID
     * @param action The action to block
     * @throws IllegalStateException if the action is not allowed
     */
    void blockIfNotAllowed(String sessionId, String action);

    /**
     * Revokes a preview session.
     * 
     * @param sessionId The preview session ID
     * @param reason The revocation reason
     */
    void revokeSession(String sessionId, String reason);

    /**
     * Gets the trust level of a preview session.
     * 
     * @param sessionId The preview session ID
     * @return Trust level
     */
    TrustLevel getTrustLevel(String sessionId);

    /**
     * Trust level enum.
     */
    enum TrustLevel {
        TRUSTED,
        UNTRUSTED,
        DEGRADED,
        UNKNOWN
    }
}
