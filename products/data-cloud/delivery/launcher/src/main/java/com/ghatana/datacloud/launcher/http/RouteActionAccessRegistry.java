package com.ghatana.datacloud.launcher.http;

import java.util.Map;

/**
 * Contract-backed route/action access registry.
 *
 * <p>Provides explicit access-level requirements for high-impact routes,
 * reducing reliance on path-prefix inference in security decisions.
 *
 * @doc.type class
 * @doc.purpose Route/action access-level registry for Data Cloud HTTP security
 * @doc.layer product
 * @doc.pattern Registry
 */
final class RouteActionAccessRegistry {

    private static final Map<String, DataCloudSecurityFilter.AccessLevel> ACCESS_BY_ACTION = Map.ofEntries(
        Map.entry("POST /api/v1/connectors", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /api/v1/connectors/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/rotate-credentials", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/enable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/disable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/connectors/{id}/test", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/connectors/{id}/sync", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/settings", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/security", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/settings/keys", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/settings/keys/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/plugins/{id}/enable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/plugins/{id}/disable", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/retention/purge", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/privacy/redact", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/governance/compliance/summary", DataCloudSecurityFilter.AccessLevel.AUDITOR),
        Map.entry("POST /api/v1/governance/policies", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/governance/policies/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("DELETE /api/v1/governance/policies/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/policies/{id}/toggle", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/learning/review/{id}/approve", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/learning/review/{id}/reject", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/models/{id}/promote", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/plugins/{id}/upgrade", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/plugins/{id}/validate", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/plugins/{id}/conformance", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/autonomy/level", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/autonomy/feedback-policy", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("PUT /api/v1/context", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/context/keys/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/context/{collection}/rag-policy-check", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/pipelines", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/pipelines/{id}", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/pipelines/{id}/execute", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/pipelines/{id}/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/cancel", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/retry", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/rollback", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/executions/{id}/restore", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/remediate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/auto-remediate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/{id}/escalate", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/groups/{id}/resolve", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("POST /api/v1/alerts/rules", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("PUT /api/v1/alerts/rules/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
        Map.entry("DELETE /api/v1/alerts/rules/{id}", DataCloudSecurityFilter.AccessLevel.OPERATOR),
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
        normalized = normalized.replaceAll("/learning/review/[^/]+/(approve|reject)$", "/learning/review/{id}/$1");
        normalized = normalized.replaceAll("/connectors/[^/]+", "/connectors/{id}");
        normalized = normalized.replaceAll("/settings/keys/[^/]+$", "/settings/keys/{id}");
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/{id}");
        normalized = normalized.replaceAll("/governance/policies/[^/]+/toggle$", "/governance/policies/{id}/toggle");
        normalized = normalized.replaceAll("/governance/policies/[^/]+$", "/governance/policies/{id}");
        normalized = normalized.replaceAll("/autonomy/plan/[^/]+$", "/autonomy/plan/{id}");
        normalized = normalized.replaceAll("/context/keys/[^/]+$", "/context/keys/{id}");
        normalized = normalized.replaceAll("/context/[^/]+/rag-policy-check$", "/context/{collection}/rag-policy-check");
        normalized = normalized.replaceAll("/pipelines/[^/]+/execute$", "/pipelines/{id}/execute");
        normalized = normalized.replaceAll("/pipelines/[^/]+/executions/[^/]+/cancel$", "/pipelines/{id}/executions/{id}/cancel");
        normalized = normalized.replaceAll("/pipelines/[^/]+", "/pipelines/{id}");
        normalized = normalized.replaceAll("/executions/[^/]+/(cancel|retry|rollback|restore)$", "/executions/{id}/$1");
        normalized = normalized.replaceAll("/alerts/groups/[^/]+/resolve$", "/alerts/groups/{id}/resolve");
        normalized = normalized.replaceAll("/alerts/rules/[^/]+$", "/alerts/rules/{id}");
        normalized = normalized.replaceAll("/alerts/[^/]+/(remediate|auto-remediate|escalate)$", "/alerts/{id}/$1");
        normalized = normalized.replaceAll("/models/[^/]+", "/models/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+/[^/]+", "/entities/{collection}/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+$", "/entities/{collection}");
        return normalized;
    }
}
