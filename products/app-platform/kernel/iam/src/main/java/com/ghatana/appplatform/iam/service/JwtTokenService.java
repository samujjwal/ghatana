/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.service;

import com.ghatana.appplatform.iam.domain.TokenClaims;
import com.ghatana.appplatform.iam.port.SigningKeyProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Issues RS256-signed JWTs from a {@link TokenClaims} value object (K01-001).
 *
 * <p>Thread-safe — Nimbus {@link RSASSASigner} instances are safe for concurrent use.
 * The signing key is fetched on each call so hot-reloaded keys take effect immediately.
 *
 * <p>Claims layout:
 * <pre>
 * {
 *   "sub":         "client-id-string",
 *   "iss":         "https://iam.ghatana.io",
 *   "aud":         ["ghatana-platform"],
 *   "iat":         1234567890,
 *   "exp":         1234571490,
 *   "jti":         "uuid",
 *   "tenant_id":   "uuid",
 *   "roles":       ["platform:admin"],
 *   "permissions": ["ledger:read","ledger:post"]
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose RS256 JWT token issuer (K01-001)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final SigningKeyProvider signingKeyProvider;

    /**
     * @param signingKeyProvider source of the active RSA signing key pair
     */
    public JwtTokenService(SigningKeyProvider signingKeyProvider) {
        this.signingKeyProvider = signingKeyProvider;
    }

    /**
     * Signs and serialises a JWT from the given claims.
     *
     * @param claims the token claims to embed
     * @return compact serialised JWT string (header.payload.signature)
     * @throws IllegalStateException if signing fails
     */
    public String issue(TokenClaims claims) {
        try {
            var rsaKey = signingKeyProvider.getSigningKey();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())
                    .build();

            JWTClaimsSet jwtClaims = new JWTClaimsSet.Builder()
                    .subject(claims.subject())
                    .issuer(claims.issuer())
                    .audience(claims.audience())
                    .issueTime(Date.from(claims.issuedAt()))
                    .expirationTime(Date.from(claims.expiresAt()))
                    .jwtID(claims.jwtId())
                    .claim("tenant_id", claims.tenantId().toString())
                    .claim("roles", claims.roles())
                    .claim("permissions", claims.permissions())
                    .build();

            SignedJWT jwt = new SignedJWT(header, jwtClaims);
            jwt.sign(new RSASSASigner(rsaKey));
            String token = jwt.serialize();
            log.debug("Issued RS256 JWT for subject={} jti={}", claims.subject(), claims.jwtId());
            return token;

        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }
}
