package com.ghatana.appplatform.plugin.manifest;

import com.ghatana.appplatform.plugin.domain.PluginManifest;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies the Ed25519 signature on a {@link PluginManifest} against the platform's
 * set of trusted publisher public keys.
 *
 * <p>The signature is computed over the canonical JSON representation of the manifest
 * (all fields sorted alphabetically, UTF-8 encoded). Key rotation is supported by
 * passing multiple trusted public keys; any one match is sufficient.
 *
 * @doc.type  class
 * @doc.purpose Ed25519 manifest signature verification for plugin admission control
 * @doc.layer  product
 * @doc.pattern Service
 */
public final class PluginManifestVerifier {

    private final CanonicalManifestSerializer serializer;

    /**
     * @param serializer converts a {@link PluginManifest} to its canonical bytes
     */
    public PluginManifestVerifier(CanonicalManifestSerializer serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    /**
     * Verifies that the manifest's embedded signature was produced by one of the
     * trusted publisher keys.
     *
     * @param manifest     manifest to verify (must have a {@code signature} field)
     * @param trustedKeys  set of known-good Ed25519 {@link PublicKey}s
     * @return {@code true} when the signature is valid for at least one trusted key
     * @throws PluginSignatureException when the manifest is unsigned, or when
     *                                  signature cannot be decoded / verified
     */
    public boolean verify(PluginManifest manifest, Set<PublicKey> trustedKeys) {
        Objects.requireNonNull(manifest,    "manifest");
        Objects.requireNonNull(trustedKeys, "trustedKeys");

        if (!manifest.isSigned()) {
            throw new PluginSignatureException("Manifest is not signed: " + manifest.name());
        }
        if (trustedKeys.isEmpty()) {
            throw new PluginSignatureException("No trusted keys configured");
        }

        byte[] canonicalBytes = serializer.toCanonicalBytes(manifest);
        byte[] sigBytes;
        try {
            sigBytes = Base64.getDecoder().decode(manifest.signature());
        } catch (IllegalArgumentException e) {
            throw new PluginSignatureException("Signature is not valid Base64", e);
        }

        for (PublicKey key : trustedKeys) {
            try {
                Signature sig = Signature.getInstance("Ed25519");
                sig.initVerify(key);
                sig.update(canonicalBytes);
                if (sig.verify(sigBytes)) {
                    return true;
                }
            } catch (Exception e) {
                // try next key
            }
        }
        return false;
    }

    /**
     * Verifies and throws if the manifest is invalid.
     *
     * @throws PluginSignatureException on failure
     */
    public void verifyOrThrow(PluginManifest manifest, Set<PublicKey> trustedKeys) {
        if (!verify(manifest, trustedKeys)) {
            throw new PluginSignatureException(
                    "Signature verification failed for plugin: " + manifest.name());
        }
    }
}
