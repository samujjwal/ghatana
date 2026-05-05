package com.ghatana.digitalmarketing.api.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P0-009: Canonical OpenAPI contract generator for DMOS.
 *
 * @doc.type class
 * @doc.purpose Generate canonical OpenAPI spec from servlet routes (DMOS-P0-009)
 * @doc.layer product
 * @doc.pattern Generator, Contract
 */
public final class DmosOpenApiGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private DmosOpenApiGenerator() {}

    public static Map<String, Object> generateOpenApiSpec() {
        Map<String, Object> openApi = new LinkedHashMap<>();
        openApi.put("openapi", "3.0.3");
        
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "DMOS API");
        info.put("description", "Digital Marketing Orchestration System API");
        info.put("version", "1.0.0");
        openApi.put("info", info);
        
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", Map.of(
            "bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT")
        ));
        openApi.put("components", components);
        openApi.put("security", java.util.List.of(Map.of("bearerAuth", java.util.List.of())));
        
        Map<String, Object> paths = new LinkedHashMap<>();
        addCampaignPaths(paths);
        addStrategyPaths(paths);
        addBudgetPaths(paths);
        addApprovalPaths(paths);
        addAiActionLogPaths(paths);
        addWorkspacePaths(paths);
        addHealthPaths(paths);
        addPublicIntakePaths(paths);
        openApi.put("paths", paths);
        
        return openApi;
    }

    private static void addPath(Map<String, Object> paths, String path, String method, 
                               String summary, String description) {
        Map<String, Object> pathItem = paths.containsKey(path) 
            ? (Map<String, Object>) paths.get(path) 
            : new LinkedHashMap<>();
        
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", summary);
        operation.put("description", description);
        operation.put("responses", Map.of(
            "200", Map.of("description", "Success"),
            "400", Map.of("description", "Bad Request"),
            "401", Map.of("description", "Unauthorized"),
            "403", Map.of("description", "Forbidden"),
            "404", Map.of("description", "Not Found"),
            "500", Map.of("description", "Internal Server Error")
        ));
        pathItem.put(method.toLowerCase(), operation);
        paths.put(path, pathItem);
    }

    private static void addCampaignPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns", "GET", 
            "List campaigns", "Paginated list of campaigns for a workspace");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns", "POST", 
            "Create campaign", "Create a new campaign in the workspace");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}", "GET", 
            "Get campaign", "Retrieve a specific campaign by ID");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/launch", "POST", 
            "Launch campaign", "Launch a campaign");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/pause", "POST", 
            "Pause campaign", "Pause a running campaign");
    }

    private static void addStrategyPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/strategies", "GET", 
            "List strategies", "List marketing strategies for a workspace");
        addPath(paths, "/v1/workspaces/{workspaceId}/strategies", "POST", 
            "Generate strategy", "Generate an AI-powered marketing strategy");
        addPath(paths, "/v1/workspaces/{workspaceId}/strategies/{id}/submit", "POST", 
            "Submit strategy for approval", "Submit a strategy for approval");
    }

    private static void addBudgetPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/budgets", "GET", 
            "List budgets", "List budget recommendations for a workspace");
        addPath(paths, "/v1/workspaces/{workspaceId}/budgets", "POST", 
            "Generate budget", "Generate AI-powered budget recommendations");
        addPath(paths, "/v1/workspaces/{workspaceId}/budgets/{id}/submit", "POST", 
            "Submit budget for approval", "Submit a budget for approval");
    }

    private static void addApprovalPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/approvals", "GET", 
            "List approvals", "List pending approvals for a workspace");
        addPath(paths, "/v1/workspaces/{workspaceId}/approvals/{id}", "GET", 
            "Get approval", "Retrieve a specific approval");
        addPath(paths, "/v1/workspaces/{workspaceId}/approvals/{id}/approve", "POST", 
            "Approve", "Approve a pending approval");
        addPath(paths, "/v1/workspaces/{workspaceId}/approvals/{id}/reject", "POST", 
            "Reject", "Reject a pending approval");
    }

    private static void addAiActionLogPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/ai-actions", "GET", 
            "List AI actions", "List AI-generated actions for a workspace");
        addPath(paths, "/v1/workspaces/{workspaceId}/ai-actions/{id}", "GET", 
            "Get AI action", "Retrieve a specific AI action");
    }

    private static void addWorkspacePaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces", "GET", 
            "List workspaces", "List workspaces accessible to the user");
        addPath(paths, "/v1/workspaces/{workspaceId}", "GET", 
            "Get workspace", "Retrieve a specific workspace");
    }

    private static void addHealthPaths(Map<String, Object> paths) {
        addPath(paths, "/health", "GET", 
            "Health check", "Check API health status");
    }

    private static void addPublicIntakePaths(Map<String, Object> paths) {
        addPath(paths, "/public/intake", "POST", 
            "Public intake", "Submit public intake questionnaire");
    }

    /**
     * CLI entry point for Gradle JavaExec tasks.
     * Usage: DmosOpenApiGenerator &lt;outputFilePath&gt;
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: DmosOpenApiGenerator <outputFilePath>");
            System.exit(1);
        }
        Map<String, Object> spec = generateOpenApiSpec();
        java.io.File outputFile = new java.io.File(args[0]);
        outputFile.getParentFile().mkdirs();
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile, spec);
        System.out.println("Generated OpenAPI spec to " + outputFile.getAbsolutePath());
    }
}
