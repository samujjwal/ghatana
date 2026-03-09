package com.ghatana.platform.security.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an API key for authentication.
 */
/**
 * Api key.
 *
 * @doc.type class
 * @doc.purpose Api key
 * @doc.layer core
 * @doc.pattern Component
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    /**
     * The unique identifier of the API key.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * The API key value.
     */
    private String key;

    /**
     * The name of the API key.
     */
    private String name;

    /**
     * The description of the API key.
     */
    private String description;

    /**
     * The owner of the API key.
     */
    private String owner;

    /**
     * The roles associated with the API key.
     */
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    /**
     * The permissions associated with the API key.
     */
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    /**
     * The creation timestamp of the API key.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * The expiration timestamp of the API key.
     */
    private Instant expiresAt;

    /**
     * The last used timestamp of the API key.
     */
    private Instant lastUsedAt;

    /**
     * Whether the API key is enabled.
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Checks if the API key is expired.
     *
     * @return true if the API key is expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Checks if the API key is valid.
     *
     * @return true if the API key is valid, false otherwise
     */
    public boolean isValid() {
        return enabled && !isExpired();
    }

    /**
     * Updates the last used timestamp.
     */
    public void updateLastUsed() {
        lastUsedAt = Instant.now();
    }

    /**
     * Adds a role to the API key.
     *
     * @param role The role to add
     * @return This API key
     */
    public ApiKey addRole(String role) {
        roles.add(role);
        return this;
    }

    /**
     * Adds a permission to the API key.
     *
     * @param permission The permission to add
     * @return This API key
     */
    public ApiKey addPermission(String permission) {
        permissions.add(permission);
        return this;
    }
}
