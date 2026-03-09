package com.ghatana.virtualorg.security;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Service for authenticating principals (agents, users, services).
 *
 * <p><b>Purpose</b><br>
 * Port interface defining authentication capabilities for virtual organization
 * security. Supports token-based, API key, and certificate authentication.
 *
 * <p><b>Architecture Role</b><br>
 * Security port interface. Implementations provide:
 * - JWT token validation and generation
 * - API key authentication
 * - mTLS certificate verification
 * - Session management
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuthenticationService auth = new JWTAuthenticationService(config);
 *
 * // Authenticate with token
 * AuthenticationResult result = auth.authenticate(token).getResult();
 * if (result.isAuthenticated()) {
 *     Principal principal = result.principal();
 *     // Use principal for authorization
 * }
 *
 * // Generate token for principal
 * String token = auth.generateToken(principal).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Authentication port for principal verification and token management
 * @doc.layer product
 * @doc.pattern Port
 */
public interface AuthenticationService {

    /**
     * Authenticate a principal using a token.
     *
     * @param token The authentication token (JWT, API key, etc.)
     * @return Promise of authentication result
     */
    @NotNull
    Promise<AuthenticationResult> authenticate(@NotNull String token);

    /**
     * Authenticate using username and password (for users).
     *
     * @param username Username
     * @param password Password
     * @return Promise of authentication result
     */
    @NotNull
    Promise<AuthenticationResult> authenticate(@NotNull String username, @NotNull String password);

    /**
     * Generate a token for an authenticated principal.
     *
     * @param principal The authenticated principal
     * @param expirySeconds Token expiry time in seconds
     * @return Promise of generated token
     */
    @NotNull
    Promise<String> generateToken(@NotNull Principal principal, long expirySeconds);

    /**
     * Validate a token without full authentication.
     *
     * @param token Token to validate
     * @return Promise of true if valid, false otherwise
     */
    @NotNull
    Promise<Boolean> validateToken(@NotNull String token);

    /**
     * Revoke a token (logout, invalidate).
     *
     * @param token Token to revoke
     * @return Promise of true if successfully revoked
     */
    @NotNull
    Promise<Boolean> revokeToken(@NotNull String token);

    /**
     * Get the principal associated with a token.
     *
     * @param token Authentication token
     * @return Promise of optional principal
     */
    @NotNull
    Promise<Optional<Principal>> getPrincipal(@NotNull String token);

    /**
     * Refresh an expiring token.
     *
     * @param token Token to refresh
     * @return Promise of new token
     */
    @NotNull
    Promise<String> refreshToken(@NotNull String token);
}
