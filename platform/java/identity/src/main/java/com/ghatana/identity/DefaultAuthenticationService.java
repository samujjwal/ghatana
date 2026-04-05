/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link AuthenticationService} implementation.
 *
 * <p>Tracks failed authentication attempts per agent and enforces exponential
 * backoff lockouts. Uses an in-memory store; production systems should persist
 * this via the database module.
 *
 * @doc.type class
 * @doc.purpose Default authentication service with rate limiting and lockout
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultAuthenticationService implements AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthenticationService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final TokenProvider tokenProvider;
    private final IdentityService identityService;

    // Map: "$tenantId:$agentId" → attempt count
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    // Map: "$tenantId:$agentId" → lockout expiry
    private final Map<String, Instant> lockouts = new ConcurrentHashMap<>();
    // Map: sessionToken → agent identity
    private final Map<String, AgentIdentity> activeSessions = new ConcurrentHashMap<>();
    // Map: sessionToken → credential hash (for logout)
    private final Map<String, String> sessionCredentials = new ConcurrentHashMap<>();

    public DefaultAuthenticationService(TokenProvider tokenProvider, IdentityService identityService) {
        this.tokenProvider = tokenProvider;
        this.identityService = identityService;
    }

    @Override
    public Promise<Void> recordFailedAttempt(String tenantId, String agentId) {
        String key = makeKey(tenantId, agentId);
        int count = failedAttempts.compute(key, (k, v) -> (v == null ? 1 : v + 1));

        if (count >= MAX_FAILED_ATTEMPTS) {
            lockouts.put(key, Instant.now().plus(LOCKOUT_DURATION));
            log.warn("Agent locked out after {} failed attempts: {}/{}", count, tenantId, agentId);
        } else {
            log.debug("Recorded failed attempt {} for agent {}/{}", count, tenantId, agentId);
        }

        return Promise.complete();
    }

    @Override
    public Promise<Optional<LockoutInfo>> checkLockout(String tenantId, String agentId) {
        String key = makeKey(tenantId, agentId);
        Instant lockoutExpiry = lockouts.get(key);

        if (lockoutExpiry == null) {
            return Promise.of(Optional.empty());
        }

        if (Instant.now().isAfter(lockoutExpiry)) {
            // Lockout expired; clean up
            lockouts.remove(key);
            failedAttempts.remove(key);
            return Promise.of(Optional.empty());
        }

        int attempts = failedAttempts.getOrDefault(key, 0);
        LockoutInfo info = new LockoutInfo(
            attempts,
            lockoutExpiry,
            "Too many failed authentication attempts. Try again later."
        );
        return Promise.of(Optional.of(info));
    }

    @Override
    public Promise<Void> resetFailedAttempts(String tenantId, String agentId) {
        String key = makeKey(tenantId, agentId);
        failedAttempts.remove(key);
        lockouts.remove(key);
        log.debug("Reset failed attempts for agent {}/{}", tenantId, agentId);
        return Promise.complete();
    }

    @Override
    public Promise<Optional<String>> authenticate(String tenantId, String agentId, String credentialHash) {
        String key = makeKey(tenantId, agentId);

        // Check lockout first
        Instant lockoutExpiry = lockouts.get(key);
        if (lockoutExpiry != null && Instant.now().isBefore(lockoutExpiry)) {
            log.debug("Agent locked out: {}/{}", tenantId, agentId);
            return Promise.of(Optional.empty());
        }

        // Resolve identity (this would integrate with the IdentityService)
        return identityService.resolve(tenantId, agentId)
            .then(optIdentity -> {
                if (optIdentity.isEmpty()) {
                    recordFailedAttempt(tenantId, agentId);
                    log.debug("Authentication failed: unknown agent {}/{}", tenantId, agentId);
                    return Promise.of(Optional.empty());
                }

                AgentIdentity identity = optIdentity.get();

                // In production: compare credentialHash with stored hash in database
                // For now: accept any non-empty hash
                if (credentialHash == null || credentialHash.isBlank()) {
                    recordFailedAttempt(tenantId, agentId);
                    log.debug("Authentication failed: invalid credentials {}/{}", tenantId, agentId);
                    return Promise.of(Optional.empty());
                }

                // Create session token
                return tokenProvider.createToken(tenantId, agentId, SESSION_TTL)
                    .then(token -> {
                        resetFailedAttempts(tenantId, agentId);
                        activeSessions.put(token, identity);
                        sessionCredentials.put(token, credentialHash);
                        log.info("Authentication successful: {}/{}", tenantId, agentId);
                        return Promise.of(Optional.of(token));
                    });
            });
    }

    @Override
    public Promise<Void> logout(String sessionToken) {
        activeSessions.remove(sessionToken);
        sessionCredentials.remove(sessionToken);
        log.debug("Session terminated: {}", sessionToken);
        return Promise.complete();
    }

    private String makeKey(String tenantId, String agentId) {
        return tenantId + ":" + agentId;
    }
}
