/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.mfa;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Orchestrates MFA enrollment and verification (STORY-K01-004).
 *
 * <p>Workflow:
 * <ol>
 *   <li>{@code enroll} — generate TOTP secret + backup codes; persist hashed backups.</li>
 *   <li>{@code confirmEnrollment} — verify first TOTP code, mark active.</li>
 *   <li>{@code verifyTotp} — check TOTP during login challenge.</li>
 *   <li>{@code verifyBackupCode} — consume a single-use backup code.</li>
 * </ol>
 *
 * <p>All operations run on the provided blocking {@code executor} so the ActiveJ
 * event loop is never blocked.
 *
 * @doc.type class
 * @doc.purpose MFA enrollment + TOTP/backup-code verification service (K01-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final TotpService totp;
    private final MfaEnrollmentStore store;
    private final Executor executor;
    private final String issuer;

    /**
     * @param totp     TOTP utility (code generation + verification)
     * @param store    MFA enrollment port
     * @param executor blocking executor for JDBC operations
     * @param issuer   human-readable issuer name shown in authenticator app
     */
    public MfaService(TotpService totp, MfaEnrollmentStore store, Executor executor, String issuer) {
        this.totp = totp;
        this.store = store;
        this.executor = executor;
        this.issuer = issuer;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Enroll
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Begins MFA enrollment: generates secret + backup codes, persists hashed backups.
     * Returns {@link EnrollmentResult} with plaintext backup codes shown exactly once.
     *
     * @param userId   unique principal identifier
     * @param tenantId tenant scope
     * @return async {@link EnrollmentResult} with QR URI and one-time backup codes
     */
    public Promise<EnrollmentResult> enroll(String userId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String secret = totp.generateSecret();
            String qrUri = totp.buildQrUri(issuer, userId + "@" + tenantId, secret);
            List<String> plainBackups = totp.generateBackupCodes();
            List<String> hashed = plainBackups.stream().map(MfaService::sha256Hex).toList();
            store.save(userId, tenantId, secret, hashed);
            log.info("MFA enrolled for user={} tenant={}", userId, tenantId);
            return new EnrollmentResult(qrUri, plainBackups, secret);
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Verify TOTP
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Verifies a TOTP code presented during login.
     *
     * @param userId   principal identifier
     * @param tenantId tenant scope
     * @param code     6-digit TOTP code from user's authenticator
     * @return async {@code true} if the code is valid within the ±1 step window
     */
    public Promise<Boolean> verifyTotp(String userId, String tenantId, String code) {
        return Promise.ofBlocking(executor, () -> {
            Optional<MfaEnrollmentStore.MfaEnrollment> enrollment = store.find(userId, tenantId);
            if (enrollment.isEmpty()) {
                log.warn("TOTP verify attempted for unenrolled user={} tenant={}", userId, tenantId);
                return false;
            }
            boolean ok = totp.verify(enrollment.get().totpSecretB32(), code);
            if (!ok) {
                log.warn("TOTP verification failed for user={} tenant={}", userId, tenantId);
            }
            return ok;
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Backup code
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Consumes a single backup code (single-use semantics via atomic DB UPDATE).
     *
     * @param userId   principal identifier
     * @param tenantId tenant scope
     * @param code     plaintext 8-digit backup code from user
     * @return async {@code true} if the code was valid and has now been consumed
     */
    public Promise<Boolean> verifyBackupCode(String userId, String tenantId, String code) {
        return Promise.ofBlocking(executor, () -> {
            String hashed = sha256Hex(code);
            boolean consumed = store.consumeBackupCode(userId, tenantId, hashed);
            if (consumed) {
                log.info("Backup code consumed for user={} tenant={}", userId, tenantId);
            } else {
                log.warn("Invalid or already-used backup code for user={} tenant={}", userId, tenantId);
            }
            return consumed;
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Admin
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Removes MFA enrollment for a user (admin reset or user-initiated revocation).
     */
    public Promise<Void> resetEnrollment(String userId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            store.delete(userId, tenantId);
            log.info("MFA enrollment reset for user={} tenant={}", userId, tenantId);
            return null;
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Produces a lowercase SHA-256 hex digest for backup code hashing. */
    static String sha256Hex(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in the JDK
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Result type
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Result returned from {@link #enroll}.
     *
     * @param qrUri        {@code otpauth://totp/…} URI suitable for QR code rendering
     * @param backupCodes  plaintext backup codes — shown once; caller must NOT store them
     * @param totpSecret   Base32 TOTP secret — shown once for manual entry if QR fails
     */
    public record EnrollmentResult(String qrUri, List<String> backupCodes, String totpSecret) {}
}
