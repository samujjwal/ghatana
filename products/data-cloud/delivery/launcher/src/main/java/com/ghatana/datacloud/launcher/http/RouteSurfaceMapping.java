package com.ghatana.datacloud.launcher.http;

import java.util.Map;
import java.util.Set;

/**
 * P1-12: Registry mapping routes to surface IDs.
 * 
 * <p>This registry provides the canonical mapping from HTTP routes to surface IDs,
 * which are used for runtime truth, UI gating, authorization metadata, audit event types,
 * OpenAPI metadata, and SDK feature flags.
 *
 * @doc.type class
 * @doc.purpose Registry mapping routes to surface IDs
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class RouteSurfaceMapping {
    
    private static final Map<String, String> ROUTE_TO_SURFACE = Map.ofEntries(
        // WS1: Align with canonical surface IDs from SurfaceRecord
        // Entity operations
        Map.entry("GET /api/v1/entities/{collection}", "data.entityStore"),
        Map.entry("GET /api/v1/entities/{collection}/{id}", "data.entityStore"),
        Map.entry("POST /api/v1/entities/{collection}", "data.entityStore"),
        Map.entry("PUT /api/v1/entities/{collection}/{id}", "data.entityStore"),
        Map.entry("DELETE /api/v1/entities/{collection}/{id}", "data.entityStore"),
        
        // Event operations
        Map.entry("POST /api/v1/events", "event.store"),
        Map.entry("GET /api/v1/events", "event.store"),
        
        // Connector operations (WS1: use data.storageProfiles when implemented)
        Map.entry("GET /api/v1/connectors/health", "data.storageProfiles"),
        Map.entry("GET /api/v1/connectors/schema", "data.storageProfiles"),
        Map.entry("POST /api/v1/connectors", "data.storageProfiles"),
        Map.entry("PUT /api/v1/connectors/{id}", "data.storageProfiles"),
        Map.entry("DELETE /api/v1/connectors/{id}", "data.storageProfiles"),
        Map.entry("POST /api/v1/connectors/{id}/rotate-credentials", "data.storageProfiles"),
        Map.entry("POST /api/v1/connectors/{id}/enable", "data.storageProfiles"),
        Map.entry("POST /api/v1/connectors/{id}/disable", "data.storageProfiles"),
        Map.entry("POST /api/v1/connectors/{id}/test", "data.storageProfiles"),
        Map.entry("POST /api/v1/connectors/{id}/sync", "data.storageProfiles"),
        Map.entry("GET /api/v1/connectors", "data.storageProfiles"),
        Map.entry("GET /api/v1/connectors/{id}", "data.storageProfiles"),
        
        // Pipeline operations (canonical Action Plane namespace)
        Map.entry("GET /api/v1/action/plugins", "action.execution"),
        Map.entry("GET /api/v1/action/plugins/{id}", "action.execution"),
        Map.entry("POST /api/v1/action/plugins/{id}/enable", "action.execution"),
        Map.entry("POST /api/v1/action/plugins/{id}/disable", "action.execution"),
        Map.entry("POST /api/v1/action/pipelines", "action.execution"),
        Map.entry("PUT /api/v1/action/pipelines/{id}", "action.execution"),
        Map.entry("DELETE /api/v1/action/pipelines/{id}", "action.execution"),
        Map.entry("POST /api/v1/action/pipelines/{id}/execute", "action.execution"),
        Map.entry("GET /api/v1/action/pipelines", "action.execution"),
        Map.entry("GET /api/v1/action/pipelines/{id}", "action.execution"),
        
        // Execution operations (canonical Action Plane namespace)
        Map.entry("POST /api/v1/action/executions/{id}/checkpoint", "action.execution"),
        Map.entry("POST /api/v1/action/executions/{id}/cancel", "action.execution"),
        Map.entry("POST /api/v1/action/executions/{id}/retry", "action.execution"),
        Map.entry("POST /api/v1/action/executions/{id}/rollback", "action.execution"),
        Map.entry("POST /api/v1/action/executions/{id}/restore", "action.execution"),
        Map.entry("GET /api/v1/action/executions/{id}", "action.execution"),
        Map.entry("GET /api/v1/action/executions/{id}/logs", "action.execution"),
        
        // Alert operations (WS1: map to governance.audit for now)
        Map.entry("POST /api/v1/alerts/{id}/remediate", "governance.audit"),
        Map.entry("POST /api/v1/alerts/{id}/auto-remediate", "governance.audit"),
        Map.entry("POST /api/v1/alerts/{id}/escalate", "governance.audit"),
        Map.entry("POST /api/v1/alerts/{id}/acknowledge", "governance.audit"),
        Map.entry("POST /api/v1/alerts/{id}/resolve", "governance.audit"),
        Map.entry("POST /api/v1/alerts/groups/{id}/resolve", "governance.audit"),
        Map.entry("POST /api/v1/alerts/suggestions/{id}/apply", "governance.audit"),
        Map.entry("POST /api/v1/alerts/rules", "governance.audit"),
        Map.entry("PUT /api/v1/alerts/rules/{id}", "governance.audit"),
        Map.entry("DELETE /api/v1/alerts/rules/{id}", "governance.audit"),
        Map.entry("GET /api/v1/alerts", "governance.audit"),
        Map.entry("GET /api/v1/alerts/{id}", "governance.audit"),
        Map.entry("GET /api/v1/alerts/groups", "governance.audit"),
        Map.entry("GET /api/v1/alerts/groups/{id}", "governance.audit"),
        
        // Governance operations
        Map.entry("POST /api/v1/governance/retention/purge", "governance.audit"),
        Map.entry("POST /api/v1/governance/privacy/redact", "governance.audit"),
        Map.entry("GET /api/v1/governance/compliance/summary", "governance.audit"),
        Map.entry("POST /api/v1/governance/policies", "governance.policyEngine"),
        Map.entry("PUT /api/v1/governance/policies/{id}", "governance.policyEngine"),
        Map.entry("DELETE /api/v1/governance/policies/{id}", "governance.policyEngine"),
        Map.entry("POST /api/v1/governance/policies/{id}/toggle", "governance.policyEngine"),
        Map.entry("GET /api/v1/governance/policies", "governance.policyEngine"),
        Map.entry("GET /api/v1/governance/policies/{id}", "governance.policyEngine"),
        
        // Learning operations (WS1: map to intelligence.aiAssist for now)
        Map.entry("POST /api/v1/learning/review/{id}/approve", "intelligence.aiAssist"),
        Map.entry("POST /api/v1/learning/review/{id}/reject", "intelligence.aiAssist"),
        
        // AI operations
        Map.entry("GET /api/v1/action/agents", "action.agentRuntime"),
        Map.entry("GET /api/v1/action/agents/{id}", "action.agentRuntime"),
        Map.entry("POST /api/v1/action/agents/{id}/execute", "action.agentRuntime"),
        Map.entry("POST /api/v1/aiassist/action", "intelligence.aiAssist"),
        Map.entry("POST /api/v1/models/{id}/promote", "intelligence.aiCompletion"),
        
        // Settings operations (WS1: map to authentication.apiKey for security settings)
        Map.entry("POST /api/v1/settings", "authentication.apiKey"),
        Map.entry("POST /api/v1/settings/security", "authentication.apiKey"),
        Map.entry("POST /api/v1/settings/keys", "authentication.apiKey"),
        Map.entry("GET /api/v1/settings/keys/{id}", "authentication.apiKey"),
        Map.entry("POST /api/v1/settings/keys/{id}/rotate", "authentication.apiKey"),
        Map.entry("DELETE /api/v1/settings/keys/{id}/revoke", "authentication.apiKey"),
        Map.entry("POST /api/v1/settings/approval-request", "governance.policyEngine"),
        Map.entry("POST /api/v1/settings/approvals/{id}/approve", "governance.policyEngine"),
        Map.entry("POST /api/v1/settings/approvals/{id}/reject", "governance.policyEngine"),
        
        // Plugin operations (WS1: map to action.execution for now)
        Map.entry("GET /api/v1/plugins", "action.execution"),
        Map.entry("GET /api/v1/plugins/{id}", "action.execution"),
        Map.entry("POST /api/v1/plugins/{id}/enable", "action.execution"),
        Map.entry("POST /api/v1/plugins/{id}/disable", "action.execution"),
        Map.entry("POST /api/v1/plugins/{id}/upgrade", "action.execution"),
        Map.entry("POST /api/v1/plugins/{id}/validate", "action.execution"),
        Map.entry("POST /api/v1/plugins/{id}/conformance", "action.execution"),
        
        // Autonomy operations (WS1: map to action.agentRuntime for now)
        Map.entry("PUT /api/v1/autonomy/level", "action.agentRuntime"),
        Map.entry("POST /api/v1/autonomy/feedback-policy", "action.agentRuntime"),
        
        // Context operations
        Map.entry("GET /api/v1/context", "context.plane"),
        Map.entry("PUT /api/v1/context", "context.plane"),
        Map.entry("DELETE /api/v1/context/keys/{id}", "context.plane"),
        Map.entry("POST /api/v1/context/{collection}/rag-policy-check", "context.plane"),

        // Media operations
        Map.entry("GET /api/v1/media/artifacts", "media.audioVideo"),
        Map.entry("GET /api/v1/media/artifacts/{id}", "media.audioVideo"),
        Map.entry("POST /api/v1/media/artifacts", "media.audioVideo"),
        Map.entry("DELETE /api/v1/media/artifacts/{id}", "media.audioVideo"),
        Map.entry("POST /api/v1/media/artifacts/{id}/process", "media.audioVideo"),
        
        // Runtime truth / surfaces
        Map.entry("GET /api/v1/surfaces", "data.entityStore"),
        Map.entry("GET /api/v1/surfaces/schema", "data.entityStore")
    );
    
    private RouteSurfaceMapping() {}
    
    /**
     * Gets the surface ID for a given route.
     *
     * @param method the HTTP method
     * @param path the route path
     * @return the surface ID, or null if not mapped
     */
    public static String getSurfaceId(String method, String path) {
        String normalized = normalizePath(path);
        return ROUTE_TO_SURFACE.get(method.toUpperCase() + " " + normalized);
    }
    
    /**
     * Checks if a route has a surface ID mapping.
     *
     * @param method the HTTP method
     * @param path the route path
     * @return true if the route has a surface ID mapping
     */
    public static boolean hasSurfaceMapping(String method, String path) {
        String normalized = normalizePath(path);
        return ROUTE_TO_SURFACE.containsKey(method.toUpperCase() + " " + normalized);
    }
    
    /**
     * Gets all registered surface IDs.
     *
     * @return set of all surface IDs
     */
    public static Set<String> getAllSurfaceIds() {
        return Set.copyOf(ROUTE_TO_SURFACE.values());
    }
    
    /**
     * Normalizes a path for mapping (replaces UUID-like patterns with {id}).
     */
    private static String normalizePath(String path) {
        String normalized = path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
        normalized = normalized.replaceAll("/connectors/[^/]+", "/connectors/{id}");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+", "/action/pipelines/{id}");
        normalized = normalized.replaceAll("/action/plugins/[^/]+", "/action/plugins/{id}");
        normalized = normalized.replaceAll("/action/agents/[^/]+", "/action/agents/{id}");
        normalized = normalized.replaceAll("/action/executions/[^/]+", "/action/executions/{id}");
        normalized = normalized.replaceAll("/executions/[^/]+", "/executions/{id}");
        normalized = normalized.replaceAll("/alerts/[^/]+", "/alerts/{id}");
        normalized = normalized.replaceAll("/alerts/groups/[^/]+", "/alerts/groups/{id}");
        normalized = normalized.replaceAll("/alerts/suggestions/[^/]+", "/alerts/suggestions/{id}");
        normalized = normalized.replaceAll("/alerts/rules/[^/]+", "/alerts/rules/{id}");
        normalized = normalized.replaceAll("/governance/policies/[^/]+", "/governance/policies/{id}");
        normalized = normalized.replaceAll("/learning/review/[^/]+", "/learning/review/{id}");
        normalized = normalized.replaceAll("/models/[^/]+", "/models/{id}");
        normalized = normalized.replaceAll("/settings/keys/[^/]+", "/settings/keys/{id}");
        normalized = normalized.replaceAll("/settings/approvals/[^/]+", "/settings/approvals/{id}");
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/{id}");
        normalized = normalized.replaceAll("/media/artifacts/[^/]+", "/media/artifacts/{id}");
        normalized = normalized.replaceAll("/context/keys/[^/]+", "/context/keys/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+", "/entities/{collection}");
        return normalized;
    }
}
