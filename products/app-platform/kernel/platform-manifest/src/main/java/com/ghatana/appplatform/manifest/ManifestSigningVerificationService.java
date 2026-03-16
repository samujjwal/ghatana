package com.ghatana.appplatform.manifest;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Ed25519-based signing and verification of release manifests via K-14 HSM.
 *              Signing flow: hash manifest YAML → sign digest via HSM → store signature + fingerprint.
 *              Verification flow: recompute hash → verify signature via HSM → block deployment if invalid.
 *              Tampered or unsigned manifests blocked at the deployment gate.
 *              Public key distributed to tenants for independent verification.
 *              All sign/verify events recorded in K-07 audit.
 * @doc.layer   Platform Manifest (PU-004)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-PU004-002: Manifest signing and verification
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS manifest_signatures (
 *   signature_id   TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   manifest_id    TEXT NOT NULL UNIQUE,
 *   content_hash   TEXT NOT NULL,          -- SHA-256 hex of canonical YAML
 *   signature_b64  TEXT NOT NULL,          -- Base64 Ed25519 signature
 *   key_alias      TEXT NOT NULL,          -- HSM key alias used
 *   signed_by      TEXT NOT NULL,
 *   signed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS manifest_verification_log (
 *   log_id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   manifest_id    TEXT NOT NULL,
 *   verified       BOOLEAN NOT NULL,
 *   verified_by    TEXT NOT NULL,
 *   detail         TEXT,
 *   verified_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class ManifestSigningVerificationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface HsmSigningPort {
        /** Sign digest bytes using Ed25519 key identified by alias. Returns Base64 signature. */
        String sign(String keyAlias, byte[] digest) throws Exception;
        /** Verify a Base64 signature for given digest. Returns true if valid. */
        boolean verify(String keyAlias, byte[] digest, String signatureB64) throws Exception;
        /** Export Base64-encoded Ed25519 public key for tenant distribution. */
        String publicKey(String keyAlias) throws Exception;
    }

    public interface ManifestContentPort {
        /** Return canonical YAML bytes for a manifest (reproducible serialization). */
        byte[] canonicalYaml(String manifestId) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String KEY_ALIAS = "platform-manifest-ca-v1";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final HsmSigningPort hsm;
    private final ManifestContentPort content;
    private final ReleaseManifestService manifestService;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter signaturesCounter;
    private final Counter verificationFailuresCounter;

    public ManifestSigningVerificationService(
        javax.sql.DataSource ds,
        HsmSigningPort hsm,
        ManifestContentPort content,
        ReleaseManifestService manifestService,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                        = ds;
        this.hsm                       = hsm;
        this.content                   = content;
        this.manifestService           = manifestService;
        this.audit                     = audit;
        this.executor                  = executor;
        this.signaturesCounter         = Counter.builder("manifest.signatures.issued").register(registry);
        this.verificationFailuresCounter = Counter.builder("manifest.signatures.verification_failures").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sign a DRAFT manifest. Transitions the manifest to SIGNED on success.
     * Computes SHA-256 of canonical YAML, signs with Ed25519 via HSM.
     */
    public Promise<String> sign(String manifestId, String signedBy) {
        return Promise.ofBlocking(executor, () -> {
            byte[] yamlBytes = content.canonicalYaml(manifestId);
            byte[] digest    = sha256(yamlBytes);
            String hash      = hexEncode(digest);
            String signatureB64 = hsm.sign(KEY_ALIAS, digest);

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO manifest_signatures (manifest_id, content_hash, signature_b64, key_alias, signed_by) " +
                     "VALUES (?,?,?,?,?) RETURNING signature_id"
                 )) {
                ps.setString(1, manifestId); ps.setString(2, hash);
                ps.setString(3, signatureB64); ps.setString(4, KEY_ALIAS);
                ps.setString(5, signedBy);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String sigId = rs.getString("signature_id");
                    // Transition manifest to SIGNED
                    manifestService.markSigned(manifestId).get();
                    signaturesCounter.increment();
                    logVerification(manifestId, true, signedBy, "signed sigId=" + sigId);
                    audit.record(signedBy, "MANIFEST_SIGNED", "manifestId=" + manifestId + " hash=" + hash);
                    return sigId;
                }
            }
        });
    }

    /**
     * Verify a manifest's signature. Returns true if valid.
     * Called by the deployment gate before any release is applied.
     */
    public Promise<Boolean> verify(String manifestId, String verifiedBy) {
        return Promise.ofBlocking(executor, () -> {
            SignatureRecord sig = loadSignature(manifestId);
            byte[] yamlBytes   = content.canonicalYaml(manifestId);
            byte[] digest      = sha256(yamlBytes);
            String recomputedHash = hexEncode(digest);

            // Guard: content hash must match what was signed
            if (!recomputedHash.equals(sig.contentHash())) {
                verificationFailuresCounter.increment();
                logVerification(manifestId, false, verifiedBy, "content_hash_mismatch");
                audit.record(verifiedBy, "MANIFEST_VERIFY_FAIL",
                    "manifestId=" + manifestId + " reason=content_hash_mismatch");
                return false;
            }

            boolean valid = hsm.verify(KEY_ALIAS, digest, sig.signatureB64());
            logVerification(manifestId, valid, verifiedBy, valid ? "ok" : "invalid_signature");
            if (!valid) {
                verificationFailuresCounter.increment();
                audit.record(verifiedBy, "MANIFEST_VERIFY_FAIL",
                    "manifestId=" + manifestId + " reason=invalid_signature");
            } else {
                audit.record(verifiedBy, "MANIFEST_VERIFY_OK", "manifestId=" + manifestId);
            }
            return valid;
        });
    }

    /**
     * Deployment gate: throws IllegalStateException if manifest is not verifiably signed.
     * Must be called before any release packaging or deployment.
     */
    public Promise<Void> assertVerified(String manifestId, String callerContext) {
        return verify(manifestId, callerContext).then(valid -> {
            if (!valid) throw new IllegalStateException(
                "Manifest " + manifestId + " failed signature verification — deployment blocked");
            return Promise.of(null);
        });
    }

    /**
     * Return the Base64 Ed25519 public key for tenant distribution.
     * Tenants can independently verify platform release signatures.
     */
    public Promise<String> getPublicKey() {
        return Promise.ofBlocking(executor, () -> hsm.publicKey(KEY_ALIAS));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private record SignatureRecord(String signatureId, String contentHash, String signatureB64) {}

    private SignatureRecord loadSignature(String manifestId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT signature_id, content_hash, signature_b64 FROM manifest_signatures WHERE manifest_id=?"
             )) {
            ps.setString(1, manifestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No signature found for manifest: " + manifestId);
                return new SignatureRecord(rs.getString("signature_id"),
                    rs.getString("content_hash"), rs.getString("signature_b64"));
            }
        }
    }

    private void logVerification(String manifestId, boolean verified, String by, String detail) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO manifest_verification_log (manifest_id, verified, verified_by, detail) VALUES (?,?,?,?)"
             )) {
            ps.setString(1, manifestId); ps.setBoolean(2, verified);
            ps.setString(3, by); ps.setString(4, detail);
            ps.executeUpdate();
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
