package com.ghatana.appplatform.sanctions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * @doc.type      Service
 * @doc.purpose   Signs air-gap update bundles using Ed25519. A signed bundle contains a sanctions
 *                list payload and a detached signature verifiable by the importer without a
 *                network connection. Keys are loaded from a secure key store at startup.
 * @doc.layer     Application
 * @doc.pattern   Cryptographic signing; offline air-gap workflow
 *
 * Story: D14-013
 */
public class AirGapBundleSigningService {

    private static final Logger log = LoggerFactory.getLogger(AirGapBundleSigningService.class);
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    private final PrivateKey signingKey;
    private final PublicKey  verifyKey;

    /**
     * @param privateKeyDer  DER-encoded Ed25519 private key bytes (loaded from HSM or vault)
     * @param publicKeyDer   DER-encoded Ed25519 public key bytes (distributed to importers)
     */
    public AirGapBundleSigningService(byte[] privateKeyDer, byte[] publicKeyDer) {
        try {
            KeyFactory kf = KeyFactory.getInstance(SIGNATURE_ALGORITHM);
            this.signingKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateKeyDer));
            this.verifyKey  = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(publicKeyDer));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load Ed25519 keys", e);
        }
    }

    /**
     * Signs a bundle payload and returns a {@link SignedBundle} containing the payload and
     * a Base64-encoded detached signature.
     *
     * @param bundleId  human-readable bundle identifier for the manifest
     * @param payload   raw bundle bytes (JSON or binary list data)
     */
    public SignedBundle sign(String bundleId, byte[] payload) {
        byte[] signatureBytes;
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initSign(signingKey);
            sig.update(payload);
            signatureBytes = sig.sign();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Bundle signing failed for " + bundleId, e);
        }

        String signatureB64 = Base64.getEncoder().encodeToString(signatureBytes);
        Instant signedAt    = Instant.now();
        log.info("AirGapBundle signed: bundleId={} payloadBytes={}", bundleId, payload.length);
        return new SignedBundle(bundleId, payload, signatureB64, signedAt);
    }

    /**
     * Verifies a {@link SignedBundle}'s signature. Used by the import workflow before
     * accepting the bundle.
     *
     * @param bundle  bundle to verify
     * @return true if signature is valid
     */
    public boolean verify(SignedBundle bundle) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(verifyKey);
            sig.update(bundle.payload());
            byte[] signatureBytes = Base64.getDecoder().decode(bundle.signatureB64());
            boolean valid = sig.verify(signatureBytes);
            if (!valid) log.warn("AirGapBundle signature INVALID: bundleId={}", bundle.bundleId());
            return valid;
        } catch (GeneralSecurityException e) {
            log.error("AirGapBundle verify error bundleId={}", bundle.bundleId(), e);
            return false;
        }
    }

    /**
     * Returns the public key as a Base64 string for distribution to importers.
     */
    public String getPublicKeyB64() {
        return Base64.getEncoder().encodeToString(verifyKey.getEncoded());
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record SignedBundle(String bundleId, byte[] payload, String signatureB64, Instant signedAt) {}
}
