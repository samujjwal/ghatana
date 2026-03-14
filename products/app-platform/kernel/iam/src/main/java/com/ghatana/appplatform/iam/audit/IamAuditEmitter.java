/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.audit;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.domain.AuditEntry.Actor;
import com.ghatana.appplatform.audit.domain.AuditEntry.Outcome;
import com.ghatana.appplatform.audit.domain.AuditEntry.Resource;
import com.ghatana.appplatform.audit.port.AuditTrailStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Domain-specific audit façade for IAM events (STORY-K01-017).
 *
 * <p>Wraps the shared {@link AuditTrailStore} (K-07) and exposes named methods for each
 * IAM concern so callers never hand-craft {@link AuditEntry} objects inline.
 *
 * <h3>Event actions</h3>
 * <ul>
 *   <li>{@code iam.login} — principal authenticated (success or failure)</li>
 *   <li>{@code iam.token.refresh} — access token refreshed via refresh token</li>
 *   <li>{@code iam.token.revoke} — refresh-token family revoked</li>
 *   <li>{@code iam.mfa.enroll} — MFA TOTP enrollment completed</li>
 *   <li>{@code iam.mfa.verify} — MFA step-up verification</li>
 *   <li>{@code iam.role.change} — role assigned or removed from a principal</li>
 *   <li>{@code iam.session.revoke} — session explicitly invalidated</li>
 *   <li>{@code iam.account.lock} — account locked due to brute-force</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose IAM audit event emitter (K01-017, integrates K-07 AuditTrailStore)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class IamAuditEmitter {

    private static final Logger log = LoggerFactory.getLogger(IamAuditEmitter.class);

    private static final String RESOURCE_TYPE_PRINCIPAL = "iam.principal";
    private static final String RESOURCE_TYPE_SESSION    = "iam.session";

    private final AuditTrailStore store;

    /**
     * @param store downstream cryptographic audit store (K-07)
     */
    public IamAuditEmitter(AuditTrailStore store) {
        this.store = store;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Login
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records a login attempt result.
     *
     * @param principalId  authenticating user or client
     * @param tenantId     tenant scope
     * @param sourceIp     originating IP address
     * @param success      whether authentication succeeded
     * @param mechanism    grant type — e.g., {@code "client_credentials"}, {@code "authorization_code"}
     * @return async completion (fire-and-forget safe to ignore)
     */
    public Promise<Void> onLogin(String principalId, String tenantId, String sourceIp,
                                  boolean success, String mechanism) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.login")
            .actor(new Actor(principalId, "principal", sourceIp, null))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, principalId))
            .details(Map.of("mechanism", mechanism))
            .outcome(success ? Outcome.SUCCESS : Outcome.FAILURE)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Token lifecycle
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records an access token refresh via a refresh token.
     *
     * @param principalId  token owner
     * @param tenantId     tenant scope
     * @param familyId     refresh token family identifier
     * @return async completion
     */
    public Promise<Void> onTokenRefresh(String principalId, String tenantId, String familyId) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.token.refresh")
            .actor(Actor.of(principalId, "principal"))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, principalId))
            .details(Map.of("family_id", familyId))
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    /**
     * Records revocation of a refresh-token family (e.g., on compromise detection).
     *
     * @param principalId  token owner
     * @param tenantId     tenant scope
     * @param familyId     family that was revoked
     * @param reason       human-readable reason code (e.g., {@code "compromise_detected"})
     * @return async completion
     */
    public Promise<Void> onTokenFamilyRevoked(String principalId, String tenantId,
                                               String familyId, String reason) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.token.revoke")
            .actor(Actor.of(principalId, "principal"))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, principalId))
            .details(Map.of("family_id", familyId, "reason", reason))
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // MFA
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records a completed MFA enrollment.
     *
     * @param principalId  enrolling user
     * @param tenantId     tenant scope
     * @param method       MFA method — e.g., {@code "totp"}
     * @return async completion
     */
    public Promise<Void> onMfaEnroll(String principalId, String tenantId, String method) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.mfa.enroll")
            .actor(Actor.of(principalId, "principal"))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, principalId))
            .details(Map.of("method", method))
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    /**
     * Records an MFA verification attempt.
     *
     * @param principalId  verifying user
     * @param tenantId     tenant scope
     * @param method       MFA method — e.g., {@code "totp"}, {@code "backup_code"}
     * @param success      whether the code was accepted
     * @return async completion
     */
    public Promise<Void> onMfaVerify(String principalId, String tenantId,
                                      String method, boolean success) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.mfa.verify")
            .actor(Actor.of(principalId, "principal"))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, principalId))
            .details(Map.of("method", method))
            .outcome(success ? Outcome.SUCCESS : Outcome.FAILURE)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // RBAC
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records a role assignment or removal.
     *
     * @param adminId      admin performing the change
     * @param targetId     principal whose role changed
     * @param tenantId     tenant scope
     * @param roleName     role name
     * @param assigned     {@code true} = assigned; {@code false} = removed
     * @return async completion
     */
    public Promise<Void> onRoleChange(String adminId, String targetId, String tenantId,
                                       String roleName, boolean assigned) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.role.change")
            .actor(Actor.of(adminId, "admin"))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, targetId))
            .details(Map.of("role", roleName, "operation", assigned ? "assign" : "remove"))
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Session
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records an explicit session invalidation (logout or admin revocation).
     *
     * @param principalId  session owner
     * @param tenantId     tenant scope
     * @param sessionId    session being revoked
     * @param reason       e.g., {@code "logout"}, {@code "admin_revoke"}, {@code "tenant_mismatch"}
     * @return async completion
     */
    public Promise<Void> onSessionRevoke(String principalId, String tenantId,
                                          String sessionId, String reason) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.session.revoke")
            .actor(Actor.of(principalId, "principal"))
            .resource(Resource.of(RESOURCE_TYPE_SESSION, sessionId))
            .details(Map.of("reason", reason))
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Brute-force / lockout
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records an account lockout event triggered by brute-force detection.
     *
     * @param principalId  locked account
     * @param tenantId     tenant scope
     * @param sourceIp     originating IP address that triggered the lockout
     * @return async completion
     */
    public Promise<Void> onAccountLocked(String principalId, String tenantId, String sourceIp) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.account.lock")
            .actor(new Actor(principalId, "principal", sourceIp, null))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, principalId))
            .details(Map.of("source_ip", sourceIp))
            .outcome(Outcome.FAILURE)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Break-glass (K01-012)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records a break-glass elevation activation.
     *
     * @param elevatedBy   admin who granted elevation
     * @param targetUserId user being elevated
     * @param tenantId     tenant scope
     * @param reason       business justification provided
     * @param sessionId    elevation session identifier
     * @return async completion
     */
    public Promise<Void> onBreakGlassActivated(String elevatedBy, String targetUserId,
                                                String tenantId, String reason, String sessionId) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.breakglass.activate")
            .actor(Actor.of(elevatedBy, "admin"))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, targetUserId))
            .details(Map.of("reason", reason, "session_id", sessionId))
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .build();
        log.warn("[audit] break-glass activated: elevatedBy={} target={} session={}",
                elevatedBy, targetUserId, sessionId);
        return store.log(entry).map(_ -> null);
    }

    /**
     * Records a break-glass elevation revocation.
     *
     * @param revokedBy    actor revoking the elevation
     * @param targetUserId user whose elevation is revoked
     * @param tenantId     tenant scope
     * @return async completion
     */
    public Promise<Void> onBreakGlassRevoked(String revokedBy, String targetUserId,
                                              String tenantId) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.breakglass.revoke")
            .actor(Actor.of(revokedBy, "admin"))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, targetUserId))
            .details(Map.of())
            .outcome(Outcome.SUCCESS)
            .tenantId(tenantId)
            .build();
        return store.log(entry).map(_ -> null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Security anomaly (K01-023)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records a detected login anomaly (new device, impossible travel, unusual time).
     *
     * @param principalId  user account flagged
     * @param tenantId     tenant scope
     * @param sourceIp     IP where the suspicious login occurred
     * @param anomalyType  e.g., {@code "new_device"}, {@code "impossible_travel"}, {@code "unusual_time"}
     * @param action       e.g., {@code "alert"} or {@code "block"}
     * @return async completion
     */
    public Promise<Void> onLoginAnomaly(String principalId, String tenantId,
                                         String sourceIp, String anomalyType, String action) {
        AuditEntry entry = AuditEntry.builder()
            .action("iam.login.anomaly")
            .actor(new Actor(principalId, "principal", sourceIp, null))
            .resource(Resource.of(RESOURCE_TYPE_PRINCIPAL, principalId))
            .details(Map.of("anomaly_type", anomalyType, "action_taken", action))
            .outcome(Outcome.FAILURE)
            .tenantId(tenantId)
            .build();
        log.warn("[security] login anomaly: userId={} type={} ip={} action={}",
                principalId, anomalyType, sourceIp, action);
        return store.log(entry).map(_ -> null);
    }
}
