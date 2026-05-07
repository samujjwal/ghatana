package com.ghatana.kernel.security;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Manager for user identity operations.
 *
 * @doc.type interface
 * @doc.purpose Identity management operations (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Service
 */
public interface IdentityManager {

    /**
     * Authenticate a user with credentials.
     *
     * @param tenantId tenant identifier
     * @param username username
     * @param password password
     * @return Promise containing authenticated user ID or empty if authentication fails
     */
    Promise<Optional<String>> authenticate(String tenantId, String username, String password);

    /**
     * Create a new user identity.
     *
     * @param tenantId tenant identifier
     * @param username username
     * @param email email address
     * @param password password
     * @return Promise containing the created user ID
     */
    Promise<String> createIdentity(String tenantId, String username, String email, String password);

    /**
     * Get user identity by ID.
     *
     * @param userId user identifier
     * @return Promise containing user identity if found
     */
    Promise<Optional<UserIdentity>> getIdentity(String userId);

    /**
     * Update user identity.
     *
     * @param userId user identifier
     * @param identity updated identity
     * @return Promise that completes when update is finished
     */
    Promise<Void> updateIdentity(String userId, UserIdentity identity);

    /**
     * Delete user identity.
     *
     * @param userId user identifier
     * @return Promise that completes when deletion is finished
     */
    Promise<Void> deleteIdentity(String userId);

    /**
     * Check if a username exists in a tenant.
     *
     * @param tenantId tenant identifier
     * @param username username
     * @return Promise containing true if username exists
     */
    Promise<Boolean> usernameExists(String tenantId, String username);
}
