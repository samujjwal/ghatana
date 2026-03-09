/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.time.Instant;
import java.util.*;

/**
 * OAuth client domain aggregate.
 *
 * @doc.type class
 * @doc.purpose OAuth client entity
 * @doc.layer platform
 * @doc.pattern Entity
 */
public class Client {
    private final ClientId clientId;
    private final TenantId tenantId;
    private final String name;
    private final String description;
    private final Set<String> redirectUris;
    private final Set<GrantType> allowedGrantTypes;
    private final Set<Scope> allowedScopes;
    private final boolean enabled;
    private final boolean confidential;
    private final String clientSecret;
    private final Instant createdAt;
    private final Instant updatedAt;

    public enum GrantType {
        AUTHORIZATION_CODE,
        CLIENT_CREDENTIALS,
        REFRESH_TOKEN
    }

    private Client(Builder builder) {
        this.clientId = Objects.requireNonNull(builder.clientId);
        this.tenantId = Objects.requireNonNull(builder.tenantId);
        this.name = Objects.requireNonNull(builder.name);
        this.description = builder.description;
        this.redirectUris = Set.copyOf(builder.redirectUris);
        this.allowedGrantTypes = Set.copyOf(builder.allowedGrantTypes);
        this.allowedScopes = Set.copyOf(builder.allowedScopes);
        this.enabled = builder.enabled;
        this.confidential = builder.confidential;
        this.clientSecret = builder.clientSecret;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public ClientId getClientId() { return clientId; }
    public TenantId getTenantId() { return tenantId; }
    public String getName() { return name; }
    public Optional<String> getDescription() { return Optional.ofNullable(description); }
    public Set<String> getRedirectUris() { return redirectUris; }
    public Set<GrantType> getAllowedGrantTypes() { return allowedGrantTypes; }
    public Set<Scope> getAllowedScopes() { return allowedScopes; }
    public boolean isEnabled() { return enabled; }
    public boolean isConfidential() { return confidential; }
    public Optional<String> getClientSecret() { return Optional.ofNullable(clientSecret); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean supportsGrantType(GrantType type) { return allowedGrantTypes.contains(type); }
    public boolean supportsScope(Scope scope) { return allowedScopes.contains(scope); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private ClientId clientId;
        private TenantId tenantId;
        private String name;
        private String description;
        private Set<String> redirectUris = new HashSet<>();
        private Set<GrantType> allowedGrantTypes = new HashSet<>();
        private Set<Scope> allowedScopes = new HashSet<>();
        private boolean enabled = true;
        private boolean confidential = false;
        private String clientSecret;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public Builder clientId(ClientId id) { this.clientId = id; return this; }
        public Builder tenantId(TenantId id) { this.tenantId = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder redirectUris(Set<String> uris) { this.redirectUris = new HashSet<>(uris); return this; }
        public Builder addRedirectUri(String uri) { this.redirectUris.add(uri); return this; }
        public Builder allowedGrantTypes(Set<GrantType> types) { this.allowedGrantTypes = new HashSet<>(types); return this; }
        public Builder addGrantType(GrantType type) { this.allowedGrantTypes.add(type); return this; }
        public Builder allowedScopes(Set<Scope> scopes) { this.allowedScopes = new HashSet<>(scopes); return this; }
        public Builder addScope(Scope scope) { this.allowedScopes.add(scope); return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder confidential(boolean confidential) { this.confidential = confidential; return this; }
        public Builder clientSecret(String secret) { this.clientSecret = secret; return this; }
        public Builder createdAt(Instant time) { this.createdAt = time; return this; }
        public Builder updatedAt(Instant time) { this.updatedAt = time; return this; }

        public Client build() {
            if (clientId == null) clientId = ClientId.random();
            if (tenantId == null) tenantId = TenantId.system();
            return new Client(this);
        }
    }
}
