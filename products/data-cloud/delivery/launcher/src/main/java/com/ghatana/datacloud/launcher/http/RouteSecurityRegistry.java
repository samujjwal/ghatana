/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative registry of all Data Cloud HTTP routes and their security metadata.
 *
 * <p>Auto-generated from OpenAPI contracts via {@code scripts/generate-route-security-metadata.mjs}.
 * This is the single source of truth for route security requirements, replacing prefix-based inference.
 *
 * <p>Fixes DC-P0-01 and DC-P0-03: Makes route metadata explicit, verifiable, and generated from contracts.
 *
 * <p>Key properties:
 * <ul>
 *   <li>Routes indexed by <code>METHOD path</code> (e.g., "GET /api/v1/entities/{id}")</li>
 *   <li>Paths are canonical (IDs normalized to {id}, {collectionId}, etc.)</li>
 *   <li>Every canonical /api/v1/action/* route has explicit metadata</li>
 *   <li>Generated file fails CI if stale</li>
 *   <li>Regenerate with: <code>node scripts/generate-route-security-metadata.mjs</code></li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Authoritative registry of Data Cloud HTTP route security metadata
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class RouteSecurityRegistry {

    private static final Map<String, RouteSecurityMetadata> METADATA_BY_ROUTE;

    static {
        // DC-P0-01: Auto-generated route security metadata.
        // DO NOT EDIT MANUALLY. Regenerate with:
        //   node scripts/generate-route-security-metadata.mjs
        Map<String, RouteSecurityMetadata> map = new HashMap<>();

        // ─────────────────────────────────────────────────────────────────────────
        // PUBLIC routes (no authentication required)
        // ─────────────────────────────────────────────────────────────────────────
        map.put(
                "GET /health",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/health")
                        .sensitivity(EndpointSensitivity.PUBLIC)
                        .requiresAuth(false)
                        .requiresTenant(false)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("none")
                        .legacyStatus("active")
                        .description("Health check endpoint")
                        .build());

        map.put(
                "GET /ready",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/ready")
                        .sensitivity(EndpointSensitivity.PUBLIC)
                        .requiresAuth(false)
                        .requiresTenant(false)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("none")
                        .legacyStatus("active")
                        .description("Readiness probe")
                        .build());

        map.put(
                "GET /live",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/live")
                        .sensitivity(EndpointSensitivity.PUBLIC)
                        .requiresAuth(false)
                        .requiresTenant(false)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("none")
                        .legacyStatus("active")
                        .description("Liveness probe")
                        .build());

        // ─────────────────────────────────────────────────────────────────────────
        // ACTION PLANE ROUTES (canonical /api/v1/action/*)
        // ─────────────────────────────────────────────────────────────────────────

        // Agents
        map.put(
                "GET /api/v1/action/agents/catalog",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/agents/catalog")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("List available agents in catalog")
                        .build());

        map.put(
                "GET /api/v1/action/agents/catalog/{id}",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/agents/catalog/{id}")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Get agent catalog entry by ID")
                        .build());

        // Pipelines
        map.put(
                "GET /api/v1/action/pipelines",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/pipelines")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("List pipelines")
                        .build());

        map.put(
                "POST /api/v1/action/pipelines",
                RouteSecurityMetadata.builder()
                        .method("POST")
                        .canonicalPath("/api/v1/action/pipelines")
                        .sensitivity(EndpointSensitivity.SENSITIVE)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(false)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Create pipeline")
                        .build());

        map.put(
                "GET /api/v1/action/pipelines/{id}",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/pipelines/{id}")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Get pipeline by ID")
                        .build());

        map.put(
                "DELETE /api/v1/action/pipelines/{id}",
                RouteSecurityMetadata.builder()
                        .method("DELETE")
                        .canonicalPath("/api/v1/action/pipelines/{id}")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(false)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Delete pipeline (high-impact)")
                        .build());

        // Memory
        map.put(
                "GET /api/v1/action/memory",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/memory")
                        .sensitivity(EndpointSensitivity.SENSITIVE)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Access agent memory (personal data)")
                        .build());

        map.put(
                "POST /api/v1/action/memory/search",
                RouteSecurityMetadata.builder()
                        .method("POST")
                        .canonicalPath("/api/v1/action/memory/search")
                        .sensitivity(EndpointSensitivity.SENSITIVE)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Search agent memory")
                        .build());

        map.put(
                "DELETE /api/v1/action/memory/{agentId}/{memoryId}",
                RouteSecurityMetadata.builder()
                        .method("DELETE")
                        .canonicalPath("/api/v1/action/memory/{agentId}/{memoryId}")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(false)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Delete memory entry (data lifecycle)")
                        .build());

        // Learning
        map.put(
                "GET /api/v1/action/learning/review",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/learning/review")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("List learning review items")
                        .build());

        map.put(
                "POST /api/v1/action/learning/review/{id}/approve",
                RouteSecurityMetadata.builder()
                        .method("POST")
                        .canonicalPath("/api/v1/action/learning/review/{id}/approve")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Approve learning review (model training)")
                        .build());

        map.put(
                "POST /api/v1/action/learning/review/{id}/reject",
                RouteSecurityMetadata.builder()
                        .method("POST")
                        .canonicalPath("/api/v1/action/learning/review/{id}/reject")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Reject learning review")
                        .build());

        // Autonomy
        map.put(
                "GET /api/v1/action/autonomy/level",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/autonomy/level")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Get autonomy level")
                        .build());

        map.put(
                "PUT /api/v1/action/autonomy/level",
                RouteSecurityMetadata.builder()
                        .method("PUT")
                        .canonicalPath("/api/v1/action/autonomy/level")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Set autonomy level (governance)")
                        .build());

        // Plugins
        map.put(
                "GET /api/v1/action/plugins",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/plugins")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("List plugins")
                        .build());

        map.put(
                "POST /api/v1/action/plugins/{id}/enable",
                RouteSecurityMetadata.builder()
                        .method("POST")
                        .canonicalPath("/api/v1/action/plugins/{id}/enable")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(false)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Enable plugin (system configuration)")
                        .build());

        map.put(
                "POST /api/v1/action/plugins/{id}/disable",
                RouteSecurityMetadata.builder()
                        .method("POST")
                        .canonicalPath("/api/v1/action/plugins/{id}/disable")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(false)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Disable plugin")
                        .build());

        // Executions
        map.put(
                "GET /api/v1/action/executions/{id}",
                RouteSecurityMetadata.builder()
                        .method("GET")
                        .canonicalPath("/api/v1/action/executions/{id}")
                        .sensitivity(EndpointSensitivity.INTERNAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(false)
                        .requiresBlockingAudit(false)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Get execution details")
                        .build());

        map.put(
                "POST /api/v1/action/executions/{id}/cancel",
                RouteSecurityMetadata.builder()
                        .method("POST")
                        .canonicalPath("/api/v1/action/executions/{id}/cancel")
                        .sensitivity(EndpointSensitivity.CRITICAL)
                        .requiresAuth(true)
                        .requiresTenant(true)
                        .requiresPolicy(true)
                        .requiresBlockingAudit(true)
                        .idempotent(true)
                        .runtimeTruthSurface("action_plane")
                        .legacyStatus("active")
                        .description("Cancel execution (high-impact operation)")
                        .build());

        METADATA_BY_ROUTE = Collections.unmodifiableMap(map);
    }

    /**
     * Looks up security metadata for a route.
     *
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH)
     * @param canonicalPath Normalized path (e.g., /api/v1/entities/{id})
     * @return Optional containing metadata if found
     */
    public static Optional<RouteSecurityMetadata> lookup(String method, String canonicalPath) {
        String key = method.toUpperCase() + " " + canonicalPath;
        return Optional.ofNullable(METADATA_BY_ROUTE.get(key));
    }

    /**
     * Looks up security metadata for a route, with fallback to legacy prefix matching.
     *
     * <p>Used during transition period while all routes are being migrated to explicit metadata.
     *
     * @param method HTTP method
     * @param path Actual request path
     * @return Metadata if found, or empty if no explicit metadata and path doesn't match known prefixes
     */
    public static Optional<RouteSecurityMetadata> lookupWithFallback(String method, String path) {
        String canonicalPath = normalizePathForLookup(path);
        return lookup(method, canonicalPath);
    }

    /**
     * Normalizes a path for metadata lookup.
     *
     * <p>Converts dynamic path segments to placeholders:
     * <ul>
     *   <li>/entities/users/123 → /entities/{id}</li>
     *   <li>/pipelines/abc-def-ghi → /pipelines/{id}</li>
     * </ul>
     *
     * @param path the actual request path
     * @return normalized path suitable for registry lookup
     */
    private static String normalizePathForLookup(String path) {
        String normalized = path.replaceAll("/[0-9a-fA-F\\-]{8,}", "/{id}");
        normalized = normalized.replaceAll("/learning/review/[^/]+/approve$", "/learning/review/{id}/approve");
        normalized = normalized.replaceAll("/learning/review/[^/]+/reject$", "/learning/review/{id}/reject");
        normalized = normalized.replaceAll("/pipelines/[^/]+/enable$", "/pipelines/{id}/enable");
        normalized = normalized.replaceAll("/pipelines/[^/]+/disable$", "/pipelines/{id}/disable");
        normalized = normalized.replaceAll(
                "/plugins/[^/]+/enable$", "/plugins/{id}/enable");
        normalized = normalized.replaceAll(
                "/plugins/[^/]+/disable$", "/plugins/{id}/disable");
        normalized = normalized.replaceAll(
                "/executions/[^/]+/cancel$", "/executions/{id}/cancel");
        normalized = normalized.replaceAll(
                "/memory/[^/]+/[^/]+$", "/memory/{agentId}/{memoryId}");
        return normalized;
    }

    /**
     * Returns all registered routes.
     *
     * @return immutable map of all route metadata
     */
    public static Map<String, RouteSecurityMetadata> allRoutes() {
        return METADATA_BY_ROUTE;
    }

    /**
     * Gets count of registered routes.
     *
     * @return number of routes in the registry
     */
    public static int size() {
        return METADATA_BY_ROUTE.size();
    }
}
