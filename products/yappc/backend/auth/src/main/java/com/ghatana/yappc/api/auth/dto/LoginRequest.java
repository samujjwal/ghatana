package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for user authentication.
 * 
 * @doc.type record
 * @doc.purpose Login credentials for JWT-based authentication
 * @doc.layer product
 * @doc.pattern DTO
 */
public record LoginRequest(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password) {

    /**
     * Validates login request fields.
     * 
     * @return true if both username and password are non-empty
     */
    public boolean isValid() {
        return username != null && !username.isBlank() &&
               password != null && !password.isBlank();
    }
}
