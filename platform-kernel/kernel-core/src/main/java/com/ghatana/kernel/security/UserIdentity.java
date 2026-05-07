package com.ghatana.kernel.security;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User identity information.
 *
 * @doc.type class
 * @doc.purpose User identity domain model (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Entity
 */
public final class UserIdentity {

    private final String id;
    private final String tenantId;
    private final String username;
    private final String email;
    private final String fullName;
    private final Set<String> roles;
    private final Map<String, String> attributes;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastLoginAt;

    private UserIdentity(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.username = Objects.requireNonNull(builder.username, "username must not be null");
        this.email = Objects.requireNonNull(builder.email, "email must not be null");
        this.fullName = builder.fullName;
        this.roles = Set.copyOf(builder.roles);
        this.attributes = Map.copyOf(builder.attributes);
        this.active = builder.active;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.lastLoginAt = builder.lastLoginAt;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public Set<String> getRoles() { return roles; }
    public Map<String, String> getAttributes() { return attributes; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .username(username)
            .email(email)
            .fullName(fullName)
            .roles(roles)
            .attributes(attributes)
            .active(active)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .lastLoginAt(lastLoginAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String username;
        private String email;
        private String fullName;
        private Set<String> roles = Set.of();
        private Map<String, String> attributes = Map.of();
        private boolean active = true;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private Instant lastLoginAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder fullName(String fullName) { this.fullName = fullName; return this; }
        public Builder roles(Set<String> roles) { this.roles = roles; return this; }
        public Builder attributes(Map<String, String> attributes) { this.attributes = attributes; return this; }
        public Builder active(boolean active) { this.active = active; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder lastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; return this; }

        public UserIdentity build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (username == null || username.isBlank()) throw new IllegalArgumentException("username must not be blank");
            if (email == null || email.isBlank()) throw new IllegalArgumentException("email must not be blank");
            return new UserIdentity(this);
        }
    }
}
