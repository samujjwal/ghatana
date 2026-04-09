/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.services.auth.mfa;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-Factor Authentication (MFA) service supporting TOTP (Time-based One-Time Password).
 *
 * <p>Implements RFC 6238 TOTP algorithm for generating and validating time-based codes.
 * Supports QR code generation for authenticator app enrollment.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>TOTP secret generation and storage</li>
 *   <li>6-digit code validation with time window tolerance</li>
 *   <li>QR code URI generation for authenticator apps</li>
 *   <li>Backup codes for account recovery</li>
 *   <li>Rate limiting for brute-force protection</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Multi-factor authentication service
 * @doc.layer platform
 * @doc.pattern Service
 */
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private static final String TOTP_ALGORITHM = "HmacSHA1";
    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_PERIOD_SECONDS = 30;
    private static final int TOTP_WINDOW = 1; // Allow 1 period before/after for clock skew
    private static final int SECRET_LENGTH_BYTES = 20; // 160 bits
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 8;

    private final Map<String, MfaConfig> userMfaConfigs = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Enrolls a user in MFA by generating a new TOTP secret and backup codes.
     *
     * @param userId User identifier
     * @param issuer Application name for QR code (e.g., "Ghatana")
     * @return Promise with enrollment data (secret, QR URI, backup codes)
     */
    public Promise<EnrollmentData> enrollUser(String userId, String issuer) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            // Generate TOTP secret
            byte[] secretBytes = new byte[SECRET_LENGTH_BYTES];
            secureRandom.nextBytes(secretBytes);
            String secret = Base64.getEncoder().encodeToString(secretBytes);

            // Generate backup codes
            String[] backupCodes = generateBackupCodes();

            // Store MFA configuration
            MfaConfig config = new MfaConfig(secret, backupCodes, false);
            userMfaConfigs.put(userId, config);

            // Generate QR code URI for authenticator apps
            String qrUri = generateQrCodeUri(userId, secret, issuer);

            log.info("MFA enrollment initiated for user: {}", userId);

            return new EnrollmentData(secret, qrUri, backupCodes);
        });
    }

    /**
     * Verifies a TOTP code and completes MFA enrollment.
     *
     * @param userId User identifier
     * @param code 6-digit TOTP code from authenticator app
     * @return Promise with verification result
     */
    public Promise<Boolean> verifyEnrollment(String userId, String code) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            MfaConfig config = userMfaConfigs.get(userId);
            if (config == null) {
                log.warn("MFA enrollment verification failed: no config for user {}", userId);
                return false;
            }

            if (config.enabled()) {
                log.warn("MFA already enabled for user {}", userId);
                return false;
            }

            boolean valid = validateTotp(config.secret(), code);
            if (valid) {
                // Mark MFA as enabled
                MfaConfig enabledConfig = new MfaConfig(config.secret(), config.backupCodes(), true);
                userMfaConfigs.put(userId, enabledConfig);
                log.info("MFA enrollment completed for user: {}", userId);
            } else {
                log.warn("MFA enrollment verification failed for user: {}", userId);
            }

            return valid;
        });
    }

    /**
     * Validates a TOTP code for an enrolled user.
     *
     * @param userId User identifier
     * @param code 6-digit TOTP code
     * @return Promise with validation result
     */
    public Promise<Boolean> validateCode(String userId, String code) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            MfaConfig config = userMfaConfigs.get(userId);
            if (config == null || !config.enabled()) {
                log.warn("MFA validation failed: MFA not enabled for user {}", userId);
                return false;
            }

            // Check rate limiting
            int attempts = failedAttempts.getOrDefault(userId, 0);
            if (attempts >= 5) {
                log.warn("MFA validation blocked: too many failed attempts for user {}", userId);
                return false;
            }

            boolean valid = validateTotp(config.secret(), code);

            if (valid) {
                failedAttempts.remove(userId);
                log.info("MFA validation successful for user: {}", userId);
            } else {
                failedAttempts.put(userId, attempts + 1);
                log.warn("MFA validation failed for user: {} (attempt {})", userId, attempts + 1);
            }

            return valid;
        });
    }

    /**
     * Validates a backup code for account recovery.
     *
     * @param userId User identifier
     * @param backupCode Backup code
     * @return Promise with validation result
     */
    public Promise<Boolean> validateBackupCode(String userId, String backupCode) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            MfaConfig config = userMfaConfigs.get(userId);
            if (config == null) {
                log.warn("Backup code validation failed: no MFA config for user {}", userId);
                return false;
            }

            // Check if backup code exists and hasn't been used
            for (int i = 0; i < config.backupCodes().length; i++) {
                if (config.backupCodes()[i] != null && config.backupCodes()[i].equals(backupCode)) {
                    // Invalidate the used backup code
                    String[] updatedCodes = config.backupCodes().clone();
                    updatedCodes[i] = null;
                    MfaConfig updatedConfig = new MfaConfig(config.secret(), updatedCodes, config.enabled());
                    userMfaConfigs.put(userId, updatedConfig);

                    failedAttempts.remove(userId);
                    log.info("Backup code validated for user: {}", userId);
                    return true;
                }
            }

            log.warn("Invalid backup code for user: {}", userId);
            return false;
        });
    }

    /**
     * Disables MFA for a user.
     *
     * @param userId User identifier
     * @return Promise with result
     */
    public Promise<Boolean> disableMfa(String userId) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            MfaConfig removed = userMfaConfigs.remove(userId);
            failedAttempts.remove(userId);

            if (removed != null) {
                log.info("MFA disabled for user: {}", userId);
                return true;
            }
            return false;
        });
    }

    /**
     * Checks if MFA is enabled for a user.
     *
     * @param userId User identifier
     * @return true if MFA is enabled
     */
    public boolean isMfaEnabled(String userId) {
        MfaConfig config = userMfaConfigs.get(userId);
        return config != null && config.enabled();
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private boolean validateTotp(String secret, String code) {
        if (code == null || code.length() != TOTP_DIGITS) {
            return false;
        }

        try {
            long currentTime = Instant.now().getEpochSecond() / TOTP_PERIOD_SECONDS;

            // Check current time window and adjacent windows for clock skew
            for (int i = -TOTP_WINDOW; i <= TOTP_WINDOW; i++) {
                String expectedCode = generateTotp(secret, currentTime + i);
                if (code.equals(expectedCode)) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.error("TOTP validation error", e);
            return false;
        }
    }

    private String generateTotp(String secret, long timeCounter) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] secretBytes = Base64.getDecoder().decode(secret);
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeCounter).array();

        Mac mac = Mac.getInstance(TOTP_ALGORITHM);
        mac.init(new SecretKeySpec(secretBytes, TOTP_ALGORITHM));
        byte[] hash = mac.doFinal(timeBytes);

        // Dynamic truncation (RFC 6238)
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
        return String.format("%0" + TOTP_DIGITS + "d", otp);
    }

    private String[] generateBackupCodes() {
        String[] codes = new String[BACKUP_CODE_COUNT];
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes[i] = generateRandomCode(BACKUP_CODE_LENGTH);
        }
        return codes;
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return code.toString();
    }

    private String generateQrCodeUri(String userId, String secret, String issuer) {
        // otpauth://totp/Issuer:user@example.com?secret=BASE32SECRET&issuer=Issuer
        String base32Secret = base32Encode(Base64.getDecoder().decode(secret));
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                issuer, userId, base32Secret, issuer, TOTP_DIGITS, TOTP_PERIOD_SECONDS);
    }

    private String base32Encode(byte[] data) {
        String base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(base32Chars.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            result.append(base32Chars.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }

        return result.toString();
    }

    // ─── Data Classes ────────────────────────────────────────────────────────

    public record MfaConfig(String secret, String[] backupCodes, boolean enabled) {}

    public record EnrollmentData(String secret, String qrCodeUri, String[] backupCodes) {}
}
