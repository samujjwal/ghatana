package com.ghatana.virtualorg.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Result of an authentication attempt.
 *
 * <p><b>Purpose</b><br>
 * Value object representing the outcome of an authentication operation,
 * including success/failure status, principal details, and metadata.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Success case
 * AuthenticationResult success = AuthenticationResult.success(principal);
 * if (success.authenticated()) {
 *     Principal p = success.principal();
 * }
 * 
 * // Failure case
 * AuthenticationResult failure = AuthenticationResult.failure(
 *     "Invalid credentials"
 * );
 * String reason = failure.failureReason();
 * }</pre>
 *
 * <p><b>States</b><br>
 * - **Success**: authenticated=true, principal present, no failureReason
 * - **Failure**: authenticated=false, no principal, failureReason present
 *
 * @param authenticated Whether authentication succeeded
 * @param principal The authenticated principal (null if failed)
 * @param failureReason Reason for failure (null if succeeded)
 * @param metadata Additional authentication metadata
 * @doc.type record
 * @doc.purpose Authentication result value object with success/failure state
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AuthenticationResult(
    boolean authenticated,
    @Nullable Principal principal,
    @Nullable String failureReason,
    @NotNull java.util.Map<String, String> metadata
) {
    /**
     * Create a successful authentication result
     */
    @NotNull
    public static AuthenticationResult success(@NotNull Principal principal) {
        return new AuthenticationResult(true, principal, null, java.util.Map.of());
    }

    /**
     * Create a successful authentication result with metadata
     */
    @NotNull
    public static AuthenticationResult success(
        @NotNull Principal principal,
        @NotNull java.util.Map<String, String> metadata
    ) {
        return new AuthenticationResult(true, principal, null, metadata);
    }

    /**
     * Create a failed authentication result
     */
    @NotNull
    public static AuthenticationResult failure(@NotNull String reason) {
        return new AuthenticationResult(false, null, reason, java.util.Map.of());
    }

    /**
     * Create a failed authentication result with metadata
     */
    @NotNull
    public static AuthenticationResult failure(
        @NotNull String reason,
        @NotNull java.util.Map<String, String> metadata
    ) {
        return new AuthenticationResult(false, null, reason, metadata);
    }

    /**
     * Get principal as Optional
     */
    @NotNull
    public Optional<Principal> getPrincipal() {
        return Optional.ofNullable(principal);
    }

    /**
     * Get failure reason as Optional
     */
    @NotNull
    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }
}
