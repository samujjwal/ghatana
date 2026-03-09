package com.ghatana.auth.exception;

/**
 * Exception thrown when a session cannot be found.
 *
 * <p><b>Purpose</b><br>
 * Indicates that a session ID does not exist in the session store, either
 * because it was never created, has been expired/deleted, or belongs to
 * a different tenant.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Optional<Session> session = sessionStore.findById(tenantId, sessionId);
 * if (session.isEmpty()) {
 *     throw new SessionNotFoundException("Session not found: " + sessionId);
 * }
 * 
 * // In HTTP adapter
 * promise.whenException(SessionNotFoundException.class, ex -> {
 *     return ResponseBuilder.unauthorized()
 *         .json(Map.of("error", "session_not_found", "message", "Session invalid or expired"))
 *         .build();
 * });
 * }</pre>
 *
 * <p><b>Possible Causes</b><br>
 * - Session expired and removed by cleanup job
 * - User logged out (session explicitly deleted)
 * - Session ID tampered with
 * - Wrong tenant context (session exists but for different tenant)
 * - Server restart cleared in-memory sessions
 *
 * <p><b>Recovery Strategy</b><br>
 * - Client should attempt refresh token flow if available
 * - Otherwise redirect to login
 * - Clear local session state on client side
 *
 * @see AuthenticationException
 * @see com.ghatana.auth.service.AuthenticationService#validateSession
 * @see com.ghatana.auth.service.AuthenticationService#refreshSession
 * @doc.type exception
 * @doc.purpose Session not found error
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class SessionNotFoundException extends AuthenticationException {
    
    /**
     * Create exception with message.
     *
     * @param message Error message
     */
    public SessionNotFoundException(String message) {
        super(message);
    }
}
