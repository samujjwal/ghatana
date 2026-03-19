/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of an authentication operation.
 *
 * <p>Contains either a successful authentication result with user session
 * or a failure with error message. This is a generic result type with
 * NO finance-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Authentication operation result - success with session or failure with error
 * @doc.layer kernel
 * @doc.pattern Result
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class AuthResult {

    private final boolean success;
    private final UserSession session;
    private final String errorMessage;

    private AuthResult(boolean success, UserSession session, String errorMessage) {
        this.success = success;
        this.session = session;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful authentication result.
     *
     * @param session the user session
     * @return successful auth result
     */
    public static AuthResult success(UserSession session) {
        return new AuthResult(true, Objects.requireNonNull(session, "session"), null);
    }

    /**
     * Creates a failed authentication result.
     *
     * @param errorMessage the error message
     * @return failed auth result
     */
    public static AuthResult failure(String errorMessage) {
        return new AuthResult(false, null, Objects.requireNonNull(errorMessage, "errorMessage"));
    }

    /**
     * Checks if authentication was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the user session if authentication was successful.
     *
     * @return optional containing the session
     */
    public Optional<UserSession> getSession() {
        return Optional.ofNullable(session);
    }

    /**
     * Gets the error message if authentication failed.
     *
     * @return optional containing the error message
     */
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthResult that = (AuthResult) o;
        return success == that.success &&
               Objects.equals(session, that.session) &&
               Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, session, errorMessage);
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("AuthResult{success=true, session=%s}", session);
        } else {
            return String.format("AuthResult{success=false, error='%s'}", errorMessage);
        }
    }
}
