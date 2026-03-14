package com.ghatana.appplatform.config.bundle;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Signs and verifies {@link ConfigBundle} instances using Ed25519 (RFC 8037).
 *
 * <p><b>Signing workflow</b>:
 * <ol>
 *   <li>Export a bundle via {@link ConfigBundleExporter#build()}.</li>
 *   <li>Call {@link #sign(ConfigBundle)} — the signer computes an Ed25519 signature
 *       over the UTF-8 bytes of {@code manifest.contentHash()} and attaches a
 *       {@link BundleSignature} to the manifest.</li>
 *   <li>Write the signed bundle via {@link ConfigBundleExporter#write(ConfigBundle, java.io.OutputStream)}.</li>
 * </ol>
 *
 * <p><b>Verification workflow</b>:
 * <ol>
 *   <li>Deserialise the bundle from the stream.</li>
 *   <li>Call {@link #verify(ConfigBundle)} — throws {@link BundleSignatureException}
 *       if the bundle is unsigned, the signature is malformed, or no trusted key
 *       validates the signature.</li>
 * </ol>
 *
 * <p><b>Key rotation</b>: pass the full set of currently trusted public keys in
 * the constructor. Verification tries each key until one succeeds.
 *
 * @doc.type class
 * @doc.purpose Ed25519 sign and verify for air-gap config bundles (K02-013)
 * @doc.layer product
 * @doc.pattern Service
 */
public class ConfigBundleSigner {

    private static final Logger LOG = Logger.getLogger(ConfigBundleSigner.class.getName());
    private static final String ALGORITHM = "Ed25519";

    private final PrivateKey   signingKey;
    private final String       keyId;
    private final Set<PublicKey> trustedKeys;

    /**
     * @param signingKey  Ed25519 private key used when signing bundles
     * @param keyId       stable identifier for {@code signingKey} (written into the manifest)
     * @param trustedKeys set of Ed25519 public keys accepted during verification;
     *                    must contain the public key corresponding to {@code signingKey}
     */
    public ConfigBundleSigner(PrivateKey signingKey, String keyId, Set<PublicKey> trustedKeys) {
        this.signingKey  = Objects.requireNonNull(signingKey, "signingKey");
        this.keyId       = Objects.requireNonNull(keyId, "keyId").strip();
        this.trustedKeys = Set.copyOf(Objects.requireNonNull(trustedKeys, "trustedKeys"));
        if (this.keyId.isEmpty())      throw new IllegalArgumentException("keyId must not be blank");
        if (this.trustedKeys.isEmpty()) throw new IllegalArgumentException("trustedKeys must not be empty");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Attaches an Ed25519 signature over the bundle's {@code contentHash} and
     * returns a new bundle with the signature embedded in the manifest.
     *
     * <p>The signed material is {@code contentHash.getBytes(UTF-8)}, ensuring the
     * signature covers exactly the hash that ties the manifest to its payload.
     *
     * @param bundle unsigned (or previously signed) bundle
     * @return new bundle with {@link BundleSignature} in the manifest
     * @throws IllegalStateException if Ed25519 is unavailable in the current JVM
     * @throws IllegalArgumentException if the bundle has no contentHash
     */
    public ConfigBundle sign(ConfigBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");

        String contentHash = bundle.manifest().contentHash();
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("Bundle manifest has no contentHash — cannot sign");
        }

        byte[] hashBytes  = contentHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] rawSig     = edSign(hashBytes);
        String signatureB64 = Base64.getEncoder().encodeToString(rawSig);

        BundleSignature   bundleSignature = new BundleSignature(keyId, ALGORITHM, signatureB64);
        ConfigBundleManifest signedManifest = bundle.manifest().withSignature(bundleSignature);

        LOG.info("[ConfigBundleSigner] Signed bundleId=" + bundle.manifest().bundleId()
            + " keyId=" + keyId);

        return bundle.withManifest(signedManifest);
    }

    /**
     * Verifies the Ed25519 signature embedded in the bundle.
     *
     * <p>Verification succeeds only when:
     * <ul>
     *   <li>The bundle has a {@link BundleSignature} attached to its manifest.</li>
     *   <li>The algorithm in the signature is {@value #ALGORITHM}.</li>
     *   <li>At least one key in {@link #trustedKeys} validates the signature against
     *       the UTF-8 bytes of {@code manifest.contentHash()}.</li>
     * </ul>
     *
     * @param bundle the bundle to verify
     * @throws BundleSignatureException if the bundle is unsigned, uses an unsupported
     *                                   algorithm, has a malformed signature, or no
     *                                   trusted key accepts it
     */
    public void verify(ConfigBundle bundle) throws BundleSignatureException {
        Objects.requireNonNull(bundle, "bundle");

        if (!bundle.manifest().isSigned()) {
            throw new BundleSignatureException(
                "Bundle " + bundle.manifest().bundleId() + " has no signature");
        }

        BundleSignature sig = bundle.manifest().signature();

        if (!ALGORITHM.equalsIgnoreCase(sig.algorithm())) {
            throw new BundleSignatureException(
                "Unsupported signing algorithm: " + sig.algorithm()
                    + " (expected " + ALGORITHM + ")");
        }

        byte[] rawSig;
        try {
            rawSig = Base64.getDecoder().decode(sig.signatureB64());
        } catch (IllegalArgumentException e) {
            throw new BundleSignatureException("Signature is not valid Base64", e);
        }

        byte[] hashBytes = bundle.manifest().contentHash()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (PublicKey candidate : trustedKeys) {
            if (edVerify(candidate, hashBytes, rawSig)) {
                LOG.info("[ConfigBundleSigner] Verified bundleId=" + bundle.manifest().bundleId()
                    + " keyId=" + sig.keyId());
                return;
            }
        }

        throw new BundleSignatureException(
            "Signature on bundle " + bundle.manifest().bundleId()
                + " could not be verified by any trusted key (keyId=" + sig.keyId() + ")");
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private byte[] edSign(byte[] data) {
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(signingKey);
            signer.update(data);
            return signer.sign();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not available in this JVM", e);
        } catch (InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to produce Ed25519 signature", e);
        }
    }

    private boolean edVerify(PublicKey publicKey, byte[] data, byte[] rawSig) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(rawSig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not available in this JVM", e);
        } catch (InvalidKeyException e) {
            // key is wrong type — treat as mismatch
            return false;
        } catch (SignatureException e) {
            // malformed signature bytes — treat as mismatch
            return false;
        }
    }
}
