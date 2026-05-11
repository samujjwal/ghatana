/**
 * Preview Session Enforcement Service Implementation
 * 
 * Production-grade implementation of preview session enforcement service.
 * Enforces preview sessions and trust policies.
 * 
 * @doc.type class
 * @doc.purpose Preview session enforcement implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import com.ghatana.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade implementation of preview session enforcement service.
 * Uses in-memory storage for demonstration; should be replaced with database persistence.
 */
public final class PreviewSessionEnforcementServiceImpl implements PreviewSessionEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(PreviewSessionEnforcementServiceImpl.class);
    private final AuditLogger auditLogger;

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    
    // Actions allowed at each trust level
    private static final Set<String> TRUSTED_LOCAL_ACTIONS = Set.of(
            "view", "navigate", "inspect", "export", "download", "share", "modify", "execute"
    );
    private static final Set<String> TRUSTED_CONTROLLED_ACTIONS = Set.of(
            "view", "navigate", "inspect", "export", "download"
    );
    private static final Set<String> SEMI_TRUSTED_ACTIONS = Set.of(
            "view", "navigate", "inspect"
    );
    private static final Set<String> UNTRUSTED_ACTIONS = Set.of(
            "view"
    );

    // In-memory storage for demonstration - replace with distributed cache in production
    private final Map<String, PreviewSession> sessions = new ConcurrentHashMap<>();

    public PreviewSessionEnforcementServiceImpl(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public String createPreviewSession(String projectId, String importJobId, String userId, Map<String, Object> metadata) {
        return createPreviewSession(projectId, importJobId, userId, TrustLevel.SEMI_TRUSTED, metadata);
    }

    /**
     * Creates a preview session with a specified trust level.
     * 
     * @param projectId The project ID
     * @param importJobId The import job ID
     * @param userId The user ID
     * @param trustLevel The trust level for the session
     * @param metadata Audit metadata for session creation
     * @return Preview session ID
     */
    public String createPreviewSession(String projectId, String importJobId, String userId, TrustLevel trustLevel, Map<String, Object> metadata) {
        String sessionId = "preview-" + java.util.UUID.randomUUID().toString();

        log.info("Creating preview session: sessionId={}, projectId={}, userId={}, trustLevel={}", 
                sessionId, projectId, userId, trustLevel);

        PreviewSession session = new PreviewSession(
                sessionId,
                projectId,
                importJobId,
                userId,
                trustLevel,
                PreviewSessionStatus.ACTIVE,
                Instant.now(),
                Instant.now().plusMillis(SESSION_TIMEOUT_MS),
                userId
        );

        sessions.put(sessionId, session);

        // Log audit event for session creation
        Map<String, Object> auditEvent = new java.util.HashMap<>();
        auditEvent.put("type", "preview.session.create");
        auditEvent.put("outcome", "succeeded");
        auditEvent.put("actor", userId);
        auditEvent.put("sessionId", sessionId);
        auditEvent.put("projectId", projectId);
        auditEvent.put("importJobId", importJobId);
        auditEvent.put("trustLevel", trustLevel.name());
        if (metadata != null) {
            auditEvent.put("metadata", metadata);
        }
        auditLogger.log(auditEvent);

        log.info("Preview session created successfully: sessionId={}, trustLevel={}", sessionId, trustLevel);
        return sessionId;
    }

    @Override
    public boolean validateSession(String sessionId) {
        PreviewSession session = sessions.get(sessionId);

        if (session == null) {
            log.warn("Preview session not found: sessionId={}", sessionId);
            return false;
        }

        if (session.status() != PreviewSessionStatus.ACTIVE) {
            log.warn("Preview session is not active: sessionId={}, status={}", sessionId, session.status());
            return false;
        }

        if (Instant.now().isAfter(session.expiresAt())) {
            log.warn("Preview session has expired: sessionId={}", sessionId);
            
            // Update session status to expired
            PreviewSession expiredSession = new PreviewSession(
                    session.sessionId(),
                    session.projectId(),
                    session.importJobId(),
                    session.userId(),
                    session.trustLevel(),
                    PreviewSessionStatus.EXPIRED,
                    session.createdAt(),
                    session.expiresAt(),
                    "Session expired due to timeout"
            );
            sessions.put(sessionId, expiredSession);
            
            // Log audit event for session expiry
            Map<String, Object> auditEvent = new java.util.HashMap<>();
            auditEvent.put("type", "preview.session.expired");
            auditEvent.put("outcome", "succeeded");
            auditEvent.put("actor", "system");
            auditEvent.put("sessionId", sessionId);
            auditEvent.put("projectId", session.projectId());
            auditEvent.put("importJobId", session.importJobId());
            auditEvent.put("trustLevel", session.trustLevel().name());
            auditEvent.put("expiredAt", session.expiresAt().toString());
            auditLogger.log(auditEvent);
            
            return false;
        }

        return true;
    }

    @Override
    public boolean enforceTrustPolicy(String sessionId, String action) {
        PreviewSession session = sessions.get(sessionId);

        if (session == null) {
            log.warn("Preview session not found: sessionId={}", sessionId);
            return false;
        }

        if (!validateSession(sessionId)) {
            return false;
        }

        TrustLevel trustLevel = session.trustLevel();
        Set<String> allowedActions = getAllowedActionsForTrustLevel(trustLevel);

        if (allowedActions.contains(action)) {
            log.debug("Action allowed by trust policy: sessionId={}, action={}, trustLevel={}", 
                    sessionId, action, trustLevel);
            return true;
        }

        log.warn("Action not allowed due to trust policy: sessionId={}, action={}, trustLevel={}", 
                sessionId, action, trustLevel);
        return false;
    }

    /**
     * Gets the set of allowed actions for a given trust level.
     * 
     * @param trustLevel the trust level
     * @return set of allowed actions
     */
    private Set<String> getAllowedActionsForTrustLevel(TrustLevel trustLevel) {
        return switch (trustLevel) {
            case TRUSTED_LOCAL -> TRUSTED_LOCAL_ACTIONS;
            case TRUSTED_CONTROLLED -> TRUSTED_CONTROLLED_ACTIONS;
            case SEMI_TRUSTED -> SEMI_TRUSTED_ACTIONS;
            case UNTRUSTED -> UNTRUSTED_ACTIONS;
        };
    }

    @Override
    public void blockIfNotAllowed(String sessionId, String action) {
        if (!enforceTrustPolicy(sessionId, action)) {
            String message = String.format("Action '%s' is not allowed for preview session '%s'", action, sessionId);
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    @Override
    public void revokeSession(String sessionId, String reason, Map<String, Object> metadata) {
        log.info("Revoking preview session: sessionId={}, reason={}", sessionId, reason);

        PreviewSession session = sessions.get(sessionId);

        if (session == null) {
            log.warn("Preview session not found for revocation: sessionId={}", sessionId);
            return;
        }

        PreviewSession revokedSession = new PreviewSession(
                session.sessionId(),
                session.projectId(),
                session.importJobId(),
                session.userId(),
                session.trustLevel(),
                PreviewSessionStatus.REVOKED,
                session.createdAt(),
                session.expiresAt(),
                reason
        );

        sessions.put(sessionId, revokedSession);

        // Log audit event for session revocation
        Map<String, Object> auditEvent = new java.util.HashMap<>();
        auditEvent.put("type", "preview.session.revoke");
        auditEvent.put("outcome", "succeeded");
        auditEvent.put("actor", session.userId());
        auditEvent.put("sessionId", sessionId);
        auditEvent.put("projectId", session.projectId());
        auditEvent.put("importJobId", session.importJobId());
        auditEvent.put("reason", reason);
        auditEvent.put("trustLevel", session.trustLevel().name());
        if (metadata != null) {
            auditEvent.put("metadata", metadata);
        }
        auditLogger.log(auditEvent);

        log.info("Preview session revoked successfully: sessionId={}", sessionId);
    }

    @Override
    public TrustLevel getTrustLevel(String sessionId) {
        PreviewSession session = sessions.get(sessionId);

        if (session == null) {
            log.warn("Preview session not found: sessionId={}, returning SEMI_TRUSTED as default", sessionId);
            return TrustLevel.SEMI_TRUSTED;
        }

        return session.trustLevel();
    }

    /**
     * Sets the trust level for a preview session.
     * 
     * @param sessionId The preview session ID
     * @param trustLevel The trust level to set
     */
    public void setTrustLevel(String sessionId, TrustLevel trustLevel) {
        log.info("Setting trust level: sessionId={}, trustLevel={}", sessionId, trustLevel);

        PreviewSession session = sessions.get(sessionId);

        if (session == null) {
            log.warn("Preview session not found: sessionId={}", sessionId);
            return;
        }

        PreviewSession updatedSession = new PreviewSession(
                session.sessionId(),
                session.projectId(),
                session.importJobId(),
                session.userId(),
                trustLevel,
                session.status(),
                session.createdAt(),
                session.expiresAt(),
                session.revocationReason()
        );

        sessions.put(sessionId, updatedSession);

        log.info("Trust level set successfully: sessionId={}, trustLevel={}", sessionId, trustLevel);
    }

    /**
     * Determines the appropriate trust level based on artifact source and validation.
     * 
     * @param isLocalArtifact true if artifact was generated locally in the workspace
     * @param isFromControlledSource true if artifact is from a controlled source
     * @param isValidationPassed true if artifact passed security validation
     * @return appropriate trust level
     */
    public TrustLevel determineTrustLevel(boolean isLocalArtifact, boolean isFromControlledSource, boolean isValidationPassed) {
        if (isLocalArtifact) {
            return TrustLevel.TRUSTED_LOCAL;
        }
        
        if (isFromControlledSource && isValidationPassed) {
            return TrustLevel.TRUSTED_CONTROLLED;
        }
        
        if (isValidationPassed) {
            return TrustLevel.SEMI_TRUSTED;
        }
        
        return TrustLevel.UNTRUSTED;
    }

    /**
     * Cleans up expired sessions.
     */
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int cleaned = 0;

        for (Map.Entry<String, PreviewSession> entry : sessions.entrySet()) {
            if (now > entry.getValue().expiresAt().toEpochMilli()) {
                sessions.remove(entry.getKey());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up expired preview sessions: count={}", cleaned);
        }
    }

    /**
     * Preview session record.
     */
    record PreviewSession(
            String sessionId,
            String projectId,
            String importJobId,
            String userId,
            TrustLevel trustLevel,
            PreviewSessionStatus status,
            Instant createdAt,
            Instant expiresAt,
            String revocationReason
    ) {}

    /**
     * Preview session status enum.
     */
    enum PreviewSessionStatus {
        ACTIVE,
        REVOKED,
        EXPIRED
    }
}
