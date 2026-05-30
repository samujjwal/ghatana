package com.ghatana.datacloud.launcher.http.security;

import java.util.*;

/**
 * Route sensitivity matrix defining security requirements for all Data Cloud routes.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized security classification for all HTTP routes, specifying
 * authentication, authorization, policy, audit, and tenant isolation requirements.
 * This matrix is the single source of truth for route-level security enforcement.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RouteSensitivity matrix = RouteSensitivityMatrix.getRouteSensitivity("/api/v1/entities");
 * if (matrix.requiresAuthentication()) {
 *     // Enforce auth
 * }
 * if (matrix.requiresPolicyCheck()) {
 *     // Enforce policy
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized route security classification matrix
 * @doc.layer product
 * @doc.pattern Security Matrix
 */
public final class RouteSensitivityMatrix {

    /**
     * Route sensitivity levels.
     */
    public enum SensitivityLevel {
        PUBLIC,           // No authentication required (health, metrics)
        AUTHENTICATED,    // Authentication required, no special permissions
        SENSITIVE,        // Authentication + specific permissions required
        CRITICAL,         // Authentication + admin/governance permissions
        ADMIN_ONLY,       // Admin role only
        AI_AUTONOMY,      // AI/autonomy operations with additional safeguards
        MEDIA,            // Media processing with privacy requirements
        GOVERNANCE        // Governance/policy operations
    }

    /**
     * Route sensitivity definition.
     */
    public static class RouteSensitivity {
        private final String routePattern;
        private final SensitivityLevel sensitivity;
        private final Set<String> requiredRoles;
        private final Set<String> requiredPermissions;
        private final boolean requiresPolicyCheck;
        private final boolean requiresAuditLogging;
        private final boolean requiresTenantIsolation;
        private final boolean allowsBreakGlass;
        private final String description;

        public RouteSensitivity(
                String routePattern,
                SensitivityLevel sensitivity,
                Set<String> requiredRoles,
                Set<String> requiredPermissions,
                boolean requiresPolicyCheck,
                boolean requiresAuditLogging,
                boolean requiresTenantIsolation,
                boolean allowsBreakGlass,
                String description) {
            this.routePattern = routePattern;
            this.sensitivity = sensitivity;
            this.requiredRoles = Collections.unmodifiableSet(new HashSet<>(requiredRoles));
            this.requiredPermissions = Collections.unmodifiableSet(new HashSet<>(requiredPermissions));
            this.requiresPolicyCheck = requiresPolicyCheck;
            this.requiresAuditLogging = requiresAuditLogging;
            this.requiresTenantIsolation = requiresTenantIsolation;
            this.allowsBreakGlass = allowsBreakGlass;
            this.description = description;
        }

        public String getRoutePattern() {
            return routePattern;
        }

        public SensitivityLevel getSensitivity() {
            return sensitivity;
        }

        public Set<String> getRequiredRoles() {
            return requiredRoles;
        }

        public Set<String> getRequiredPermissions() {
            return requiredPermissions;
        }

        public boolean requiresAuthentication() {
            return sensitivity != SensitivityLevel.PUBLIC;
        }

        public boolean requiresPolicyCheck() {
            return requiresPolicyCheck;
        }

        public boolean requiresAuditLogging() {
            return requiresAuditLogging;
        }

        public boolean requiresTenantIsolation() {
            return requiresTenantIsolation;
        }

        public boolean allowsBreakGlass() {
            return allowsBreakGlass;
        }

        public String getDescription() {
            return description;
        }

        public boolean matches(String path) {
            if (routePattern.endsWith("/**")) {
                String prefix = routePattern.substring(0, routePattern.length() - 3);
                return path.startsWith(prefix);
            }
            if (routePattern.endsWith("*")) {
                String prefix = routePattern.substring(0, routePattern.length() - 1);
                return path.startsWith(prefix);
            }
            return path.equals(routePattern);
        }
    }

    private static final Map<String, RouteSensitivity> ROUTE_MATRIX = new HashMap<>();

    static {
        // Initialize route matrix
        initializePublicRoutes();
        initializeAuthenticatedRoutes();
        initializeSensitiveRoutes();
        initializeCriticalRoutes();
        initializeAdminOnlyRoutes();
        initializeAiAutonomyRoutes();
        initializeMediaRoutes();
        initializeGovernanceRoutes();
    }

