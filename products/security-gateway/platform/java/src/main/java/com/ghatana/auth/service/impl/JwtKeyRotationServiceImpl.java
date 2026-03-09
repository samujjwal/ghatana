package com.ghatana.auth.service.impl;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ghatana.auth.core.port.JwtKeyRotationService;
import com.ghatana.auth.exception.JwtValidationException;
import com.ghatana.platform.domain.auth.KeyRotationStatus;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.observability.MetricsCollector;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.activej.promise.Promise;

/**
 * JWT key rotation service implementation supporting zero-downtime key
 * rotation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Manages JWT signing key lifecycle including generation, versioning, rotation,
 * and graceful key transitions. Maintains current and recent (within validation
 * window) keys for both token generation and validation without service
 * interruption.
 *
 * <p>
 * <b>Architecture</b><br>
 * - Current key: Used for all new token generation - Previous key(s): Accepted
 * within validation window (default 7 days) - Expired keys: Retained for audit,
 * not used for validation - Each key tracked with keyId, algorithm,
 * creation/expiration times
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * JwtKeyRotationService rotationService = new JwtKeyRotationServiceImpl(
 *     Duration.ofDays(90),  // Rotation interval
 *     Duration.ofDays(7),   // Validation window
 *     metricsCollector
 * );
 *
 * // Generate tokens with automatic key ID tracking
 * String token = rotationService.generateToken(
 *     TenantId.of("tenant-123"),
 *     principal,
 *     Duration.ofHours(1)
 * ).get();
 *
 * // Validate tokens (accepts current + recent keys)
 * UserPrincipal validated = rotationService.validateToken(
 *     TenantId.of("tenant-123"),
 *     token
 * ).get();
 *
 * // Rotate keys (usually scheduled)
 * rotationService.rotateKeys(TenantId.of("tenant-123")).get();
 * }</pre>
 *
 * <p>
 * <b>Key Rotation Flow</b><br>
 * 1. Current key (v1) remains active for signing 2. rotateKeys() called: v1
 * becomes previous, v2 becomes current 3. Both v1 and v2 accepted for
 * validation (within window) 4. After window expires (7d): v1 only for audit,
 * validation uses v2 5. Process repeats every 90 days
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap for key storage. RSAKey is immutable. All
 * time-based operations use Instant for thread-safe comparisons.
 *
 * @see JwtKeyRotationService
 * @see KeyRotationStatus
 * @doc.type class
 * @doc.purpose JWT key rotation implementation with zero-downtime support
 * @doc.layer product
 * @doc.pattern Service
 */
public class JwtKeyRotationServiceImpl implements JwtKeyRotationService {

    private static final Logger logger = LoggerFactory.getLogger(JwtKeyRotationServiceImpl.class);
    private static final ForkJoinPool CRYPTO_POOL = ForkJoinPool.commonPool();

    private static final String ISSUER = "ghatana-auth-platform";
    private static final String AUDIENCE = "ghatana-api";
    private static final int RSA_KEY_SIZE = 2048;

    private final Duration rotationInterval;
    private final Duration validationWindow;
    private final MetricsCollector metrics;

    // Per-tenant key storage: TenantId -> KeyVersions
    private final Map<String, TenantKeyVersions> keysByTenant = new ConcurrentHashMap<>();

    /**
     * Creates JWT key rotation service with specified rotation and validation
     * windows.
     *
     * @param rotationInterval time between key rotations (e.g., 90 days)
     * @param validationWindow time to accept previous keys (e.g., 7 days)
     * @param metrics metrics collector for observability
     * @throws NullPointerException if any parameter is null
     */
    public JwtKeyRotationServiceImpl(Duration rotationInterval, Duration validationWindow, MetricsCollector metrics) {
        this.rotationInterval = Objects.requireNonNull(rotationInterval, "rotationInterval must not be null");
        this.validationWindow = Objects.requireNonNull(validationWindow, "validationWindow must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");

        if (rotationInterval.isZero() || rotationInterval.isNegative()) {
            throw new IllegalArgumentException("rotationInterval must be positive");
        }
        if (validationWindow.isZero() || validationWindow.isNegative()) {
            throw new IllegalArgumentException("validationWindow must be positive");
        }

        logger.info("Initialized JWT key rotation service: rotation={}, window={}",
                rotationInterval.toDays() + "d", validationWindow.toDays() + "d");
    }

