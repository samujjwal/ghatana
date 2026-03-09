package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for user registration.
 *
 * @doc.type record
 * @doc.purpose Registration data for creating new user accounts
 * @doc.layer product
 * @doc.pattern DTO
 */
public record RegisterRequest(
        @JsonProperty("username") String username,
        @JsonProperty("email") String email,
        @JsonProperty("password") String password,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName) {

    /**
     * Validates registration request fields.
     *
     * @return true if all required fields are non-empty
     */
    public boolean isValid() {
        return username != null && !username.isBlank()
                && email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }
}
