package com.ghatana.auth.service.impl;

import com.ghatana.auth.core.port.JwtTokenProvider;
import com.ghatana.auth.core.port.JwtClaims;
import com.ghatana.auth.exception.JwtValidationException;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.observability.MetricsCollector;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

/**
 * JWT token provider implementation using Nimbus JOSE+JWT library with RS256 signing.
 *
 * <p><b>Purpose</b><br>
 * Implements JWT token generation, validation, revocation, and management for the auth platform.
 * Uses RS256 (RSA + SHA-256) for secure token signing with 2048-bit keys. Supports tenant isolation,
 * token refresh, and revocation tracking.
 *
 * <p><b>Architecture Role</b><br>
 * Service implementation for JwtTokenProvider port interface. Uses Nimbus JOSE+JWT library
 * (canonical JWT library per project standards) for all cryptographic operations.
 * All operations return ActiveJ Promise<T> for non-blocking async execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetricsCollector metrics = metricsFactory.create();
 * JwtTokenProvider provider = new JwtTokenProviderImpl(metrics);
 *
 * // Generate access token
 * UserPrincipal principal = UserPrincipal.builder()
 *     .userId("user-123")
 *     .email("user@example.com")
 *     .name("John Doe")
 *     .roles(Set.of("USER"))
 *     .permissions(Set.of("READ", "WRITE"))
 *     .build();
 *
 * String token = runPromise(() -> provider.generateToken(
 *     TenantId.of("tenant-456"),
 *     principal,
 *     Duration.ofHours(1)
 * ));
 *
 * // Validate token
 * JwtClaims claims = runPromise(() -> provider.validateToken(
 *     TenantId.of("tenant-456"),
 *     token
 * ));
 *
 * // Revoke token
 * boolean wasRevoked = runPromise(() -> provider.revokeToken(
 *     TenantId.of("tenant-456"),
 *     claims.getJwtId()
 * ));
 * }</pre>
 *
 * <p><b>Security Properties</b><br>
 * - RS256 Algorithm: RSA signature with SHA-256 hash (asymmetric, more secure than HS256)
 * - Key Size: 2048-bit RSA key pairs (industry standard)
 * - Token TTL: 1 hour for access tokens, 30 days for refresh tokens
 * - Refresh Tokens: Omit roles/permissions for security (reduced exposure)
 * - Tenant Isolation: All tokens scoped to tenant, validation enforces tenant match
 * - Revocation: In-memory store (production: Redis/database)
 * - Claims: Standard (iss, aud, sub, jti, iat, exp) + custom (tenantId, email, name, roles, permissions)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. RSAKey and signers are immutable. Revocation store uses ConcurrentHashMap.
 * All Promise operations execute in ActiveJ Eventloop context.
 *
 * @see JwtTokenProvider
 * @see JwtClaims
 * @see UserPrincipal
 * @doc.type class
 * @doc.purpose JWT token provider implementation with RS256 signing
 * @doc.layer product
 * @doc.pattern Service Adapter
 */