    @Override
    public Promise<String> generateToken(TenantId tenantId, UserPrincipal principal, Duration ttl) {
        if (tenantId == null) {
            return Promise.ofException(new JwtValidationException("tenantId must not be null"));
        }
        if (principal == null) {
            return Promise.ofException(new JwtValidationException("principal must not be null"));
        }
        if (ttl == null) {
            return Promise.ofException(new JwtValidationException("ttl must not be null"));
        }

        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                TenantKeyVersions versions = getOrCreateKeyVersions(tenantId);
                JwtKey currentKey = versions.getCurrentKey();

                Instant now = Instant.now();
                Instant expiresAt = now.plus(ttl);

                JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .audience(Collections.singletonList(AUDIENCE))
                        .subject(principal.getUserId())
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(expiresAt))
                        .claim("tenantId", tenantId.value())
                        .claim("email", principal.getEmail())
                        .claim("roles", new ArrayList<>(principal.getRoles()))
                        .claim("permissions", new ArrayList<>(principal.getPermissions()))
                        .build();

                SignedJWT jwt = new SignedJWT(currentKey.getHeader(), claimsSet);
                jwt.sign(currentKey.getSigner());

                String token = jwt.serialize();

                metrics.incrementCounter("jwt.token.generated", "tenant", tenantId.value(), "type", "access");

