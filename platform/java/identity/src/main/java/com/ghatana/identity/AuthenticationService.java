/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Service for agent authentication: login/logout, MFA, and account lockout.
 *
 * <p>Abstracts the details of credential verification, multi-factor authentication,
 * and security policies (e.g., rate limiting, account lockout after N failed attempts).
 *
 * @doc.type interface
 * @doc.purpose Agent authentication with MFA and lockout support
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AuthenticationService {

    /**
     * Record a failed authentication attempt for an agent.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return void
     */
    Promise<Void> recordFailedAttempt(String tenantId, String agentId);

    /**
     * Check if an agent is currently locked out due to too many failed attempts.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return lockout details if locked, or empty if allowed
     */
    Promise<Optional<LockoutInfo>> checkLockout(String tenantId, String agentId);

    /**
     * Reset the failed attempt counter for an agent (typically after successful auth).
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return void
     */
    Promise<Void> resetFailedAttempts(String tenantId, String agentId);

    /**
     * Authenticate an agent using their credentials.
     *
     * @param tenantId       tenant scope
     * @param agentId        agent identifier
     * @param credentialHash hash of the credential (e.g., password hash)
     * @return authenticated session token, or empty if credentials invalid
     */
    Promise<Optional<String>> authenticate(String tenantId, String agentId, String credentialHash);

    /**
     * Invalidate a session token (logout).
     *
     * @param sessionToken the token to revoke
     * @return void
     */
    Promise<Void> logout(String sessionToken);
}
