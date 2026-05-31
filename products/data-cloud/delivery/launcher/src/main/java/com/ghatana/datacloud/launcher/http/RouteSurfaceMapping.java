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
        // Entity operations
        Map.entry("GET /api/v1/entities/{collection}", "data.entities.read"),
        Map.entry("GET /api/v1/entities/{collection}/{id}", "data.entities.read"),
        Map.entry("POST /api/v1/entities/{collection}", "data.entities.write"),
        Map.entry("PUT /api/v1/entities/{collection}/{id}", "data.entities.write"),
        Map.entry("DELETE /api/v1/entities/{collection}/{id}", "data.entities.write"),
        
        // Event operations
        Map.entry("POST /api/v1/events", "event.append"),
        Map.entry("GET /api/v1/events", "event.read"),
        
        // Connector operations
        Map.entry("GET /api/v1/connectors/health", "data.connectors"),
        Map.entry("GET /api/v1/connectors/schema", "data.connectors"),
        Map.entry("POST /api/v1/connectors", "connectors.register"),
        Map.entry("PUT /api/v1/connectors/{id}", "connectors.update"),
        Map.entry("DELETE /api/v1/connectors/{id}", "connectors.delete"),
        Map.entry("POST /api/v1/connectors/{id}/rotate-credentials", "connectors.rotate"),
        Map.entry("POST /api/v1/connectors/{id}/enable", "connectors.enable"),
        Map.entry("POST /api/v1/connectors/{id}/disable", "connectors.disable"),
        Map.entry("POST /api/v1/connectors/{id}/test", "connectors.test"),
        Map.entry("POST /api/v1/connectors/{id}/sync", "connectors.sync"),
        Map.entry("GET /api/v1/connectors", "connectors.read"),
        Map.entry("GET /api/v1/connectors/{id}", "connectors.read"),
        
        // Pipeline operations (canonical Action Plane namespace)
        Map.entry("GET /api/v1/action/plugins", "plugin-management"),
        Map.entry("GET /api/v1/action/plugins/{id}", "plugin-management"),
        Map.entry("POST /api/v1/action/plugins/{id}/enable", "plugin-management"),
        Map.entry("POST /api/v1/action/plugins/{id}/disable", "plugin-management"),
        Map.entry("POST /api/v1/action/pipelines", "pipelines.create"),
        Map.entry("PUT /api/v1/action/pipelines/{id}", "pipelines.update"),
        Map.entry("DELETE /api/v1/action/pipelines/{id}", "pipelines.delete"),
        Map.entry("POST /api/v1/action/pipelines/{id}/execute", "action.pipelines.execute"),
        Map.entry("GET /api/v1/action/pipelines", "pipelines.read"),
        Map.entry("GET /api/v1/action/pipelines/{id}", "pipelines.read"),
        
        // Execution operations (canonical Action Plane namespace)
        Map.entry("POST /api/v1/action/executions/{id}/checkpoint", "executions.checkpoint"),
        Map.entry("POST /api/v1/action/executions/{id}/cancel", "executions.cancel"),
        Map.entry("POST /api/v1/action/executions/{id}/retry", "executions.retry"),
        Map.entry("POST /api/v1/action/executions/{id}/rollback", "executions.rollback"),
        Map.entry("POST /api/v1/action/executions/{id}/restore", "executions.restore"),
        Map.entry("GET /api/v1/action/executions/{id}", "executions.read"),
        Map.entry("GET /api/v1/action/executions/{id}/logs", "executions.read"),
        
        // Alert operations
        Map.entry("POST /api/v1/alerts/{id}/remediate", "alerts.remediate"),
        Map.entry("POST /api/v1/alerts/{id}/auto-remediate", "alerts.auto-remediate"),
        Map.entry("POST /api/v1/alerts/{id}/escalate", "alerts.escalate"),
        Map.entry("POST /api/v1/alerts/{id}/acknowledge", "alerts.acknowledge"),
        Map.entry("POST /api/v1/alerts/{id}/resolve", "alerts.resolve"),
        Map.entry("POST /api/v1/alerts/groups/{id}/resolve", "alerts.group.resolve"),
        Map.entry("POST /api/v1/alerts/suggestions/{id}/apply", "alerts.suggestions.apply"),
        Map.entry("POST /api/v1/alerts/rules", "alerts.rules.create"),
        Map.entry("PUT /api/v1/alerts/rules/{id}", "alerts.rules.update"),
        Map.entry("DELETE /api/v1/alerts/rules/{id}", "alerts.rules.delete"),
        Map.entry("GET /api/v1/alerts", "alerts.read"),
        Map.entry("GET /api/v1/alerts/{id}", "alerts.read"),
        Map.entry("GET /api/v1/alerts/groups", "alerts.groups.read"),
        Map.entry("GET /api/v1/alerts/groups/{id}", "alerts.groups.read"),
        
        // Governance operations
        Map.entry("POST /api/v1/governance/retention/purge", "governance.retention.purge"),
        Map.entry("POST /api/v1/governance/privacy/redact", "governance.privacy.redact"),
        Map.entry("GET /api/v1/governance/compliance/summary", "governance.compliance.read"),
        Map.entry("POST /api/v1/governance/policies", "governance.policy.create"),
        Map.entry("PUT /api/v1/governance/policies/{id}", "governance.policy.update"),
        Map.entry("DELETE /api/v1/governance/policies/{id}", "governance.policy.delete"),
        Map.entry("POST /api/v1/governance/policies/{id}/toggle", "governance.policy.toggle"),
        Map.entry("GET /api/v1/governance/policies", "governance.policy.read"),
        Map.entry("GET /api/v1/governance/policies/{id}", "governance.policy.read"),
        
        // Learning operations
        Map.entry("POST /api/v1/learning/review/{id}/approve", "learning.review.approve"),
        Map.entry("POST /api/v1/learning/review/{id}/reject", "learning.review.reject"),
        
        // AI operations
        Map.entry("GET /api/v1/action/agents", "action.agentRuntime"),
        Map.entry("GET /api/v1/action/agents/{id}", "action.agentRuntime"),
        Map.entry("POST /api/v1/action/agents/{id}/execute", "action.agentRuntime"),
        Map.entry("POST /api/v1/aiassist/action", "ai.suggestions.apply"),
        Map.entry("POST /api/v1/models/{id}/promote", "ai.models.promote"),
        
        // Settings operations
        Map.entry("POST /api/v1/settings", "settings.update"),
        Map.entry("POST /api/v1/settings/security", "settings.security.update"),
        Map.entry("POST /api/v1/settings/keys", "settings.keys.create"),
        Map.entry("GET /api/v1/settings/keys/{id}", "settings.keys.read"),
        Map.entry("POST /api/v1/settings/keys/{id}/rotate", "settings.keys.rotate"),
        Map.entry("DELETE /api/v1/settings/keys/{id}/revoke", "settings.keys.revoke"),
        Map.entry("POST /api/v1/settings/approval-request", "settings.approval.request"),
        Map.entry("POST /api/v1/settings/approvals/{id}/approve", "settings.approvals.approve"),
        Map.entry("POST /api/v1/settings/approvals/{id}/reject", "settings.approvals.reject"),
        
        // Plugin operations
        Map.entry("GET /api/v1/plugins", "plugin-management"),
        Map.entry("GET /api/v1/plugins/{id}", "plugin-management"),
        Map.entry("POST /api/v1/plugins/{id}/enable", "plugins.enable"),
        Map.entry("POST /api/v1/plugins/{id}/disable", "plugins.disable"),
        Map.entry("POST /api/v1/plugins/{id}/upgrade", "plugins.upgrade"),
        Map.entry("POST /api/v1/plugins/{id}/validate", "plugins.validate"),
        Map.entry("POST /api/v1/plugins/{id}/conformance", "plugins.conformance"),
        
        // Autonomy operations
        Map.entry("PUT /api/v1/autonomy/level", "autonomy.level.set"),
        Map.entry("POST /api/v1/autonomy/feedback-policy", "autonomy.feedback.set"),
        
        // Context operations
        Map.entry("GET /api/v1/context", "context.plane"),
        Map.entry("PUT /api/v1/context", "context.update"),
        Map.entry("DELETE /api/v1/context/keys/{id}", "context.keys.delete"),
        Map.entry("POST /api/v1/context/{collection}/rag-policy-check", "context.rag.check"),

        // Media operations
        Map.entry("GET /api/v1/media/artifacts", "media.audioVideo"),
        Map.entry("GET /api/v1/media/artifacts/{id}", "media.audioVideo"),
        Map.entry("POST /api/v1/media/artifacts", "media.audioVideo"),
        Map.entry("DELETE /api/v1/media/artifacts/{id}", "media.audioVideo"),
        Map.entry("POST /api/v1/media/artifacts/{id}/process", "media.audioVideo"),
        
        // Runtime truth / surfaces
        Map.entry("GET /api/v1/surfaces", "runtime.truth.read"),
        Map.entry("GET /api/v1/surfaces/schema", "runtime.truth.read")
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
