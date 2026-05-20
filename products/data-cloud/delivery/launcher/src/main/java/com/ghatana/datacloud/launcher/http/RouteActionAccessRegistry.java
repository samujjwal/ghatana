package com.ghatana.datacloud.launcher.http;

import java.util.Map;

/**
 * Contract-backed route/action access registry.
 *
 * <p>Provides explicit access-level requirements for high-impact routes,
 * reducing reliance on path-prefix inference in security decisions.
 *
 * <p>DC-P1-04: Route entries are generated from OpenAPI contracts via
 * {@code scripts/generate-route-security-metadata.mjs}. Do not edit manually.
 * Regenerate with: {@code node scripts/generate-route-security-metadata.mjs}
 *
 * @doc.type class
 * @doc.purpose Route/action access-level registry for Data Cloud HTTP security
 * @doc.layer product
 * @doc.pattern Registry
 */
final class RouteActionAccessRegistry {

    // DC-P1-04: Auto-generated from OpenAPI contracts
    // Regenerate with: node scripts/generate-route-security-metadata.mjs
    private static final Map<String, DataCloudSecurityFilter.AccessLevel> ACCESS_BY_ACTION = Map.ofEntries(
        // Auto-generated entries from contracts/openapi/data-cloud.yaml and contracts/openapi/action-plane.yaml
        // DO NOT EDIT MANUALLY - regenerate with the script
        // Placeholder entries - replace with generated content
        Map.entry("POST /api/v1/connectors", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /api/v1/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/rotate-credentials", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/enable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/disable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/test", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/connectors/{id}/sync", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Settings
        Map.entry("POST /api/v1/settings", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/security", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/keys", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/keys/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/keys/{id}/rotate", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /api/v1/settings/keys/{id}/revoke", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/approval-request", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/approvals/{id}/approve", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/approvals/{id}/reject", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Plugins - canonical Action Plane routes only (legacy routes removed per DC-P1-03)
        Map.entry("POST /api/v1/action/plugins/{id}/enable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/plugins/{id}/disable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/plugins/{id}/upgrade", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/plugins/{id}/validate", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/plugins/{id}/conformance", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Governance
        Map.entry("POST /api/v1/governance/retention/purge", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/privacy/redact", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/governance/compliance/summary", DataCloudSecurityFilter.AccessLevel.AUDITOR),
        Map.entry("POST /api/v1/governance/policies", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/governance/policies/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /api/v1/governance/policies/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/policies/{id}/toggle", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Learning - canonical Action Plane routes only (legacy routes removed per DC-P1-03)
        Map.entry("POST /api/v1/action/learning/review/{id}/approve", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/learning/review/{id}/reject", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Models
        Map.entry("POST /api/v1/models/{id}/promote", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Autonomy - canonical Action Plane routes only (legacy routes removed per DC-P1-03)
        Map.entry("PUT /api/v1/action/autonomy/level", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/autonomy/feedback-policy", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Context
        Map.entry("PUT /api/v1/context", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/context/keys/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/context/{collection}/rag-policy-check", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Pipelines - canonical Action Plane routes only (legacy routes removed per DC-P1-03)
        Map.entry("POST /api/v1/action/pipelines", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/action/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/action/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/action/pipelines/{id}/execute", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/pipelines/{id}/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Executions - canonical Action Plane routes only (legacy routes removed per DC-P1-03)
        Map.entry("POST /api/v1/action/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/retry", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/rollback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/action/executions/{id}/restore", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Memory - canonical Action Plane routes
        Map.entry("POST /api/v1/action/memory/{agentId}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/action/memory/{agentId}/{memoryId}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/action/memory/{agentId}/{memoryId}/retain", DataCloudSecurityFilter.AccessLevel.ADMIN),

        // Agents - canonical Action Plane routes
        Map.entry("GET /api/v1/action/agents/catalog", DataCloudSecurityFilter.AccessLevel.VIEWER),

        // Alerts
        Map.entry("POST /api/v1/alerts/{id}/remediate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/auto-remediate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/escalate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/acknowledge", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/resolve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/groups/{id}/resolve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/suggestions/{id}/apply", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/rules", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/alerts/rules/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/alerts/rules/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),

        // Core data operations
        Map.entry("POST /api/v1/events", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/entities/{collection}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/entities/{collection}/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN)
    );

    private RouteActionAccessRegistry() {
    }

    static DataCloudSecurityFilter.AccessLevel requiredAccess(String method, String path) {
        String normalized = normalizePath(path);
        return ACCESS_BY_ACTION.get(method.toUpperCase() + " " + normalized);
    }

    private static String normalizePath(String path) {
        String normalized = path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
        // P1-03: DO NOT normalize /api/v1/action/* to /api/v1/* - preserve canonical namespace
        // This ensures action routes are looked up with their canonical paths
        normalized = normalized.replaceAll("/learning/review/[^/]+/(approve|reject)$", "/learning/review/{id}/$1");
        normalized = normalized.replaceAll("/action/learning/review/[^/]+/(approve|reject)$", "/action/learning/review/{id}/$1");
        normalized = normalized.replaceAll("/connectors/[^/]+", "/connectors/{id}");
        normalized = normalized.replaceAll("/settings/keys/[^/]+/rotate$", "/settings/keys/{id}/rotate");
        normalized = normalized.replaceAll("/settings/keys/[^/]+/revoke$", "/settings/keys/{id}/revoke");
        normalized = normalized.replaceAll("/settings/keys/[^/]+$", "/settings/keys/{id}");
        normalized = normalized.replaceAll("/settings/approvals/[^/]+/(approve|reject)$", "/settings/approvals/{id}/$1");
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/{id}");
        normalized = normalized.replaceAll("/action/plugins/[^/]+", "/action/plugins/{id}");
        normalized = normalized.replaceAll("/governance/policies/[^/]+/toggle$", "/governance/policies/{id}/toggle");
        normalized = normalized.replaceAll("/governance/policies/[^/]+$", "/governance/policies/{id}");
        normalized = normalized.replaceAll("/autonomy/plan/[^/]+$", "/autonomy/plan/{id}");
        normalized = normalized.replaceAll("/action/autonomy/plan/[^/]+$", "/action/autonomy/plan/{id}");
        normalized = normalized.replaceAll("/context/keys/[^/]+$", "/context/keys/{id}");
        normalized = normalized.replaceAll("/context/[^/]+/rag-policy-check$", "/context/{collection}/rag-policy-check");
        normalized = normalized.replaceAll("/pipelines/[^/]+/execute$", "/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+/execute$", "/action/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions/[^/]+/cancel$", "/pipelines/{id}/executions/{id}/cancel");
        normalized = normalized.replaceAll("/pipelines/[^/]+", "/pipelines/{id}");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+", "/action/pipelines/{id}");
        normalized = normalized.replaceAll("/executions/[^/]+/(cancel|retry|rollback|restore)$", "/executions/{id}/$1");
        normalized = normalized.replaceAll("/action/executions/[^/]+/(cancel|retry|rollback|restore)$", "/action/executions/{id}/$1");
        normalized = normalized.replaceAll("/alerts/groups/[^/]+/resolve$", "/alerts/groups/{id}/resolve");
        normalized = normalized.replaceAll("/alerts/suggestions/[^/]+/apply$", "/alerts/suggestions/{id}/apply");
        normalized = normalized.replaceAll("/alerts/rules/[^/]+$", "/alerts/rules/{id}");
        normalized = normalized.replaceAll("/alerts/[^/]+/(remediate|auto-remediate|escalate|acknowledge|resolve)$", "/alerts/{id}/$1");
        normalized = normalized.replaceAll("/models/[^/]+", "/models/{id}");
        normalized = normalized.replaceAll("/action/memory/[^/]+/[^/]+", "/action/memory/{agentId}/{memoryId}");
        normalized = normalized.replaceAll("/entities/[^/]+/[^/]+", "/entities/{collection}/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+$", "/entities/{collection}");
        return normalized;
    }
}
