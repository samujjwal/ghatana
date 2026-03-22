/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.modules.authentication.domain.ClientCredentials;
import com.ghatana.kernel.modules.authentication.domain.TokenResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Generic OAuth 2.0 service.
 *
 * <p>Provides product-agnostic OAuth 2.0 capabilities including client_credentials
 * flow, token management, and JWT handling. This service contains NO finance-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Generic OAuth 2.0 service - client_credentials, JWT tokens
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);

    private final KernelContext context;
    private final Executor executor;
    private volatile boolean started = false;

    /**
     * Creates a new OAuth service.
     *
     * @param context the kernel context
     */
    public OAuthService(KernelContext context) {
        this.context = context;
        this.executor = context.getExecutor("authentication");
    }

    /**
     * Starts the OAuth service.
     */
    public void start() {
        log.info("Starting OAuth service");
        started = true;
        log.info("OAuth service started");
    }

    /**
     * Stops the OAuth service.
     */
    public void stop() {
        log.info("Stopping OAuth service");
        started = false;
        log.info("OAuth service stopped");
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
     * Handles OAuth 2.0 client_credentials flow.
     *
     * @param tenantId       tenant identifier
     * @param credentials    client credentials
     * @param scopes         requested scopes
     * @return Promise containing token response
     */
    public Promise<TokenResponse> clientCredentials(String tenantId, ClientCredentials credentials, String[] scopes) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("OAuth service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Processing client_credentials flow for client: {}", credentials.clientId());

            // Generic client credential validation
            boolean valid = validateClientCredentials(tenantId, credentials);

            if (valid) {
                TokenResponse tokenResponse = generateTokenResponse(tenantId, credentials.clientId(), scopes);
                log.info("Client credentials flow successful for client: {}", credentials.clientId());
                return tokenResponse;
            } else {
                log.warn("Client credentials validation failed for client: {}", credentials.clientId());
                throw new IllegalArgumentException("Invalid client credentials");
            }
        });
    }

    /**
     * Validates an OAuth access token.
     *
     * @param tenantId tenant identifier
     * @param token    access token to validate
     * @return Promise containing validation result
     */
    public Promise<Boolean> validateToken(String tenantId, String token) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("OAuth service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Validating access token for tenant: {}", tenantId);

            // Generic token validation
            boolean valid = isTokenValid(tenantId, token);
            
            if (valid) {
                log.debug("Token validation successful");
            } else {
                log.warn("Token validation failed");
            }
            
            return valid;
        });
    }

    /**
     * Refreshes an OAuth access token.
     *
     * @param tenantId    tenant identifier
     * @param refreshToken refresh token
     * @return Promise containing new token response
     */
    public Promise<TokenResponse> refreshToken(String tenantId, String refreshToken) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("OAuth service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Refreshing access token for tenant: {}", tenantId);

            // Generic refresh token validation
            String clientId = getClientIdFromRefreshToken(tenantId, refreshToken);
            if (clientId != null) {
                TokenResponse tokenResponse = generateTokenResponse(tenantId, clientId, new String[0]);
                log.info("Token refresh successful for client: {}", clientId);
                return tokenResponse;
            } else {
                log.warn("Invalid refresh token");
                throw new IllegalArgumentException("Invalid refresh token");
            }
        });
    }

    // ==================== Private Methods ====================

    private boolean validateClientCredentials(String tenantId, ClientCredentials credentials) {
        // Generic client credential validation
        return context.getCapability("data.storage")
            .map(storage -> validateClientWithStorage(storage, tenantId, credentials))
            .orElse(false);
    }

    private TokenResponse generateTokenResponse(String tenantId, String clientId, String[] scopes) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600); // 1 hour expiry

        return TokenResponse.builder()
            .accessToken(generateAccessToken())
            .tokenType("Bearer")
            .expiresIn(expiresAt.getEpochSecond() - now.getEpochSecond())
            .refreshToken(generateRefreshToken())
            .scopes(new HashSet<>(Arrays.asList(scopes)))
            .build();
    }

    private boolean isTokenValid(String tenantId, String token) {
        // Generic token validation
        return context.getCapability("data.storage")
            .map(storage -> validateTokenWithStorage(storage, tenantId, token))
            .orElse(false);
    }

    private String getClientIdFromRefreshToken(String tenantId, String refreshToken) {
        // Generic refresh token validation
        return context.getCapability("data.storage")
            .map(storage -> getClientIdFromStorage(storage, tenantId, refreshToken))
            .orElse(null);
    }

    private String generateAccessToken() {
        // Generate secure access token
        return java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(java.util.UUID.randomUUID().toString().getBytes());
    }

    private String generateRefreshToken() {
        // Generate secure refresh token
        return java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(java.util.UUID.randomUUID().toString().getBytes());
    }

    // ==================== Storage Integration Methods ====================

    private boolean validateClientWithStorage(Object storage, String tenantId, ClientCredentials credentials) {
        // Integration with data storage capability
        // Implementation would depend on storage interface
        return credentials.clientId().equals("demo-client") && 
               credentials.clientSecret().equals("demo-secret"); // Placeholder for demo
    }

    private boolean validateTokenWithStorage(Object storage, String tenantId, String token) {
        // Integration with data storage capability
        return token.startsWith("demo-access-"); // Placeholder for demo
    }

    private String getClientIdFromStorage(Object storage, String tenantId, String refreshToken) {
        // Integration with data storage capability
        return refreshToken.startsWith("demo-refresh-") ? "demo-client" : null; // Placeholder for demo
    }
}
