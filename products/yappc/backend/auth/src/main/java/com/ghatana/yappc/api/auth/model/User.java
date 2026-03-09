package com.ghatana.yappc.api.auth.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * User entity representing authenticated users in the system.
 * 
 * @doc.type class
 * @doc.purpose Core user model for authentication and authorization
 * @doc.layer product
 * @doc.pattern Entity
 */
public class User {
    private UUID id;
    private String username;
    private String email;
    private String passwordHash;
    private Set<String> roles;
    private boolean active;
    private String passwordResetToken;
    private Instant passwordResetExpiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    public User() {
        this.id = UUID.randomUUID();
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public User(String username, String email, String passwordHash, Set<String> roles) {
        this();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public String getPasswordResetToken() {
        return passwordResetToken;
    }

    public void setPasswordResetToken(String passwordResetToken) {
        this.passwordResetToken = passwordResetToken;
        this.updatedAt = Instant.now();
    }

    public Instant getPasswordResetExpiresAt() {
        return passwordResetExpiresAt;
    }

    public void setPasswordResetExpiresAt(Instant passwordResetExpiresAt) {
        this.passwordResetExpiresAt = passwordResetExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if password reset token is still valid.
     * 
     * @return true if token exists and hasn't expired
     */
    public boolean isPasswordResetTokenValid() {
        return passwordResetToken != null &&
               passwordResetExpiresAt != null &&
               Instant.now().isBefore(passwordResetExpiresAt);
    }
}
