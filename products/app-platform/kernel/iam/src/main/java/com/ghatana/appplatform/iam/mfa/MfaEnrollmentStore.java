package com.ghatana.appplatform.iam.mfa;

import java.util.Optional;

/**
 * Port for MFA enrollment persistence (STORY-K01-004).
 *
 * <p>Stores the TOTP secret and hashed backup codes per user.
 * All JDBC implementations must use {@code Promise.ofBlocking}.
 *
 * @doc.type interface
 * @doc.purpose MFA enrollment store port (K01-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface MfaEnrollmentStore {

    /**
     * Persists a new TOTP enrollment for the user.
     *
     * @param userId         unique user/principal identifier
     * @param tenantId       tenant scope
     * @param totpSecretB32  Base32-encoded TOTP secret
     * @param hashedBackups  SHA-256 hex hashes of the 10 backup codes
     */
    void save(String userId, String tenantId, String totpSecretB32, java.util.List<String> hashedBackups);

    /**
     * Retrieves a TOTP enrollment for the user, or empty if not enrolled.
     */
    Optional<MfaEnrollment> find(String userId, String tenantId);

    /**
     * Marks a specific hashed backup code as consumed (single-use).
     * Returns {@code true} if the code was found and consumed, {@code false} if already used.
     */
    boolean consumeBackupCode(String userId, String tenantId, String hashedCode);

    /**
     * Removes a user's MFA enrollment (admin reset or user revocation).
     */
    void delete(String userId, String tenantId);

    /** Enrolled MFA state for a user. */
    record MfaEnrollment(
        String userId,
        String tenantId,
        String totpSecretB32,
        java.util.List<String> unconsumedHashedBackups
    ) {}
}
