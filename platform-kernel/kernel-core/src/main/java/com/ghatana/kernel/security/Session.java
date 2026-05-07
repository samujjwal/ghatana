package com.ghatana.kernel.security;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * User session information.
 *
 * @doc.type class
 * @doc.purpose Session domain model (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Entity
 */
public final class Session {

    private final String id;
    private final String userId;
    private final String tenantId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant lastAccessedAt;
    private final Map<String, String> attributes;

    private Session(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.userId = Objects.requireNonNull(builder.userId, "userId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(builder.expiresAt, "expiresAt must not be null");
        this.lastAccessedAt = Objects.requireNonNull(builder.lastAccessedAt, "lastAccessedAt must not be null");
        this.attributes = Map.copyOf(builder.attributes);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public Map<String, String> getAttributes() { return attributes; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isExpired();
    }

    public Session touch() {
        return toBuilder()
            .lastAccessedAt(Instant.now())
            .build();
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .userId(userId)
            .tenantId(tenantId)
            .createdAt(createdAt)
            .expiresAt(expiresAt)
            .lastAccessedAt(lastAccessedAt)
            .attributes(attributes);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String userId;
        private String tenantId;
        private Instant createdAt = Instant.now();
        private Instant expiresAt;
        private Instant lastAccessedAt = Instant.now();
        private Map<String, String> attributes = Map.of();

        public Builder id(String id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder lastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; return this; }
        public Builder attributes(Map<String, String> attributes) { this.attributes = attributes; return this; }

        public Session build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null");
            return new Session(this);
        }
    }
}
