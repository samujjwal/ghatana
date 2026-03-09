package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Set;

/**
 * User profile information exposed via API.
 * 
 * @doc.type record
 * @doc.purpose Public user information (excludes password hash)
 * @doc.layer product
 * @doc.pattern DTO
 */
public record UserProfile(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username,
        @JsonProperty("email") String email,
        @JsonProperty("roles") Set<String> roles,
        @JsonProperty("active") boolean active,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("lastLoginAt") Instant lastLoginAt) {
}
