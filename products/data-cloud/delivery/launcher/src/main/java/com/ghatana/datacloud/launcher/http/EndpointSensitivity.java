package com.ghatana.datacloud.launcher.http;

import java.util.Set;

/**
 * Data-Cloud endpoint sensitivity classification.
 *
 * <p>Every HTTP route is assigned a sensitivity level used by the
 * {@link DataCloudSecurityFilter} to decide which policy checks to enforce
 * and which audit events to emit.
 *
 * <h2>Classification Levels</h2>
 * <ul>
 *   <li>{@link #PUBLIC} — health / metrics probes, no authentication required.</li>
 *   <li>{@link #INTERNAL} — read-only data operations with authentication.</li>
 *   <li>{@link #SENSITIVE} — mutations, personal-data reads, AI inference.</li>
 *   <li>{@link #CRITICAL} — governance mutations, model promotions, data deletion,
 *       voice transcripts, and policy configuration.</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Endpoint sensitivity classification for policy and audit routing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum EndpointSensitivity {

    /**
     * No authentication required.
     * Routes: /health, /ready, /live, /metrics, /info.
     */
    PUBLIC,

    /**
     * Authentication required; read-only access to data.
     * Routes: GET entities, GET events, GET pipelines*, GET checkpoints,
     *         GET analytics results, GET reports, GET models, GET features.
     */
    INTERNAL,

    /**
     * Authentication required; write or potentially sensitive read operations.
     * Routes: POST entities, POST events, POST pipelines, POST analytics/query,
     *         GET memory, POST memory/search, AI suggestions, voice intents.
     * Audit events are emitted for every request.
     */
    SENSITIVE,

    /**
     * Authentication required; high-impact operations requiring additional policy checks.
     * Routes: DELETE entities, model promote, memory delete/retain,
     *         governance endpoints, learning review approve/reject,
     *         voice transcript management, data lifecycle operations.
     * Audit events are emitted and policy engine is consulted.
     */
    CRITICAL;

    // ─────────────────────────────────────────────────────────────────────────
    // Path prefix → sensitivity mapping table
    // Evaluated in order; first prefix match wins.
    // ─────────────────────────────────────────────────────────────────────────

    /** Paths that are always PUBLIC (no auth). */
    public static final Set<String> PUBLIC_PATHS = Set.of(
        "/health",
        "/health/detail",
        "/ready",
        "/live",
        "/metrics",
        "/info"
    );

    /**
     * Path prefixes that are ALWAYS CRITICAL regardless of HTTP method.
     *
     * <p>Every request whose path starts with one of these prefixes is subject to
     * policy-engine consultation and mandatory audit emission, no matter whether
     * the method is GET, POST, PUT, or DELETE.  Use this set for resources where
     * even read access is a high-impact, audited operation (governance dashboards,
     * voice transcript management, learning review actions).
     */
    public static final Set<String> CRITICAL_PATH_PREFIXES = Set.of(
        "/api/v1/governance/",        // all governance and lifecycle endpoints
        "/api/v1/data/lifecycle/",    // lifecycle purge/redaction style operations
        "/api/v1/learning/review/",   // approve / reject review items
        "/api/v1/voice/transcripts"   // transcript management (access + deletion)
    );

    /**
     * Path prefixes for which only DELETE (destructive) operations escalate to
     * CRITICAL; non-DELETE requests fall through to SENSITIVE / INTERNAL.
     *
     * <p>Extend this set whenever a resource supports DELETE and the deletion is
     * considered a high-impact, policy-audited operation (pipeline teardown,
     * entity removal, memory purge, model removal).
     */
    public static final Set<String> DELETE_CRITICAL_PREFIXES = Set.of(
        "/api/v1/entities/",
        "/api/v1/pipelines/",
        "/api/v1/checkpoints/",
        "/api/v1/memory/",
        "/api/v1/models/"
    );

    /**
     * DC-P1-04: Action engine routes - pipeline automation and execution.
     * All action routes are SENSITIVE as they involve automated execution and potential side effects.
     */
    public static final Set<String> ACTION_PATH_PREFIXES = Set.of(
        "/api/v1/actions/"
    );

    /**
     * Path prefixes whose operations are SENSITIVE (authenticated writes, AI inference, voice).
     * Checked only after CRITICAL checks have not matched.
     *
     * <p>Learning trigger mutations are included so they always require
     * authentication and produce audit events, even when the policy-engine is
     * not invoked.
     */
    public static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
        "/api/v1/entities/",
        "/api/v1/events",
        "/api/v1/alerts",
        "/api/v1/pipelines",
        "/api/v1/checkpoints",
        "/api/v1/analytics/",
        "/api/v1/connectors",
        "/api/v1/settings",
        "/api/v1/plugins",
        "/api/v1/autonomy",
        "/api/v1/context",
        "/api/v1/data-products",
        "/api/v1/reports",            // POST (create report) and list operations
        "/api/v1/brain/",
        "/api/v1/memory/",
        "/api/v1/features",
        "/api/v1/models",
        "/api/v1/learning/",          // POST trigger and streaming reads
        "/api/v1/voice/",
        "/api/v1/action/",            // Canonical Action Plane routes
        "/api/v1/actions/"            // Legacy action namespace (if still used)
    );

    /**
     * Contract-backed explicit CRITICAL route-action entries.
     */
    private static final Set<String> CRITICAL_ROUTE_ACTIONS = Set.of(
        "POST /api/v1/governance/retention/purge",
        "POST /api/v1/governance/privacy/redact",
        "POST /api/v1/data/lifecycle/retention/purge",
        "POST /api/v1/settings/security",
        "POST /api/v1/plugins/{id}/enable",
        "POST /api/v1/connectors/{id}/rotate-credentials",
        "PUT /api/v1/autonomy/level",
        "POST /api/v1/learning/review/{id}/approve",
        "POST /api/v1/learning/review/{id}/reject",
        "POST /api/v1/models/{id}/promote",
        "DELETE /api/v1/entities/{collection}/{id}"
    );

    /**
     * Contract-backed explicit SENSITIVE route-action entries.
     */
    private static final Set<String> SENSITIVE_ROUTE_ACTIONS = Set.of(
        "POST /api/v1/entities/{collection}",
        "POST /api/v1/events",
        "POST /api/v1/voice/intent",
        "POST /api/v1/voice/classify",
        "POST /api/v1/memory/search",
        "POST /api/v1/pipelines/{id}/execute",
        "POST /api/v1/actions/{id}",           // DC-P1-04: Execute action
        "POST /api/v1/actions",                // DC-P1-04: Create action
        "GET /api/v1/actions",                 // DC-P1-04: List actions
        "GET /api/v1/actions/{id}",            // DC-P1-04: Get action
        "PUT /api/v1/actions/{id}",            // DC-P1-04: Update action
        "DELETE /api/v1/actions/{id}"          // DC-P1-04: Delete action
    );

    /**
     * Classify a given HTTP method + path pair.
     *
     * <p>DC-P0-01: Now uses the authoritative RouteSecurityRegistry instead of prefix-based inference.
     *
     * <p>Rules (evaluated in order):
     * <ol>
     *   <li>If path is in {@link #PUBLIC_PATHS} → {@link #PUBLIC}.</li>
     *   <li>Look up route in {@link RouteSecurityRegistry} → use explicit sensitivity.</li>
     *   <li>If registry has legacy entry that is deprecated → try old prefix-based fallback (temp).</li>
     *   <li>Otherwise → {@link #INTERNAL}.</li>
     * </ol>
     *
     * @param method HTTP method string (e.g. "GET", "POST", "DELETE")
     * @param path   request path without query string (e.g. "/api/v1/entities/users/123")
     * @return the computed sensitivity level
     */
    public static EndpointSensitivity classify(String method, String path) {
        // Quick check for public paths (no registry lookup needed)
        if (PUBLIC_PATHS.contains(path)) {
            return PUBLIC;
        }

        // DC-P0-01: First try explicit route security registry
        var metadata = RouteSecurityRegistry.lookupWithFallback(method, path);
        if (metadata.isPresent()) {
            return metadata.get().sensitivity();
        }

        // DC-P0-01: Temporary fallback to legacy prefix-based classification
        // This will be removed once all routes are registered in RouteSecurityRegistry.
        return classifyLegacy(method, path);
    }

    /**
     * Legacy classification logic (prefix-based).
     *
     * <p>DC-P0-01: This method is temporary and will be removed when all routes
     * are registered in {@link RouteSecurityRegistry}.
     *
     * @param method HTTP method
     * @param path request path
     * @return sensitivity level
     */
    private static EndpointSensitivity classifyLegacy(String method, String path) {
        String actionKey = method.toUpperCase() + " " + normalizePath(path);
        if (CRITICAL_ROUTE_ACTIONS.contains(actionKey)) {
            return CRITICAL;
        }
        if (SENSITIVE_ROUTE_ACTIONS.contains(actionKey)) {
            return SENSITIVE;
        }

        // ── 1. Always-CRITICAL paths (governance, voice transcripts, review) ──
        for (String prefix : CRITICAL_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return CRITICAL;
            }
        }

        // ── 2. DELETE-is-CRITICAL paths ───────────────────────────────────────
        if ("DELETE".equalsIgnoreCase(method)) {
            for (String prefix : DELETE_CRITICAL_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return CRITICAL;
                }
            }
        }

        // ── 3. Sub-path mutation overrides ────────────────────────────────────
        if (path.contains("/promote")
                || path.contains("/approve")
                || path.contains("/reject")
                || path.contains("/retain")
                || path.contains("/rotate")
                || path.contains("/revoke")) {
            return CRITICAL;
        }

        // ── 4. AI/semantic inference endpoints are always SENSITIVE ───────────
        if (path.contains("/similar") || path.contains("/rag")) {
            return SENSITIVE;
        }

        // ── 5. Authenticated reads ──────────────────────────────────────────
        if ("GET".equalsIgnoreCase(method)) {
            if (path.startsWith("/mcp/")) {
                return INTERNAL;
            }
            if (path.equals("/api/v1/memory") || path.startsWith("/api/v1/memory/")) {
                return SENSITIVE;
            }
            if (path.startsWith("/api/v1/")) {
                return INTERNAL;
            }
        }

        // ── 6. Remaining writes ─────────────────────────────────────────────
        for (String prefix : SENSITIVE_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return SENSITIVE;
            }
        }

        if (path.equals("/api/v1/memory")) {
            return SENSITIVE;
        }

        // Final fallback
        DataCloudSecurityFilter.AccessLevel accessLevel = RouteActionAccessRegistry.requiredAccess(method, path);
        if (accessLevel == DataCloudSecurityFilter.AccessLevel.ADMIN
                || accessLevel == DataCloudSecurityFilter.AccessLevel.AUDITOR) {
            return CRITICAL;
        }
        if (accessLevel == DataCloudSecurityFilter.AccessLevel.OPERATOR) {
            return SENSITIVE;
        }

        return INTERNAL;
    }

    private static String normalizePath(String path) {
        String normalized = path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
        normalized = normalized.replaceAll("/learning/review/[^/]+/(approve|reject)$", "/learning/review/{id}/$1");
        normalized = normalized.replaceAll("/models/[^/]+/promote$", "/models/{id}/promote");
        normalized = normalized.replaceAll("/pipelines/[^/]+/execute$", "/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/plugins/[^/]+/enable$", "/plugins/{id}/enable");
        normalized = normalized.replaceAll("/connectors/[^/]+/rotate-credentials$", "/connectors/{id}/rotate-credentials");
        normalized = normalized.replaceAll("/entities/[^/]+/[^/]+$", "/entities/{collection}/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+$", "/entities/{collection}");
        return normalized;
    }
}
