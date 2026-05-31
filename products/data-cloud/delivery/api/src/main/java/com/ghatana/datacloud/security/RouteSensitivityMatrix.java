package com.ghatana.datacloud.security;

import java.util.*;

/**
 * Route sensitivity matrix defining security requirements per endpoint.
 *
 * <p><b>Purpose</b><br>
 * Defines the sensitivity level and security requirements for each API route,
 * including authentication, authorization, rate limiting, data classification,
 * and audit logging requirements.
 *
 * <p><b>Sensitivity Levels</b><br>
 * - PUBLIC: No authentication required, minimal logging
 * - LOW: Authentication required, basic authorization
 * - MEDIUM: Authentication + role-based authorization, rate limiting
 * - HIGH: Authentication + RBAC + data access checks, comprehensive logging
 * - CRITICAL: Maximum security, PII/biometric data, strict audit trail
 *
 * @doc.type class
 * @doc.purpose Route sensitivity matrix for security enforcement
 * @doc.layer product
 * @doc.pattern Security Matrix
 */
public final class RouteSensitivityMatrix {

    public enum SensitivityLevel {
        PUBLIC,     // No auth, minimal logging
        LOW,        // Auth required, basic authz
        MEDIUM,     // Auth + RBAC, rate limiting
        HIGH,       // Auth + RBAC + data access, comprehensive logging
        CRITICAL    // Max security, PII/biometric, strict audit
    }

    public enum AuthenticationRequirement {
        NONE,           // No authentication
        OPTIONAL,       // Authentication optional
        REQUIRED,       // Authentication required
        STRICT          // Authentication with MFA
    }

    public enum AuthorizationRequirement {
        NONE,           // No authorization
        BASIC,          // Basic role check
        RBAC,           // Role-based access control
        DATA_ACCESS,    // Data access validation
        POLICY          // Policy-based authorization
    }

    public record RouteSensitivity(
            String route,
            String method,
            SensitivityLevel sensitivity,
            AuthenticationRequirement authRequirement,
            AuthorizationRequirement authzRequirement,
            boolean requiresTenantIsolation,
            boolean requiresRateLimiting,
            boolean requiresAuditLogging,
            boolean requiresDataClassification,
            Set<String> requiredRoles,
            Set<String> requiredPermissions,
            String dataClassification
    ) {
        public RouteSensitivity {
            if (requiredRoles == null) requiredRoles = Set.of();
            if (requiredPermissions == null) requiredPermissions = Set.of();
        }
    }

    private final Map<String, RouteSensitivity> sensitivityMap;

    public RouteSensitivityMatrix() {
        this.sensitivityMap = new HashMap<>();
        initializeDefaultRoutes();
    }

