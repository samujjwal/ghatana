/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * User session representation.
 *
 * @doc.type class
 * @doc.purpose Authenticated user session entity
 * @doc.layer platform
 * @doc.pattern Entity
 */
public class Session {
    private final SessionId sessionId;
    private final UserId userId;
    private final TenantId tenantId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant lastAccessedAt;
    private final String ipAddress;
    private final String userAgent;
    private final boolean valid;

    public Session(SessionId sessionId, UserId userId, TenantId tenantId,
                   Instant createdAt, Instant expiresAt, Instant lastAccessedAt,
                   String ipAddress, String userAgent, boolean valid) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.userId = Objects.requireNonNull(userId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.lastAccessedAt = lastAccessedAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.valid = valid;
    }

    public SessionId getSessionId() { return sessionId; }
    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Optional<Instant> getLastAccessedAt() { return Optional.ofNullable(lastAccessedAt); }
    public Optional<String> getIpAddress() { return Optional.ofNullable(ipAddress); }
    public Optional<String> getUserAgent() { return Optional.ofNullable(userAgent); }
    public boolean isValid() { return valid && Instant.now().isBefore(expiresAt); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private SessionId sessionId;
        private UserId userId;
        private TenantId tenantId;
        private Instant createdAt = Instant.now();
        private Instant expiresAt;
        private Instant lastAccessedAt;
        private String ipAddress;
        private String userAgent;
        private boolean valid = true;

        public Builder sessionId(SessionId id) { this.sessionId = id; return this; }
        public Builder userId(UserId id) { this.userId = id; return this; }
        public Builder tenantId(TenantId id) { this.tenantId = id; return this; }
        public Builder createdAt(Instant time) { this.createdAt = time; return this; }
        public Builder expiresAt(Instant time) { this.expiresAt = time; return this; }
        public Builder lastAccessedAt(Instant time) { this.lastAccessedAt = time; return this; }
        public Builder ipAddress(String ip) { this.ipAddress = ip; return this; }
        public Builder userAgent(String agent) { this.userAgent = agent; return this; }
        public Builder valid(boolean valid) { this.valid = valid; return this; }

        public Session build() {
            if (sessionId == null) sessionId = new SessionId(UUID.randomUUID().toString());
            return new Session(sessionId, userId, tenantId, createdAt, expiresAt, lastAccessedAt, ipAddress, userAgent, valid);
        }
    }
}
