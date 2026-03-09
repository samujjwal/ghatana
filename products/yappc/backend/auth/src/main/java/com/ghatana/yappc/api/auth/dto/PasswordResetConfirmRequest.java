package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for password reset confirmation.
 * 
 * @doc.type record
 * @doc.purpose Reset token and new password to complete reset flow
 * @doc.layer product
 * @doc.pattern DTO
 */
public record PasswordResetConfirmRequest(
        @JsonProperty("token") String token,
        @JsonProperty("newPassword") String newPassword) {

    /**
     * Validates password reset confirmation.
     * 
     * @return true if token and new password are non-empty
     */
    public boolean isValid() {
        return token != null && !token.isBlank() &&
               newPassword != null && newPassword.length() >= 8;
    }
}
