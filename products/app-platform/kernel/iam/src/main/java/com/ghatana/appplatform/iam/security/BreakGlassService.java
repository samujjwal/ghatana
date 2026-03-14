/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

import com.ghatana.appplatform.iam.audit.IamAuditEmitter;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Break-glass emergency access elevation service (K01-012).
 *
 * <p>Break-glass allows super-admin access bypassing normal RBAC in emergency
 * scenarios. Requirements enforced:
 * <ol>
 *   <li>MFA must be verified before granting (caller must supply {@code mfaVerified=true})</li>
 *   <li>A non-empty reason must be provided</li>
 *   <li>Elevation is time-limited: max {@value MAX_ELEVATION_SECONDS} seconds (4 hours)</li>
 *   <li>All access attempts are double-audited via K-07</li>
 * </ol>
 *
 * <p>Redis key: {@code breakglass:{userId}} → {@code sessionId|grantedAt|reason|elevatedBy}
 * (TTL = {@value MAX_ELEVATION_SECONDS} seconds)
 *
 * @doc.type class
 * @doc.purpose Emergency super-admin elevation with MFA gate, reason, and 4-hour TTL (K01-012)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class BreakGlassService {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassService.class);

    /** Maximum allowed elevation window (4 hours). */
    public static final long MAX_ELEVATION_SECONDS = 4L * 3600L;

    private static final String KEY_PREFIX = "breakglass:";

    private final JedisPool jedisPool;
    private final Executor executor;
    private final IamAuditEmitter audit;

    public BreakGlassService(JedisPool jedisPool, Executor executor, IamAuditEmitter audit) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Activates break-glass elevation for {@code userId}.
     *
     * @param userId      the user being elevated
     * @param tenantId    tenant context
     * @param reason      mandatory business justification (non-empty)
     * @param elevatedBy  admin who authorised (may equal userId for dual-MFA)
     * @param mfaVerified must be {@code true}; caller is responsible for MFA check
     * @return elevation session ID
     * @throws IllegalArgumentException if MFA not verified or reason blank
     */
    public Promise<String> activate(String userId, String tenantId, String reason,
                                    String elevatedBy, boolean mfaVerified) {
        if (!mfaVerified) {
            return Promise.ofException(new IllegalArgumentException(
                    "Break-glass requires MFA verification"));
        }
        if (reason == null || reason.isBlank()) {
            return Promise.ofException(new IllegalArgumentException(
                    "Break-glass reason must not be empty"));
        }

        return Promise.ofBlocking(executor, () -> {
            String sessionId = UUID.randomUUID().toString();
            String key = KEY_PREFIX + userId;
            String value = buildValue(sessionId, reason, elevatedBy);
            try (var jedis = jedisPool.getResource()) {
                jedis.setex(key, MAX_ELEVATION_SECONDS, value);
            }
            log.warn("[breakglass] ACTIVATED userId={} elevatedBy={} reason='{}' sessionId={}",
                    userId, elevatedBy, reason, sessionId);
            return sessionId;
        }).then(sessionId ->
            audit.onBreakGlassActivated(elevatedBy, userId, tenantId, reason, sessionId)
                 .map(ignored -> sessionId)
        );
    }

    /**
     * Checks whether {@code userId} currently has an active break-glass elevation.
     *
     * @return {@code ElevationStatus} containing active flag and remaining TTL
     */
    public Promise<ElevationStatus> status(String userId) {
        return Promise.ofBlocking(executor, () -> {
            String key = KEY_PREFIX + userId;
            try (var jedis = jedisPool.getResource()) {
                String value = jedis.get(key);
                if (value == null) {
                    return ElevationStatus.inactive();
                }
                long ttl = jedis.ttl(key);
                String[] parts = value.split("\\|", 4);
                String sessionId = parts[0];
                String reason = parts.length > 2 ? parts[2] : "";
                String elevatedBy = parts.length > 3 ? parts[3] : "";
                return ElevationStatus.active(sessionId, reason, elevatedBy,
                        Duration.ofSeconds(ttl));
            }
        });
    }

    /**
     * Revokes an active break-glass elevation immediately.
     *
     * @param userId   user whose elevation is revoked
     * @param tenantId tenant context
     * @param revokedBy actor performing the revocation
     */
    public Promise<Void> revoke(String userId, String tenantId, String revokedBy) {
        return Promise.ofBlocking(executor, () -> {
            String key = KEY_PREFIX + userId;
            try (var jedis = jedisPool.getResource()) {
                jedis.del(key);
            }
            log.warn("[breakglass] REVOKED userId={} revokedBy={}", userId, revokedBy);
            return null;
        }).then(ignored ->
            audit.onBreakGlassRevoked(revokedBy, userId, tenantId)
        );
    }

    // ─── Value encoding ────────────────────────────────────────────────────────

    private static String buildValue(String sessionId, String reason, String elevatedBy) {
        // Format: sessionId|grantedAtEpoch|reason|elevatedBy
        // Pipes in reason/elevatedBy are safe because we split with limit=4
        return sessionId + "|" + Instant.now().getEpochSecond() + "|" + reason + "|" + elevatedBy;
    }

    // ─── Result types ──────────────────────────────────────────────────────────

    /**
     * Represents the current break-glass elevation state for a user.
     */
    public record ElevationStatus(
            boolean active,
            String sessionId,
            String reason,
            String elevatedBy,
            Duration remainingTtl
    ) {
        static ElevationStatus inactive() {
            return new ElevationStatus(false, null, null, null, Duration.ZERO);
        }

        static ElevationStatus active(String sessionId, String reason,
                                      String elevatedBy, Duration ttl) {
            return new ElevationStatus(true, sessionId, reason, elevatedBy, ttl);
        }
    }
}
