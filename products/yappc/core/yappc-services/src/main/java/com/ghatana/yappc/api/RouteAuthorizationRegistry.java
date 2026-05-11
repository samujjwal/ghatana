/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.Permission;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated: no principal attached to request");
        }

        // Extract resource scope from request headers/body/path params with normalized priority
        String tenantId = principal.getTenantId();
        String workspaceId = extractScopeValue("workspaceId", request, pathParameters);
        String projectId = extractScopeValue("projectId", request, pathParameters);
        String artifactId = extractScopeValue("artifactId", request, pathParameters);
        String runId = extractScopeValue("runId", request, pathParameters);

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
                    principal, workspaceId, Permission.WORKSPACE_READ
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
     */
    private void registerCanonicalRoutes() {
        // Intent phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/intent/capture",
            "intent.capture", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.intent.capture", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/intent/analyze",
            "intent.analyze", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.intent.analyze", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.GET, "/api/v1/yappc/intent/:id",
            "intent.get", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.intent.get", PrivacyClassification.INTERNAL);

        // Shape phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/shape/derive",
            "shape.derive", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.shape.derive", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/shape/model",
            "shape.generate_model", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.shape.generate_model", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.GET, "/api/v1/yappc/shape/:id",
            "shape.get", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.shape.get", PrivacyClassification.INTERNAL);

        // Validation phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/validate",
            "validate.execute", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.validate.execute", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/validate/with-config",
            "validate.with_config", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.validate.with_config", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/validate/with-policy",
            "validate.with_policy", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.validate.with_policy", PrivacyClassification.INTERNAL);

        // Generation phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/generate",
            "generate.execute", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.generate.execute", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/generate/diff",
            "generate.diff", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.generate.diff", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/apply",
            "generate.apply", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.generate.apply", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/reject",
            "generate.reject", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.generate.reject", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/rollback",
            "generate.rollback", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.generate.rollback", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.GET, "/api/v1/yappc/generate/artifacts/:id",
            "generate.artifacts", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.generate.artifacts", PrivacyClassification.INTERNAL);

        // Run phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/run",
            "run.execute", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.run.execute", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/run/with-observation",
            "run.with_observation", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.run.with_observation", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/run/rollback",
            "run.rollback", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.run.rollback", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/run/promote",
            "run.promote", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.run.promote", PrivacyClassification.CONFIDENTIAL);

        // Observe phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/observe",
            "observe.collect", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.observe.collect", PrivacyClassification.INTERNAL);

        // Learn phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/learn",
            "learn.analyze", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.learn.analyze", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/learn/with-context",
            "learn.with_context", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.learn.with_context", PrivacyClassification.INTERNAL);

        // Evolve phase
        registerRoute(HttpMethod.POST, "/api/v1/yappc/evolve",
            "evolve.propose", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.evolve.propose", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/evolve/with-constraints",
            "evolve.with_constraints", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.evolve.with_constraints", PrivacyClassification.INTERNAL);

        // Full lifecycle
        registerRoute(HttpMethod.POST, "/api/v1/yappc/lifecycle/execute",
            "lifecycle.execute", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.lifecycle.execute", PrivacyClassification.CONFIDENTIAL);

        // Artifact compiler
        registerRoute(HttpMethod.POST, "/api/v1/yappc/artifact/graph/ingest",
            "artifact.ingest", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.artifact.ingest", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/artifact/graph/analyze",
            "artifact.analyze", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.artifact.analyze", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/artifact/graph/merge",
            "artifact.merge", Permission.PROJECT_UPDATE, ResourceScope.PROJECT,
            "yappc.artifact.merge", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/artifact/graph/query",
            "artifact.query", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.artifact.query", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/artifact/residual/analyze",
            "artifact.residual", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.artifact.residual", PrivacyClassification.INTERNAL);

        // Preview sessions
        registerRoute(HttpMethod.POST, "/api/v1/yappc/preview/session/create",
            "preview.create", Permission.PROJECT_READ, ResourceScope.ARTIFACT,
            "yappc.preview.create", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.POST, "/api/v1/yappc/preview/session/validate",
            "preview.validate", Permission.PROJECT_READ, ResourceScope.ARTIFACT,
            "yappc.preview.validate", PrivacyClassification.INTERNAL);

        // Phase cockpit packet
        registerRoute(HttpMethod.POST, "/api/v1/yappc/phase/packet",
            "phase.packet", Permission.PROJECT_READ, ResourceScope.PROJECT,
            "yappc.phase.packet", PrivacyClassification.INTERNAL);

        // Page artifacts
        registerRoute(HttpMethod.PUT, "/api/v1/page-artifacts/:artifactId/document",
            "page_artifact.save", Permission.PROJECT_UPDATE, ResourceScope.ARTIFACT,
            "yappc.page_artifact.save", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/document",
            "page_artifact.load", Permission.PROJECT_READ, ResourceScope.ARTIFACT,
            "yappc.page_artifact.load", PrivacyClassification.INTERNAL);
        registerRoute(HttpMethod.POST, "/api/v1/page-artifacts/:artifactId/review-decisions",
            "page_artifact.review", Permission.PROJECT_UPDATE, ResourceScope.ARTIFACT,
            "yappc.page_artifact.review", PrivacyClassification.CONFIDENTIAL);
        registerRoute(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/operation-log/export",
            "page_artifact.export", Permission.PROJECT_READ, ResourceScope.ARTIFACT,
            "yappc.page_artifact.export", PrivacyClassification.INTERNAL);

        // Health check (public)
        registerRoute(HttpMethod.GET, "/health",
            "health.check", Permission.ADMIN_SYSTEM, ResourceScope.SYSTEM,
            "yappc.health.check", PrivacyClassification.PUBLIC);

        // API info (public)
        registerRoute(HttpMethod.GET, "/api/v1/yappc/info",
            "api.info", Permission.ADMIN_SYSTEM, ResourceScope.SYSTEM,
            "yappc.api.info", PrivacyClassification.PUBLIC);

        log.info("Registered {} routes in authorization registry", routes.size());
    }

    private void registerRoute(
            HttpMethod method,
            String path,
            String action,
            String requiredPermission,
            ResourceScope resourceScope,
            String auditEventType,
            PrivacyClassification privacyClassification
    ) {
        RouteKey key = new RouteKey(method, path);
        RouteDefinition definition = new RouteDefinition(
            action, requiredPermission, resourceScope, auditEventType, privacyClassification
        );
        routes.put(key, definition);
        
        // Register pattern for parameterized routes
        if (path.contains(":")) {
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
                        parameters.put(paramName, value);
                    }
                }
                return new RoutePatternMatch(pattern.definition(), parameters);
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
                    queryParams.put(parts[0], parts[1]);
                } else if (parts.length == 1) {
                    queryParams.put(parts[0], "");
                }
            }
        }
        return queryParams;
    }

    private boolean hasPermission(Principal principal, String permission) {
        return authorizationService.hasPermission(principal, permission);
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
            PrivacyClassification privacyClassification
    ) {}

    /**
     * Composite key for route lookup.
     */
    private record RouteKey(HttpMethod method, String path) {}

    /**
     * Route pattern for parameterized route matching.
     */
    private record RoutePattern(
            Pattern pattern,
            RouteDefinition definition,
            Map<String, Integer> parameterNames
    ) {
        RoutePattern(String path, RouteDefinition definition) {
            this(compilePattern(path), definition, extractParameterNames(path));
        }

        private static Pattern compilePattern(String path) {
            String regex = path.replaceAll(":([^/]+)", "([^/]+)");
            return Pattern.compile("^" + regex + "$");
        }

        private static Map<String, Integer> extractParameterNames(String path) {
            Map<String, Integer> names = new HashMap<>();
            String[] segments = path.split("/");
            int groupIndex = 1;
            for (String segment : segments) {
                if (segment.startsWith(":")) {
                    names.put(segment.substring(1), groupIndex++);
                } else if (!segment.isEmpty()) {
                    groupIndex++;
                }
            }
            return names;
        }
    }

    /**
     * Result of matching a route pattern.
     */
    private record RoutePatternMatch(
            RouteDefinition definition,
            Map<String, String> parameters
    ) {}
}
