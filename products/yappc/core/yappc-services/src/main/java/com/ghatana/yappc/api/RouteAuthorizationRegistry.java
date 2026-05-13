/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.yappc.api.generated.GeneratedRouteRegistry;
import com.ghatana.yappc.governance.route.AuthMode;
import com.ghatana.yappc.governance.route.RouteEntry;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Canonical route/action authorization registry for YAPPC.
 *
 * <p>Every YAPPC API route must be registered with its required permissions,
 * resource scope, and audit classification. This registry provides centralized
 * authorization enforcement that replaces scattered per-controller checks.
 *
 * <p>Routes are classified by:
 * <ul>
 *   <li>method - HTTP method (GET, POST, PUT, DELETE)</li>
 *   <li>path - API route pattern</li>
 *   <li>action - Business action being performed</li>
 *   <li>requiredPermission - Minimum permission required</li>
 *   <li>resourceScope - Resource isolation level (tenant, workspace, project, artifact)</li>
 *   <li>auditEventType - Audit event type for the action</li>
 *   <li>privacyClassification - Data privacy classification (public, internal, confidential, restricted)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Canonical route/action authorization registry for YAPPC API
 * @doc.layer api
 * @doc.pattern Registry
 */
public final class RouteAuthorizationRegistry {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationRegistry.class);

    private final Map<RouteKey, RouteDefinition> routes = new HashMap<>();
    private final Map<HttpMethod, List<RoutePattern>> routePatterns = new HashMap<>();
    private final YappcAuthorizationService authorizationService;

    public RouteAuthorizationRegistry(@NotNull YappcAuthorizationService authorizationService) {
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService is required");
        registerCanonicalRoutes();
    }

    /**
     * Authorizes a request against the registered route definition.
     *
     * @param request the HTTP request
     * @throws AccessDeniedException if authorization fails
     */
    public void authorize(@NotNull HttpRequest request) {
        HttpMethod method = request.getMethod();
        String path = firstNonBlank(request.getRelativePath(), request.getPath());
        
        // Try exact match first
        RouteKey key = new RouteKey(method, path);
        RouteDefinition definition = routes.get(key);
        
        // If no exact match, try pattern matching for parameterized routes
        Map<String, String> pathParameters = new HashMap<>();
        if (definition == null) {
            RoutePatternMatch match = findMatchingRoute(method, path);
            if (match == null) {
                log.warn("Unregistered route accessed: {} {}", method, path);
                throw new AccessDeniedException(
                    String.format("Route %s %s is not registered in the authorization registry", method, path)
                );
            }
            definition = match.definition();
            pathParameters = match.parameters();
        }

        // Bypass authentication for public routes
        if (definition.authMode() == AuthMode.PUBLIC) {
            log.debug("Public route accessed: {} {} - bypassing authentication", method, path);
            return;
        }

        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated: no principal attached to request");
        }

        // Extract resource scope from request headers/body/path params with normalized priority
        String tenantId = principal.getTenantId();
        String workspaceId = extractScopeValue("workspaceId", request, pathParameters);
        String projectId = extractScopeValue("projectId", request, pathParameters);
        String artifactId = extractScopeValue("artifactId", request, pathParameters);

        // Validate tenant scope (principal's tenant must match request tenant)
        if (definition.resourceScope() == ResourceScope.TENANT
            || definition.resourceScope() == ResourceScope.WORKSPACE
            || definition.resourceScope() == ResourceScope.PROJECT
            || definition.resourceScope() == ResourceScope.ARTIFACT) {
            if (!tenantId.equals(principal.getTenantId())) {
                log.warn("Tenant scope mismatch: principalTenant={}, requestTenant={}",
                    principal.getTenantId(), tenantId);
                throw new AccessDeniedException(
                    String.format("Principal tenant %s does not match request tenant %s",
                        principal.getTenantId(), tenantId)
                );
            }
        }

        // Enforce resource-level authorization based on scope
        switch (definition.resourceScope()) {
            case WORKSPACE -> {
                if (workspaceId == null || workspaceId.isBlank()) {
                    throw new AccessDeniedException("Workspace ID is required for this route");
                }
                authorizationService.authorizeWorkspaceAccess(
                    principal, workspaceId, definition.requiredPermission()
                );
            }
            case PROJECT -> {
                if (projectId == null || projectId.isBlank()) {
                    throw new AccessDeniedException("Project ID is required for this route");
                }
                if (workspaceId == null || workspaceId.isBlank()) {
                    throw new AccessDeniedException("Workspace ID is required for project-scoped routes");
                }
                authorizationService.authorizeProjectAccess(
                    principal, tenantId, workspaceId, projectId, definition.requiredPermission()
                );
            }
            case ARTIFACT -> {
                if (artifactId == null || artifactId.isBlank()) {
                    throw new AccessDeniedException("Artifact ID is required for this route");
                }
                if (projectId == null || projectId.isBlank()) {
                    throw new AccessDeniedException("Project ID is required for artifact-scoped routes");
                }
                if (workspaceId == null || workspaceId.isBlank()) {
                    throw new AccessDeniedException("Workspace ID is required for artifact-scoped routes");
                }
                authorizationService.authorizeArtifactAccess(
                    principal, tenantId, workspaceId, projectId, artifactId, definition.requiredPermission()
                );
            }
            case TENANT, SYSTEM -> {
                // Tenant or system scope - just check permission
                if (!authorizationService.hasPermission(principal, definition.requiredPermission())) {
                    throw new AccessDeniedException(
                        String.format("Principal %s lacks permission %s",
                            principal.getName(), definition.requiredPermission())
                    );
                }
            }
        }

        log.debug("Authorization granted: {} {} for principal={}",
            method, path, principal.getName());
    }

    /**
     * Registers all canonical YAPPC routes with their authorization requirements.
     * Routes are loaded from the generated route registry based on route-manifest.yaml.
     */
    private void registerCanonicalRoutes() {
        var manifest = GeneratedRouteRegistry.getManifest();
        var yappcServicesRoutes = manifest.getRoutesForServer("yappc-services");
        
        for (RouteEntry route : yappcServicesRoutes) {
            try {
                HttpMethod method = HttpMethod.valueOf(route.method());
                String permission = mapScopeToPermission(route.scopes());
                ResourceScope resourceScope = mapScopeToResourceScope(route.scopes());
                String action = route.operationId();
                String auditEventType = "yappc." + route.operationId();
                PrivacyClassification privacy = mapDefaultPrivacy(route.auth());
                AuthMode authMode = route.auth();
                
                registerRoute(method, route.path(), action, permission, resourceScope, auditEventType, privacy, authMode);
            } catch (Exception e) {
                log.error("Failed to register route {} {}: {}", route.method(), route.path(), e.getMessage());
                throw new RuntimeException("Failed to register route from manifest", e);
            }
        }
        
        log.info("Registered {} routes from generated manifest", routes.size());
    }

    /**
     * Maps scope strings to Permission enum values.
     * Default to PROJECT_READ if no scopes defined.
     */
    private String mapScopeToPermission(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Permission.ADMIN_SYSTEM; // Public routes get system permission
        }
        
        // Map common scope patterns to permissions
        for (String scope : scopes) {
            if (scope.contains(":read")) {
                if (scope.startsWith("workspace")) return Permission.WORKSPACE_READ;
                if (scope.startsWith("project")) return Permission.PROJECT_READ;
                if (scope.startsWith("artifact")) return Permission.PROJECT_READ; // Use project read for artifacts
                if (scope.startsWith("tenant")) return Permission.ADMIN_SYSTEM;
            }
            if (scope.contains(":write") || scope.contains(":update")) {
                if (scope.startsWith("workspace")) return Permission.WORKSPACE_UPDATE;
                if (scope.startsWith("project")) return Permission.PROJECT_UPDATE;
                if (scope.startsWith("artifact")) return Permission.PROJECT_UPDATE;
                if (scope.startsWith("tenant")) return Permission.ADMIN_SYSTEM;
            }
            if (scope.contains(":delete")) {
                if (scope.startsWith("workspace")) return Permission.WORKSPACE_DELETE;
                if (scope.startsWith("project")) return Permission.PROJECT_DELETE;
                if (scope.startsWith("artifact")) return Permission.PROJECT_DELETE;
            }
            if ("admin".equals(scope) || scope.endsWith(":admin") || scope.contains(":admin")) {
                return Permission.ADMIN_SYSTEM;
            }
        }
        
        // Default to project read if unknown scope
        return Permission.PROJECT_READ;
    }

    /**
     * Maps scope strings to ResourceScope enum values.
     */
    private ResourceScope mapScopeToResourceScope(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return ResourceScope.SYSTEM;
        }
        
        for (String scope : scopes) {
            if (scope.startsWith("workspace")) return ResourceScope.WORKSPACE;
            if (scope.startsWith("project")) return ResourceScope.PROJECT;
            if (scope.startsWith("artifact")) return ResourceScope.ARTIFACT;
            if (scope.startsWith("tenant")) return ResourceScope.TENANT;
        }
        
        return ResourceScope.SYSTEM;
    }

    /**
     * Maps auth mode to default privacy classification.
     */
    private PrivacyClassification mapDefaultPrivacy(AuthMode auth) {
        return switch (auth) {
            case PUBLIC -> PrivacyClassification.PUBLIC;
            case REQUIRED -> PrivacyClassification.INTERNAL;
            case OPTIONAL -> PrivacyClassification.INTERNAL;
        };
    }

    private void registerRoute(
            HttpMethod method,
            String path,
            String action,
            String requiredPermission,
            ResourceScope resourceScope,
            String auditEventType,
            PrivacyClassification privacyClassification,
            AuthMode authMode
    ) {
        RouteKey key = new RouteKey(method, path);
        RouteDefinition definition = new RouteDefinition(
            action, requiredPermission, resourceScope, auditEventType, privacyClassification, authMode
        );
        routes.put(key, definition);
        
        // Register pattern for parameterized routes
        if (path.contains("{")) {
            RoutePattern pattern = new RoutePattern(path, definition);
            routePatterns.computeIfAbsent(method, k -> new ArrayList<>()).add(pattern);
        }
    }

    /**
     * Normalized scope extraction with priority: path params > query params > headers > body.
     *
     * @param scopeName the scope parameter name (e.g., "workspaceId", "projectId")
     * @param request the HTTP request
     * @param pathParameters extracted path parameters
     * @return the scope value, or null if not found
     */
    @Nullable
    private String extractScopeValue(
            @NotNull String scopeName,
            @NotNull HttpRequest request,
            @NotNull Map<String, String> pathParameters
    ) {
        // Priority 1: Path parameters
        String value = pathParameters.get(scopeName);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // Priority 2: Query parameters
        Map<String, String> queryParams = extractQueryParameters(request);
        value = queryParams.get(scopeName);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // Priority 3: Headers (try kebab-case first, then camelCase)
        value = extractHeader(request, "X-" + scopeName.replace("Id", "-Id"));
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = extractHeader(request, "X-" + scopeName.substring(0, 1).toUpperCase() + scopeName.substring(1));
        if (value != null && !value.isBlank()) {
            return value;
        }

        // Priority 4: Request body (for POST/PUT with JSON)
        // Note: Body extraction would require parsing the request body, which may consume the stream
        // For now, we rely on headers/query/path params for scope to avoid stream consumption issues
        // Body scope extraction should be handled at the controller level after authorization

        return null;
    }

    private String extractHeader(HttpRequest request, String headerName) {
        String value = request.getHeader(HttpHeaders.of(headerName));
        if (value == null) {
            // Try with hyphen variant
            value = request.getHeader(HttpHeaders.of(headerName.replace("_", "-")));
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return "";
    }

    /**
     * Finds a matching route pattern for the given method and path.
     *
     * @param method the HTTP method
     * @param path the request path
     * @return the route pattern match with extracted parameters, or null if no match
     */
    /**
     * Checks if a route is public (bypasses authentication).
     *
     * @param method the HTTP method
     * @param path the request path
     * @return true if the route is public, false otherwise
     */
    public boolean isPublicRoute(@NotNull HttpMethod method, @NotNull String path) {
        try {
            RouteDefinition definition = getRouteDefinition(method, path);
            return definition != null && definition.authMode() == AuthMode.PUBLIC;
        } catch (Exception e) {
            // On error, fail closed (not public)
            return false;
        }
    }

    /**
     * Gets the route definition for a given method and path.
     *
     * @param method the HTTP method
     * @param path the request path
     * @return the route definition, or null if not found
     */
    @Nullable
    public RouteDefinition getRouteDefinition(@NotNull HttpMethod method, @NotNull String path) {
        // Try exact match first
        RouteKey key = new RouteKey(method, path);
        RouteDefinition definition = routes.get(key);
        
        // If no exact match, try pattern matching for parameterized routes
        if (definition == null) {
            RoutePatternMatch match = findMatchingRoute(method, path);
            if (match != null) {
                definition = match.definition();
            }
        }
        
        return definition;
    }

    @Nullable
    private RoutePatternMatch findMatchingRoute(@NotNull HttpMethod method, @NotNull String path) {
        List<RoutePattern> patterns = routePatterns.get(method);
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }

        for (RoutePattern pattern : patterns) {
            Matcher matcher = pattern.pattern().matcher(path);
            if (matcher.matches()) {
                Map<String, String> parameters = new HashMap<>();
                for (Map.Entry<String, Integer> entry : pattern.parameterNames().entrySet()) {
                    String paramName = entry.getKey();
                    int groupIndex = entry.getValue();
                    String value = matcher.group(groupIndex);
                    if (value != null) {
                        try {
                            // Decode URL-encoded path parameter values
                            String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
                            parameters.put(paramName, decodedValue);
                        } catch (IllegalArgumentException e) {
                            log.warn("Failed to decode path parameter {}: {}", paramName, value, e);
                            // Use raw value if decoding fails
                            parameters.put(paramName, value);
                        }
                    }
                }
                return new RoutePatternMatch(pattern.routePath(), pattern.definition(), parameters);
            }
        }

        return null;
    }

    /**
     * Extracts query parameters from the request.
     *
     * @param request the HTTP request
     * @return map of query parameter names to values
     */
    private Map<String, String> extractQueryParameters(@NotNull HttpRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        String query = request.getQuery();
        if (query != null && !query.isBlank()) {
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2) {
                    try {
                        String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                        queryParams.put(key, value);
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to decode query parameter: {}", param, e);
                        // Use raw value if decoding fails
                        queryParams.put(parts[0], parts[1]);
                    }
                } else if (parts.length == 1) {
                    try {
                        String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                        queryParams.put(key, "");
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to decode query parameter key: {}", parts[0], e);
                        queryParams.put(parts[0], "");
                    }
                }
            }
        }
        return queryParams;
    }

    /**
     * Resource isolation levels for authorization.
     */
    public enum ResourceScope {
        SYSTEM,      // No resource isolation (system-level operations)
        TENANT,      // Tenant-level isolation
        WORKSPACE,   // Workspace-level isolation
        PROJECT,     // Project-level isolation
        ARTIFACT     // Artifact-level isolation
    }

    /**
     * Data privacy classification for routes.
     */
    public enum PrivacyClassification {
        PUBLIC,         // No sensitive data
        INTERNAL,       // Internal company data
        CONFIDENTIAL,   // Confidential business data
        RESTRICTED      // Highly sensitive data (PII, secrets)
    }

    /**
     * Route definition with authorization requirements.
     */
    public record RouteDefinition(
            String action,
            String requiredPermission,
            ResourceScope resourceScope,
            String auditEventType,
            PrivacyClassification privacyClassification,
            AuthMode authMode
    ) {
        public RouteDefinition(
                String action,
                String requiredPermission,
                ResourceScope resourceScope,
                String auditEventType,
                PrivacyClassification privacyClassification
        ) {
            this(action, requiredPermission, resourceScope, auditEventType, privacyClassification, AuthMode.REQUIRED);
        }
    }

    /**
     * Composite key for route lookup.
     */
    private record RouteKey(HttpMethod method, String path) {}

    /**
     * Route pattern for parameterized route matching.
     */
    private record RoutePattern(
            String routePath,
            Pattern pattern,
            RouteDefinition definition,
            Map<String, Integer> parameterNames
    ) {
        RoutePattern(String path, RouteDefinition definition) {
            this(path, compilePattern(path), definition, extractParameterNames(path));
        }

        private static Pattern compilePattern(String path) {
            String regex = path.replaceAll("\\{([^/]+)\\}", "([^/]+)");
            return Pattern.compile("^" + regex + "$");
        }

        private static Map<String, Integer> extractParameterNames(String path) {
            Map<String, Integer> names = new HashMap<>();
            String[] segments = path.split("/");
            int groupIndex = 1;
            for (String segment : segments) {
                if (segment.startsWith("{") && segment.endsWith("}")) {
                    names.put(segment.substring(1, segment.length() - 1), groupIndex++);
                }
            }
            return names;
        }
    }

    /**
     * Result of matching a route pattern.
     */
    private record RoutePatternMatch(
            String routePath,
            RouteDefinition definition,
            Map<String, String> parameters
    ) {}

    // ─────────────────────────────────────────────────────────────────────────────
    // Controller-level validation helpers (task 1.2.5)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Validates that a scope provided in a request body matches the authorized scope.
     * This prevents scope escalation attacks where a user attempts to override their
     * authorized scope by passing a different scope in the request body.
     *
     * @param bodyScope the scope provided in the request body (e.g., "project:read")
     * @param authorizedScopes the set of scopes the principal is authorized for
     * @throws AccessDeniedException if bodyScope is not in authorizedScopes
     */
    public static void validateBodyScopeAgainstAuthorized(
            @Nullable String bodyScope,
            @NotNull Set<String> authorizedScopes
    ) {
        if (bodyScope == null || bodyScope.isBlank()) {
            // No scope in body, nothing to validate
            return;
        }

        if (authorizedScopes == null || authorizedScopes.isEmpty()) {
            throw new AccessDeniedException(
                String.format("Scope '%s' provided in request body but no authorized scopes available", bodyScope)
            );
        }

        // Check if body scope matches any authorized scope
        boolean isAuthorized = authorizedScopes.stream()
            .anyMatch(authorized -> scopeMatches(bodyScope, authorized));

        if (!isAuthorized) {
            throw new AccessDeniedException(
                String.format("Scope '%s' in request body is not authorized. Authorized scopes: %s",
                    bodyScope, authorizedScopes)
            );
        }
    }

    /**
     * Validates that multiple scopes provided in a request body match authorized scopes.
     *
     * @param bodyScopes the scopes provided in the request body
     * @param authorizedScopes the set of scopes the principal is authorized for
     * @throws AccessDeniedException if any bodyScope is not in authorizedScopes
     */
    public static void validateBodyScopesAgainstAuthorized(
            @Nullable Set<String> bodyScopes,
            @NotNull Set<String> authorizedScopes
    ) {
        if (bodyScopes == null || bodyScopes.isEmpty()) {
            // No scopes in body, nothing to validate
            return;
        }

        if (authorizedScopes == null || authorizedScopes.isEmpty()) {
            throw new AccessDeniedException(
                String.format("Scopes %s provided in request body but no authorized scopes available", bodyScopes)
            );
        }

        for (String bodyScope : bodyScopes) {
            if (bodyScope != null && !bodyScope.isBlank()) {
                validateBodyScopeAgainstAuthorized(bodyScope, authorizedScopes);
            }
        }
    }

    /**
     * Checks if two scope strings match for authorization purposes.
     * A scope matches if it is exactly equal or if it's a wildcard/admin scope.
     *
     * @param requested the requested scope
     * @param authorized the authorized scope
     * @return true if the scopes match for authorization
     */
    private static boolean scopeMatches(String requested, String authorized) {
        if (requested.equals(authorized)) {
            return true;
        }

        // Admin scope grants all other scopes
        if ("admin".equalsIgnoreCase(authorized)) {
            return true;
        }

        // Check resource-level wildcard (e.g., project:* matches project:read)
        if (authorized.endsWith(":*")) {
            String resource = authorized.substring(0, authorized.length() - 2);
            if (requested.startsWith(resource + ":")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the authorized scopes for a given route and principal.
     * This can be used by controllers to determine what scopes are authorized
     * before validating body scope.
     *
     * @param method the HTTP method
     * @param path the request path
     * @param principal the authenticated principal
     * @return the set of authorized scopes for this route
     * @throws AccessDeniedException if the route is not registered or principal is null
     */
    public Set<String> getAuthorizedScopesForRoute(
            @NotNull HttpMethod method,
            @NotNull String path,
            @NotNull Principal principal
    ) {
        RouteKey key = new RouteKey(method, path);
        RouteDefinition definition = routes.get(key);
        String matchedPath = path;
        
        if (definition == null) {
            RoutePatternMatch match = findMatchingRoute(method, path);
            if (match == null) {
                throw new AccessDeniedException(
                    String.format("Route %s %s is not registered in the authorization registry", method, path)
                );
            }
            definition = match.definition();
            matchedPath = match.routePath();
        }

        final String lookupPath = matchedPath;

        // Get the route entry from generated registry to extract scopes
        // Use the same server key as route registration (yappc-services)
        RouteEntry routeEntry = GeneratedRouteRegistry.getManifest()
            .getRoutesForServer("yappc-services")
            .stream()
            .filter(r -> r.method().equals(method.name()) && r.path().equals(lookupPath))
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException(
                String.format("Route %s %s not found in generated registry", method, lookupPath)
            ));

        return routeEntry.scopes();
    }
}