                return token;
            } catch (Exception e) {
                logger.error("Failed to generate JWT token for tenant {}", tenantId, e);
                metrics.incrementCounter("jwt.token.generation.failed", "tenant", tenantId.value());
                throw new JwtValidationException("Failed to generate JWT token", e);
            }
        });
    }

    @Override
    public Promise<String> generateRefreshToken(TenantId tenantId, UserPrincipal principal, Duration ttl) {
        if (tenantId == null) {
            return Promise.ofException(new JwtValidationException("tenantId must not be null"));
        }
        if (principal == null) {
            return Promise.ofException(new JwtValidationException("principal must not be null"));
        }
        if (ttl == null) {
            return Promise.ofException(new JwtValidationException("ttl must not be null"));
        }

        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                TenantKeyVersions versions = getOrCreateKeyVersions(tenantId);
                JwtKey currentKey = versions.getCurrentKey();

                Instant now = Instant.now();
                Instant expiresAt = now.plus(ttl);

                // Refresh tokens exclude roles/permissions for security
                JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .audience(Collections.singletonList(AUDIENCE))
                        .subject(principal.getUserId())
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(expiresAt))
                        .claim("tenantId", tenantId.value())
                        .claim("email", principal.getEmail())
                        .claim("tokenType", "refresh")
                        .build();

                SignedJWT jwt = new SignedJWT(currentKey.getHeader(), claimsSet);
                jwt.sign(currentKey.getSigner());

                String token = jwt.serialize();

                metrics.incrementCounter("jwt.token.generated", "tenant", tenantId.value(), "type", "refresh");

                return token;
            } catch (Exception e) {
                logger.error("Failed to generate refresh token for tenant {}", tenantId, e);
                metrics.incrementCounter("jwt.token.generation.failed", "tenant", tenantId.value(), "type", "refresh");
                throw new JwtValidationException("Failed to generate refresh token", e);
            }
        });
    }

    @Override
    public Promise<UserPrincipal> validateToken(TenantId tenantId, String token) {
        if (tenantId == null) {
            return Promise.ofException(new JwtValidationException("tenantId must not be null"));
        }
        if (token == null || token.isEmpty()) {
            return Promise.ofException(new JwtValidationException("token must not be empty"));
        }

        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                SignedJWT jwt = SignedJWT.parse(token);

                // Extract key ID from header and validate with appropriate key
                String keyId = jwt.getHeader().getKeyID();
                if (keyId == null) {
                    throw new JwtValidationException("Token missing key ID (kid) header");
                }

                TenantKeyVersions versions = keysByTenant.get(tenantId.value());
                if (versions == null) {
                    throw new JwtValidationException("No keys found for tenant " + tenantId);
                }

                JwtKey jwtKey = versions.getKeyById(keyId);
                if (jwtKey == null) {
                    metrics.incrementCounter("jwt.validation.failed", "tenant", tenantId.value(), "reason", "key_not_found");
                    throw new JwtValidationException("Key not found for key ID: " + keyId);
                }

                if (!jwtKey.isWithinValidationWindow()) {
                    metrics.incrementCounter("jwt.validation.failed", "tenant", tenantId.value(), "reason", "key_expired");
                    throw new JwtValidationException("Key expired for validation: " + keyId);
                }

                // Verify signature
                if (!jwt.verify(jwtKey.getVerifier())) {
                    metrics.incrementCounter("jwt.validation.failed", "tenant", tenantId.value(), "reason", "signature_invalid");
                    throw new JwtValidationException("JWT signature verification failed");
                }

                JWTClaimsSet claims = jwt.getJWTClaimsSet();

                // Validate tenant claim
                String claimTenant = (String) claims.getClaim("tenantId");
                if (!tenantId.value().equals(claimTenant)) {
                    metrics.incrementCounter("jwt.validation.failed", "tenant", tenantId.value(), "reason", "tenant_mismatch");
                    throw new JwtValidationException("Tenant ID mismatch in token");
                }

                // Validate expiration
                if (claims.getExpirationTime().before(new Date())) {
                    metrics.incrementCounter("jwt.validation.failed", "tenant", tenantId.value(), "reason", "token_expired");
                    throw new JwtValidationException("Token has expired");
                }

                // Build UserPrincipal from claims
                @SuppressWarnings("unchecked")
                List<String> rolesList = (List<String>) claims.getClaim("roles");
                @SuppressWarnings("unchecked")
                List<String> permissionsList = (List<String>) claims.getClaim("permissions");

                UserPrincipal principal = UserPrincipal.builder()
                        .userId(claims.getSubject())
                        .email((String) claims.getClaim("email"))
                        .tenantId(tenantId)
                        .roles(rolesList != null ? new HashSet<>(rolesList) : new HashSet<>())
                        .permissions(permissionsList != null ? new HashSet<>(permissionsList) : new HashSet<>())
                        .build();

                metrics.incrementCounter("jwt.validation.succeeded", "tenant", tenantId.value());

                return principal;
            } catch (ParseException e) {
                logger.debug("Failed to parse JWT token", e);
                metrics.incrementCounter("jwt.validation.failed", "tenant", tenantId.value(), "reason", "parse_error");
                throw new JwtValidationException("Failed to parse JWT token", e);
            }
        });
    }

    @Override
    public Promise<Void> rotateKeys(TenantId tenantId) {
        if (tenantId == null) {
            return Promise.ofException(new JwtValidationException("tenantId must not be null"));
        }

        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                TenantKeyVersions versions = getOrCreateKeyVersions(tenantId);
                versions.rotate(rotationInterval, validationWindow);

                logger.info("Rotated JWT keys for tenant {}", tenantId);
                metrics.incrementCounter("jwt.key.rotated", "tenant", tenantId.value());

                return null;
            } catch (Exception e) {
                logger.error("Failed to rotate JWT keys for tenant {}", tenantId, e);
                metrics.incrementCounter("jwt.key.rotation.failed", "tenant", tenantId.value());
                throw new JwtValidationException("Failed to rotate JWT keys", e);
            }
        });
    }

    @Override
    public Promise<KeyRotationStatus> getRotationStatus(TenantId tenantId) {
        if (tenantId == null) {
            return Promise.ofException(new JwtValidationException("tenantId must not be null"));
        }

        return Promise.of((Void) null).then(unused -> {
            TenantKeyVersions versions = keysByTenant.get(tenantId.value());
            if (versions == null) {
                throw new JwtValidationException("No keys found for tenant " + tenantId);
            }

            return Promise.of(versions.getRotationStatus(rotationInterval, validationWindow));
        });
    }

    @Override
    public Promise<Boolean> isKeyExpired(TenantId tenantId, String token) {
        if (tenantId == null || token == null) {
            return Promise.ofException(new JwtValidationException("tenantId and token must not be null"));
        }

        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                SignedJWT jwt = SignedJWT.parse(token);
                String keyId = jwt.getHeader().getKeyID();

                TenantKeyVersions versions = keysByTenant.get(tenantId.value());
                if (versions == null) {
                    return true; // No keys = key expired
                }

                JwtKey jwtKey = versions.getKeyById(keyId);
                return jwtKey == null || !jwtKey.isWithinValidationWindow();
            } catch (ParseException e) {
                return true; // Parse error = key expired
            }
        });
    }

    @Override
    public Promise<String> getCurrentKeyId(TenantId tenantId) {
        if (tenantId == null) {
            return Promise.ofException(new JwtValidationException("tenantId must not be null"));
        }

        return Promise.of((Void) null).then(unused -> {
            TenantKeyVersions versions = keysByTenant.get(tenantId.value());
            if (versions == null) {
                throw new JwtValidationException("No keys found for tenant " + tenantId);
            }
            return Promise.of(versions.getCurrentKey().getKeyId());
        });
    }

    @Override
    public Promise<Void> emergencyRotation(TenantId tenantId, String reason) {
        if (tenantId == null || reason == null) {
            return Promise.ofException(new JwtValidationException("tenantId and reason must not be null"));
        }

        logger.warn("Emergency key rotation requested for tenant {}: {}", tenantId, reason);
        metrics.incrementCounter("jwt.key.emergency_rotation", "tenant", tenantId.value(), "reason", reason);

        // Force immediate rotation (same as regular rotation for now)
        return rotateKeys(tenantId);
    }

    private TenantKeyVersions getOrCreateKeyVersions(TenantId tenantId) throws Exception {
        return keysByTenant.computeIfAbsent(tenantId.value(), k -> {
            try {
                return new TenantKeyVersions();
            } catch (Exception e) {
                logger.error("Failed to create initial key for tenant {}", tenantId, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Inner class managing all key versions for a tenant.
     */
    private static class TenantKeyVersions {

        private final Map<String, JwtKey> keyVersions = new ConcurrentHashMap<>();
        private volatile String currentKeyId;
        private volatile String previousKeyId;
        private volatile Instant lastRotationTime;

        TenantKeyVersions() throws Exception {
            // Generate initial key
            JwtKey initialKey = new JwtKey();
            keyVersions.put(initialKey.getKeyId(), initialKey);
            currentKeyId = initialKey.getKeyId();
            previousKeyId = null;
            lastRotationTime = Instant.now();
        }

        JwtKey getCurrentKey() {
            return keyVersions.get(currentKeyId);
        }

        JwtKey getKeyById(String keyId) {
            return keyVersions.get(keyId);
        }

        void rotate(Duration rotationInterval, Duration validationWindow) throws Exception {
            JwtKey newKey = new JwtKey();
            keyVersions.put(newKey.getKeyId(), newKey);
            previousKeyId = currentKeyId;
            currentKeyId = newKey.getKeyId();
            lastRotationTime = Instant.now();

            // Schedule cleanup of expired keys
            scheduleKeyCleanup(validationWindow);
        }

        private void scheduleKeyCleanup(Duration validationWindow) {
            // Cleanup old keys after validation window
            Instant now = Instant.now();
            keyVersions.forEach((keyId, key) -> {
                if (!keyId.equals(currentKeyId) && !keyId.equals(previousKeyId)) {
                    if (key.getCreatedAt().plus(validationWindow).isBefore(now)) {
                        keyVersions.remove(keyId);
                    }
                }
            });
        }

        KeyRotationStatus getRotationStatus(Duration rotationInterval, Duration validationWindow) {
            JwtKey current = getCurrentKey();
            Instant nextRotation = lastRotationTime.plus(rotationInterval);
            Instant validationEnd = lastRotationTime.plus(validationWindow);
            Instant now = Instant.now();

            long daysUntilRotation = Duration.between(now, nextRotation).toDays();
            long daysInWindow = Duration.between(lastRotationTime, validationEnd).toDays();
            boolean withinWindow = now.isBefore(validationEnd);

            return KeyRotationStatus.builder()
                    .currentKeyId(currentKeyId)
                    .previousKeyId(previousKeyId)
                    .lastRotationTime(lastRotationTime)
                    .rotationInterval(rotationInterval)
                    .validationWindow(validationWindow)
                    .build();
        }
    }

    /**
     * Inner class representing a single JWT key version.
     */
    private static class JwtKey {

        private final String keyId;
        private final RSAKey rsaKey;
        private final JWSSigner signer;
        private final JWSVerifier verifier;
        private final com.nimbusds.jose.JWSHeader header;
        private final Instant createdAt;

        JwtKey() throws Exception {
            this.keyId = "v" + System.currentTimeMillis();
            this.rsaKey = new RSAKeyGenerator(RSA_KEY_SIZE)
                    .keyID(keyId)
                    .generate();
            this.signer = new RSASSASigner(rsaKey);
            this.verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            this.header = new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build();
            this.createdAt = Instant.now();
        }

        String getKeyId() {
            return keyId;
        }

        com.nimbusds.jose.JWSHeader getHeader() {
            return header;
        }

        JWSSigner getSigner() {
            return signer;
        }

        JWSVerifier getVerifier() {
            return verifier;
        }

        Instant getCreatedAt() {
            return createdAt;
        }

        boolean isWithinValidationWindow(Duration window) {
            return Instant.now().isBefore(createdAt.plus(window).plusSeconds(86400)); // Add 1 day grace period
        }

        boolean isWithinValidationWindow() {
            return isWithinValidationWindow(Duration.ofDays(7)); // Default 7-day window
        }
    }
}
