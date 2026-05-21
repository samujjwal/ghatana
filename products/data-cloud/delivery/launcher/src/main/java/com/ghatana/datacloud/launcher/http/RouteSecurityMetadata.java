/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import java.util.Objects;

/**
 * Immutable metadata for an HTTP route's security requirements.
 *
 * <p>Generated from OpenAPI contracts via {@code scripts/generate-route-security-metadata.mjs}.
 * This is the single source of truth for route sensitivity, access requirements, policy needs,
 * and audit requirements.
 *
 * <p>Fixes DC-P0-01: Replaces prefix-based inference with explicit generated metadata.
 *
 * @doc.type class
 * @doc.purpose Route security metadata (generated from OpenAPI contracts)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class RouteSecurityMetadata {

    private final String method;
    private final String canonicalPath;
    private final EndpointSensitivity sensitivity;
    private final boolean requiresAuth;
    private final boolean requiresTenant;
    private final boolean requiresPolicy;
    private final boolean requiresBlockingAudit;
    private final DataCloudSecurityFilter.AccessLevel requiredAccess;
    private final boolean idempotent;
    private final String runtimeTruthSurface;
    private final String legacyStatus; // "active" | "deprecated" | "compatibility-only"
    private final String description;

    /**
     * Constructs metadata for a specific route.
     *
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH)
     * @param canonicalPath Normalized path (e.g. /api/v1/entities/{id})
     * @param sensitivity Endpoint sensitivity level
     * @param requiresAuth Whether authentication is mandatory
     * @param requiresTenant Whether tenant context is required
     * @param requiresPolicy Whether policy engine must be invoked
     * @param requiresBlockingAudit Whether audit failure should block the request
     * @param requiredAccess Required role/access posture for the route
     * @param idempotent Whether the route is idempotent
     * @param runtimeTruthSurface Which UI surface this route exposes (e.g., "action_plane")
     * @param legacyStatus Legacy status ("active", "deprecated", "compatibility-only")
     * @param description Human-readable description
     */
    public RouteSecurityMetadata(
            String method,
            String canonicalPath,
            EndpointSensitivity sensitivity,
            boolean requiresAuth,
            boolean requiresTenant,
            boolean requiresPolicy,
            boolean requiresBlockingAudit,
            DataCloudSecurityFilter.AccessLevel requiredAccess,
            boolean idempotent,
            String runtimeTruthSurface,
            String legacyStatus,
            String description) {
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.canonicalPath = Objects.requireNonNull(canonicalPath, "canonicalPath must not be null");
        this.sensitivity = Objects.requireNonNull(sensitivity, "sensitivity must not be null");
        this.requiresAuth = requiresAuth;
        this.requiresTenant = requiresTenant;
        this.requiresPolicy = requiresPolicy;
        this.requiresBlockingAudit = requiresBlockingAudit;
        this.requiredAccess = requiredAccess != null ? requiredAccess : DataCloudSecurityFilter.AccessLevel.VIEWER;
        this.idempotent = idempotent;
        this.runtimeTruthSurface = runtimeTruthSurface;
        this.legacyStatus = legacyStatus != null ? legacyStatus : "active";
        this.description = description != null ? description : "";
    }

    public String method() {
        return method;
    }

    public String canonicalPath() {
        return canonicalPath;
    }

    public EndpointSensitivity sensitivity() {
        return sensitivity;
    }

    public boolean requiresAuth() {
        return requiresAuth;
    }

    public boolean requiresTenant() {
        return requiresTenant;
    }

    public boolean requiresPolicy() {
        return requiresPolicy;
    }

    public boolean requiresBlockingAudit() {
        return requiresBlockingAudit;
    }

    public DataCloudSecurityFilter.AccessLevel requiredAccess() {
        return requiredAccess;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public String runtimeTruthSurface() {
        return runtimeTruthSurface;
    }

    public String legacyStatus() {
        return legacyStatus;
    }

    public String description() {
        return description;
    }

    public boolean isDeprecated() {
        return "deprecated".equals(legacyStatus) || "compatibility-only".equals(legacyStatus);
    }

    public boolean isCompatibilityOnly() {
        return "compatibility-only".equals(legacyStatus);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteSecurityMetadata that)) return false;
        return requiresAuth == that.requiresAuth
                && requiresTenant == that.requiresTenant
                && requiresPolicy == that.requiresPolicy
                && requiresBlockingAudit == that.requiresBlockingAudit
                && idempotent == that.idempotent
                && Objects.equals(method, that.method)
                && Objects.equals(canonicalPath, that.canonicalPath)
                && sensitivity == that.sensitivity
                && requiredAccess == that.requiredAccess
                && Objects.equals(runtimeTruthSurface, that.runtimeTruthSurface)
                && Objects.equals(legacyStatus, that.legacyStatus)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                method,
                canonicalPath,
                sensitivity,
                requiresAuth,
                requiresTenant,
                requiresPolicy,
                requiresBlockingAudit,
                requiredAccess,
                idempotent,
                runtimeTruthSurface,
                legacyStatus,
                description);
    }

    @Override
    public String toString() {
        return "RouteSecurityMetadata{"
                + method
                + " "
                + canonicalPath
                + ", sensitivity="
                + sensitivity
                + ", requiresAuth="
                + requiresAuth
                + ", requiresTenant="
                + requiresTenant
                + ", requiresPolicy="
                + requiresPolicy
                + ", requiresBlockingAudit="
                + requiresBlockingAudit
                + ", requiredAccess="
                + requiredAccess
                + ", idempotent="
                + idempotent
                + ", runtimeTruthSurface='"
                + runtimeTruthSurface
                + '\''
                + ", legacyStatus='"
                + legacyStatus
                + '\''
                + '}';
    }

    /**
     * Builder for fluent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RouteSecurityMetadata.
     */
    public static final class Builder {
        private String method;
        private String canonicalPath;
        private EndpointSensitivity sensitivity = EndpointSensitivity.INTERNAL;
        private boolean requiresAuth = true;
        private boolean requiresTenant = true;
        private boolean requiresPolicy = false;
        private boolean requiresBlockingAudit = false;
        private DataCloudSecurityFilter.AccessLevel requiredAccess = DataCloudSecurityFilter.AccessLevel.VIEWER;
        private boolean idempotent = false;
        private String runtimeTruthSurface;
        private String legacyStatus = "active";
        private String description = "";

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder canonicalPath(String canonicalPath) {
            this.canonicalPath = canonicalPath;
            return this;
        }

        public Builder sensitivity(EndpointSensitivity sensitivity) {
            this.sensitivity = sensitivity;
            return this;
        }

        public Builder requiresAuth(boolean requiresAuth) {
            this.requiresAuth = requiresAuth;
            return this;
        }

        public Builder requiresTenant(boolean requiresTenant) {
            this.requiresTenant = requiresTenant;
            return this;
        }

        public Builder requiresPolicy(boolean requiresPolicy) {
            this.requiresPolicy = requiresPolicy;
            return this;
        }

        public Builder requiresBlockingAudit(boolean requiresBlockingAudit) {
            this.requiresBlockingAudit = requiresBlockingAudit;
            return this;
        }

        public Builder requiredAccess(DataCloudSecurityFilter.AccessLevel requiredAccess) {
            this.requiredAccess = requiredAccess;
            return this;
        }

        public Builder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        public Builder runtimeTruthSurface(String runtimeTruthSurface) {
            this.runtimeTruthSurface = runtimeTruthSurface;
            return this;
        }

        public Builder legacyStatus(String legacyStatus) {
            this.legacyStatus = legacyStatus;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public RouteSecurityMetadata build() {
            return new RouteSecurityMetadata(
                    method,
                    canonicalPath,
                    sensitivity,
                    requiresAuth,
                    requiresTenant,
                    requiresPolicy,
                    requiresBlockingAudit,
                    requiredAccess,
                    idempotent,
                    runtimeTruthSurface,
                    legacyStatus,
                    description);
        }
    }
}
