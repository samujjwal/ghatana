package com.ghatana.digitalmarketing.api.security;

import java.util.List;

/**
 * Canonical DMOS backend route-to-capability registry.
 *
 * <p>This class is generated from the canonical route manifest.
 * Do not edit manually - regenerate from dmos-route-manifest.yaml.</p>
 *
 * @doc.type class
 * @doc.purpose Canonical backend route capability authorization for DMOS APIs
 * @doc.layer product
 * @doc.pattern Policy, Registry
 */
public final class DmosRouteCapabilityRegistry {

    private record RouteCapability(String pathTemplate, String capabilityKey) {
    }

    private static final List<RouteCapability> ROUTES = List.of(
        new RouteCapability("/v1/workspaces/:workspaceId/advanced-channels", "dmos.advanced_channels"),
        new RouteCapability("/v1/workspaces/:workspaceId/agency", "dmos.agency"),
        new RouteCapability("/v1/workspaces/:workspaceId/ai-actions", null),
        new RouteCapability("/v1/workspaces/:workspaceId/ai-actions/:actionId", null),
        new RouteCapability("/v1/workspaces/:workspaceId/ai-optimization", "dmos.ai_optimization"),
        new RouteCapability("/v1/workspaces/:workspaceId/ai-optimization", "dmos.ai_optimization"),
        new RouteCapability("/v1/workspaces/:workspaceId/approvals", null),
        new RouteCapability("/v1/workspaces/:workspaceId/approvals/:requestId", null),
        new RouteCapability("/v1/workspaces/:workspaceId/approvals/:requestId/decide", null),
        new RouteCapability("/v1/workspaces/:workspaceId/attribution", "dmos.reporting"),
        new RouteCapability("/v1/workspaces/:workspaceId/budget", "dmos.budget"),
        new RouteCapability("/v1/workspaces/:workspaceId/budget-recommendation", "dmos.budget"),
        new RouteCapability("/v1/workspaces/:workspaceId/budget-recommendation/:recId/approve", "dmos.budget"),
        new RouteCapability("/v1/workspaces/:workspaceId/budget-recommendation/:recId/submit", "dmos.budget"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/approve", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/archive", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/complete", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/duplicate", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/launch", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/pause", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/request-approval", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/rollback", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/campaigns/:id/transition", "dmos.campaigns"),
        new RouteCapability("/v1/workspaces/:workspaceId/connectors/google-ads/:connectorId/readiness", "dmos.connectors"),
        new RouteCapability("/v1/workspaces/:workspaceId/dashboard", null),
        new RouteCapability("/v1/workspaces/:workspaceId/funnel-analytics", "dmos.reporting"),
        new RouteCapability("/v1/workspaces/:workspaceId/localization", "dmos.localization"),
        new RouteCapability("/v1/workspaces/:workspaceId/market-research", "dmos.market_research"),
        new RouteCapability("/v1/workspaces/:workspaceId/next-best-action-recommendations", "dmos.ai_optimization"),
        new RouteCapability("/v1/workspaces/:workspaceId/next-best-action-recommendations", "dmos.ai_optimization"),
        new RouteCapability("/v1/workspaces/:workspaceId/next-best-action-recommendations/:recId", "dmos.ai_optimization"),
        new RouteCapability("/v1/workspaces/:workspaceId/next-best-action-recommendations/:recId/approve", "dmos.ai_optimization"),
        new RouteCapability("/v1/workspaces/:workspaceId/next-best-action-recommendations/:recId/reject", "dmos.ai_optimization"),
        new RouteCapability("/v1/workspaces/:workspaceId/release-readiness", "dmos.release_readiness"),
        new RouteCapability("/v1/workspaces/:workspaceId/roi-roas", "dmos.reporting"),
        new RouteCapability("/v1/workspaces/:workspaceId/self-marketing-funnel", "dmos.self_marketing"),
        new RouteCapability("/v1/workspaces/:workspaceId/strategy", "dmos.strategy"),
        new RouteCapability("/v1/workspaces/:workspaceId/strategy", "dmos.strategy"),
        new RouteCapability("/v1/workspaces/:workspaceId/strategy/:strategyId/approve", "dmos.strategy"),
        new RouteCapability("/v1/workspaces/:workspaceId/strategy/:strategyId/submit", "dmos.strategy")
    );

    private DmosRouteCapabilityRegistry() {
    }

    public static String capabilityForPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalizedPath = trimQuery(path);
        for (RouteCapability route : ROUTES) {
            if (matches(route.pathTemplate(), normalizedPath)) {
                return route.capabilityKey();
            }
        }
        return null;
    }

    private static boolean matches(String template, String path) {
        String[] templateSegments = segments(template);
        String[] pathSegments = segments(path);
        if (templateSegments.length != pathSegments.length) {
            return false;
        }
        for (int i = 0; i < templateSegments.length; i++) {
            String templateSegment = templateSegments[i];
            if (templateSegment.startsWith(":")) {
                if (pathSegments[i].isBlank()) {
                    return false;
                }
                continue;
            }
            if (!templateSegment.equals(pathSegments[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] segments(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.isBlank() ? new String[0] : normalized.split("/");
    }

    private static String trimQuery(String path) {
        int queryStart = path.indexOf('?');
        return queryStart >= 0 ? path.substring(0, queryStart) : path;
    }
}
