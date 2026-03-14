package com.ghatana.appplatform.config.bundle;

import java.util.Objects;

/**
 * Ed25519 digital signature over a config bundle's content hash.
 *
 * @param keyId        identifies which signing key was used (for key rotation support)
 * @param algorithm    always "Ed25519"
 * @param signatureB64 base64-encoded Ed25519 signature bytes over the contentHash
 *
 * @doc.type record
 * @doc.purpose Digital signature metadata for a config bundle
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BundleSignature(
    String keyId,
    String algorithm,
    String signatureB64
) {
    public BundleSignature {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(signatureB64, "signatureB64");
        if (keyId.isBlank())        throw new IllegalArgumentException("keyId must not be blank");
        if (signatureB64.isBlank()) throw new IllegalArgumentException("signatureB64 must not be blank");
    }
}