    private static void initializePublicRoutes() {
        // Health and monitoring
        addRoute("/health", SensitivityLevel.PUBLIC, Set.of(), Set.of(), false, false, false, false,
                "Health check endpoint");
        addRoute("/ready", SensitivityLevel.PUBLIC, Set.of(), Set.of(), false, false, false, false,
                "Readiness check endpoint");
        addRoute("/live", SensitivityLevel.PUBLIC, Set.of(), Set.of(), false, false, false, false,
                "Liveness check endpoint");
        addRoute("/info", SensitivityLevel.PUBLIC, Set.of(), Set.of(), false, false, false, false,
                "Service information endpoint");
        addRoute("/metrics", SensitivityLevel.PUBLIC, Set.of(), Set.of(), false, false, false, false,
                "Prometheus metrics endpoint");
        addRoute("/api/v1/status/**", SensitivityLevel.PUBLIC, Set.of(), Set.of(), false, false, false, false,
                "Status endpoints");
    }

    private static void initializeAuthenticatedRoutes() {
        // Read-only data access
        addRoute("/api/v1/entities/*/get", SensitivityLevel.AUTHENTICATED, Set.of(), Set.of("entity:read"), false, true, true, false,
                "Get single entity");
        addRoute("/api/v1/entities/*/list", SensitivityLevel.AUTHENTICATED, Set.of(), Set.of("entity:read"), false, true, true, false,
                "List entities");
        addRoute("/api/v1/collections/*/get", SensitivityLevel.AUTHENTICATED, Set.of(), Set.of("collection:read"), false, true, true, false,
                "Get collection");
        addRoute("/api/v1/datasets/*/get", SensitivityLevel.AUTHENTICATED, Set.of(), Set.of("dataset:read"), false, true, true, false,
                "Get dataset");
        addRoute("/api/v1/events/*/tail", SensitivityLevel.AUTHENTICATED, Set.of(), Set.of("event:read"), false, true, true, false,
                "Tail events");
        addRoute("/api/v1/query/execute", SensitivityLevel.AUTHENTICATED, Set.of(), Set.of("query:execute"), false, true, true, false,
                "Execute query");
    }

    private static void initializeSensitiveRoutes() {
        // Write operations
        addRoute("/api/v1/entities/*/create", SensitivityLevel.SENSITIVE, Set.of(), Set.of("entity:write"), true, true, true, false,
                "Create entity");
        addRoute("/api/v1/entities/*/update", SensitivityLevel.SENSITIVE, Set.of(), Set.of("entity:write"), true, true, true, false,
                "Update entity");
        addRoute("/api/v1/entities/*/delete", SensitivityLevel.SENSITIVE, Set.of(), Set.of("entity:delete"), true, true, true, false,
                "Delete entity");
        addRoute("/api/v1/collections/*/create", SensitivityLevel.SENSITIVE, Set.of(), Set.of("collection:write"), true, true, true, false,
                "Create collection");
        addRoute("/api/v1/datasets/*/create", SensitivityLevel.SENSITIVE, Set.of(), Set.of("dataset:write"), true, true, true, false,
                "Create dataset");
        addRoute("/api/v1/events/*/append", SensitivityLevel.SENSITIVE, Set.of(), Set.of("event:write"), true, true, true, false,
                "Append event");
        
        // Pipeline operations
        addRoute("/api/v1/pipelines/*/create", SensitivityLevel.SENSITIVE, Set.of(), Set.of("pipeline:create"), true, true, true, false,
                "Create pipeline");
        addRoute("/api/v1/pipelines/*/update", SensitivityLevel.SENSITIVE, Set.of(), Set.of("pipeline:update"), true, true, true, false,
                "Update pipeline");
        addRoute("/api/v1/pipelines/*/execute", SensitivityLevel.SENSITIVE, Set.of(), Set.of("pipeline:execute"), true, true, true, false,
                "Execute pipeline");
        addRoute("/api/v1/pipelines/*/cancel", SensitivityLevel.SENSITIVE, Set.of(), Set.of("pipeline:cancel"), true, true, true, false,
                "Cancel pipeline");
        
        // Action Plane operations
        addRoute("/api/v1/action/patterns/*/create", SensitivityLevel.SENSITIVE, Set.of(), Set.of("pattern:create"), true, true, true, false,
                "Create pattern");
        addRoute("/api/v1/action/patterns/*/promote", SensitivityLevel.SENSITIVE, Set.of(), Set.of("pattern:promote"), true, true, true, false,
                "Promote pattern");
    }