public class JwtTokenProviderImpl implements JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProviderImpl.class);
    private static final ForkJoinPool CRYPTO_POOL = ForkJoinPool.commonPool();

    private static final String ISSUER = "ghatana-auth-platform";
    private static final String AUDIENCE = "ghatana-api";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";

    private final MetricsCollector metrics;
    private final RSAKey rsaKey;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    // In-memory revocation store (production: Redis/database with TTL)
    // Key: tenantId.toString(), Value: Set of revoked token IDs (jti)
    private final Map<String, Set<String>> revokedTokens = new ConcurrentHashMap<>();

    /**
     * Creates JWT token provider with auto-generated RSA 2048-bit key pair.
     *
     * @param metrics the metrics collector for observability
     * @throws NullPointerException if metrics is null
     * @throws RuntimeException if RSA key generation fails
     */
    public JwtTokenProviderImpl(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");

        try {
            // Generate RSA 2048-bit key pair for RS256 signing
            this.rsaKey = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            this.signer = new RSASSASigner(rsaKey);
            this.verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());

            logger.info("Initialized JwtTokenProviderImpl with RS256 signing (kid: {})",
                    rsaKey.getKeyID());
        } catch (Exception e) {
            logger.error("Failed to initialize JWT provider", e);
            throw new RuntimeException("Failed to initialize JWT provider", e);
        }
    }

    @Override
    public Promise<String> generateToken(TenantId tenantId, UserPrincipal principal, Duration ttl) {
        // Validate input early on the eventloop to avoid throwing from worker threads
        if (tenantId == null) {
            return Promise.ofException(new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("tenantId must not be null"));
        }

        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                String token = buildToken(tenantId, principal, ttl, false);

                metrics.incrementCounter("jwt.token.generated",
                        "tenant", tenantId.toString(),
                        "type", "access");

                logger.debug("Generated access token for user: {} in tenant: {}",
                        principal.getUserId(), tenantId);

                return token;
            } catch (Exception e) {
                metrics.incrementCounter("jwt.token.generation.error",
                        "tenant", tenantId.toString(),
                        "type", "access");
                logger.error("Failed to generate token for tenant: {}", tenantId, e);
                throw new RuntimeException("Token generation failed", e);
            }
        });
    }

    @Override
    public Promise<String> generateRefreshToken(TenantId tenantId, UserPrincipal principal, Duration ttl) {
        // Validate input early on the eventloop to avoid throwing from worker threads
        if (tenantId == null) {
            return Promise.ofException(new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("tenantId must not be null"));
        }

        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                String token = buildToken(tenantId, principal, ttl, true);

                metrics.incrementCounter("jwt.token.generated",
                        "tenant", tenantId.toString(),
                        "type", "refresh");

                logger.debug("Generated refresh token for user: {} in tenant: {}",
                        principal.getUserId(), tenantId);

                return token;
            } catch (Exception e) {
                metrics.incrementCounter("jwt.token.generation.error",
                        "tenant", tenantId.toString(),
                        "type", "refresh");
                logger.error("Failed to generate refresh token for tenant: {}", tenantId, e);
                throw new RuntimeException("Refresh token generation failed", e);
            }
        });
    }

    @Override
    public Promise<JwtClaims> validateToken(TenantId tenantId, String token) {
        // Defensive: validate input early and return a failed Promise if input invalid
        if (tenantId == null) {
            return Promise.ofException(new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("tenantId must not be null"));
        }
        // We'll return an Object[] from the blocking lambda indicating either
        // success ("ok", JwtClaims) or error ("err", Exception). Then, in the
        // eventloop continuation (.then) convert the error into a failed Promise
        // using Promise.ofException(...) to avoid uncaught exceptions on worker threads
        // bubbling up as fatal eventloop errors.
        return Promise.ofBlocking(CRYPTO_POOL, () -> {
            try {
                // Parse and verify signature
                final SignedJWT signedJWT;
                try {
                    signedJWT = SignedJWT.parse(token);
                } catch (ParseException pe) {
                    metrics.incrementCounter("jwt.validation.failed",
                            "tenant", tenantId.toString(),
                            "reason", "malformed");
                    return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Malformed token", pe)};
                }

                if (!signedJWT.verify(verifier)) {
                    metrics.incrementCounter("jwt.validation.failed",
                            "tenant", tenantId.toString(),
                            "reason", "signature_invalid");
                    return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Invalid token signature")};
                }

                // Extract claims
                JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

                // Validate expiration
                Date expirationTime = claimsSet.getExpirationTime();
                if (expirationTime == null || expirationTime.before(new Date())) {
                    metrics.incrementCounter("jwt.validation.failed",
                            "tenant", tenantId.toString(),
                            "reason", "expired");
                    return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Token expired")};
                }

                // Validate issuer
                if (!ISSUER.equals(claimsSet.getIssuer())) {
                    metrics.incrementCounter("jwt.validation.failed",
                            "tenant", tenantId.toString(),
                            "reason", "issuer_mismatch");
                    return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Invalid issuer")};
                }

                // Validate audience
                if (!claimsSet.getAudience().contains(AUDIENCE)) {
                    metrics.incrementCounter("jwt.validation.failed",
                            "tenant", tenantId.toString(),
                            "reason", "audience_mismatch");
                    return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Invalid audience")};
                }

                // Validate tenant
                String tokenTenantId = claimsSet.getStringClaim(CLAIM_TENANT_ID);
                if (!tenantId.toString().equals(tokenTenantId)) {
                    metrics.incrementCounter("jwt.validation.failed",
                            "tenant", tenantId.toString(),
                            "reason", "tenant_mismatch");
                    return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Tenant mismatch")};
                }

                // Check revocation
                String jti = claimsSet.getJWTID();
                if (isTokenRevokedSync(tenantId, jti)) {
                    metrics.incrementCounter("jwt.validation.failed",
                            "tenant", tenantId.toString(),
                            "reason", "revoked");
                    return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Token revoked")};
                }

                // Build JwtClaims
                JwtClaims claims = buildClaims(tenantId, claimsSet);

                metrics.incrementCounter("jwt.validation.success",
                        "tenant", tenantId.toString());

                logger.debug("Validated token for user: {} in tenant: {}",
                        claims.getUserId(), tenantId);

                return new Object[]{"ok", claims};
            } catch (Throwable t) {
                // Any unexpected error - return as err to be converted to failed Promise
                return new Object[]{"err", new com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException("Validation error", t)};
            }
        }).then(resultObj -> {
            Object[] res = (Object[]) resultObj;
            if ("err".equals(res[0])) {
                Throwable t = (Throwable) res[1];
                if (t instanceof Exception) {
                    return Promise.ofException((Exception) t);
                } else {
                    return Promise.ofException(new RuntimeException(t));
                }
            } else {
                return Promise.of((JwtClaims) res[1]);
            }
        });
    }

    @Override
    public Promise<Boolean> revokeToken(TenantId tenantId, String tokenId) {
        // Token revocation is fast (just adding to set), so compute immediately and wrap in Promise
        Set<String> tenantRevokedTokens = revokedTokens.computeIfAbsent(
                tenantId.toString(),
                k -> ConcurrentHashMap.newKeySet()
        );

        boolean wasRevoked = !tenantRevokedTokens.add(tokenId);

        if (!wasRevoked) {
            metrics.incrementCounter("jwt.token.revoked",
                    "tenant", tenantId.toString());
            logger.info("Revoked token: {} for tenant: {}", tokenId, tenantId);
        }

        return Promise.of(!wasRevoked);
    }

    @Override
    public Promise<Integer> revokeAllTokensForUser(TenantId tenantId, String userId) {
        // This is simplified - production would track user's active tokens in database/cache
        logger.info("Revoke all tokens requested for user: {} in tenant: {}",
                userId, tenantId);
        metrics.incrementCounter("jwt.user.tokens.revoked",
                "tenant", tenantId.toString(),
                "user", userId);
        return Promise.of(0); // Placeholder - implement with token tracking
    }

    @Override
    public Promise<Boolean> isTokenRevoked(TenantId tenantId, String tokenId) {
        return Promise.of(isTokenRevokedSync(tenantId, tokenId));
    }

    // Private helper methods

    /**
     * Builds a signed JWT token.
     *
     * @param tenantId the tenant ID
     * @param principal the user principal with claims
     * @param ttl the time-to-live duration
     * @param isRefresh whether this is a refresh token (excludes roles/permissions)
     * @return serialized JWT token string
     * @throws JOSEException if signing fails
     */
    private String buildToken(TenantId tenantId, UserPrincipal principal, Duration ttl, boolean isRefresh)
            throws JOSEException {
        Instant now = Instant.now();
        Instant expiration = now.plus(ttl);

        // Build claims set
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(principal.getUserId())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiration))
                .claim(CLAIM_TENANT_ID, tenantId.toString())
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_NAME, principal.getName());

        // For refresh tokens, exclude roles/permissions (security best practice)
        if (!isRefresh) {
            claimsBuilder
                    .claim(CLAIM_ROLES, new ArrayList<>(principal.getRoles()))
                    .claim(CLAIM_PERMISSIONS, new ArrayList<>(principal.getPermissions()));
        }

        JWTClaimsSet claimsSet = claimsBuilder.build();

        // Build JWT header with RS256 algorithm and key ID
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();

        // Sign JWT
        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    /**
     * Builds JwtClaims from JWT claims set.
     *
     * @param tenantId the tenant ID
     * @param claimsSet the JWT claims set
     * @return JwtClaims value object
     * @throws ParseException if audience parsing fails
     */
    private JwtClaims buildClaims(TenantId tenantId, JWTClaimsSet claimsSet) throws ParseException {
        // Safely get string list claims with null handling
        List<String> rolesList = claimsSet.getStringListClaim(CLAIM_ROLES);
        Set<String> roles = new HashSet<>(rolesList != null ? rolesList : Collections.emptyList());
        
        List<String> permissionsList = claimsSet.getStringListClaim(CLAIM_PERMISSIONS);
        Set<String> permissions = new HashSet<>(permissionsList != null ? permissionsList : Collections.emptyList());
        
        // Audience in JWTClaimsSet is a list, but JwtClaims.Builder expects a single string
        // Use the first audience value or a default
        List<String> audienceList = claimsSet.getAudience();
        String audience = (audienceList != null && !audienceList.isEmpty()) ? audienceList.get(0) : AUDIENCE;
        
        return JwtClaims.builder()
                .tokenId(claimsSet.getJWTID())
                .subject(claimsSet.getSubject())
                .issuer(claimsSet.getIssuer())
                .audience(audience)
                .issuedAt(claimsSet.getIssueTime().toInstant())
                .expiresAt(claimsSet.getExpirationTime().toInstant())
                .tenantId(com.ghatana.platform.domain.auth.TenantId.of(tenantId.value()))
                .email(claimsSet.getStringClaim(CLAIM_EMAIL))
                .name(claimsSet.getStringClaim(CLAIM_NAME))
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    /**
     * Synchronously checks if token is revoked (for internal use in validation).
     *
     * @param tenantId the tenant ID
     * @param tokenId the token ID (jti)
     * @return true if token is revoked, false otherwise
     */
    private boolean isTokenRevokedSync(TenantId tenantId, String tokenId) {
        Set<String> tenantRevokedTokens = revokedTokens.get(tenantId.toString());
        return tenantRevokedTokens != null && tenantRevokedTokens.contains(tokenId);
    }
}
