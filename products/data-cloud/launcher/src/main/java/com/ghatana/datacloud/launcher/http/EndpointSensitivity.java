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
        "/health/deep",
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
        "/api/v1/data-products",
        "/api/v1/reports",            // POST (create report) and list operations
        "/api/v1/brain/",
        "/api/v1/memory/",
        "/api/v1/features",
        "/api/v1/models",
        "/api/v1/learning/",          // POST trigger and streaming reads
        "/api/v1/voice/"
    );

    /**
     * Classify a given HTTP method + path pair.
     *
     * <p>Rules (evaluated in order):
     * <ol>
     *   <li>If path is in {@link #PUBLIC_PATHS} → {@link #PUBLIC}.</li>
     *   <li>If method is DELETE and path starts with any CRITICAL prefix → {@link #CRITICAL}.</li>
     *   <li>If path starts with any CRITICAL prefix (any method) → {@link #CRITICAL}.</li>
     *   <li>If path starts with any SENSITIVE prefix → {@link #SENSITIVE}.</li>
     *   <li>Otherwise → {@link #INTERNAL}.</li>
     * </ol>
     *
     * @param method HTTP method string (e.g. "GET", "POST", "DELETE")
     * @param path   request path without query string (e.g. "/api/v1/entities/users/123")
     * @return the computed sensitivity level
     */
    public static EndpointSensitivity classify(String method, String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return PUBLIC;
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
        // Model promotion, learning approve/reject, and memory retain are all
        // high-impact mutations that require policy-engine consultation regardless
        // of their parent resource's default sensitivity level.
        if (path.contains("/promote")
                || path.contains("/approve")
                || path.contains("/reject")
                || path.contains("/retain")) {
            return CRITICAL;
        }

        // ── 4. AI/semantic inference endpoints are always SENSITIVE ───────────
        if (path.contains("/similar") || path.contains("/rag")) {
            return SENSITIVE;
        }

        // ── 5. Authenticated reads: most GETs are INTERNAL; memory is SENSITIVE ─
        if ("GET".equalsIgnoreCase(method)) {
            // Memory tier reads expose personal-data → elevated to SENSITIVE.
            if (path.equals("/api/v1/memory") || path.startsWith("/api/v1/memory/")) {
                return SENSITIVE;
            }
            // All other authenticated GETs default to INTERNAL.
            if (path.startsWith("/api/v1/")) {
                return INTERNAL;
            }
        }

        // ── 6. Remaining writes / mutations → SENSITIVE if prefix matches ─────
        for (String prefix : SENSITIVE_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return SENSITIVE;
            }
        }

        if (path.equals("/api/v1/memory")) {
            return SENSITIVE;
        }

        return INTERNAL;
    }
}