    private static void initializeCriticalRoutes() {
        // Governance and compliance
        addRoute("/api/v1/governance/policies/*/create", SensitivityLevel.CRITICAL, Set.of("admin", "governance"), Set.of("policy:write"), true, true, true, true,
                "Create governance policy");
        addRoute("/api/v1/governance/policies/*/update", SensitivityLevel.CRITICAL, Set.of("admin", "governance"), Set.of("policy:write"), true, true, true, true,
                "Update governance policy");
        addRoute("/api/v1/governance/policies/*/delete", SensitivityLevel.CRITICAL, Set.of("admin", "governance"), Set.of("policy:delete"), true, true, true, true,
                "Delete governance policy");
        addRoute("/api/v1/governance/retention/*/create", SensitivityLevel.CRITICAL, Set.of("admin", "governance"), Set.of("retention:write"), true, true, true, true,
                "Create retention policy");
        
        // Data lifecycle
        addRoute("/api/v1/data/lifecycle/purge", SensitivityLevel.CRITICAL, Set.of("admin", "operator"), Set.of("data:purge"), true, true, true, true,
                "Purge data");
        addRoute("/api/v1/data/lifecycle/archive", SensitivityLevel.CRITICAL, Set.of("admin", "operator"), Set.of("data:archive"), true, true, true, true,
                "Archive data");
        
        // Security operations
        addRoute("/api/v1/security/keys/*/rotate", SensitivityLevel.CRITICAL, Set.of("admin", "security"), Set.of("key:rotate"), true, true, true, true,
                "Rotate encryption keys");
        addRoute("/api/v1/security/audit/*/export", SensitivityLevel.CRITICAL, Set.of("admin", "auditor"), Set.of("audit:export"), true, true, true, false,
                "Export audit logs");
    }

    private static void initializeAdminOnlyRoutes() {
        // System configuration
        addRoute("/api/v1/admin/config/*/update", SensitivityLevel.ADMIN_ONLY, Set.of("admin"), Set.of("config:write"), true, true, true, true,
                "Update system configuration");
        addRoute("/api/v1/admin/users/*/create", SensitivityLevel.ADMIN_ONLY, Set.of("admin"), Set.of("user:create"), true, true, true, true,
                "Create user");
        addRoute("/api/v1/admin/users/*/delete", SensitivityLevel.ADMIN_ONLY, Set.of("admin"), Set.of("user:delete"), true, true, true, true,
                "Delete user");
        addRoute("/api/v1/admin/tenants/*/create", SensitivityLevel.ADMIN_ONLY, Set.of("admin"), Set.of("tenant:create"), true, true, true, true,
                "Create tenant");
        addRoute("/api/v1/admin/tenants/*/delete", SensitivityLevel.ADMIN_ONLY, Set.of("admin"), Set.of("tenant:delete"), true, true, true, true,
                "Delete tenant");
        
        // Plugin management
        addRoute("/api/v1/plugins/*/install", SensitivityLevel.ADMIN_ONLY, Set.of("admin"), Set.of("plugin:install"), true, true, true, true,
                "Install plugin");
        addRoute("/api/v1/plugins/*/uninstall", SensitivityLevel.ADMIN_ONLY, Set.of("admin"), Set.of("plugin:uninstall"), true, true, true, true,
                "Uninstall plugin");
    }

    private static void initializeAiAutonomyRoutes() {
        // AI operations
        addRoute("/api/v1/ai/models/*/deploy", SensitivityLevel.AI_AUTONOMY, Set.of("admin", "ai-operator"), Set.of("ai:deploy"), true, true, true, true,
                "Deploy AI model");
        addRoute("/api/v1/ai/assist/*/execute", SensitivityLevel.AI_AUTONOMY, Set.of(), Set.of("ai:assist"), true, true, true, false,
                "Execute AI assist");
        addRoute("/api/v1/ai/patterns/*/generate", SensitivityLevel.AI_AUTONOMY, Set.of(), Set.of("ai:generate"), true, true, true, false,
                "Generate AI pattern");
        
        // Autonomy operations
        addRoute("/api/v1/autonomy/agents/*/execute", SensitivityLevel.AI_AUTONOMY, Set.of(), Set.of("agent:execute"), true, true, true, false,
                "Execute autonomous agent");
        addRoute("/api/v1/autonomy/workflows/*/approve", SensitivityLevel.AI_AUTONOMY, Set.of("admin", "approver"), Set.of("workflow:approve"), true, true, true, true,
                "Approve autonomous workflow");
    }

