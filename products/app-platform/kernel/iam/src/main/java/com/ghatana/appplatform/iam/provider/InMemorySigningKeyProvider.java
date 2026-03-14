/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.provider;

import com.ghatana.appplatform.iam.port.SigningKeyProvider;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * In-memory RSA-2048 signing key provider suitable for development and tests.
 *
 * <p>Generates a fresh key pair on construction. The key pair is discarded when
 * the JVM exits — DO NOT use in production.
 *
 * <p>Production deployments should use a {@code SecretsBackedSigningKeyProvider}
 * (backed by K-14 secrets store) or an HSM-based implementation.
 *
 * @doc.type class
 * @doc.purpose In-memory RSA key provider for dev/test environments (K01-003)
 * @doc.layer core
 * @doc.pattern Provider
 */
public final class InMemorySigningKeyProvider implements SigningKeyProvider {

    private final RSAKey rsaKey;

    /**
     * Generates a new RSA-2048 key pair and wraps it as a Nimbus {@link RSAKey}.
     * The kid is a random UUID so JWKS lookups remain correct across multiple
     * instances within the same test run.
     */
    public InMemorySigningKeyProvider() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            this.rsaKey = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                    .privateKey((RSAPrivateKey) kp.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm unavailable on this JVM", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public RSAKey getSigningKey() {
        return rsaKey;
    }

    /** {@inheritDoc} */
    @Override
    public String getKeyId() {
        return rsaKey.getKeyID();
    }
}
