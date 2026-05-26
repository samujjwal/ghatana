package com.ghatana.digitalmarketing.api.openapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DMOS OpenAPI generator")
class DmosOpenApiGeneratorTest {

    @Test
    @DisplayName("includes campaign lifecycle servlet routes")
    void includesCampaignLifecycleServletRoutes() {
        Map<String, Object> spec = DmosOpenApiGenerator.generateOpenApiSpec();
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");

        assertThat(paths).containsKeys(
            "/v1/workspaces/{workspaceId}/campaigns/{id}/transition",
            "/v1/workspaces/{workspaceId}/campaigns/{id}/request-approval",
            "/v1/workspaces/{workspaceId}/campaigns/{id}/duplicate"
        );
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/transition", "post"))
            .containsEntry("summary", "Transition campaign");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/request-approval", "post"))
            .containsEntry("summary", "Request campaign approval");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/duplicate", "post"))
            .containsEntry("summary", "Duplicate campaign");
    }

    @Test
    @DisplayName("includes next-best-action servlet routes")
    void includesNextBestActionServletRoutes() {
        Map<String, Object> spec = DmosOpenApiGenerator.generateOpenApiSpec();
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");

        assertThat(paths).containsKeys(
            "/v1/workspaces/{workspaceId}/next-best-action-recommendations",
            "/v1/workspaces/{workspaceId}/next-best-action-recommendations/{recId}",
            "/v1/workspaces/{workspaceId}/next-best-action-recommendations/{recId}/approve",
            "/v1/workspaces/{workspaceId}/next-best-action-recommendations/{recId}/reject"
        );
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/next-best-action-recommendations", "get"))
            .containsEntry("summary", "List next-best-action recommendations");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/next-best-action-recommendations", "post"))
            .containsEntry("summary", "Publish next-best-action recommendation");
    }

    @Test
    @DisplayName("preserves non-breaking compatibility paths")
    void preservesNonBreakingCompatibilityPaths() {
        Map<String, Object> spec = DmosOpenApiGenerator.generateOpenApiSpec();
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");

        assertThat(paths).containsKey("/v1/workspaces/{workspaceId}/budgets");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/budgets", "get"))
            .containsEntry("summary", "List budgets");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/budgets", "post"))
            .containsEntry("summary", "Generate budget");
        assertThat(responses(paths, "/health", "get")).containsKey("503");
    }

    @Test
    @DisplayName("includes stable route manifest paths")
    void includesStableRouteManifestPaths() {
        Map<String, Object> spec = DmosOpenApiGenerator.generateOpenApiSpec();
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");

        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/dashboard", "get"))
            .containsEntry("summary", "Get dashboard summary");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/connectors/google-ads/{connectorId}/readiness", "get"))
            .containsEntry("summary", "Get Google Ads connector readiness");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/release-readiness", "get"))
            .containsEntry("summary", "Get release readiness");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/approvals/{id}/decide", "post"))
            .containsEntry("summary", "Decide approval");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/strategy", "get"))
            .containsEntry("summary", "Get strategy");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/strategy", "post"))
            .containsEntry("summary", "Generate strategy");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/strategy/{strategyId}/submit", "post"))
            .containsEntry("summary", "Submit strategy for approval");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/strategy/{strategyId}/approve", "post"))
            .containsEntry("summary", "Approve strategy");
        assertThat(operation(paths, "/v1/workspaces/{workspaceId}/budget", "get"))
            .containsEntry("summary", "Get budget");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responses(Map<String, Object> paths, String path, String method) {
        return (Map<String, Object>) operation(paths, path, method).get("responses");
    }
}
