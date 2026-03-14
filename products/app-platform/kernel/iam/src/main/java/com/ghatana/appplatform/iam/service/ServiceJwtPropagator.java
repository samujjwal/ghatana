/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ghatana.appplatform.iam.port.SigningKeyProvider;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Utility for service-to-service JWT propagation (K01-016).
 *
 * <h3>Two propagation modes</h3>
 * <ol>
 *   <li><b>User context propagation</b>: forwards the original user JWT unchanged
 *       as {@code X-Forwarded-Authorization}.  Downstream services can inspect
 *       the user's identity while knowing it originated from an upstream service.</li>
 *   <li><b>Service-initiated calls</b>: generates a short-lived RS256 JWT scoped
 *       to the calling service (no user context) placed in the standard
 *       {@code Authorization: Bearer …} header.</li>
 * </ol>
 *
 * <h3>Header contract</h3>
 * <ul>
 *   <li>{@code X-Forwarded-Authorization} — carries the end-user JWT when
 *       a service acts on behalf of a user.</li>
 *   <li>{@code Authorization} — carries the service-level JWT for
 *       service-originated calls.</li>
 * </ul>
 *
 * <p>Spoofing prevention: the gateway (K-11) rejects incoming external requests
 * that already carry {@code X-Forwarded-Authorization}, so only authenticated
 * internal services can set this header.
 *
 * @doc.type class
 * @doc.purpose Service-to-service JWT context propagation (K01-016)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ServiceJwtPropagator {

    private static final Logger log = LoggerFactory.getLogger(ServiceJwtPropagator.class);

    /** Header carrying the original user JWT in internal service calls. */
    public static final String FORWARDED_AUTH_HEADER = "X-Forwarded-Authorization";

    /** Short-lived service JWT TTL (5 minutes). */
    private static final int SERVICE_JWT_TTL_SECONDS = 300;

    private final SigningKeyProvider keyProvider;
    private final Executor executor;
    private final String serviceName; // e.g., "kernel-iam", "kernel-ledger"
    private final String issuer;

    public ServiceJwtPropagator(SigningKeyProvider keyProvider, Executor executor,
                                 String serviceName, String issuer) {
        this.keyProvider   = keyProvider;
        this.executor      = executor;
        this.serviceName   = serviceName;
        this.issuer        = issuer;
    }

    // ─── User context propagation ──────────────────────────────────────────────

    /**
     * Extracts the user JWT from the {@code X-Forwarded-Authorization} header, if present.
     *
     * <p>Downstream services call this to recover the original user's token.
     *
     * @param forwardedAuthHeaderValue value of {@code X-Forwarded-Authorization} (may be null)
     * @return the bare Bearer token string, or empty if header absent
     */
    public Optional<String> extractForwardedUserJwt(String forwardedAuthHeaderValue) {
        if (forwardedAuthHeaderValue == null || forwardedAuthHeaderValue.isBlank()) {
            return Optional.empty();
        }
        String token = forwardedAuthHeaderValue.startsWith("Bearer ")
                ? forwardedAuthHeaderValue.substring(7)
                : forwardedAuthHeaderValue;
        return Optional.of(token);
    }

    /**
     * Produces the value for the {@code X-Forwarded-Authorization} header, forwarding
     * the calling user's JWT into a downstream request.
     *
     * @param userJwt raw JWT string (without "Bearer " prefix)
     * @return header value to set on the outbound request
     */
    public String buildForwardedAuthHeader(String userJwt) {
        return "Bearer " + userJwt;
    }

    // ─── Service-initiated JWT ─────────────────────────────────────────────────

    /**
     * Generates a short-lived service-level JWT (no user context) for service-to-service calls.
     *
     * <p>Claims: {@code sub} = serviceName, {@code iss} = issuer, {@code aud} = audience,
     * {@code jti} = random UUID, {@code exp} = now + 5 minutes, {@code svc} = true.
     *
     * @param audience target service name
     * @return signed JWT string (bearer token)
     */
    public Promise<String> generateServiceJwt(String audience) {
        return Promise.ofBlocking(executor, () -> {
            var signingKey = keyProvider.getSigningKey();
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(serviceName)
                    .issuer(issuer)
                    .audience(audience)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(SERVICE_JWT_TTL_SECONDS)))
                    .claim("svc", true)
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(signingKey.getKeyID())
                    .build();
            var jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(signingKey));
            log.debug("[svc-jwt] generated service JWT: sub={} aud={} jti={}",
                    serviceName, audience, claims.getJWTID());
            return jwt.serialize();
        });
    }
}
