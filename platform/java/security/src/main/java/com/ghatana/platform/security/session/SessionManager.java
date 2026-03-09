package com.ghatana.platform.security.session;

import io.activej.promise.Promise;

import java.util.Optional;
import java.util.Set;

/**
 * Session management interface for CRUD operations and queries.
 * <p>
 * SessionManager defines the contract for session storage backends. Implementations
 * handle session persistence, retrieval, and lifecycle management.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>CRUD Operations</b>: Create, get, save, delete sessions</li>
 *   <li><b>Query Support</b>: Find sessions by userId or tenantId</li>
 *   <li><b>Cleanup</b>: Delete expired sessions</li>
 *   <li><b>Promise-Based</b>: All operations return ActiveJ Promise</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * SessionManager manager = new RedisSessionManager(...);
 *
 * // Create new session
 * SessionState session = SessionState.builder()
 *     .userId("user123")
 *     .tenantId("tenant1")
 *     .build();
 * manager.createSession(session).get();
 *
 * // Retrieve session
 * SessionState loaded = manager.getSession(session.getSessionId()).get();
 *
 * // Update session
 * loaded.setAttribute("lastLogin", Instant.now());
 * manager.saveSession(loaded).get();
 *
 * // Query sessions
 * List<SessionState> userSessions = manager.findSessionsByUserId("user123").get();
 * List<SessionState> tenantSessions = manager.findSessionsByTenantId("tenant1").get();
 *
 * // Delete session
 * manager.deleteSession(session.getSessionId()).get();
 *
 * // Cleanup expired sessions
 * int deleted = manager.deleteExpiredSessions().get();
 * }</pre>
 * 
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li><b>Non-Blocking</b>: Use Promise.ofBlocking() for I/O operations</li>
 *   <li><b>Error Handling</b>: Return Promise.ofException(), don't throw</li>
 *   <li><b>Idempotent</b>: getSession() returns empty for missing sessions</li>
 *   <li><b>TTL Support</b>: Auto-expire sessions based on maxInactiveInterval</li>
 * </ul>
 * 
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link RedisSessionManager}: Redis-based distributed storage</li>
 *   <li>PostgreSQLSessionManager: Database-backed storage (future)</li>
 *   <li>InMemorySessionManager: Testing/development only (future)</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit, Integration
 * @thread_safety Implementation-dependent
 * @performance Implementation-dependent
 * @see SessionState
 * @see RedisSessionManager
 
 *
 * @doc.type interface
 * @doc.purpose Session manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public interface SessionManager {
    
    /**
     * Create a new session.
     */
    Promise<SessionState> createSession();
    
    /**
     * Get a session by ID.
     */
    Promise<Optional<SessionState>> getSession(String sessionId);
    
    /**
     * Save a session.
     */
    Promise<Void> saveSession(SessionState session);
    
    /**
     * Delete a session.
     */
    Promise<Boolean> deleteSession(String sessionId);
    
    /**
     * Find sessions by user ID.
     */
    Promise<Set<String>> findSessionsByUserId(String userId);
    
    /**
     * Find sessions by tenant ID.
     */
    Promise<Set<String>> findSessionsByTenantId(String tenantId);
    
    /**
     * Delete expired sessions.
     */
    Promise<Long> deleteExpiredSessions();
}
