/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.service;

import com.ghatana.appplatform.iam.domain.ClientCredentials;
import com.ghatana.appplatform.iam.domain.TokenClaims;
import com.ghatana.appplatform.iam.domain.TokenResponse;
import com.ghatana.appplatform.iam.port.ClientCredentialStore;
import com.ghatana.platform.security.crypto.PasswordHasher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * OAuth 2.0 {@code client_credentials} grant handler (STORY-K01-001).
 *
 * <p>Validates {clientIdStr, rawSecret} → loads permissions → issues RS256 JWT.
 *
 * <p>Uses {@link PasswordHasher} from {@code platform:java:security} for bcrypt
 * secret verification (reuse, not reimplementation).
 *
 * @doc.type class
 * @doc.purpose client_credentials grant: authenticate machine client and issue JWT
 * @doc.layer core
 * @doc.pattern Service
 */
public final class ClientCredentialsGrant {

    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsGrant.class);

    private final ClientCredentialStore store;
    private final JwtTokenService tokenService;
    private final PasswordHasher passwordHasher;
    private final String issuer;
    private final String audience;
    private final long tokenTtlSeconds;

    /**
     * @param store           persistence port for client credentials and permissions
     * @param tokenService    RS256 JWT issuer
     * @param issuer          JWT {@code iss} claim, e.g. {@code "https://auth.ghatana.io"}
     * @param audience        JWT {@code aud} claim, e.g. {@code "ghatana-api"}
     * @param tokenTtlSeconds token lifetime in seconds, e.g. {@code 3600}
     */
    public ClientCredentialsGrant(
            ClientCredentialStore store,
            JwtTokenService tokenService,
            String issuer,
            String audience,
            long tokenTtlSeconds) {
        this.store = store;
        this.tokenService = tokenService;
        this.passwordHasher = new PasswordHasher();
        this.issuer = issuer;
        this.audience = audience;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /**
     * Exchange client credentials for a JWT access token.
     *
     * @param clientIdStr raw client identifier
     * @param rawSecret   raw client secret (not hashed)
     * @return promise of {@link TokenResponse} on success
     * @throws InvalidClientException  if client not found or secret incorrect
     * @throws AccessDeniedException   if client is not active (suspended / revoked)
     */
    public Promise<TokenResponse> exchange(String clientIdStr, String rawSecret) {
        return store.getByClientIdStr(clientIdStr)
                .then(optCred -> {
                    ClientCredentials cred = optCred.orElseThrow(
                            () -> new InvalidClientException("Unknown client: " + clientIdStr));

                    if (!passwordHasher.verify(rawSecret, cred.clientSecretHash())) {
                        throw new InvalidClientException("Invalid client credentials");
                    }

                    if (!cred.isActive()) {
                        throw new AccessDeniedException(
                                "Client is " + cred.status().name().toLowerCase() + ": " + clientIdStr);
                    }

                    return store.loadPermissionsForRoles(cred.grantedScopes(), cred.tenantId())
                            .map(permissions -> issueToken(cred, permissions));
                })
                .whenComplete((response, ex) -> {
                    if (ex == null) {
                        log.debug("client_credentials grant succeeded for {}", clientIdStr);
                        updateLastUsedAsync(clientIdStr);
                    } else {
                        log.warn("client_credentials grant failed for {}: {}", clientIdStr, ex.getMessage());
                    }
                });
    }

    private TokenResponse issueToken(ClientCredentials cred, List<String> permissions) {
        Instant now = Instant.now();
        TokenClaims claims = TokenClaims.builder()
                .subject(cred.clientIdStr())
                .tenantId(cred.tenantId())
                .roles(cred.grantedScopes())
                .permissions(permissions)
                .issuer(issuer)
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(tokenTtlSeconds))
                .build();

        String token = tokenService.issue(claims);
        String scope = String.join(" ", cred.grantedScopes());
        return new TokenResponse(token, "Bearer", tokenTtlSeconds, scope);
    }

    /**
     * Fire-and-forget last-used timestamp update.
     * Failures are logged but never propagated to the caller.
     */
    private void updateLastUsedAsync(String clientIdStr) {
        store.getByClientIdStr(clientIdStr)
                .then(optCred -> optCred
                        .map(c -> store.updateLastUsed(c.clientId()))
                        .orElse(Promise.complete()))
                .whenException(ex ->
                        log.warn("Failed to update last_used for {}: {}", clientIdStr, ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Domain exceptions
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Thrown when client credentials are invalid (not found or wrong secret).
     * Intentionally vague to resist credential enumeration attacks.
     *
     * @doc.type class
     * @doc.purpose signals invalid OAuth 2.0 client credentials
     * @doc.layer core
     * @doc.pattern ExceptionType
     */
    public static final class InvalidClientException extends RuntimeException {
        public InvalidClientException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when a valid client is not permitted to authenticate
     * (suspended or revoked status).
     *
     * @doc.type class
     * @doc.purpose signals suspended or revoked OAuth 2.0 client
     * @doc.layer core
     * @doc.pattern ExceptionType
     */
    public static final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
