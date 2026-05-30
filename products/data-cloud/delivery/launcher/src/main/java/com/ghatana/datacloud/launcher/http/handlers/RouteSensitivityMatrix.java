package com.ghatana.datacloud.launcher.http.handlers;

import java.util.*;

/**
 * Route sensitivity matrix for Data Cloud security enforcement.
 *
 * <p><b>Purpose</b><br>
 * Defines sensitivity levels for all Data Cloud routes to ensure proper
 * backend policy enforcement before handler logic execution.
 *
 * <p><b>Sensitivity Levels</b><br>
 * - PUBLIC: No authentication required (health, metrics, info)
 * - AUTHENTICATED: Valid identity required (read-only operations)
 * - SENSITIVE: Tenant isolation and policy checks required (writes, data access)
 * - CRITICAL: Multi-factor authorization and audit required (destructive actions)
 * - ADMIN_ONLY: Platform admin role required (system configuration)
 * - AI_AUTONOMY: Approval workflow required for AI-generated actions
 * - MEDIA: Privacy consent and retention policy checks required
 * - GOVERNANCE: Policy enforcement and compliance checks required
 *
 * @doc.type class
 * @doc.purpose Route sensitivity classification for security enforcement
 * @doc.layer product
 * @doc.pattern Security Matrix
 */
public class RouteSensitivityMatrix {

    /**
     * Route sensitivity levels.
     */
    public enum SensitivityLevel {
        PUBLIC,
        AUTHENTICATED,
        SENSITIVE,
        CRITICAL,
        ADMIN_ONLY,
        AI_AUTONOMY,
        MEDIA,
        GOVERNANCE
    }

    /**
     * Route sensitivity configuration.
     */
    public static class RouteConfig {
        private final String path;
        private final String method;
        private final SensitivityLevel sensitivity;
        private final Set<String> requiredPermissions;
        private final boolean requiresTenantIsolation;
        private final boolean requiresAudit;
        private final boolean requiresPolicyCheck;
        private final boolean requiresApproval;

        public RouteConfig(String path, String method, SensitivityLevel sensitivity,
                          Set<String> requiredPermissions, boolean requiresTenantIsolation,
                          boolean requiresAudit, boolean requiresPolicyCheck, boolean requiresApproval) {
            this.path = path;
            this.method = method;
            this.sensitivity = sensitivity;
            this.requiredPermissions = requiredPermissions;
            this.requiresTenantIsolation = requiresTenantIsolation;
            this.requiresAudit = requiresAudit;
            this.requiresPolicyCheck = requiresPolicyCheck;
            this.requiresApproval = requiresApproval;
        }

        public String getPath() { return path; }
        public String getMethod() { return method; }
        public SensitivityLevel getSensitivity() { return sensitivity; }
        public Set<String> getRequiredPermissions() { return requiredPermissions; }
        public boolean requiresTenantIsolation() { return requiresTenantIsolation; }
        public boolean requiresAudit() { return requiresAudit; }
        public boolean requiresPolicyCheck() { return requiresPolicyCheck; }
        public boolean requiresApproval() { return requiresApproval; }
    }

    private static final Map<String, RouteConfig> ROUTE_MATRIX = new HashMap<>();

