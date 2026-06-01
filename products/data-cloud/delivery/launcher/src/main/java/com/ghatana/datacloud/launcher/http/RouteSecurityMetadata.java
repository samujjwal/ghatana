/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import java.util.Objects;
import java.util.Set;

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
    private final String routeId;
    private final String operationId;
    private final Set<String> requiredPermissions;

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
        this(method, canonicalPath, sensitivity, requiresAuth, requiresTenant, requiresPolicy,
             requiresBlockingAudit, requiredAccess, idempotent, runtimeTruthSurface, legacyStatus,
             description, null, null, Set.of());
    }

    /**
     * Constructs metadata for a specific route with canonical permission support.
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
     * @param routeId Canonical route identifier for policy/audit context
     * @param operationId Canonical operation identifier for tracing
     * @param requiredPermissions Set of canonical permissions required for this route
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
            String description,
            String routeId,
            String operationId,
            Set<String> requiredPermissions) {
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
        this.routeId = routeId != null ? routeId : method + " " + canonicalPath;
        this.operationId = operationId != null ? operationId : method.toLowerCase() + canonicalPath.replace("/", ":").replace("{", "").replace("}", "");
        this.requiredPermissions = requiredPermissions != null ? Set.copyOf(requiredPermissions) : Set.of();
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

    /**
     * Compatibility alias for older route-security tests.
     */
    public DataCloudSecurityFilter.AccessLevel requiredAccessLevel() {
        return requiredAccess();
    }

    /**
     * Compatibility alias for older route-security tests.
     */
    public boolean requiresTenantIsolation() {
        return requiresTenant();
    }

    /**
     * Compatibility alias for older route-security tests.
     */
    public boolean requiresPolicyEnforcement() {
        return requiresPolicy();
    }

    /**
     * Compatibility alias for older route-security tests.
     */
    public boolean requiresAuditLogging() {
        return requiresBlockingAudit();
    }

    /**
     * Compatibility alias for approval-gated critical mutations.
     */
    public boolean requiresApproval() {
        return requiresPolicy() && requiresBlockingAudit();
    }

    /**
     * Media endpoints require the media privacy and consent layer.
     */
    public boolean requiresConsentCheck() {
        return canonicalPath.startsWith("/api/v1/media/");
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

    /**
     * WS1: Returns the effective lifecycle derived from SurfaceRecord.
     * This is the canonical source of truth for route lifecycle, derived from runtime surface state.
     * Falls back to hardcoded legacyStatus if no surface mapping exists.
     *
     * @return effective lifecycle string
     */
    public String effectiveLifecycle() {
        String surfaceId = RouteSurfaceMapping.getSurfaceId(method, canonicalPath);
        if (surfaceId == null || surfaceId.isBlank()) {
            return legacyStatus;
        }
        // Derive from surface records in RouteSecurityRegistry
        return RouteSecurityRegistry.deriveLifecycleFromSurface(surfaceId, legacyStatus);
    }

    public String description() {
        return description;
    }

    /**
     * Canonical route identifier for policy/audit context.
     * Defaults to "METHOD path" if not explicitly set.
     */
    public String routeId() {
        return routeId;
    }

    /**
     * Canonical operation identifier for tracing.
     * Defaults to method:path format if not explicitly set.
     */
    public String operationId() {
        return operationId;
    }

    /**
     * Set of canonical permissions required for this route.
     * Empty set means no fine-grained permissions required (coarse-grained access level only).
     */
    public Set<String> requiredPermissions() {
        return requiredPermissions;
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
                && Objects.equals(description, that.description)
                && Objects.equals(routeId, that.routeId)
                && Objects.equals(operationId, that.operationId)
                && Objects.equals(requiredPermissions, that.requiredPermissions);
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
                description,
                routeId,
                operationId,
                requiredPermissions);
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
                + ", routeId='"
                + routeId
                + '\''
                + ", operationId='"
                + operationId
                + '\''
                + ", requiredPermissions="
                + requiredPermissions
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
        private String routeId;
        private String operationId;
        private Set<String> requiredPermissions = Set.of();

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

        public Builder routeId(String routeId) {
            this.routeId = routeId;
            return this;
        }

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder requiredPermissions(Set<String> requiredPermissions) {
            this.requiredPermissions = requiredPermissions != null ? requiredPermissions : Set.of();
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
                    description,
                    routeId,
                    operationId,
                    requiredPermissions);
        }
    }
}
