/*
 * Copyright (c) 2025-2026 Ghatana
 */
package com.ghatana.services.auth;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Token revocation blocklist for refresh tokens.
 *
 * <p>Implementations maintain a blocklist of revoked token identifiers (jti)
 * to allow refresh tokens to be revoked before their natural expiry. This is
 * a security requirement for proper session management.
 *
 * <p>For production deployments, use {@code JdbcTokenBlocklist} backed by a
 * database. For testing only, {@code InMemoryTokenBlocklist} can be used.
 *
 * @doc.type interface
 * @doc.purpose Token revocation blocklist contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface TokenBlocklist {

    /**
     * Adds a token identifier to the blocklist.
     *
     * @param jti the JWT ID claim to block
     * @param expiresAt when the token would naturally expire (for cleanup)
     * @return void promise
     */
    @NotNull
    Promise<Void> block(@NotNull String jti, long expiresAt);

    /**
     * Checks if a token identifier is blocked.
     *
     * @param jti the JWT ID claim to check
     * @return true if blocked, false otherwise
     */
    @NotNull
    Promise<Boolean> isBlocked(@NotNull String jti);

    /**
     * Removes expired entries from the blocklist.
     *
     * <p>Should be called periodically to prevent unbounded growth.
     *
     * @return number of entries removed
     */
    @NotNull
    Promise<Integer> cleanupExpired();
}