    private void initializeDefaultRoutes() {
        // Health and readiness - PUBLIC
        addRoute("/health", "GET", SensitivityLevel.PUBLIC, AuthenticationRequirement.NONE,
                AuthorizationRequirement.NONE, false, false, false, false, Set.of(), Set.of(), null);
        addRoute("/readiness", "GET", SensitivityLevel.PUBLIC, AuthenticationRequirement.NONE,
                AuthorizationRequirement.NONE, false, false, false, false, Set.of(), Set.of(), null);

        // Entity CRUD - HIGH (tenant-isolated, data access checks)
        addRoute("/api/v1/entities", "GET", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-reader"), Set.of("data-cloud:entity-read"), "INTERNAL");
        addRoute("/api/v1/entities", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-writer"), Set.of("data-cloud:entity-write"), "INTERNAL");
        addRoute("/api/v1/entities/*", "GET", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-reader"), Set.of("data-cloud:entity-read"), "INTERNAL");
        addRoute("/api/v1/entities/*", "PUT", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-writer"), Set.of("data-cloud:entity-write"), "INTERNAL");
        addRoute("/api/v1/entities/*", "DELETE", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-admin"), Set.of("data-cloud:entity-delete"), "INTERNAL");

        // Collections - MEDIUM
        addRoute("/api/v1/collections", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/collections", "POST", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/collections/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/collections/*", "PUT", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/collections/*", "DELETE", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-admin"), Set.of("data-cloud:entity-delete"), null);

        // Entities - MEDIUM (generic entity endpoint for testing)
        addRoute("/api/v1/entities", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);

        // Datasets - MEDIUM
        addRoute("/api/v1/datasets", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/datasets", "POST", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/datasets/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/datasets/*", "PUT", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/datasets/*", "DELETE", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-admin"), Set.of("data-cloud:entity-delete"), null);

        // Data Sources - MEDIUM
        addRoute("/api/v1/data-sources", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/data-sources", "POST", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/data-sources/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/data-sources/*", "PUT", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/data-sources/*", "DELETE", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-admin"), Set.of("data-cloud:entity-delete"), null);

        // Connectors - MEDIUM (secret access)
        addRoute("/api/v1/connectors", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/connectors", "POST", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/connectors/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/connectors/*", "PUT", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/connectors/*", "DELETE", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-admin"), Set.of("data-cloud:entity-delete"), null);
        addRoute("/api/v1/connectors/*/secrets", "GET", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-admin"), Set.of("data-cloud:entity-read"), "CONFIDENTIAL");
        addRoute("/api/v1/connectors/*/secrets", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-admin"), Set.of("data-cloud:entity-write"), "CONFIDENTIAL");

        // Media Artifacts - CRITICAL (PII/biometric data)
        addRoute("/api/v1/media/artifacts", "GET", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("media-reader"), Set.of("data-cloud:entity-read"), "INTERNAL");
        addRoute("/api/v1/media/artifacts", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("media-writer"), Set.of("data-cloud:entity-write"), "INTERNAL");
        addRoute("/api/v1/media/artifacts/*", "GET", SensitivityLevel.CRITICAL, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.POLICY, true, true, true, true, Set.of("media-reader"), Set.of("data-cloud:entity-read"), "RESTRICTED");
        addRoute("/api/v1/media/artifacts/*", "DELETE", SensitivityLevel.CRITICAL, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.POLICY, true, true, true, true, Set.of("media-admin"), Set.of("data-cloud:entity-delete"), "RESTRICTED");
        addRoute("/api/v1/media/artifacts/*/jobs", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("media-writer"), Set.of("data-cloud:pipeline-execute"), "INTERNAL");
        addRoute("/api/v1/media/artifacts/*/transcripts", "GET", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("media-reader"), Set.of("data-cloud:entity-read"), "INTERNAL");
        addRoute("/api/v1/media/transcripts/search", "GET", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("media-reader"), Set.of("data-cloud:query-execute"), "INTERNAL");

        // Events - MEDIUM
        addRoute("/api/v1/events", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/events", "POST", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/events/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("data-reader"), Set.of("data-cloud:entity-read"), null);

        // Query - HIGH (data access validation)
        addRoute("/api/v1/query", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-reader"), Set.of("data-cloud:query-execute"), "INTERNAL");
        addRoute("/api/v1/federated-query", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("data-reader"), Set.of("data-cloud:query-execute"), "INTERNAL");

        // Pipelines - MEDIUM
        addRoute("/api/v1/pipelines", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("pipeline-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/pipelines", "POST", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("pipeline-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/pipelines/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("pipeline-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/pipelines/*", "PUT", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("pipeline-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/pipelines/*", "DELETE", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("pipeline-admin"), Set.of("data-cloud:entity-delete"), null);
        addRoute("/api/v1/pipelines/*/execute", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("pipeline-writer"), Set.of("data-cloud:pipeline-execute"), "INTERNAL");

        // Agent Catalog - MEDIUM
        addRoute("/api/v1/agents", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("agent-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/agents", "POST", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("agent-writer"), Set.of("data-cloud:entity-write"), null);
        addRoute("/api/v1/agents/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("agent-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/agents/*/execute", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.DATA_ACCESS, true, true, true, true, Set.of("agent-writer"), Set.of("data-cloud:pipeline-execute"), "INTERNAL");

        // Governance - HIGH (policy enforcement)
        addRoute("/api/v1/policies", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("governance-reader"), Set.of("data-cloud:policy-manage"), null);
        addRoute("/api/v1/policies", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.POLICY, true, true, true, true, Set.of("governance-admin"), Set.of("data-cloud:policy-manage"), "CONFIDENTIAL");
        addRoute("/api/v1/policies/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("governance-reader"), Set.of("data-cloud:policy-manage"), null);
        addRoute("/api/v1/policies/*", "PUT", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.POLICY, true, true, true, true, Set.of("governance-admin"), Set.of("data-cloud:policy-manage"), "CONFIDENTIAL");
        addRoute("/api/v1/policies/*", "DELETE", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.POLICY, true, true, true, true, Set.of("governance-admin"), Set.of("data-cloud:policy-manage"), "CONFIDENTIAL");

        // Approval Requests - HIGH (human-in-the-loop)
        addRoute("/api/v1/approvals", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("governance-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/approvals/*", "GET", SensitivityLevel.MEDIUM, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.RBAC, true, true, true, false, Set.of("governance-reader"), Set.of("data-cloud:entity-read"), null);
        addRoute("/api/v1/approvals/*/approve", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.POLICY, true, true, true, true, Set.of("governance-admin"), Set.of("data-cloud:governance-apply"), "CONFIDENTIAL");
        addRoute("/api/v1/approvals/*/deny", "POST", SensitivityLevel.HIGH, AuthenticationRequirement.REQUIRED,
                AuthorizationRequirement.POLICY, true, true, true, true, Set.of("governance-admin"), Set.of("data-cloud:governance-apply"), "CONFIDENTIAL");

        // Observability - LOW (read-only metrics)
        addRoute("/api/v1/metrics", "GET", SensitivityLevel.LOW, AuthenticationRequirement.OPTIONAL,
                AuthorizationRequirement.BASIC, false, false, false, false, Set.of(), Set.of(), null);
        addRoute("/api/v1/health", "GET", SensitivityLevel.PUBLIC, AuthenticationRequirement.NONE,
                AuthorizationRequirement.NONE, false, false, false, false, Set.of(), Set.of(), null);
    }

    private void addRoute(String route, String method, SensitivityLevel sensitivity,
                         AuthenticationRequirement authRequirement, AuthorizationRequirement authzRequirement,
                         boolean requiresTenantIsolation, boolean requiresRateLimiting, boolean requiresAuditLogging,
                         boolean requiresDataClassification, Set<String> requiredRoles, Set<String> requiredPermissions,
                         String dataClassification) {
        String key = method + ":" + route;
        sensitivityMap.put(key, new RouteSensitivity(
                route, method, sensitivity, authRequirement, authzRequirement,
                requiresTenantIsolation, requiresRateLimiting, requiresAuditLogging,
                requiresDataClassification, requiredRoles, requiredPermissions, dataClassification
        ));
    }

    public Optional<RouteSensitivity> getSensitivity(String route, String method) {
        String key = method + ":" + route;
        return Optional.ofNullable(sensitivityMap.get(key));
    }

    public void addCustomRoute(String route, String method, RouteSensitivity sensitivity) {
        String key = method + ":" + route;
        sensitivityMap.put(key, sensitivity);
    }

    public Map<String, RouteSensitivity> getAllRoutes() {
        return Map.copyOf(sensitivityMap);
    }
}
