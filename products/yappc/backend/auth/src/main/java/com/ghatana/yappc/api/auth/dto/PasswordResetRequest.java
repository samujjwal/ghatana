package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for password reset initiation.
 * 
 * @doc.type record
 * @doc.purpose Email address to send password reset token
 * @doc.layer product
 * @doc.pattern DTO
 */
public record PasswordResetRequest(
        @JsonProperty("email") String email) {

    /**
     * Validates password reset request.
     * 
     * @return true if email is non-empty
     */
    public boolean isValid() {
        return email != null && !email.isBlank() && email.contains("@");
    }
}
