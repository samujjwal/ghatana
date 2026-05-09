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
        Map.entry("POST /api/v1/governance/retention/purge", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/governance/privacy/redact", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("GET /api/v1/governance/compliance/summary", DataCloudSecurityFilter.AccessLevel.AUDITOR),
        Map.entry("POST /api/v1/learning/review/{id}/approve", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/learning/review/{id}/reject", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/models/{id}/promote", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/plugins/{id}/upgrade", DataCloudSecurityFilter.AccessLevel.ADMIN),
        Map.entry("POST /api/v1/pipelines/{id}/execute", DataCloudSecurityFilter.AccessLevel.OPERATOR),
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
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/{id}");
        normalized = normalized.replaceAll("/pipelines/[^/]+", "/pipelines/{id}");
        normalized = normalized.replaceAll("/models/[^/]+", "/models/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+/[^/]+", "/entities/{collection}/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+$", "/entities/{collection}");
        return normalized;
    }
}
