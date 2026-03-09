package com.ghatana.auth.core.port;

import com.ghatana.platform.domain.auth.KeyRotationStatus;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import io.activej.promise.Promise;

import java.time.Duration;

/**
 * Port interface for JWT key rotation and management.
 *
 * <p><b>Purpose</b><br>
 * Manages JWT signing key rotation with versioning support following security best practices.
 * Maintains multiple key versions (current + previous within validation window) for zero-downtime
 * key rotation. All operations return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Architecture Role</b><br>
 * This is a **port interface** in hexagonal architecture. Adapters provide concrete implementations
 * for key generation, storage, and rotation policies. Core authentication depends only on this port.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Initialize with rotation policy
 * JwtKeyRotationService keyService = new JwtKeyRotationServiceImpl(
 *     Duration.ofDays(90),  // Rotation interval
 *     Duration.ofDays(7)    // Validation window
 * );
 *
 * // Generate token with current key (includes 'kid' header)
 * Promise<String> tokenPromise = keyService.generateToken(tenantId, principal, ttl);
 *
 * // Validate token (accepts current or recent keys within window)
 * Promise<UserPrincipal> principalPromise = keyService.validateToken(tenantId, token);
 *
 * // Rotate keys (scheduled job)
 * Promise<Void> rotatePromise = keyService.rotateKeys(tenantId);
 *
 * // Check rotation status
 * Promise<KeyRotationStatus> statusPromise = keyService.getRotationStatus(tenantId);
 * }</pre>
 *
 * <p><b>Key Rotation Flow</b><br>
 * <pre>
 * T0: Rotation starts
 *     - Current key (v1) moves to activeKeys as "previous"
 *     - New key (v2) generated and becomes "current"
 *     - Both v1 and v2 accepted for validation
 *
 * T0 to T+7d: Validation window
 *     - Tokens signed with v1 (old) still accepted
 *     - New tokens signed with v2 (current)
 *     - Smooth migration for existing tokens
 *
 * T+7d: Window expires
 *     - Old key (v1) removed from activeKeys
 *     - Only current key (v2) accepted
 *     - v1 tokens rejected
 *
 * T+90d: Next rotation
 *     - v2 becomes previous, v3 becomes current
 *     - Cycle repeats
 * </pre>
 *
 * <p><b>Security Considerations</b><br>
 * - Keys stored securely (production: HSM, vault, encrypted database)
 * - Rotation interval: 90 days recommended (configurable)
 * - Validation window: 7 days recommended (smooth transition)
 * - Key ID (kid) in JWT header enables key lookup
 * - Expired keys discarded after validation window
 * - Audit trail maintained for all key operations
 *
 * <p><b>Multi-Tenancy</b><br>
 * Each tenant has independent key rotation:
 * - TenantId scopes all key operations
 * - Separate rotation schedules per tenant
 * - Isolated key storage
 *
 * @see JwtTokenProvider
 * @see KeyRotationStatus
 * @doc.type interface
 * @doc.purpose JWT key rotation and versioning port
 * @doc.layer core
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface JwtKeyRotationService {

    /**
     * Generate JWT access token with current key.
     *
     * <p>Token includes 'kid' (key ID) header for key lookup during validation.
     *
     * @param tenantId the tenant identifier
     * @param principal the user principal
     * @param ttl the token time-to-live
     * @return Promise of JWT token string with kid header
     */
    Promise<String> generateToken(TenantId tenantId, UserPrincipal principal, Duration ttl);

    /**
     * Generate JWT refresh token with current key.
     *
     * @param tenantId the tenant identifier
     * @param principal the user principal
     * @param ttl the token time-to-live (longer than access tokens)
     * @return Promise of JWT refresh token string with kid header
     */
    Promise<String> generateRefreshToken(TenantId tenantId, UserPrincipal principal, Duration ttl);

    /**
     * Validate JWT token using current or recent key.
     *
     * <p><b>Validation Process</b><br>
     * 1. Extract 'kid' from token header
     * 2. Look up key by kid in activeKeys map
     * 3. Verify signature with found key
     * 4. Check expiration, issuer, audience, tenant
     * 5. Return user principal if valid
     *
     * <p>Accepts tokens signed with:
     * - Current key (always)
     * - Previous keys within validation window
     *
     * @param tenantId the tenant identifier (must match token claim)
     * @param token the JWT token string
     * @return Promise of UserPrincipal if valid
     * @throws JwtTokenProvider.JwtValidationException if invalid or expired key
     */
    Promise<UserPrincipal> validateToken(TenantId tenantId, String token);

    /**
     * Rotate signing keys for tenant.
     *
     * <p><b>Rotation Process</b><br>
     * 1. Current key (e.g., v1) stays in activeKeys as "previous"
     * 2. Generate new key (e.g., v2) and set as "current"
     * 3. Update rotation schedule (lastRotation, nextRotation)
     * 4. Record in key history for audit
     * 5. Schedule cleanup of old key after validation window
     *
     * <p>Should be called by scheduled job (e.g., every 90 days).
     *
     * @param tenantId the tenant identifier
     * @return Promise of void when rotation complete
     */
    Promise<Void> rotateKeys(TenantId tenantId);

    /**
     * Get current key rotation status for tenant.
     *
     * <p>Returns rotation schedule, key versions, validation window status.
     *
     * @param tenantId the tenant identifier
     * @return Promise of KeyRotationStatus
     */
    Promise<KeyRotationStatus> getRotationStatus(TenantId tenantId);

    /**
     * Check if token was signed with expired key (outside validation window).
     *
     * <p>Fast check without full validation. Used for audit and debugging.
     *
     * @param tenantId the tenant identifier
     * @param token the JWT token string
     * @return Promise of true if key is expired
     */
    Promise<Boolean> isKeyExpired(TenantId tenantId, String token);

    /**
     * Get current key ID (kid) for tenant.
     *
     * <p>Used for monitoring and audit. Returns version identifier like "v2".
     *
     * @param tenantId the tenant identifier
     * @return Promise of current key ID
     */
    Promise<String> getCurrentKeyId(TenantId tenantId);

    /**
     * Force immediate key rotation (emergency use only).
     *
     * <p><b>Emergency Rotation</b><br>
     * Use when:
     * - Key compromise suspected
     * - Security incident
     * - Immediate revocation needed
     *
     * <p>Unlike scheduled rotation, this:
     * - Rotates immediately (ignores schedule)
     * - May have shorter validation window
     * - Triggers audit alerts
     *
     * @param tenantId the tenant identifier
     * @param reason the reason for emergency rotation (for audit)
     * @return Promise of void when rotation complete
     */
    Promise<Void> emergencyRotation(TenantId tenantId, String reason);
}
