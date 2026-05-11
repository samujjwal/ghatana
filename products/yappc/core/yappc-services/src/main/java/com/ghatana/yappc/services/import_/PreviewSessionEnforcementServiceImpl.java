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

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    private static final Set<String> TRUSTED_ACTIONS = Set.of(
            "view",
            "navigate",
            "inspect"
    );
    private static final Set<String> UNTRUSTED_ACTIONS = Set.of(
            "export",
            "download",
            "share",
            "modify"
    );

    // In-memory storage for demonstration - replace with distributed cache in production
    private final Map<String, PreviewSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String createPreviewSession(String projectId, String importJobId, String userId) {
        String sessionId = "preview-" + java.util.UUID.randomUUID().toString();

        log.info("Creating preview session: sessionId={}, projectId={}, userId={}", 
                sessionId, projectId, userId);

        PreviewSession session = new PreviewSession(
                sessionId,
                projectId,
                importJobId,
                userId,
                TrustLevel.UNTRUSTED,
                PreviewSessionStatus.ACTIVE,
                Instant.now(),
                Instant.now().plusMillis(SESSION_TIMEOUT_MS),
                userId
        );

        sessions.put(sessionId, session);

        log.info("Preview session created successfully: sessionId={}", sessionId);
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
            sessions.remove(sessionId);
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

        // Allow trusted actions for all trust levels
        if (TRUSTED_ACTIONS.contains(action)) {
            return true;
        }

        // Block untrusted actions for untrusted sessions
        if (UNTRUSTED_ACTIONS.contains(action) && trustLevel != TrustLevel.TRUSTED) {
            log.warn("Action not allowed due to trust policy: sessionId={}, action={}, trustLevel={}", 
                    sessionId, action, trustLevel);
            return false;
        }

        return true;
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
    public void revokeSession(String sessionId, String reason) {
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

        log.info("Preview session revoked successfully: sessionId={}", sessionId);
    }

    @Override
    public TrustLevel getTrustLevel(String sessionId) {
        PreviewSession session = sessions.get(sessionId);

        if (session == null) {
            log.warn("Preview session not found: sessionId={}", sessionId);
            return TrustLevel.UNKNOWN;
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
