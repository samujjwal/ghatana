/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.launcher.runtime.RuntimeProfile;
import io.activej.http.HttpMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authoritative registry of Data Cloud runtime routes and security metadata.
 *
 * <p>The registry is generated from {@link DataCloudRouterBuilder} route registrations and
 * checked by {@code scripts/generate-route-manifest.mjs}. Every live runtime route must be
 * represented here; production-like profiles fail closed when metadata is missing.
 *
 * @doc.type class
 * @doc.purpose Runtime registry for route-level security metadata and policy posture
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class RouteSecurityRegistry {

    // GENERATED_ROUTER_CHECKSUM: b1da1073da6dcccbc86b9b03e5ac042562a95734da56290d7f7428d564447c3b

    private static final Map<String, RouteSecurityMetadata> METADATA_BY_ROUTE;
    private static final Map<String, Pattern> MATCHERS_BY_ROUTE;
    private static final Map<String, RouteSecurityMetadata> DYNAMIC_METADATA = new HashMap<>();
    // WS1: Surface records for deriving runtime lifecycle
    private static volatile java.util.Map<String, SurfaceRecord> surfaceRecords = java.util.Map.of();

    /**
     * Pass 8: Populates route security metadata from RouteRegistrar instances.
     *
     * @param registrars list of route registrars
     */
    public static void populateFromRegistrars(java.util.List<RouteRegistrar> registrars) {
        for (RouteRegistrar registrar : registrars) {
            for (RouteRegistrar.RouteMetadata metadata : registrar.getRouteMetadata()) {
                String key = metadata.method().name() + " " + metadata.path();
                RouteSecurityMetadata secMetadata = new RouteSecurityMetadata(
                    metadata.method().name(),
                    metadata.path(),
                    EndpointSensitivity.SENSITIVE,
                    true,
                    true,
                    metadata.method() == HttpMethod.POST || metadata.method() == HttpMethod.PUT || metadata.method() == HttpMethod.DELETE,
                    false,
                    DataCloudSecurityFilter.AccessLevel.OPERATOR,
                    metadata.method() == HttpMethod.GET,
                    metadata.tags().getOrDefault("plane", "unknown"),
                    "active",
                    metadata.description()
                );
                DYNAMIC_METADATA.put(key, secMetadata);
            }
        }
    }

    /**
     * Pass 8: Gets route metadata including dynamically registered routes.
     *
     * @param method HTTP method
     * @param path request path
     * @return route metadata if found
     */
    public static Optional<RouteSecurityMetadata> getMetadataIncludingDynamic(HttpMethod method, String path) {
        String key = method.name() + " " + path;
        if (DYNAMIC_METADATA.containsKey(key)) {
            return Optional.of(DYNAMIC_METADATA.get(key));
        }
        return Optional.ofNullable(getMetadata(method, path));
    }

    /**
     * Pass 8: Clears dynamically registered route metadata.
     * Used primarily in tests to isolate test cases.
     */
    public static void clearDynamicMetadata() {
        DYNAMIC_METADATA.clear();
    }

    /**
     * WS1: Sets surface records for deriving runtime lifecycle.
     * Route lifecycle is now derived from SurfaceRecord lifecycle instead of hardcoded legacyStatus.
     *
     * @param records map of surface ID to SurfaceRecord
     */
    public static void setSurfaceRecords(java.util.Map<String, SurfaceRecord> records) {
        surfaceRecords = java.util.Map.copyOf(records);
    }

    /**
     * WS1: Derives route lifecycle from surface record.
     * Falls back to hardcoded legacyStatus if no surface mapping exists.
     *
     * @param surfaceId the surface ID
     * @param fallbackLegacyStatus the hardcoded legacy status fallback
     * @return derived lifecycle string
     */
    public static String deriveLifecycleFromSurface(String surfaceId, String fallbackLegacyStatus) {
        if (surfaceId == null || surfaceId.isBlank()) {
            return fallbackLegacyStatus;
        }
        SurfaceRecord record = surfaceRecords.get(surfaceId);
        if (record == null) {
            return fallbackLegacyStatus;
        }
        // Map SurfaceRecord lifecycle to legacy status format
        String lifecycle = record.lifecycle();
        if (lifecycle == null || lifecycle.isBlank()) {
            return fallbackLegacyStatus;
        }
        // Map lifecycle values to legacy status format
        return switch (lifecycle.toLowerCase()) {
            case "stable" -> "active";
            case "preview" -> "active"; // Preview surfaces are active but marked as preview
            case "deprecated" -> "deprecated";
            case "experimental" -> "active"; // Experimental surfaces are active
            default -> fallbackLegacyStatus;
        };
    }

    static {
        Map<String, RouteSecurityMetadata> map = new HashMap<>();
        route(map, "GET", "/api/v1/action/agents/catalog", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/agents/catalog");
        route(map, "GET", "/api/v1/action/agents/catalog/{id}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/agents/catalog/{id}");
        route(map, "GET", "/api/v1/action/autonomy/domains", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/autonomy/domains");
        route(map, "GET", "/api/v1/action/autonomy/domains/{domain}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/autonomy/domains/{domain}");
        route(map, "POST", "/api/v1/action/autonomy/feedback-policy", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/autonomy/feedback-policy");
        route(map, "GET", "/api/v1/action/autonomy/level", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/autonomy/level");
        route(map, "PUT", "/api/v1/action/autonomy/level", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "PUT /api/v1/action/autonomy/level");
        route(map, "GET", "/api/v1/action/autonomy/logs", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/autonomy/logs");
        route(map, "GET", "/api/v1/action/autonomy/plan/{actionType}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/autonomy/plan/{actionType}");
        route(map, "GET", "/api/v1/action/executions/{executionId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/executions/{executionId}");
        route(map, "POST", "/api/v1/action/executions/{executionId}/cancel", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/executions/{executionId}/cancel");
        route(map, "POST", "/api/v1/action/executions/{executionId}/checkpoint", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/executions/{executionId}/checkpoint");
        route(map, "GET", "/api/v1/action/executions/{executionId}/checkpoints", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/executions/{executionId}/checkpoints");
        route(map, "GET", "/api/v1/action/executions/{executionId}/logs", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/executions/{executionId}/logs");
        route(map, "POST", "/api/v1/action/executions/{executionId}/restore", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/executions/{executionId}/restore");
        route(map, "POST", "/api/v1/action/executions/{executionId}/retry", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/executions/{executionId}/retry");
        route(map, "POST", "/api/v1/action/executions/{executionId}/rollback", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/executions/{executionId}/rollback");
        route(map, "GET", "/api/v1/action/learning/review", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/learning/review");
        route(map, "POST", "/api/v1/action/learning/review/{reviewId}/approve", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/learning/review/{reviewId}/approve");
        route(map, "POST", "/api/v1/action/learning/review/{reviewId}/reject", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/learning/review/{reviewId}/reject");
        route(map, "GET", "/api/v1/action/learning/status", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/learning/status");
        route(map, "POST", "/api/v1/action/learning/trigger", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/learning/trigger");
        route(map, "GET", "/api/v1/action/memory", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "action_plane", "active", "GET /api/v1/action/memory");
        route(map, "GET", "/api/v1/action/memory/{agentId}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "action_plane", "active", "GET /api/v1/action/memory/{agentId}");
        route(map, "POST", "/api/v1/action/memory/{agentId}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/memory/{agentId}");
        route(map, "DELETE", "/api/v1/action/memory/{agentId}/{memoryId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "DELETE /api/v1/action/memory/{agentId}/{memoryId}");
        route(map, "PUT", "/api/v1/action/memory/{agentId}/{memoryId}/retain", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "PUT /api/v1/action/memory/{agentId}/{memoryId}/retain");
        route(map, "GET", "/api/v1/action/memory/{agentId}/{tier}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "action_plane", "active", "GET /api/v1/action/memory/{agentId}/{tier}");
        route(map, "POST", "/api/v1/action/memory/{agentId}/search", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/memory/{agentId}/search");
        route(map, "GET", "/api/v1/action/pipelines", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/pipelines");
        route(map, "POST", "/api/v1/action/pipelines", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/pipelines");
        route(map, "POST", "/api/v1/action/pipelines/draft", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/pipelines/draft");
        route(map, "DELETE", "/api/v1/action/pipelines/{pipelineId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "DELETE /api/v1/action/pipelines/{pipelineId}");
        route(map, "GET", "/api/v1/action/pipelines/{pipelineId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/pipelines/{pipelineId}");
        route(map, "PUT", "/api/v1/action/pipelines/{pipelineId}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "action_plane", "active", "PUT /api/v1/action/pipelines/{pipelineId}");
        route(map, "POST", "/api/v1/action/pipelines/{pipelineId}/execute", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/pipelines/{pipelineId}/execute", java.util.Set.of("action:pipeline:execute"));
        route(map, "GET", "/api/v1/action/pipelines/{pipelineId}/executions", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/pipelines/{pipelineId}/executions");
        route(map, "GET", "/api/v1/action/pipelines/{pipelineId}/executions/{executionId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/pipelines/{pipelineId}/executions/{executionId}");
        route(map, "POST", "/api/v1/action/pipelines/{pipelineId}/executions/{executionId}/cancel", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/pipelines/{pipelineId}/executions/{executionId}/cancel");
        route(map, "GET", "/api/v1/action/pipelines/{pipelineId}/executions/{executionId}/logs", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "action_plane", "active", "GET /api/v1/action/pipelines/{pipelineId}/executions/{executionId}/logs");
        route(map, "POST", "/api/v1/action/pipelines/{pipelineId}/optimise-hint", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "action_plane", "active", "POST /api/v1/action/pipelines/{pipelineId}/optimise-hint");
        route(map, "GET", "/api/v1/action/plugins", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/plugins");
        route(map, "GET", "/api/v1/action/plugins/{id}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/plugins/{id}");
        route(map, "POST", "/api/v1/action/plugins/{id}/conformance", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/plugins/{id}/conformance");
        route(map, "POST", "/api/v1/action/plugins/{id}/disable", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/plugins/{id}/disable");
        route(map, "POST", "/api/v1/action/plugins/{id}/enable", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/plugins/{id}/enable");
        route(map, "GET", "/api/v1/action/plugins/{id}/sandbox", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/plugins/{id}/sandbox");
        route(map, "POST", "/api/v1/action/plugins/{id}/upgrade", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/plugins/{id}/upgrade");
        route(map, "POST", "/api/v1/action/plugins/{id}/validate", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "action_plane", "active", "POST /api/v1/action/plugins/{id}/validate");
        route(map, "GET", "/api/v1/action/plugins/marketplace", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "action_plane", "active", "GET /api/v1/action/plugins/marketplace");
        route(map, "GET", "/api/v1/agents/catalog", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/agents/catalog");
        route(map, "GET", "/api/v1/agents/catalog/{id}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/agents/catalog/{id}");
        route(map, "GET", "/api/v1/ai/actions", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/ai/actions");
        route(map, "GET", "/api/v1/ai/advisories/fabric/{collectionId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/ai/advisories/fabric/{collectionId}");
        route(map, "GET", "/api/v1/ai/advisories/quality/{collectionId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/ai/advisories/quality/{collectionId}");
        route(map, "GET", "/api/v1/ai/advisories/workflows/{workflowId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/ai/advisories/workflows/{workflowId}");
        route(map, "POST", "/api/v1/ai/context/rank", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/context/rank");
        route(map, "GET", "/api/v1/ai/correlations", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/ai/correlations");
        route(map, "GET", "/api/v1/ai/feedback", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/ai/feedback");
        route(map, "POST", "/api/v1/ai/memory/retention", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/memory/retention");
        route(map, "POST", "/api/v1/ai/next-action", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/next-action");
        route(map, "GET", "/api/v1/ai/quality-summary", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/ai/quality-summary");
        route(map, "POST", "/api/v1/ai/quality/drift-detect", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/quality/drift-detect");
        route(map, "POST", "/api/v1/ai/rag-feedback", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/rag-feedback");
        route(map, "POST", "/api/v1/ai/suggestions", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/suggestions");
        route(map, "POST", "/api/v1/ai/suggestions/{id}/apply", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/suggestions/{id}/apply");
        route(map, "POST", "/api/v1/ai/suggestions/{id}/feedback", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/ai/suggestions/{id}/feedback");
        route(map, "GET", "/api/v1/alerts", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/alerts");
        route(map, "POST", "/api/v1/alerts/{alertId}/acknowledge", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/{alertId}/acknowledge");
        route(map, "POST", "/api/v1/alerts/{alertId}/resolve", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/{alertId}/resolve");
        route(map, "POST", "/api/v1/alerts/{id}/auto-remediate", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/{id}/auto-remediate");
        route(map, "POST", "/api/v1/alerts/{id}/escalate", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/{id}/escalate");
        route(map, "POST", "/api/v1/alerts/{id}/remediate", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/{id}/remediate");
        route(map, "POST", "/api/v1/alerts/{id}/remediate/rollback", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/alerts/{id}/remediate/rollback");
        route(map, "GET", "/api/v1/alerts/{id}/remediations", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/alerts/{id}/remediations");
        route(map, "GET", "/api/v1/alerts/groups", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/alerts/groups");
        route(map, "POST", "/api/v1/alerts/groups/{groupId}/resolve", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/groups/{groupId}/resolve");
        route(map, "GET", "/api/v1/alerts/rules", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/alerts/rules");
        route(map, "POST", "/api/v1/alerts/rules", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/rules");
        route(map, "DELETE", "/api/v1/alerts/rules/{ruleId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/alerts/rules/{ruleId}");
        route(map, "PUT", "/api/v1/alerts/rules/{ruleId}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "data_cloud", "active", "PUT /api/v1/alerts/rules/{ruleId}");
        route(map, "GET", "/api/v1/alerts/stream", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/alerts/stream");
        route(map, "GET", "/api/v1/alerts/suggestions", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/alerts/suggestions");
        route(map, "POST", "/api/v1/alerts/suggestions/{suggestionId}/apply", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/alerts/suggestions/{suggestionId}/apply");
        route(map, "POST", "/api/v1/analytics/aggregation", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/analytics/aggregation");
        route(map, "POST", "/api/v1/analytics/automate", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/analytics/automate");
        route(map, "POST", "/api/v1/analytics/explain", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/analytics/explain");
        route(map, "DELETE", "/api/v1/analytics/queries/{queryId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/analytics/queries/{queryId}");
        route(map, "POST", "/api/v1/analytics/query", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/analytics/query");
        route(map, "GET", "/api/v1/analytics/query/{queryId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/analytics/query/{queryId}");
        route(map, "GET", "/api/v1/analytics/query/{queryId}/plan", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/analytics/query/{queryId}/plan");
        route(map, "POST", "/api/v1/analytics/suggest", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/analytics/suggest");
        route(map, "GET", "/api/v1/anomalies", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/anomalies");
        route(map, "GET", "/api/v1/autonomy/domains", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/autonomy/domains");
        route(map, "GET", "/api/v1/autonomy/domains/{domain}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/autonomy/domains/{domain}");
        route(map, "POST", "/api/v1/autonomy/feedback-policy", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/autonomy/feedback-policy");
        route(map, "GET", "/api/v1/autonomy/level", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/autonomy/level");
        route(map, "PUT", "/api/v1/autonomy/level", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "data_cloud", "compatibility-only", "PUT /api/v1/autonomy/level");
        route(map, "GET", "/api/v1/autonomy/logs", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/autonomy/logs");
        route(map, "GET", "/api/v1/autonomy/plan/{actionType}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/autonomy/plan/{actionType}");
        route(map, "POST", "/api/v1/brain/attention/elevate", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/brain/attention/elevate");
        route(map, "GET", "/api/v1/brain/attention/thresholds", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/attention/thresholds");
        route(map, "PUT", "/api/v1/brain/attention/thresholds", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "data_cloud", "active", "PUT /api/v1/brain/attention/thresholds");
        route(map, "GET", "/api/v1/brain/config", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/config");
        route(map, "POST", "/api/v1/brain/explain", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/brain/explain");
        route(map, "GET", "/api/v1/brain/health", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/health");
        route(map, "GET", "/api/v1/brain/patterns", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/patterns");
        route(map, "POST", "/api/v1/brain/patterns/match", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/brain/patterns/match");
        route(map, "GET", "/api/v1/brain/salience/{itemId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/salience/{itemId}");
        route(map, "GET", "/api/v1/brain/stats", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/stats");
        route(map, "GET", "/api/v1/brain/workspace", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/workspace");
        route(map, "GET", "/api/v1/brain/workspace/stream", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/brain/workspace/stream");
        route(map, "GET", "/api/v1/checkpoints", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/checkpoints");
        route(map, "POST", "/api/v1/checkpoints", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/checkpoints");
        route(map, "DELETE", "/api/v1/checkpoints/{checkpointId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "data_cloud", "active", "DELETE /api/v1/checkpoints/{checkpointId}");
        route(map, "GET", "/api/v1/checkpoints/{checkpointId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/checkpoints/{checkpointId}");
        route(map, "GET", "/api/v1/collections", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/collections");
        route(map, "POST", "/api/v1/collections/{collection}/metadata", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/collections/{collection}/metadata");
        route(map, "GET", "/api/v1/collections/{id}/cost-report", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/collections/{id}/cost-report");
        route(map, "POST", "/api/v1/collections/{id}/migrate", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/collections/{id}/migrate");
        route(map, "POST", "/api/v1/compliance/evidence-package", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/compliance/evidence-package");
        route(map, "GET", "/api/v1/compliance/legal-holds", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/compliance/legal-holds");
        route(map, "POST", "/api/v1/compliance/legal-holds", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/compliance/legal-holds");
        route(map, "POST", "/api/v1/compliance/legal-holds/{id}/extend", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/compliance/legal-holds/{id}/extend");
        route(map, "POST", "/api/v1/compliance/legal-holds/{id}/release", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/compliance/legal-holds/{id}/release");
        route(map, "GET", "/api/v1/compliance/posture", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/compliance/posture");
        route(map, "GET", "/api/v1/conformance", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/conformance");
        route(map, "GET", "/api/v1/conformance/entity-store", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/conformance/entity-store");
        route(map, "GET", "/api/v1/conformance/event-log-store", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/conformance/event-log-store");
        route(map, "GET", "/api/v1/connectors", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/connectors");
        route(map, "POST", "/api/v1/connectors", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/connectors");
        route(map, "DELETE", "/api/v1/connectors/{connectionId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/connectors/{connectionId}");
        route(map, "GET", "/api/v1/connectors/{connectionId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/connectors/{connectionId}");
        route(map, "PUT", "/api/v1/connectors/{connectionId}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "data_cloud", "active", "PUT /api/v1/connectors/{connectionId}");
        route(map, "POST", "/api/v1/connectors/{connectionId}/disable", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/connectors/{connectionId}/disable");
        route(map, "POST", "/api/v1/connectors/{connectionId}/enable", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/connectors/{connectionId}/enable");
        route(map, "GET", "/api/v1/connectors/{connectionId}/health", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/connectors/{connectionId}/health");
        route(map, "POST", "/api/v1/connectors/{connectionId}/rotate-credentials", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/connectors/{connectionId}/rotate-credentials");
        route(map, "GET", "/api/v1/connectors/{connectionId}/schema", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/connectors/{connectionId}/schema");
        route(map, "POST", "/api/v1/connectors/{connectionId}/sync", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/connectors/{connectionId}/sync");
        route(map, "GET", "/api/v1/connectors/{connectionId}/sync/status", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/connectors/{connectionId}/sync/status");
        route(map, "POST", "/api/v1/connectors/{connectionId}/test", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/connectors/{connectionId}/test");
        route(map, "POST", "/api/v1/connectors/{connectorId}/sync-health", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/connectors/{connectorId}/sync-health");
        route(map, "POST", "/api/v1/connectors/suggest-mapping", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/connectors/suggest-mapping");
        route(map, "GET", "/api/v1/context", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "context_plane", "active", "GET /api/v1/context");
        route(map, "PUT", "/api/v1/context", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "context_plane", "active", "PUT /api/v1/context");
        route(map, "GET", "/api/v1/context/{collection}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "context_plane", "active", "GET /api/v1/context/{collection}");
        route(map, "GET", "/api/v1/context/{collection}/lineage/trust", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "context_plane", "active", "GET /api/v1/context/{collection}/lineage/trust");
        route(map, "POST", "/api/v1/context/{collection}/rag", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "context_plane", "active", "POST /api/v1/context/{collection}/rag");
        route(map, "POST", "/api/v1/context/{collection}/rag-policy-check", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "context_plane", "active", "POST /api/v1/context/{collection}/rag-policy-check");
        route(map, "DELETE", "/api/v1/context/keys/{key}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "context_plane", "active", "DELETE /api/v1/context/keys/{key}");
        route(map, "GET", "/api/v1/context/snapshot", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "context_plane", "active", "GET /api/v1/context/snapshot");
        route(map, "GET", "/api/v1/data-products", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/data-products");
        route(map, "POST", "/api/v1/data-products", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/data-products");
        route(map, "POST", "/api/v1/data-products/{productId}/contract-check", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/data-products/{productId}/contract-check");
        route(map, "POST", "/api/v1/data-products/{productId}/sla-monitor", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/data-products/{productId}/sla-monitor");
        route(map, "POST", "/api/v1/data-products/{productId}/subscribe", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/data-products/{productId}/subscribe");
        route(map, "GET", "/api/v1/data-quality/trust-scores", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/data-quality/trust-scores");
        route(map, "GET", "/api/v1/entities/{collection}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/entities/{collection}");
        route(map, "POST", "/api/v1/entities/{collection}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}");
        route(map, "DELETE", "/api/v1/entities/{collection}/{id}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/entities/{collection}/{id}");
        route(map, "GET", "/api/v1/entities/{collection}/{id}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/entities/{collection}/{id}");
        route(map, "GET", "/api/v1/entities/{collection}/{id}/history", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/entities/{collection}/{id}/history");
        route(map, "POST", "/api/v1/entities/{collection}/anomalies", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}/anomalies");
        route(map, "DELETE", "/api/v1/entities/{collection}/batch", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/entities/{collection}/batch");
        route(map, "POST", "/api/v1/entities/{collection}/batch", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}/batch");
        route(map, "GET", "/api/v1/entities/{collection}/export", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/entities/{collection}/export");
        route(map, "POST", "/api/v1/entities/{collection}/export", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}/export");
        route(map, "POST", "/api/v1/entities/{collection}/infer-schema", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}/infer-schema");
        route(map, "GET", "/api/v1/entities/{collection}/query/stream", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/entities/{collection}/query/stream");
        route(map, "GET", "/api/v1/entities/{collection}/search", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/entities/{collection}/search");
        route(map, "GET", "/api/v1/entities/{collection}/similar", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "data_cloud", "active", "GET /api/v1/entities/{collection}/similar");
        route(map, "GET", "/api/v1/entities/{collection}/stream", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/entities/{collection}/stream");
        route(map, "POST", "/api/v1/entities/{collection}/suggest", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}/suggest");
        route(map, "POST", "/api/v1/entities/{collection}/validate", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}/validate");
        route(map, "POST", "/api/v1/entities/{collection}/validate/batch", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/entities/{collection}/validate/batch");
        route(map, "GET", "/api/v1/events", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "event_store", "active", "GET /api/v1/events");
        route(map, "POST", "/api/v1/events", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "event_store", "active", "POST /api/v1/events");
        route(map, "GET", "/api/v1/events/{offset}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "event_store", "active", "GET /api/v1/events/{offset}");
        route(map, "GET", "/api/v1/events/notifications", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "event_store", "active", "GET /api/v1/events/notifications");
        route(map, "GET", "/api/v1/executions/{executionId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/executions/{executionId}");
        route(map, "POST", "/api/v1/executions/{executionId}/cancel", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "compatibility-only", "POST /api/v1/executions/{executionId}/cancel");
        route(map, "POST", "/api/v1/executions/{executionId}/checkpoint", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/executions/{executionId}/checkpoint");
        route(map, "GET", "/api/v1/executions/{executionId}/checkpoints", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/executions/{executionId}/checkpoints");
        route(map, "GET", "/api/v1/executions/{executionId}/logs", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/executions/{executionId}/logs");
        route(map, "POST", "/api/v1/executions/{executionId}/restore", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "compatibility-only", "POST /api/v1/executions/{executionId}/restore");
        route(map, "POST", "/api/v1/executions/{executionId}/retry", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/executions/{executionId}/retry");
        route(map, "POST", "/api/v1/executions/{executionId}/rollback", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "compatibility-only", "POST /api/v1/executions/{executionId}/rollback");
        route(map, "POST", "/api/v1/features", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/features");
        route(map, "GET", "/api/v1/features/{entityId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/features/{entityId}");
        route(map, "GET", "/api/v1/governance/compliance/summary", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/governance/compliance/summary");
        route(map, "GET", "/api/v1/governance/inventory", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/governance/inventory");
        route(map, "GET", "/api/v1/governance/policies", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/governance/policies");
        route(map, "POST", "/api/v1/governance/policies", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/governance/policies");
        route(map, "DELETE", "/api/v1/governance/policies/{id}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/governance/policies/{id}");
        route(map, "GET", "/api/v1/governance/policies/{id}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/governance/policies/{id}");
        route(map, "PUT", "/api/v1/governance/policies/{id}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "PUT /api/v1/governance/policies/{id}");
        route(map, "POST", "/api/v1/governance/policies/{id}/toggle", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/governance/policies/{id}/toggle");
        route(map, "POST", "/api/v1/governance/policies/simulate", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/governance/policies/simulate");
        route(map, "GET", "/api/v1/governance/privacy/pii-fields", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/governance/privacy/pii-fields");
        route(map, "POST", "/api/v1/governance/privacy/redact", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/governance/privacy/redact");
        route(map, "GET", "/api/v1/governance/privacy/verify", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/governance/privacy/verify");
        route(map, "POST", "/api/v1/governance/recommend", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/governance/recommend");
        route(map, "POST", "/api/v1/governance/retention/classify", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/governance/retention/classify");
        route(map, "GET", "/api/v1/governance/retention/policy", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/governance/retention/policy");
        route(map, "POST", "/api/v1/governance/retention/purge", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/governance/retention/purge");
        route(map, "GET", "/api/v1/learning/stream", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/learning/stream");
        route(map, "GET", "/api/v1/lineage/{collection}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/lineage/{collection}");
        route(map, "GET", "/api/v1/lineage/{collection}/impact", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/lineage/{collection}/impact");
        route(map, "POST", "/api/v1/mastery/learning-deltas/{deltaId}/dry-run-promotion", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/mastery/learning-deltas/{deltaId}/dry-run-promotion");
        route(map, "POST", "/api/v1/mastery/obsolescence-events/process", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/mastery/obsolescence-events/process");
        route(map, "GET", "/api/v1/mastery/preview/decision", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/mastery/preview/decision");
        route(map, "GET", "/api/v1/mastery/preview/retrieval", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/mastery/preview/retrieval");
        route(map, "GET", "/api/v1/media/artifacts", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/media/artifacts", java.util.Set.of("media:artifact:read"));
        route(map, "POST", "/api/v1/media/artifacts", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/media/artifacts", java.util.Set.of("media:artifact:create"));
        route(map, "DELETE", "/api/v1/media/artifacts/{artifactId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/media/artifacts/{artifactId}", java.util.Set.of("media:artifact:delete"));
        route(map, "GET", "/api/v1/media/artifacts/{artifactId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/media/artifacts/{artifactId}", java.util.Set.of("media:artifact:read"));
        route(map, "POST", "/api/v1/media/artifacts/{artifactId}/analyze", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/media/artifacts/{artifactId}/analyze", java.util.Set.of("media:artifact:process"));
        route(map, "POST", "/api/v1/media/artifacts/{artifactId}/transcribe", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/media/artifacts/{artifactId}/transcribe", java.util.Set.of("media:artifact:process"));
        route(map, "GET", "/api/v1/models", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/models");
        route(map, "POST", "/api/v1/models", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/models");
        route(map, "GET", "/api/v1/models/{modelName}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/models/{modelName}");
        route(map, "POST", "/api/v1/models/{modelName}/promote", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/models/{modelName}/promote");
        route(map, "POST", "/api/v1/operations/anomaly-group", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/operations/anomaly-group");
        route(map, "POST", "/api/v1/operations/forecast", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/operations/forecast");
        route(map, "GET", "/api/v1/operations/jobs", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/operations/jobs");
        route(map, "GET", "/api/v1/operations/jobs/{operationId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/operations/jobs/{operationId}");
        route(map, "POST", "/api/v1/operations/jobs/{operationId}/cancel", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/operations/jobs/{operationId}/cancel");
        route(map, "POST", "/api/v1/pipelines/{pipelineId}/optimise-hint", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/pipelines/{pipelineId}/optimise-hint");
        route(map, "POST", "/api/v1/pipelines/draft", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/pipelines/draft");
        route(map, "GET", "/api/v1/plugins", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/plugins");
        route(map, "GET", "/api/v1/plugins/{id}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/plugins/{id}");
        route(map, "POST", "/api/v1/plugins/{id}/conformance", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/plugins/{id}/conformance");
        route(map, "POST", "/api/v1/plugins/{id}/disable", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/plugins/{id}/disable");
        route(map, "POST", "/api/v1/plugins/{id}/enable", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/plugins/{id}/enable");
        route(map, "GET", "/api/v1/plugins/{id}/sandbox", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/plugins/{id}/sandbox");
        route(map, "POST", "/api/v1/plugins/{id}/upgrade", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/plugins/{id}/upgrade");
        route(map, "POST", "/api/v1/plugins/{id}/validate", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/plugins/{id}/validate");
        route(map, "GET", "/api/v1/plugins/marketplace", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /api/v1/plugins/marketplace");
        route(map, "GET", "/api/v1/queries/estimate", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/queries/estimate");
        route(map, "POST", "/api/v1/queries/explain", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "compatibility-only", "POST /api/v1/queries/explain");
        route(map, "POST", "/api/v1/queries/federated", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/queries/federated");
        route(map, "POST", "/api/v1/query/nlq", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/query/nlq");
        route(map, "GET", "/api/v1/reports", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/reports");
        route(map, "POST", "/api/v1/reports", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/reports");
        route(map, "GET", "/api/v1/reports/{reportId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/reports/{reportId}");
        route(map, "GET", "/api/v1/release-readiness", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/release-readiness");
        route(map, "POST", "/api/v1/release-readiness", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/release-readiness");
        route(map, "GET", "/api/v1/release-readiness/stats", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/release-readiness/stats");
        route(map, "GET", "/api/v1/release-readiness/{productId}/{productVersion}/{releaseTarget}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/release-readiness/{productId}/{productVersion}/{releaseTarget}");
        route(map, "DELETE", "/api/v1/release-readiness/{id}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/release-readiness/{id}");
        route(map, "GET", "/api/v1/settings", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/settings");
        route(map, "POST", "/api/v1/settings", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/settings");
        route(map, "POST", "/api/v1/settings/approval-request", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/settings/approval-request");
        route(map, "GET", "/api/v1/settings/approvals", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/settings/approvals");
        route(map, "POST", "/api/v1/settings/approvals/{id}/approve", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/settings/approvals/{id}/approve");
        route(map, "POST", "/api/v1/settings/approvals/{id}/reject", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/settings/approvals/{id}/reject");
        route(map, "GET", "/api/v1/settings/keys", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/settings/keys");
        route(map, "POST", "/api/v1/settings/keys", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/settings/keys");
        route(map, "GET", "/api/v1/settings/keys/{id}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/settings/keys/{id}");
        route(map, "DELETE", "/api/v1/settings/keys/{id}/revoke", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/settings/keys/{id}/revoke");
        route(map, "POST", "/api/v1/settings/keys/{id}/rotate", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/settings/keys/{id}/rotate");
        route(map, "GET", "/api/v1/settings/notifications", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/settings/notifications");
        route(map, "PATCH", "/api/v1/settings/notifications", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "PATCH /api/v1/settings/notifications");
        route(map, "GET", "/api/v1/settings/preferences", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/settings/preferences");
        route(map, "PATCH", "/api/v1/settings/preferences", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "PATCH /api/v1/settings/preferences");
        route(map, "GET", "/api/v1/settings/profile", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/settings/profile");
        route(map, "PATCH", "/api/v1/settings/profile", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "PATCH /api/v1/settings/profile");
        route(map, "GET", "/api/v1/settings/security", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "GET /api/v1/settings/security");
        route(map, "POST", "/api/v1/settings/security", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/settings/security");
        route(map, "GET", "/api/v1/sovereign/audit", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/audit");
        route(map, "GET", "/api/v1/sovereign/backup", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/backup");
        route(map, "POST", "/api/v1/sovereign/backup", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/sovereign/backup");
        route(map, "GET", "/api/v1/sovereign/conformance", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/conformance");
        route(map, "GET", "/api/v1/sovereign/data-residency", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/data-residency");
        route(map, "GET", "/api/v1/sovereign/data-subject-controls", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/data-subject-controls");
        route(map, "PUT", "/api/v1/sovereign/data-subject-controls", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "PUT /api/v1/sovereign/data-subject-controls");
        route(map, "GET", "/api/v1/sovereign/models", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/models");
        route(map, "GET", "/api/v1/sovereign/profile", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/profile");
        route(map, "PUT", "/api/v1/sovereign/profile", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "PUT /api/v1/sovereign/profile");
        route(map, "GET", "/api/v1/sovereign/region-policy", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.AUDITOR, true, "data_cloud", "active", "GET /api/v1/sovereign/region-policy");
        route(map, "POST", "/api/v1/sovereign/restore", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/sovereign/restore");
        route(map, "POST", "/api/v1/sovereign/validate-transfer", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, false, "data_cloud", "active", "POST /api/v1/sovereign/validate-transfer");
        route(map, "GET", "/api/v1/surfaces", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/surfaces");
        route(map, "GET", "/api/v1/surfaces/schema", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/surfaces/schema");
        route(map, "GET", "/api/v1/storage-profiles", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/storage-profiles");
        route(map, "POST", "/api/v1/storage-profiles", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/storage-profiles");
        route(map, "DELETE", "/api/v1/storage-profiles/{profileId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "data_cloud", "active", "DELETE /api/v1/storage-profiles/{profileId}");
        route(map, "GET", "/api/v1/storage-profiles/{profileId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/storage-profiles/{profileId}");
        route(map, "PUT", "/api/v1/storage-profiles/{profileId}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "data_cloud", "active", "PUT /api/v1/storage-profiles/{profileId}");
        route(map, "GET", "/api/v1/storage-profiles/{profileId}/metrics", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/storage-profiles/{profileId}/metrics");
        route(map, "POST", "/api/v1/storage-profiles/{profileId}/set-default", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/storage-profiles/{profileId}/set-default");
        route(map, "POST", "/api/v1/user-activity/log", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/user-activity/log");
        route(map, "GET", "/api/v1/user-activity/recent", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/user-activity/recent");
        route(map, "POST", "/api/v1/voice/intent", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/voice/intent");
        route(map, "POST", "/api/v1/voice/intent/classify", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/voice/intent/classify");
        route(map, "GET", "/api/v1/voice/intents", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "active", "GET /api/v1/voice/intents");
        route(map, "POST", "/api/v1/workflows/analyze-risk", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/workflows/analyze-risk");
        route(map, "POST", "/api/v1/workflows/validate", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "data_cloud", "active", "POST /api/v1/workflows/validate");
        route(map, "GET", "/data-fabric/connectors", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "compatibility", "compatibility-only", "GET /data-fabric/connectors");
        route(map, "POST", "/data-fabric/connectors", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "compatibility", "compatibility-only", "POST /data-fabric/connectors");
        route(map, "DELETE", "/data-fabric/connectors/{connectionId}", EndpointSensitivity.CRITICAL, true, true, true, true, DataCloudSecurityFilter.AccessLevel.ADMIN, true, "compatibility", "compatibility-only", "DELETE /data-fabric/connectors/{connectionId}");
        route(map, "GET", "/data-fabric/connectors/{connectionId}", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "compatibility", "compatibility-only", "GET /data-fabric/connectors/{connectionId}");
        route(map, "PUT", "/data-fabric/connectors/{connectionId}", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, true, "compatibility", "compatibility-only", "PUT /data-fabric/connectors/{connectionId}");
        route(map, "POST", "/data-fabric/connectors/{connectionId}/disable", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "compatibility", "compatibility-only", "POST /data-fabric/connectors/{connectionId}/disable");
        route(map, "POST", "/data-fabric/connectors/{connectionId}/enable", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "compatibility", "compatibility-only", "POST /data-fabric/connectors/{connectionId}/enable");
        route(map, "GET", "/data-fabric/connectors/{connectionId}/statistics", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "compatibility", "compatibility-only", "GET /data-fabric/connectors/{connectionId}/statistics");
        route(map, "POST", "/data-fabric/connectors/{connectionId}/sync", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "compatibility", "compatibility-only", "POST /data-fabric/connectors/{connectionId}/sync");
        route(map, "POST", "/data-fabric/connectors/{connectionId}/test", EndpointSensitivity.SENSITIVE, true, true, false, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "compatibility", "compatibility-only", "POST /data-fabric/connectors/{connectionId}/test");
        route(map, "GET", "/data-fabric/metrics", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "compatibility", "compatibility-only", "GET /data-fabric/metrics");
        route(map, "GET", "/events/notifications", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /events/notifications");
        route(map, "GET", "/events/stream", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "data_cloud", "compatibility-only", "GET /events/stream");
        route(map, "GET", "/health", EndpointSensitivity.PUBLIC, false, false, false, false, DataCloudSecurityFilter.AccessLevel.NONE, true, "none", "active", "GET /health");
        route(map, "GET", "/health/deep", EndpointSensitivity.PUBLIC, false, false, false, false, DataCloudSecurityFilter.AccessLevel.NONE, true, "none", "active", "GET /health/deep");
        route(map, "GET", "/health/detail", EndpointSensitivity.PUBLIC, false, false, false, false, DataCloudSecurityFilter.AccessLevel.NONE, true, "none", "active", "GET /health/detail");
        route(map, "GET", "/info", EndpointSensitivity.PUBLIC, false, false, false, false, DataCloudSecurityFilter.AccessLevel.NONE, true, "none", "active", "GET /info");
        route(map, "GET", "/live", EndpointSensitivity.PUBLIC, false, false, false, false, DataCloudSecurityFilter.AccessLevel.NONE, true, "none", "active", "GET /live");
        route(map, "GET", "/mcp/v1/tools", EndpointSensitivity.INTERNAL, true, true, false, false, DataCloudSecurityFilter.AccessLevel.VIEWER, true, "mcp", "active", "GET /mcp/v1/tools");
        route(map, "POST", "/mcp/v1/tools", EndpointSensitivity.SENSITIVE, true, true, true, false, DataCloudSecurityFilter.AccessLevel.OPERATOR, false, "mcp", "active", "POST /mcp/v1/tools");
        route(map, "GET", "/metrics", EndpointSensitivity.PUBLIC, false, false, false, false, DataCloudSecurityFilter.AccessLevel.NONE, true, "none", "active", "GET /metrics");
        route(map, "GET", "/ready", EndpointSensitivity.PUBLIC, false, false, false, false, DataCloudSecurityFilter.AccessLevel.NONE, true, "none", "active", "GET /ready");
        METADATA_BY_ROUTE = Collections.unmodifiableMap(map);

        Map<String, Pattern> matchers = new HashMap<>();
        for (String key : METADATA_BY_ROUTE.keySet()) {
            int separator = key.indexOf(' ');
            String method = key.substring(0, separator);
            String routePath = key.substring(separator + 1);
            String regex = routePath.replaceAll("\\{[^}]+}", "[^/]+");
            matchers.put(key, Pattern.compile("^" + Pattern.quote(method) + " " + regex + "$"));
        }
        MATCHERS_BY_ROUTE = Collections.unmodifiableMap(matchers);
    }

    private RouteSecurityRegistry() {
    }

    private static void route(
            Map<String, RouteSecurityMetadata> map,
            String method,
            String canonicalPath,
            EndpointSensitivity sensitivity,
            boolean requiresAuth,
            boolean requiresTenant,
            boolean requiresPolicy,
            boolean requiresBlockingAudit,
            DataCloudSecurityFilter.AccessLevel requiredAccess,
            boolean idempotent,
            String runtimeTruthSurface,
            String legacyStatus,
            String description) {
        map.put(
                method + " " + canonicalPath,
                RouteSecurityMetadata.builder()
                        .method(method)
                        .canonicalPath(canonicalPath)
                        .sensitivity(sensitivity)
                        .requiresAuth(requiresAuth)
                        .requiresTenant(requiresTenant)
                        .requiresPolicy(requiresPolicy)
                        .requiresBlockingAudit(requiresBlockingAudit)
                        .requiredAccess(requiredAccess)
                        .idempotent(idempotent)
                        .runtimeTruthSurface(runtimeTruthSurface)
                        .legacyStatus(legacyStatus)
                        .description(description)
                        .build());
    }

    private static void route(
            Map<String, RouteSecurityMetadata> map,
            String method,
            String canonicalPath,
            EndpointSensitivity sensitivity,
            boolean requiresAuth,
            boolean requiresTenant,
            boolean requiresPolicy,
            boolean requiresBlockingAudit,
            DataCloudSecurityFilter.AccessLevel requiredAccess,
            boolean idempotent,
            String runtimeTruthSurface,
            String legacyStatus,
            String description,
            java.util.Set<String> requiredPermissions) {
        map.put(
                method + " " + canonicalPath,
                RouteSecurityMetadata.builder()
                        .method(method)
                        .canonicalPath(canonicalPath)
                        .sensitivity(sensitivity)
                        .requiresAuth(requiresAuth)
                        .requiresTenant(requiresTenant)
                        .requiresPolicy(requiresPolicy)
                        .requiresBlockingAudit(requiresBlockingAudit)
                        .requiredAccess(requiredAccess)
                        .idempotent(idempotent)
                        .runtimeTruthSurface(runtimeTruthSurface)
                        .legacyStatus(legacyStatus)
                        .description(description)
                        .requiredPermissions(requiredPermissions)
                        .build());
    }

    public static Optional<RouteSecurityMetadata> lookup(String method, String canonicalPath) {
        String key = method.toUpperCase() + " " + canonicalPath;
        return Optional.ofNullable(METADATA_BY_ROUTE.get(key));
    }

    public static Optional<RouteSecurityMetadata> lookupWithFallback(String method, String path) {
        return lookup(method, path).or(() -> lookupRuntimePath(method, path));
    }

    /**
     * Compatibility accessor for tests and callers that use ActiveJ method enums.
     *
     * <p>Returns {@code null} when a route is unknown so legacy assertions can
     * continue to distinguish missing metadata from present metadata.
     */
    public static RouteSecurityMetadata getMetadata(HttpMethod method, String path) {
        if (method == null) {
            return null;
        }
        return lookupWithFallback(method.name(), path).orElse(null);
    }

    public static Optional<RouteSecurityMetadata> lookupRuntimePath(String method, String path) {
        String requestKey = method.toUpperCase() + " " + stripQuery(path);
        RouteSecurityMetadata bestMatch = null;
        int bestParameterCount = Integer.MAX_VALUE;
        int bestPathLength = -1;

        for (Map.Entry<String, Pattern> entry : MATCHERS_BY_ROUTE.entrySet()) {
            if (entry.getValue().matcher(requestKey).matches()) {
                RouteSecurityMetadata candidate = METADATA_BY_ROUTE.get(entry.getKey());
                int parameterCount = countPathParameters(candidate.canonicalPath());
                int pathLength = candidate.canonicalPath().length();

                if (parameterCount < bestParameterCount
                        || (parameterCount == bestParameterCount && pathLength > bestPathLength)) {
                    bestMatch = candidate;
                    bestParameterCount = parameterCount;
                    bestPathLength = pathLength;
                }
            }
        }
        return Optional.ofNullable(bestMatch);
    }

    private static int countPathParameters(String canonicalPath) {
        int count = 0;
        for (int i = 0; i < canonicalPath.length(); i++) {
            if (canonicalPath.charAt(i) == '{') {
                count++;
            }
        }
        return count;
    }

    public static RouteSecurityMetadata requireForRequest(String method, String path, String profile) {
        return lookupRuntimePath(method, path)
                .orElseThrow(() -> new IllegalStateException(
                        "No route security metadata for " + method.toUpperCase() + " " + path + " in " + profile + " profile"));
    }

    public static boolean isProductionLikeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return RuntimeProfile.resolve().isProduction();
        }
        String normalized = profile.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("production") || normalized.equals("staging") || normalized.equals("sovereign");
    }

    public static Map<String, RouteSecurityMetadata> allRoutes() {
        return METADATA_BY_ROUTE;
    }

    public static int size() {
        return METADATA_BY_ROUTE.size();
    }

    private static String stripQuery(String path) {
        int queryStart = path.indexOf('?');
        return queryStart >= 0 ? path.substring(0, queryStart) : path;
    }
}