    private static void initializeMediaRoutes() {
        // Media operations
        addRoute("/api/v1/media/artifacts/*/upload", SensitivityLevel.MEDIA, Set.of(), Set.of("media:upload"), true, true, true, false,
                "Upload media artifact");
        addRoute("/api/v1/media/artifacts/*/delete", SensitivityLevel.MEDIA, Set.of(), Set.of("media:delete"), true, true, true, true,
                "Delete media artifact");
        addRoute("/api/v1/media/transcripts/*/create", SensitivityLevel.MEDIA, Set.of(), Set.of("media:transcribe"), true, true, true, false,
                "Create transcript");
        addRoute("/api/v1/media/frames/*/extract", SensitivityLevel.MEDIA, Set.of(), Set.of("media:extract"), true, true, true, false,
                "Extract frames");
        addRoute("/api/v1/media/consent/*/grant", SensitivityLevel.MEDIA, Set.of(), Set.of("media:consent"), true, true, true, false,
                "Grant media consent");
        addRoute("/api/v1/media/consent/*/revoke", SensitivityLevel.MEDIA, Set.of(), Set.of("media:consent"), true, true, true, true,
                "Revoke media consent");
    }

    private static void initializeGovernanceRoutes() {
        // Policy evaluation
        addRoute("/api/v1/governance/policies/*/evaluate", SensitivityLevel.GOVERNANCE, Set.of(), Set.of("policy:evaluate"), true, true, true, false,
                "Evaluate policy");
        addRoute("/api/v1/governance/compliance/*/check", SensitivityLevel.GOVERNANCE, Set.of(), Set.of("compliance:check"), true, true, true, false,
                "Check compliance");
        addRoute("/api/v1/governance/consent/*/request", SensitivityLevel.GOVERNANCE, Set.of(), Set.of("consent:request"), true, true, true, false,
                "Request consent");
        addRoute("/api/v1/governance/consent/*/approve", SensitivityLevel.GOVERNANCE, Set.of("admin", "approver"), Set.of("consent:approve"), true, true, true, true,
                "Approve consent request");
    }

    private static void addRoute(String pattern, SensitivityLevel sensitivity, Set<String> roles,
                                 Set<String> permissions, boolean policyCheck, boolean auditLog,
                                 boolean tenantIsolation, boolean breakGlass, String description) {
        ROUTE_MATRIX.put(pattern, new RouteSensitivity(pattern, sensitivity, roles, permissions,
                policyCheck, auditLog, tenantIsolation, breakGlass, description));
    }

    /**
     * Gets the route sensitivity for a specific path.
     */
    public static RouteSensitivity getRouteSensitivity(String path) {
        // Exact match first
        RouteSensitivity exact = ROUTE_MATRIX.get(path);
        if (exact != null) {
            return exact;
        }

        // Pattern match
        for (RouteSensitivity sensitivity : ROUTE_MATRIX.values()) {
            if (sensitivity.matches(path)) {
                return sensitivity;
            }
        }

        // Default to authenticated for unknown routes
        return new RouteSensitivity(path, SensitivityLevel.AUTHENTICATED, Set.of(), Set.of(),
                true, true, true, false, "Default authenticated route");
    }

    /**
     * Gets all route sensitivities.
     */
    public static Map<String, RouteSensitivity> getAllRouteSensitivities() {
        return Collections.unmodifiableMap(ROUTE_MATRIX);
    }

    /**
     * Checks if a route requires authentication.
     */
    public static boolean requiresAuthentication(String path) {
        return getRouteSensitivity(path).requiresAuthentication();
    }

    /**
     * Checks if a route requires policy check.
     */
    public static boolean requiresPolicyCheck(String path) {
        return getRouteSensitivity(path).requiresPolicyCheck();
    }

    /**
     * Checks if a route requires audit logging.
     */
    public static boolean requiresAuditLogging(String path) {
        return getRouteSensitivity(path).requiresAuditLogging();
    }

    /**
     * Checks if a route requires tenant isolation.
     */
    public static boolean requiresTenantIsolation(String path) {
        return getRouteSensitivity(path).requiresTenantIsolation();
    }

    /**
     * Gets required roles for a route.
     */
    public static Set<String> getRequiredRoles(String path) {
        return getRouteSensitivity(path).getRequiredRoles();
    }

    /**
     * Gets required permissions for a route.
     */
    public static Set<String> getRequiredPermissions(String path) {
        return getRouteSensitivity(path).getRequiredPermissions();
    }
}
