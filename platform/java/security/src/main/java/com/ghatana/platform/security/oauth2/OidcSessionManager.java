package com.ghatana.platform.security.oauth2;

import com.ghatana.platform.security.model.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages OIDC sessions, including session creation, validation, and invalidation.
 * <p>
 * This class is thread-safe and uses Caffeine cache for efficient session storage
 * with automatic expiration. Supports pluggable token revocation via
 * {@link TokenRevocationHandler}.
 *
 * @doc.type class
 * @doc.purpose OIDC session lifecycle management with token revocation
 * @doc.layer core
 * @doc.pattern Manager
*/
public class OidcSessionManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(OidcSessionManager.class);
    
    private final Cache<String, OidcSession> sessionCache;
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Duration sessionTimeout;
    private final TokenRevocationHandler revocationHandler;

    /**
     * Functional interface for pluggable token revocation (RFC 7009).
     * Implementations should call the OIDC provider's revocation endpoint.
     */
    @FunctionalInterface
    public interface TokenRevocationHandler {
        /**
         * Revoke a token.
         *
         * @param token the token value to revoke
         * @param tokenTypeHint one of "access_token" or "refresh_token"
         */
        void revoke(String token, String tokenTypeHint);
    }

    /**
     * Creates a new OidcSessionManager with the specified session timeout and no token revocation.
     *
     * @param sessionTimeout The duration after which sessions expire
     */
    public OidcSessionManager(Duration sessionTimeout) {
        this(sessionTimeout, null);
    }

    /**
     * Creates a new OidcSessionManager with the specified session timeout and revocation handler.
     *
     * @param sessionTimeout    The duration after which sessions expire
     * @param revocationHandler optional handler for token revocation (may be null)
     */
    public OidcSessionManager(Duration sessionTimeout, TokenRevocationHandler revocationHandler) {
        this.sessionTimeout = Objects.requireNonNull(sessionTimeout, "sessionTimeout cannot be null");
        this.revocationHandler = revocationHandler;
        
        // Initialize the session cache with the specified timeout
        this.sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(sessionTimeout)
            .removalListener((key, value, cause) -> {
                if (key != null && value != null) {
                    OidcSession session = (OidcSession) value;
                    logger.debug("Session {} expired for user {}", key, session.getUserId());
                    userSessionMap.remove(session.getUserId(), key);
                    
                    // Revoke tokens on session expiry
                    revokeSessionTokens(session);
                }
            })
            .build();
            
        // Initialize the scheduler for background tasks
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oidc-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup of expired sessions
        this.scheduler.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            1, 1, TimeUnit.HOURS
        );
    }
    
    /**
     * Create a new session for the user.
     *
     * @param user The authenticated user (required)
     * @param idToken The ID token from the OIDC provider (required)
     * @param accessToken The access token from the OIDC provider (required)
     * @param refreshToken The refresh token from the OIDC provider (optional)
     * @return The session ID
     * @throws NullPointerException if user, idToken, or accessToken is null
     */
    public String createSession(User user, String idToken, String accessToken, String refreshToken) {
        Objects.requireNonNull(user, "User cannot be null");
        Objects.requireNonNull(idToken, "ID token cannot be null");
        Objects.requireNonNull(accessToken, "Access token cannot be null");
        
        // Generate a new session ID
        String sessionId = generateSessionId();
        
        // Create a new session
        OidcSession session = new OidcSession(
            sessionId,  // Add sessionId as the first parameter
            user.getUserId(),
            user,
            idToken,
            accessToken,
            refreshToken,
            Instant.now(),
            Instant.now().plus(sessionTimeout)
        );
        
        // Invalidate any existing session for this user
        String existingSessionId = userSessionMap.get(user.getUserId());
        if (existingSessionId != null) {
            logger.debug("Invalidating existing session {} for user {}", existingSessionId, user.getUserId());
            sessionCache.invalidate(existingSessionId);
        }
        
        // Store the new session
        sessionCache.put(sessionId, session);
        userSessionMap.put(user.getUserId(), sessionId);
        
        logger.info("Created new session {} for user {} (expires: {})", 
            sessionId, user.getUserId(), session.getExpiresAt());
            
        return sessionId;
    }
    
    /**
     * Get a session by ID asynchronously.
     *
     * @param sessionId The session ID
     * @return A Promise containing the session if found, or an empty Optional if not found or expired
     */
    public Promise<Optional<OidcSession>> getSessionAsync(String sessionId) {
        return Promise.of(getSession(sessionId));
    }
    
    /**
     * Get a session by ID.
     *
     * @param sessionId The session ID
     * @return The session, or empty if not found or expired
     */
    public Optional<OidcSession> getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }
        
        OidcSession session = sessionCache.getIfPresent(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        
        // Check if the session is expired
        if (session.getExpiresAt().isBefore(Instant.now())) {
            logger.debug("Session {} is expired", sessionId);
            sessionCache.invalidate(sessionId);
            return Optional.empty();
        }
        
        // Update last accessed time
        session.setLastAccessed(Instant.now());
        
        return Optional.of(session);
    }
    
    /**
     * Get a session by user ID.
     *
     * @param userId The user ID
     * @return The session, or empty if not found or expired
     */
    public Optional<OidcSession> getSessionByUserId(String userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId == null) {
            return Optional.empty();
        }
        return getSession(sessionId);
    }
    
    /**
     * Invalidate a session asynchronously.
     *
     * @param sessionId The session ID to invalidate
     * @return A Promise that completes with true if the session was found and invalidated, false otherwise
     */
    public Promise<Boolean> invalidateSessionAsync(String sessionId) {
        return Promise.of(invalidateSession(sessionId));
    }
    
    /**
     * Invalidate a session.
     *
     * @param sessionId The session ID to invalidate
     * @return true if the session was found and invalidated, false otherwise
     */
    public boolean invalidateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        
        OidcSession session = sessionCache.getIfPresent(sessionId);
        if (session != null) {
            logger.debug("Invalidating session {} for user {}", sessionId, session.getUserId());
            sessionCache.invalidate(sessionId);
            userSessionMap.remove(session.getUserId(), sessionId);
            
            // Revoke tokens associated with the session
            revokeSessionTokens(session);
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Invalidate all sessions for a user.
     *
     * @param userId The user ID
     * @return The number of sessions invalidated
     */
    public int invalidateAllSessionsForUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return 0;
        }
        
        String sessionId = userSessionMap.remove(userId);
        if (sessionId != null) {
            sessionCache.invalidate(sessionId);
            return 1;
        }
        
        return 0;
    }
    
    /**
     * Clean up expired sessions.
     * This is called periodically by the scheduler.
     */
    private void cleanupExpiredSessions() {
        try {
            logger.debug("Running session cleanup");
            sessionCache.cleanUp();
        } catch (Exception e) {
            logger.error("Error during session cleanup", e);
        }
    }
    
    /**
     * Invalidate all sessions for a user.
     *
     * @param userId The user ID
     */
    public void invalidateSessionByUserId(String userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId != null) {
            invalidateSession(sessionId);
        }
    }
    
    /**
     * Refresh a session with new tokens.
     *
     * @param sessionId The session ID to refresh
     * @param accessToken The new access token
     * @param refreshToken The new refresh token (optional)
     * @return The updated session, or empty if the session doesn't exist
     */
    public Optional<OidcSession> refreshSession(String sessionId, String accessToken, String refreshToken) {
        return getSession(sessionId).map(session -> {
            OidcSession refreshedSession = new OidcSession(
                session.getSessionId(),
                session.getUserId(),
                session.getUser(),
                session.getIdToken(),
                accessToken,
                refreshToken != null ? refreshToken : session.getRefreshToken().orElse(null),
                session.getCreatedAt(),
                Instant.now().plus(sessionTimeout)
            );
            
            sessionCache.put(sessionId, refreshedSession);
            return refreshedSession;
        });
    }
    
    /**
     * Get the number of active sessions.
     *
     * @return The number of active sessions
     */
    public long getActiveSessionCount() {
        return sessionCache.estimatedSize();
    }
    
    /**
     * Generate a new session ID.
     * 
     * @return A new unique session ID
     */
    private String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Revoke all tokens associated with a session.
     * Calls the registered {@link TokenRevocationHandler} if present.
     *
     * @param session the session whose tokens should be revoked
     */
    private void revokeSessionTokens(OidcSession session) {
        if (revocationHandler == null || session == null) {
            return;
        }
        try {
            if (session.getAccessToken() != null) {
                revocationHandler.revoke(session.getAccessToken(), "access_token");
                logger.debug("Revoked access token for session {}", session.getSessionId());
            }
            session.getRefreshToken().ifPresent(refreshToken -> {
                revocationHandler.revoke(refreshToken, "refresh_token");
                logger.debug("Revoked refresh token for session {}", session.getSessionId());
            });
        } catch (Exception e) {
            logger.warn("Token revocation failed for session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    /**
     * Close the session manager and release resources.
     */
    @Override
    public void close() {
        try {
            logger.info("Shutting down OIDC session manager");
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            sessionCache.invalidateAll();
            userSessionMap.clear();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted during shutdown");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
    
    /**
     * Represents an OIDC session with user authentication details and token information.
     */
    public static class OidcSession {
        private final String sessionId;
        private final String userId;
        private final User user;
        private final String idToken;
        private final String accessToken;
        private final String refreshToken;
        private final Instant createdAt;
        private final Instant expiresAt;
        private Instant lastAccessed;

        /**
         * Creates a new OIDC session.
         *
         * @param sessionId The unique session ID
         * @param userId The user ID associated with this session
         * @param user The authenticated user
         * @param idToken The ID token from the OIDC provider
         * @param accessToken The access token from the OIDC provider
         * @param refreshToken The refresh token from the OIDC provider (can be null)
         * @param createdAt When the session was created
         * @param expiresAt When the session expires
         * @throws NullPointerException if any required parameter is null
         */
        public OidcSession(
                String sessionId,
                String userId,
                User user,
                String idToken,
                String accessToken,
                String refreshToken,
                Instant createdAt,
                Instant expiresAt) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
            this.userId = Objects.requireNonNull(userId, "userId cannot be null");
            this.user = Objects.requireNonNull(user, "user cannot be null");
            this.idToken = Objects.requireNonNull(idToken, "idToken cannot be null");
            this.accessToken = Objects.requireNonNull(accessToken, "accessToken cannot be null");
            this.refreshToken = refreshToken; // Can be null
            this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
            this.lastAccessed = createdAt;
        }

        /**
         * Gets the session ID.
         * @return The session ID
         */
        public String getSessionId() {
            return sessionId;
        }

        /**
         * Gets the user ID associated with this session.
         * @return The user ID
         */
        public String getUserId() {
            return userId;
        }

        /**
         * Gets the authenticated user.
         * @return The user object
         */
        public User getUser() {
            return user;
        }

        /**
         * Gets the ID token.
         * @return The ID token
         */
        public String getIdToken() {
            return idToken;
        }

        /**
         * Gets the access token.
         * @return The access token
         */
        public String getAccessToken() {
            return accessToken;
        }

        /**
         * Gets the refresh token, if available.
         * @return An Optional containing the refresh token, or empty if not available
         */
        public Optional<String> getRefreshToken() {
            return Optional.ofNullable(refreshToken);
        }

        /**
         * Gets when the session was created.
         * @return The creation timestamp
         */
        public Instant getCreatedAt() {
            return createdAt;
        }

        /**
         * Gets when the session expires.
         * @return The expiration timestamp
         */
        public Instant getExpiresAt() {
            return expiresAt;
        }

        /**
         * Gets when the session was last accessed.
         * @return The last accessed timestamp
         */
        public Instant getLastAccessed() {
            return lastAccessed;
        }

        /**
         * Sets when the session was last accessed.
         * @param lastAccessed The last accessed timestamp
         * @throws NullPointerException if lastAccessed is null
         */
        public void setLastAccessed(Instant lastAccessed) {
            this.lastAccessed = Objects.requireNonNull(lastAccessed, "lastAccessed cannot be null");
        }

        /**
         * Checks if the session is expired.
         * @return true if the session is expired, false otherwise
         */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        /**
         * Checks if the session is active (not expired).
         * @return true if the session is active, false otherwise
         */
        public boolean isActive() {
            return !isExpired();
        }

        @Override
        public String toString() {
            return "OidcSession{" +
                    "sessionId='" + sessionId + '\'' +
                    ", userId='" + userId + '\'' +
                    ", createdAt=" + createdAt +
                    ", expiresAt=" + expiresAt +
                    ", lastAccessed=" + lastAccessed +
                    '}';
        }
    }
    
    /**
     * Exception thrown when an OIDC session operation fails.
     */
    public static class OidcSessionException extends RuntimeException {
        public OidcSessionException(String message) {
            super(message);
        }

        public OidcSessionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
