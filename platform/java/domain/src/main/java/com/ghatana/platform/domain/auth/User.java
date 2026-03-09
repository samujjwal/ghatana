/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable domain aggregate for user identity, authentication metadata, and role assignments.
 *
 * <p>Central to authentication and authorization decisions. Supports both internal
 * authentication (email/password) and OAuth 2.1 / OIDC federated authentication.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * User user = User.forInternalAuth()
 *     .tenantId(tenantId)
 *     .userId(userId)
 *     .email("admin@example.com")
 *     .username("admin")
 *     .passwordHash(hasher.hash("secret"))
 *     .addRoleByName("ADMIN")
 *     .status(UserStatus.ACTIVE)
 *     .build();
 *
 * if (user.canAuthenticate() && user.hasRole("ADMIN")) { ... }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose User identity and authorization aggregate (supports INTERNAL + OAUTH)
 * @doc.layer core
 * @doc.pattern Aggregate Root
 * @see UserStatus
 * @see AuthenticationType
 * @see Role
 */
public final class User {

    private final TenantId tenantId;
    private final UserId userId;
    private final AuthenticationType authenticationType;
    private final UserStatus status;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final String phoneNumber;
    private final String displayName;
    private final Set<Role> roles;
    private final Set<Permission> permissions;
    private final boolean emailVerified;
    private final boolean phoneVerified;
    private final boolean mfaEnabled;
    private final boolean active;
    private final boolean locked;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastLoginAt;
    private final Map<String, String> metadata;

    private User(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.userId = Objects.requireNonNull(builder.userId, "userId");
        this.authenticationType = builder.authenticationType != null
                ? builder.authenticationType : AuthenticationType.INTERNAL;
        this.status = builder.status != null ? builder.status : UserStatus.ACTIVE;
        this.username = Objects.requireNonNull(builder.username, "username");
        this.email = Objects.requireNonNull(builder.email, "email");
        this.passwordHash = builder.passwordHash;
        this.phoneNumber = builder.phoneNumber;
        this.displayName = builder.displayName != null ? builder.displayName : username;
        this.roles = Set.copyOf(builder.roles);
        this.permissions = Set.copyOf(builder.permissions);
        this.emailVerified = builder.emailVerified;
        this.phoneVerified = builder.phoneVerified;
        this.mfaEnabled = builder.mfaEnabled;
        this.active = builder.active;
        this.locked = builder.locked;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        this.lastLoginAt = builder.lastLoginAt;
        this.metadata = Map.copyOf(builder.metadata);

        if (authenticationType.requiresPasswordHash() && passwordHash == null) {
            throw new IllegalArgumentException("passwordHash required for " + authenticationType + " authentication");
        }
    }

    // ── Getters ──────────────────────────────────────────────

    public TenantId getTenantId() { return tenantId; }
    public UserId getUserId() { return userId; }
    public AuthenticationType getAuthenticationType() { return authenticationType; }
    public UserStatus getStatus() { return status; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public Optional<String> getPasswordHash() { return Optional.ofNullable(passwordHash); }
    public Optional<String> getPhoneNumber() { return Optional.ofNullable(phoneNumber); }
    public String getDisplayName() { return displayName; }
    public Set<Role> getRoles() { return roles; }
    public Set<Permission> getPermissions() { return permissions; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public boolean isActive() { return active; }
    public boolean isLocked() { return locked; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Optional<Instant> getLastLoginAt() { return Optional.ofNullable(lastLoginAt); }
    public Map<String, String> getMetadata() { return metadata; }

    // ── Behaviour ────────────────────────────────────────────

    /** Checks if user has the given role. */
    public boolean hasRole(Role role) { return roles.contains(role); }

    /** Checks if user has a role by string name (backward-compatible). */
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleName));
    }

    /** Checks if user has the given permission. */
    public boolean hasPermission(Permission permission) { return permissions.contains(permission); }

    /** Whether the user can authenticate (status + active + not locked). */
    public boolean canAuthenticate() { return status.canAuthenticate() && active && !locked; }

    // ── Factory Methods ──────────────────────────────────────

    /** Builder pre-configured for internal (password) authentication. */
    public static Builder forInternalAuth() {
        return new Builder().authenticationType(AuthenticationType.INTERNAL);
    }

    /** Builder pre-configured for OAuth authentication. */
    public static Builder forOAuth() {
        return new Builder().authenticationType(AuthenticationType.OAUTH);
    }

    /** Builder pre-configured for OIDC authentication. */
    public static Builder forOIDC() {
        return new Builder().authenticationType(AuthenticationType.OIDC);
    }

    /** General-purpose builder. */
    public static Builder builder() { return new Builder(); }

    // ── Builder ──────────────────────────────────────────────

    public static class Builder {
        private TenantId tenantId;
        private UserId userId;
        private AuthenticationType authenticationType;
        private UserStatus status;
        private String username;
        private String email;
        private String passwordHash;
        private String phoneNumber;
        private String displayName;
        private final Set<Role> roles = new HashSet<>();
        private final Set<Permission> permissions = new HashSet<>();
        private boolean emailVerified;
        private boolean phoneVerified;
        private boolean mfaEnabled;
        private boolean active = true;
        private boolean locked;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant lastLoginAt;
        private Map<String, String> metadata = new HashMap<>();

        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder userId(UserId userId) { this.userId = userId; return this; }
        public Builder authenticationType(AuthenticationType t) { this.authenticationType = t; return this; }
        public Builder status(UserStatus status) { this.status = status; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder passwordHash(String hash) { this.passwordHash = hash; return this; }
        public Builder phoneNumber(String phone) { this.phoneNumber = phone; return this; }
        public Builder displayName(String name) { this.displayName = name; return this; }
        public Builder roles(Set<Role> roles) { this.roles.clear(); this.roles.addAll(roles); return this; }
        public Builder addRole(Role role) { this.roles.add(role); return this; }
        public Builder addRoleByName(String name) { this.roles.add(new Role(name)); return this; }
        public Builder rolesFromStrings(Set<String> names) {
            this.roles.clear();
            this.roles.addAll(names.stream().map(Role::new).collect(Collectors.toSet()));
            return this;
        }
        public Builder permissions(Set<Permission> perms) { this.permissions.clear(); this.permissions.addAll(perms); return this; }
        public Builder addPermission(Permission p) { this.permissions.add(p); return this; }
        public Builder emailVerified(boolean v) { this.emailVerified = v; return this; }
        public Builder phoneVerified(boolean v) { this.phoneVerified = v; return this; }
        public Builder mfaEnabled(boolean v) { this.mfaEnabled = v; return this; }
        public Builder active(boolean v) { this.active = v; return this; }
        public Builder locked(boolean v) { this.locked = v; return this; }
        public Builder createdAt(Instant t) { this.createdAt = t; return this; }
        public Builder updatedAt(Instant t) { this.updatedAt = t; return this; }
        public Builder lastLoginAt(Instant t) { this.lastLoginAt = t; return this; }
        public Builder metadata(Map<String, String> m) { this.metadata = new HashMap<>(m); return this; }
        public Builder addMetadata(String k, String v) { this.metadata.put(k, v); return this; }

        public User build() { return new User(this); }
    }

    // ── Object ───────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(tenantId, user.tenantId) && Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, userId); }

    @Override
    public String toString() {
        return "User{tenantId=" + tenantId + ", userId=" + userId +
                ", username='" + username + "', active=" + active + '}';
    }
}
