/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;

/**
 * Result of an authentication operation.
 *
 * <p>Encapsulates either successful authentication data (session + token)
 * or error information. Uses the result pattern instead of exceptions
 * for expected failure cases.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuthResult result = authService.authenticate(credentials);
 * if (result.isSuccess()) {
 *     Session session = result.getSession();
 *     Token token = result.getToken();
 * } else {
 *     log.warn("Auth failed: {}", result.getErrorMessage());
 * }
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Authentication result with session and token or error
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record AuthResult(
        boolean success,
        Session session,
        Token token,
        String errorMessage
) {

    /**
     * Validates invariants: success requires session+token; failure requires errorMessage.
     */
    public AuthResult {
        if (success) {
            Objects.requireNonNull(session, "session is required for success result");
            Objects.requireNonNull(token, "token is required for success result");
            if (errorMessage != null) {
                throw new IllegalArgumentException("errorMessage must be null for success result");
            }
        } else {
            if (session != null || token != null) {
                throw new IllegalArgumentException("session and token must be null for failure result");
            }
            Objects.requireNonNull(errorMessage, "errorMessage is required for failure result");
            if (errorMessage.isBlank()) {
                throw new IllegalArgumentException("errorMessage cannot be blank");
            }
        }
    }

    /** Creates a successful authentication result. */
    public static AuthResult success(Session session, Token token) {
        return new AuthResult(true, session, token, null);
    }

    /** Creates a failed authentication result. */
    public static AuthResult failure(String errorMessage) {
        return new AuthResult(false, null, null, errorMessage);
    }

    /** @return true if authentication succeeded */
    public boolean isSuccess() { return success; }

    /** @return true if authentication failed */
    public boolean isFailure() { return !success; }

    /**
     * @return the session (only valid if success)
     * @throws IllegalStateException if called on failure result
     */
    public Session getSession() {
        if (!success) throw new IllegalStateException("Cannot get session from failure result");
        return session;
    }

    /**
     * @return the token (only valid if success)
     * @throws IllegalStateException if called on failure result
     */
    public Token getToken() {
        if (!success) throw new IllegalStateException("Cannot get token from failure result");
        return token;
    }

    /**
     * @return the error message (only valid if failure)
     * @throws IllegalStateException if called on success result
     */
    public String getErrorMessage() {
        if (success) throw new IllegalStateException("Cannot get errorMessage from success result");
        return errorMessage;
    }
}
