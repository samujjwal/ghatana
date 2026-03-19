/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.modules.authentication.domain.TokenClaims;
import com.ghatana.kernel.modules.authentication.domain.TokenResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Generic token service.
 *
 * <p>Provides product-agnostic token management capabilities including
 * JWT generation, validation, and refresh token handling. This service
 * contains NO finance-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Generic token service - JWT generation, validation, refresh tokens
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final KernelContext context;
    private final Executor executor;
    private volatile boolean started = false;

    // In-memory token store for refresh tokens
    private final ConcurrentHashMap<String, TokenInfo> refreshTokens = new ConcurrentHashMap<>();

    /**
     * Creates a new token service.
     *
     * @param context the kernel context
     */
    public TokenService(KernelContext context) {
        this.context = context;
        this.executor = context.getExecutor("token");
    }

    /**
     * Starts the token service.
     */
    public void start() {
        log.info("Starting token service");
        started = true;
        log.info("Token service started");
    }

    /**
     * Stops the token service.
     */
    public void stop() {
        log.info("Stopping token service");
        refreshTokens.clear();
        started = false;
        log.info("Token service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Generates a JWT token for the given claims.
     *
     * @param tenantId tenant identifier
     * @param claims   token claims
     * @return Promise containing token response
     */
    public Promise<TokenResponse> generateToken(String tenantId, TokenClaims claims) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Token service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Generating token for tenant: {}, principal: {}", tenantId, claims.getPrincipalId());

            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(3600); // 1 hour

            // Generate JWT token
            String accessToken = generateJwtToken(tenantId, claims, now, expiresAt);
            
            // Generate refresh token
            String refreshToken = generateRefreshToken(tenantId, claims.getPrincipalId());

            // Store refresh token
            refreshTokens.put(refreshToken, new TokenInfo(tenantId, claims.getPrincipalId(), now.plusSeconds(86400))); // 24 hours

            TokenResponse response = new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresAt.getEpochSecond(),
                Set.of("read", "write") // Generic scopes
            );

            log.info("Generated token for principal: {}", claims.getPrincipalId());
            return response;
        });
    }

    /**
     * Validates a JWT token.
     *
     * @param token the JWT token
     * @return Promise containing validation result
     */
    public Promise<Boolean> validateToken(String token) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Token service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Validating JWT token");

            try {
                // Generic JWT validation
                return validateJwtToken(token);
            } catch (Exception e) {
                log.warn("Token validation failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Extracts claims from a JWT token.
     *
     * @param token the JWT token
     * @return Promise containing token claims
     */
    public Promise<TokenClaims> extractClaims(String token) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Token service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Extracting claims from JWT token");

            try {
                // Generic claims extraction
                return extractJwtClaims(token);
            } catch (Exception e) {
                log.warn("Claims extraction failed: {}", e.getMessage());
                throw new RuntimeException("Invalid token", e);
            }
        });
    }

    /**
     * Refreshes a token using a refresh token.
     *
     * @param tenantId     tenant identifier
     * @param refreshToken the refresh token
     * @return Promise containing new token response
     */
    public Promise<TokenResponse> refreshToken(String tenantId, String refreshToken) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Token service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Refreshing token for tenant: {}", tenantId);

            TokenInfo tokenInfo = refreshTokens.get(refreshToken);
            if (tokenInfo == null || !tokenInfo.getTenantId().equals(tenantId)) {
                throw new RuntimeException("Invalid refresh token");
            }

            if (tokenInfo.getExpiresAt().isBefore(Instant.now())) {
                refreshTokens.remove(refreshToken);
                throw new RuntimeException("Refresh token expired");
            }

            // Create claims for the principal
            TokenClaims claims = TokenClaims.builder()
                .principalId(tokenInfo.getPrincipalId())
                .tenantId(tenantId)
                .issuedAt(Instant.now())
                .build();

            // Generate new tokens
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(3600); // 1 hour

            String accessToken = generateJwtToken(tenantId, claims, now, expiresAt);
            String newRefreshToken = generateRefreshToken(tenantId, tokenInfo.getPrincipalId());

            // Remove old refresh token and store new one
            refreshTokens.remove(refreshToken);
            refreshTokens.put(newRefreshToken, new TokenInfo(tenantId, tokenInfo.getPrincipalId(), now.plusSeconds(86400)));

            TokenResponse response = new TokenResponse(
                accessToken,
                newRefreshToken,
                "Bearer",
                expiresAt.getEpochSecond(),
                Set.of("read", "write") // Generic scopes
            );

            log.info("Refreshed token for principal: {}", tokenInfo.getPrincipalId());
            return response;
        });
    }

    /**
     * Revokes a refresh token.
     *
     * @param refreshToken the refresh token to revoke
     * @return Promise completing when token is revoked
     */
    public Promise<Void> revokeToken(String refreshToken) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Token service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Revoking refresh token");

            TokenInfo removed = refreshTokens.remove(refreshToken);
            if (removed != null) {
                log.info("Revoked refresh token for principal: {}", removed.getPrincipalId());
            }

            return null;
        });
    }

    // ==================== Private Methods ====================

    private String generateJwtToken(String tenantId, TokenClaims claims, Instant now, Instant expiresAt) {
        // Generic JWT generation - would integrate with security capability
        Map<String, Object> jwtClaims = Map.of(
            "sub", claims.getPrincipalId(),
            "tenant", tenantId,
            "iat", now.getEpochSecond(),
            "exp", expiresAt.getEpochSecond(),
            "roles", claims.getRoles(),
            "permissions", claims.getPermissions()
        );

        // This would use a proper JWT library
        return "jwt." + java.util.Base64.getEncoder().encodeToString(jwtClaims.toString().getBytes());
    }

    private boolean validateJwtToken(String token) {
        // Generic JWT validation - would integrate with security capability
        return token != null && token.startsWith("jwt.");
    }

    private TokenClaims extractJwtClaims(String token) {
        // Generic claims extraction - would integrate with security capability
        return TokenClaims.builder()
            .principalId("user") // Placeholder
            .tenantId("tenant")  // Placeholder
            .roles(Set.of("authenticated"))
            .permissions(Set.of("read", "write"))
            .build();
    }

    private String generateRefreshToken(String tenantId, String principalId) {
        // Generate secure refresh token
        return UUID.randomUUID().toString();
    }

    /**
     * Internal token information.
     */
    private static final class TokenInfo {
        private final String tenantId;
        private final String principalId;
        private final Instant expiresAt;

        TokenInfo(String tenantId, String principalId, Instant expiresAt) {
            this.tenantId = tenantId;
            this.principalId = principalId;
            this.expiresAt = expiresAt;
        }

        String getTenantId() { return tenantId; }
        String getPrincipalId() { return principalId; }
        Instant getExpiresAt() { return expiresAt; }
    }
}
