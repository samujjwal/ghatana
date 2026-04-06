package com.ghatana.kernel.security;

import java.util.*;

/**
 * Tenant-specific security context implementation.
 *
 * <p>Immutable security context that includes tenant isolation and
 * multi-tenant security features.</p>
 *
 * @doc.type class
 * @doc.purpose Tenant-aware security context implementation
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class TenantSecurityContext implements SecurityContext {

    private final String tenantId;
    private final String userId;
    private final String sessionId;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Map<String, Object> attributes;
    private final long authenticationTime;
    private final boolean authenticated;

    private TenantSecurityContext(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId cannot be null");
        this.userId = Objects.requireNonNull(builder.userId, "userId cannot be null");
        this.sessionId = Objects.requireNonNull(builder.sessionId, "sessionId cannot be null");
        this.roles = Collections.unmodifiableSet(new HashSet<>(builder.roles));
        this.permissions = Collections.unmodifiableSet(new HashSet<>(builder.permissions));
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.authenticationTime = builder.authenticationTime;
        this.authenticated = builder.authenticated;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public long getAuthenticationTime() {
        return authenticationTime;
    }

    /**
     * Gets user permissions.
     *
     * @return set of permissions
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Creates a new builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TenantSecurityContext.
     */
    public static class Builder {
        private String tenantId;
        private String userId;
        private String sessionId;
        private Set<String> roles = new HashSet<>();
        private Set<String> permissions = new HashSet<>();
        private Map<String, Object> attributes = new HashMap<>();
        private long authenticationTime = System.currentTimeMillis();
        private boolean authenticated = true;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder role(String role) {
            this.roles.add(role);
            return this;
        }

        public Builder roles(Set<String> roles) {
            this.roles.addAll(roles);
            return this;
        }

        public Builder permission(String permission) {
            this.permissions.add(permission);
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            this.permissions.addAll(permissions);
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public Builder authenticationTime(long authenticationTime) {
            this.authenticationTime = authenticationTime;
            return this;
        }

        public Builder authenticated(boolean authenticated) {
            this.authenticated = authenticated;
            return this;
        }

        public TenantSecurityContext build() {
            return new TenantSecurityContext(this);
        }
    }

    @Override
    public String toString() {
        return String.format("TenantSecurityContext{tenantId='%s', userId='%s', roles=%s, authenticated=%b}",
            tenantId, userId, roles, authenticated);
    }
}
