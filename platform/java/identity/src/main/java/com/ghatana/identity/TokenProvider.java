/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Optional;

/**
 * Service for creating, validating, and managing JWT-based authentication tokens.
 *
 * <p>Abstracts the details of token signing (HMAC, RSA, etc.) and provides
 * async-friendly APIs for token lifecycle operations. Supports key rotation
 * with a brief overlap period for in-flight tokens.
 *
 * @doc.type interface
 * @doc.purpose JWT token lifecycle management with key rotation support
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface TokenProvider {

    /**
     * Create a new signed token for the given agent.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @param ttl      requested token lifetime (service may impose limits)
     * @return compact JWT string
     */
    Promise<String> createToken(String tenantId, String agentId, Duration ttl);

    /**
     * Verify the signature of a compact JWT and extract claims.
     *
     * @param compactJwt the JWT string from an API call
     * @return decoded token claims, or empty if signature invalid or expired
     */
    Promise<Optional<TokenClaims>> verifyToken(String compactJwt);

    /**
     * Extract claims from a token without verification (use sparingly; mainly for debugging).
     *
     * @param compactJwt the JWT string
     * @return decoded claims (signature not checked), or empty if malformed
     */
    Promise<Optional<TokenClaims>> decodeTokenWithoutVerification(String compactJwt);

    /**
     * Rotate to a new signing key. Previous key remains valid for a grace period
     * to allow on-the-fly tokens to expire naturally.
     *
     * @param gracePeriod how long to keep accepting the old key
     * @return void when rotation complete
     */
    Promise<Void> rotateSigningKey(Duration gracePeriod);
}
