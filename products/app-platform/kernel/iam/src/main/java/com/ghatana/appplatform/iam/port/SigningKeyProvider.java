/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.port;

import com.nimbusds.jose.jwk.RSAKey;

/**
 * Port for obtaining the active RS256 signing key.
 *
 * <p>Implementations may load the key from:
 * <ul>
 *   <li>In-memory (dev/test): {@code InMemorySigningKeyProvider}.</li>
 *   <li>K-14 secrets store: {@code SecretsBackedSigningKeyProvider}.</li>
 *   <li>HSM / PKCS#11 slot.</li>
 * </ul>
 *
 * <p>The returned {@link RSAKey} MUST include the private key material for signing.
 * The corresponding public key is derived from it automatically.
 *
 * @doc.type interface
 * @doc.purpose Port for RS256 signing key access (K01-003)
 * @doc.layer core
 * @doc.pattern Port
 */
public interface SigningKeyProvider {

    /**
     * Returns the current active RSA signing key (includes private material).
     *
     * @return the RSA key, never {@code null}
     */
    RSAKey getSigningKey();

    /**
     * Returns the kid (key ID) embedded in the JWS header so verifiers can
     * look up the correct public key from the JWKS endpoint.
     *
     * @return the key identifier
     */
    String getKeyId();
}
