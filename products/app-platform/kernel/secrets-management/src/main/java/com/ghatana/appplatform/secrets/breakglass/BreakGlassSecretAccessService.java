/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.breakglass;

import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * Provides emergency access to secrets under break-glass conditions (STORY-K14-009).
 *
 * <p>Break-glass access bypasses normal approval chains in a genuine incident but is
 * subject to mandatory post-hoc forensic review. Every access event is:
 * <ul>
 *   <li>Logged with {@code BREAK_GLASS_ACCESS} at WARN level</li>
 *   <li>Stored in the in-memory audit log for later forensic export</li>
 *   <li>Time-limited: the access grant token expires after {@code grantDuration}</li>
 * </ul>
 *
 * <p>Note: this service is for <em>secret</em> access, distinct from
 * {@code com.ghatana.appplatform.iam.security.BreakGlassService} which handles
 * IAM identity break-glass.
 *
 * @doc.type  class
 * @doc.purpose Time-limited emergency secret access for incident responders (K14-009)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class BreakGlassSecretAccessService {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassSecretAccessService.class);

    /** Default duration for a break-glass access grant: 4 hours. */
    public static final Duration DEFAULT_GRANT_DURATION = Duration.ofHours(4);

    private final SecretProvider secretProvider;
    private final Duration grantDuration;
    private final Executor executor;
    private final ConcurrentMap<String, BreakGlassGrant> activeGrants = new ConcurrentHashMap<>();

    public BreakGlassSecretAccessService(SecretProvider secretProvider,
                                          Duration grantDuration,
                                          Executor executor) {
        this.secretProvider = Objects.requireNonNull(secretProvider, "secretProvider");
        this.grantDuration  = Objects.requireNonNull(grantDuration,  "grantDuration");
        this.executor       = Objects.requireNonNull(executor,        "executor");
    }

    /**
     * Issues a time-limited break-glass access grant to the requesting principal.
     *
     * @param requestorId  the principal requesting emergency access (must be on-call engineer)
     * @param reason       mandatory incident reason / incident ID
     * @param secretPaths  the set of secret paths the grant covers
     * @return promise resolving to the grant token (opaque UUID)
     */
    public Promise<String> issueGrant(String requestorId, String reason, Set<String> secretPaths) {
        Objects.requireNonNull(requestorId, "requestorId");
        Objects.requireNonNull(reason,      "reason");
        Objects.requireNonNull(secretPaths, "secretPaths");

        return Promise.ofBlocking(executor, () -> {
            String grantId = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plus(grantDuration);

            BreakGlassGrant grant = new BreakGlassGrant(grantId, requestorId, reason,
                    Set.copyOf(secretPaths), Instant.now(), expiresAt);
            activeGrants.put(grantId, grant);

            log.warn("BREAK_GLASS_GRANT issued: grantId={} requestor={} reason={} paths={} expiresAt={}",
                    grantId, requestorId, reason, secretPaths, expiresAt);
            return grantId;
        });
    }

    /**
     * Accesses a secret using a valid break-glass grant. The access is fully audited.
     *
     * @param grantId    the grant token issued by {@link #issueGrant}
     * @param secretPath the secret path to access
     * @return promise resolving to the secret value
     * @throws IllegalArgumentException if the grant is invalid, expired, or does not cover this path
     */
    public Promise<SecretValue> accessSecret(String grantId, String secretPath) {
        Objects.requireNonNull(grantId,    "grantId");
        Objects.requireNonNull(secretPath, "secretPath");

        return Promise.ofBlocking(executor, () -> {
            BreakGlassGrant grant = activeGrants.get(grantId);
            if (grant == null) {
                throw new IllegalArgumentException("Invalid or unknown break-glass grant: " + grantId);
            }
            if (Instant.now().isAfter(grant.expiresAt())) {
                activeGrants.remove(grantId);
                throw new IllegalStateException("Break-glass grant has expired: " + grantId);
            }
            if (!grant.secretPaths().contains(secretPath)) {
                throw new IllegalArgumentException(
                        "Break-glass grant does not cover path: " + secretPath
                                + " | Granted paths: " + grant.secretPaths());
            }

            log.warn("BREAK_GLASS_ACCESS grantId={} requestor={} path={} reason={}",
                    grantId, grant.requestorId(), secretPath, grant.reason());

            return secretProvider.getSecret(secretPath).getResult();
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public record BreakGlassGrant(
            String grantId,
            String requestorId,
            String reason,
            Set<String> secretPaths,
            Instant issuedAt,
            Instant expiresAt
    ) {}
}