    static {
        // ============ PUBLIC ROUTES ============
        // Health and metrics
        addRoute("/health", "GET", SensitivityLevel.PUBLIC, Set.of(), false, false, false, false);
        addRoute("/ready", "GET", SensitivityLevel.PUBLIC, Set.of(), false, false, false, false);
        addRoute("/live", "GET", SensitivityLevel.PUBLIC, Set.of(), false, false, false, false);
        addRoute("/info", "GET", SensitivityLevel.PUBLIC, Set.of(), false, false, false, false);
        addRoute("/metrics", "GET", SensitivityLevel.PUBLIC, Set.of(), false, false, false, false);

        // ============ AUTHENTICATED ROUTES ============
        // Read-only entity operations
        addRoute("/api/v1/entities", "GET", SensitivityLevel.AUTHENTICATED, 
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/entities/:id", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/collections", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/collections/:id", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/datasets", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/datasets/:id", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);

        // Read-only event operations
        addRoute("/api/v1/events", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/events/tail", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/events/replay", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);

        // Read-only pipeline operations
        addRoute("/api/v1/pipelines", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/pipelines/:id", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);

        // Read-only agent catalog
        addRoute("/api/v1/agents", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);
        addRoute("/api/v1/agents/:id", "GET", SensitivityLevel.AUTHENTICATED,
                Set.of("data-cloud:entity-read"), true, true, false, false);

        // ============ SENSITIVE ROUTES ============
        // Entity write operations
        addRoute("/api/v1/entities", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/entities/:id", "PUT", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/entities/:id", "DELETE", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-delete"), true, true, true, false);

        // Collection write operations
        addRoute("/api/v1/collections", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/collections/:id", "PUT", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/collections/:id", "DELETE", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-delete"), true, true, true, false);

        // Dataset write operations
        addRoute("/api/v1/datasets", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/datasets/:id", "PUT", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/datasets/:id", "DELETE", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-delete"), true, true, true, false);

        // Event append
        addRoute("/api/v1/events", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);

        // Pipeline write operations
        addRoute("/api/v1/pipelines", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:pipeline-execute"), true, true, true, false);
        addRoute("/api/v1/pipelines/:id", "PUT", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:pipeline-execute"), true, true, true, false);
        addRoute("/api/v1/pipelines/:id", "DELETE", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-delete"), true, true, true, false);
        addRoute("/api/v1/pipelines/:id/execute", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:pipeline-execute"), true, true, true, false);

        // Query execution
        addRoute("/api/v1/query", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:query-execute"), true, true, true, false);

        // Connector operations
        addRoute("/api/v1/connectors", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/connectors/:id", "PUT", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/connectors/:id", "DELETE", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-delete"), true, true, true, false);
        addRoute("/api/v1/connectors/:id/test", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/connectors/:id/sync", "POST", SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-write"), true, true, true, false);

        // ============ CRITICAL ROUTES ============
        // Destructive actions
        addRoute("/api/v1/collections/:id/purge", "POST", SensitivityLevel.CRITICAL,
                Set.of("data-cloud:entity-delete", "data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/datasets/:id/purge", "POST", SensitivityLevel.CRITICAL,
                Set.of("data-cloud:entity-delete", "data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/entities/:id/purge", "POST", SensitivityLevel.CRITICAL,
                Set.of("data-cloud:entity-delete", "data-cloud:governance-apply"), true, true, true, true);

        // Pipeline cancellation
        addRoute("/api/v1/pipelines/:id/cancel", "POST", SensitivityLevel.CRITICAL,
                Set.of("data-cloud:pipeline-execute"), true, true, true, true);

        // ============ ADMIN_ONLY ROUTES ============
        // System configuration
        addRoute("/api/v1/admin/config", "GET", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, false);
        addRoute("/api/v1/admin/config", "PUT", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, true);

        // User management
        addRoute("/api/v1/admin/users", "GET", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, false);
        addRoute("/api/v1/admin/users", "POST", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, true);
        addRoute("/api/v1/admin/users/:id", "PUT", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, true);
        addRoute("/api/v1/admin/users/:id", "DELETE", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, true);

        // Tenant management
        addRoute("/api/v1/admin/tenants", "GET", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, false);
        addRoute("/api/v1/admin/tenants", "POST", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, true);
        addRoute("/api/v1/admin/tenants/:id", "PUT", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, true);
        addRoute("/api/v1/admin/tenants/:id", "DELETE", SensitivityLevel.ADMIN_ONLY,
                Set.of("data-cloud:policy-manage"), false, true, true, true);

        // ============ AI_AUTONOMY ROUTES ============
        // AI-generated actions requiring approval
        addRoute("/api/v1/ai/alerts/:id/remediate", "POST", SensitivityLevel.AI_AUTONOMY,
                Set.of("data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/ai/suggestions/:id/apply", "POST", SensitivityLevel.AI_AUTONOMY,
                Set.of("data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/ai/patterns/:id/promote", "POST", SensitivityLevel.AI_AUTONOMY,
                Set.of("data-cloud:pipeline-execute", "data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/ai/agents/:id/execute", "POST", SensitivityLevel.AI_AUTONOMY,
                Set.of("data-cloud:pipeline-execute"), true, true, true, true);

        // Autonomy operations
        addRoute("/api/v1/autonomy/decisions", "POST", SensitivityLevel.AI_AUTONOMY,
                Set.of("data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/autonomy/decisions/:id/approve", "POST", SensitivityLevel.AI_AUTONOMY,
                Set.of("data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/autonomy/decisions/:id/deny", "POST", SensitivityLevel.AI_AUTONOMY,
                Set.of("data-cloud:governance-apply"), true, true, true, true);

        // ============ MEDIA ROUTES ============
        // Media artifact operations with privacy/retention checks
        addRoute("/api/v1/media/artifacts", "POST", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/media/artifacts", "GET", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-read"), true, true, true, false);
        addRoute("/api/v1/media/artifacts/:id", "GET", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-read"), true, true, true, false);
        addRoute("/api/v1/media/artifacts/:id", "DELETE", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-delete", "data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/media/artifacts/:id/consent", "POST", SensitivityLevel.MEDIA,
                Set.of("data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/media/artifacts/:id/retention", "PUT", SensitivityLevel.MEDIA,
                Set.of("data-cloud:governance-apply"), true, true, true, true);

        // Media processing jobs
        addRoute("/api/v1/media/jobs", "POST", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/media/jobs/:id", "GET", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-read"), true, true, true, false);
        addRoute("/api/v1/media/jobs/:id/start", "POST", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-write"), true, true, true, false);
        addRoute("/api/v1/media/jobs/:id/cancel", "POST", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-write"), true, true, true, true);

        // Transcript operations
        addRoute("/api/v1/media/transcripts", "GET", SensitivityLevel.MEDIA,
                Set.of("data-cloud:entity-read"), true, true, true, false);
        addRoute("/api/v1/media/transcripts/search", "POST", SensitivityLevel.MEDIA,
                Set.of("data-cloud:query-execute"), true, true, true, false);

        // ============ GOVERNANCE ROUTES ============
        // Policy management
        addRoute("/api/v1/governance/policies", "GET", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, false);
        addRoute("/api/v1/governance/policies", "POST", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, true);
        addRoute("/api/v1/governance/policies/:id", "PUT", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, true);
        addRoute("/api/v1/governance/policies/:id", "DELETE", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, true);
        addRoute("/api/v1/governance/policies/:id/apply", "POST", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:governance-apply"), true, true, true, true);

        // Retention policies
        addRoute("/api/v1/governance/retention", "GET", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, false);
        addRoute("/api/v1/governance/retention", "POST", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, true);
        addRoute("/api/v1/governance/retention/:id", "PUT", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, true);
        addRoute("/api/v1/governance/retention/:id", "DELETE", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, true);

        // Redaction
        addRoute("/api/v1/governance/redaction", "POST", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:governance-apply"), true, true, true, true);
        addRoute("/api/v1/governance/redaction/:id", "GET", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:entity-read"), true, true, true, false);

        // Compliance
        addRoute("/api/v1/governance/compliance", "GET", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, false);
        addRoute("/api/v1/governance/compliance/reports", "GET", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, false);

        // Audit log
        addRoute("/api/v1/governance/audit", "GET", SensitivityLevel.GOVERNANCE,
                Set.of("data-cloud:policy-manage"), true, true, true, false);
    }

    private static void addRoute(String path, String method, SensitivityLevel sensitivity,
                                 Set<String> requiredPermissions, boolean requiresTenantIsolation,
                                 boolean requiresAudit, boolean requiresPolicyCheck, boolean requiresApproval) {
        String key = method + ":" + path;
        ROUTE_MATRIX.put(key, new RouteConfig(path, method, sensitivity, requiredPermissions,
                requiresTenantIsolation, requiresAudit, requiresPolicyCheck, requiresApproval));
    }

    /**
     * Gets route configuration for a given path and method.
     */
    public static Optional<RouteConfig> getRouteConfig(String path, String method) {
        // Try exact match first
        String exactKey = method + ":" + path;
        if (ROUTE_MATRIX.containsKey(exactKey)) {
            return Optional.of(ROUTE_MATRIX.get(exactKey));
        }

        // Try pattern matching for paths with parameters
        for (Map.Entry<String, RouteConfig> entry : ROUTE_MATRIX.entrySet()) {
            String patternPath = entry.getValue().getPath();
            if (patternMatches(patternPath, path) && entry.getValue().getMethod().equals(method)) {
                return Optional.of(entry.getValue());
            }
        }

        // Default to SENSITIVE for unknown routes (fail-closed)
        return Optional.of(new RouteConfig(path, method, SensitivityLevel.SENSITIVE,
                Set.of("data-cloud:entity-read"), true, true, true, false));
    }

    /**
     * Checks if a path pattern matches a given path.
     */
    private static boolean patternMatches(String pattern, String path) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");

        if (patternParts.length != pathParts.length) {
            return false;
        }

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];

            if (patternPart.startsWith(":")) {
                // Parameter match
                continue;
            } else if (!patternPart.equals(pathPart)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets all routes by sensitivity level.
     */
    public static List<RouteConfig> getRoutesBySensitivity(SensitivityLevel sensitivity) {
        return ROUTE_MATRIX.values().stream()
                .filter(config -> config.getSensitivity() == sensitivity)
                .toList();
    }

    /**
     * Gets all routes requiring tenant isolation.
     */
    public static List<RouteConfig> getRoutesRequiringTenantIsolation() {
        return ROUTE_MATRIX.values().stream()
                .filter(RouteConfig::requiresTenantIsolation)
                .toList();
    }

    /**
     * Gets all routes requiring audit.
     */
    public static List<RouteConfig> getRoutesRequiringAudit() {
        return ROUTE_MATRIX.values().stream()
                .filter(RouteConfig::requiresAudit)
                .toList();
    }

    /**
     * Gets all routes requiring policy check.
     */
    public static List<RouteConfig> getRoutesRequiringPolicyCheck() {
        return ROUTE_MATRIX.values().stream()
                .filter(RouteConfig::requiresPolicyCheck)
                .toList();
    }

    /**
     * Gets all routes requiring approval.
     */
    public static List<RouteConfig> getRoutesRequiringApproval() {
        return ROUTE_MATRIX.values().stream()
                .filter(RouteConfig::requiresApproval)
                .toList();
    }
}
