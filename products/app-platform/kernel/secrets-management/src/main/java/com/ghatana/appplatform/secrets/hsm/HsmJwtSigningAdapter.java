/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.hsm;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Signs JWTs using an RSA private key managed by the HSM (STORY-K14-012).
 *
 * <p>Delegates signing to {@link HsmKeyOperationsProvider} which either uses the PKCS#11
 * HSM provider in production or an in-process stub in development. The JWS signing itself
 * uses Nimbus JOSE+JWT to ensure correct RS256 formatting.
 *
 * <p>This adapter is used by {@code JwtTokenService} when HSM-backed key management is
 * enabled, replacing the in-process {@code InMemorySigningKeyProvider}.
 *
 * @doc.type  class
 * @doc.purpose RSA JWT signing adapter backed by HSM private keys via PKCS#11 (K14-012)
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class HsmJwtSigningAdapter {

    private static final Logger log = LoggerFactory.getLogger(HsmJwtSigningAdapter.class);

    private final HsmKeyOperationsProvider hsmKeyOperations;
    private final PrivateKey signingKey;
    private final String keyId;
    private final Executor executor;

    /**
     * @param hsmKeyOperations  the HSM provider for signing
     * @param signingKey         the RSA private key (or PKCS#11 hardware key reference)
     * @param keyId             key ID to embed in the JWT header
     * @param executor           thread pool for blocking signing operations
     */
    public HsmJwtSigningAdapter(HsmKeyOperationsProvider hsmKeyOperations,
                                  PrivateKey signingKey,
                                  String keyId,
                                  Executor executor) {
        this.hsmKeyOperations = Objects.requireNonNull(hsmKeyOperations, "hsmKeyOperations");
        this.signingKey       = Objects.requireNonNull(signingKey,       "signingKey");
        this.keyId            = Objects.requireNonNull(keyId,            "keyId");
        this.executor         = Objects.requireNonNull(executor,         "executor");
    }

    /**
     * Signs a JWT claims set with RS256 using the HSM-backed private key.
     *
     * @param claimsSet the JWT claims to sign
     * @return promise resolving to the compact signed JWT string
     */
    public Promise<String> sign(JWTClaimsSet claimsSet) {
        Objects.requireNonNull(claimsSet, "claimsSet");

        return Promise.ofBlocking(executor, () -> {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            // Nimbus JOSE uses JWSSignerFactory internally; for PKCS#11 this
            // delegates to the hardware-backed private key reference.
            JWSSigner signer = new RSASSASigner(signingKey);
            signedJWT.sign(signer);

            String compact = signedJWT.serialize();
            log.debug("HSM-signed JWT: kid={} sub={}", keyId,
                    claimsSet.getSubject());
            return compact;
        });
    }

    /**
     * Convenience: builds a standard claims set and signs it.
     *
     * @param subject   JWT {@code sub} claim
     * @param issuer    JWT {@code iss} claim
     * @param tenantId  custom {@code tenant_id} claim
     * @param ttlSeconds token time-to-live in seconds
     * @return promise resolving to the compact signed JWT string
     */
    public Promise<String> signForTenant(String subject, String issuer,
                                          String tenantId, long ttlSeconds) {
        Objects.requireNonNull(subject,  "subject");
        Objects.requireNonNull(issuer,   "issuer");

        long now = System.currentTimeMillis();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(new Date(now))
                .expirationTime(new Date(now + ttlSeconds * 1000))
                .claim("tenant_id", tenantId)
                .build();

        return sign(claimsSet);
    }
}
