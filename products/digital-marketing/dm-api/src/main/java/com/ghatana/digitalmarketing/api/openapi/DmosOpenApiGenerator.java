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
        
        // Security schemes
        components.put("securitySchemes", Map.of(
            "bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT")
        ));
        
        // P0-009: Component schemas for domain types and error envelope
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("CampaignType", Map.of(
            "type", "string",
            "enum", java.util.List.of("EMAIL", "SOCIAL", "PAID_SEARCH", "PUSH", "SMS", "OMNICHANNEL"),
            "description", "The channel or method type of a DMOS campaign"
        ));
        schemas.put("CampaignStatus", Map.of(
            "type", "string",
            "enum", java.util.List.of(
                "DRAFT",
                "PENDING_APPROVAL",
                "APPROVED",
                "PENDING_LAUNCH",
                "LAUNCH_RUNNING",
                "LAUNCH_FAILED",
                "EXTERNAL_EXECUTION_BLOCKED",
                "LAUNCHED",
                "PAUSED",
                "COMPLETED",
                "ARCHIVED",
                "ROLLED_BACK"
            ),
            "description", "Lifecycle status of a DMOS campaign"
        ));
        schemas.put("ErrorBody", Map.of(
            "type", "object",
            "required", java.util.List.of("error", "message", "status", "correlationId"),
            "properties", Map.of(
                "error", Map.of("type", "string", "description", "Error code (e.g., BAD_REQUEST, FORBIDDEN)"),
                "message", Map.of("type", "string", "description", "Human-readable error message"),
                "status", Map.of("type", "integer", "description", "HTTP status code"),
                "correlationId", Map.of("type", "string", "description", "Correlation ID for tracing"),
                "details", Map.of("type", "object", "description", "Additional error details")
            )
        ));
        schemas.put("Campaign", Map.of(
            "type", "object",
            "required", java.util.List.of("id", "workspaceId", "name", "status", "type", "createdBy", "createdAt", "updatedAt"),
            "properties", Map.of(
                "id", Map.of("type", "string", "description", "Campaign ID"),
                "workspaceId", Map.of("type", "string", "description", "Workspace ID"),
                "name", Map.of("type", "string", "description", "Campaign name"),
                "status", Map.of("$ref", "#/components/schemas/CampaignStatus"),
                "type", Map.of("$ref", "#/components/schemas/CampaignType"),
                "createdBy", Map.of("type", "string", "description", "User who created the campaign"),
                "createdAt", Map.of("type", "string", "format", "date-time", "description", "Creation timestamp"),
                "updatedAt", Map.of("type", "string", "format", "date-time", "description", "Last update timestamp")
            )
        ));
        schemas.put("CreateCampaignRequest", Map.of(
            "type", "object",
            "required", java.util.List.of("name", "type"),
            "properties", Map.of(
                "name", Map.of("type", "string", "description", "Campaign name"),
                "type", Map.of("$ref", "#/components/schemas/CampaignType")
            )
        ));
        schemas.put("CampaignListResponse", Map.of(
            "type", "object",
            "required", java.util.List.of("items", "count", "offset"),
            "properties", Map.of(
                "items", Map.of("type", "array", "items", Map.of("$ref", "#/components/schemas/Campaign")),
                "count", Map.of("type", "integer", "description", "Number of items in this page"),
                "offset", Map.of("type", "integer", "description", "Pagination offset")
            )
        ));
        components.put("schemas", schemas);
        
        openApi.put("components", components);
        openApi.put("security", java.util.List.of(Map.of("bearerAuth", java.util.List.of())));
        
        Map<String, Object> paths = new LinkedHashMap<>();
        addCampaignPaths(paths);
        addStrategyPaths(paths);
        addBudgetPaths(paths);
        addApprovalPaths(paths);
        addAiActionLogPaths(paths);
        addWorkspacePaths(paths);
        addCapabilitiesPaths(paths);
        addAdCopyPaths(paths);
        addCompetitorResearchPaths(paths);
        addContentValidationPaths(paths);
        addContentVersionPaths(paths);
        addEmailFollowUpPaths(paths);
        addIntakeQuestionnairePaths(paths);
        addLandingPagePaths(paths);
        addLeadScoringPaths(paths);
        addProposalPaths(paths);
        addSowPaths(paths);
        addWebsiteAuditPaths(paths);
        addRouteEntitlementPaths(paths);
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
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/complete", "POST",
            "Complete campaign", "Mark a campaign as completed (P1-005)");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/archive", "POST",
            "Archive campaign", "Archive a completed campaign (P1-005)");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/rollback", "POST",
            "Rollback campaign", "Rollback a campaign to DRAFT status (P1-005)");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/approve", "POST", 
            "Approve campaign", "Approve a pending campaign approval");
        addPath(paths, "/v1/workspaces/{workspaceId}/campaigns/{id}/reject", "POST", 
            "Reject campaign", "Reject a pending campaign approval");
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
        // P1-5: Standardize on /budget-recommendation to match servlet and UI implementation
        addPath(paths, "/v1/workspaces/{workspaceId}/budget-recommendation", "GET",
            "List budget recommendations", "List budget recommendations for a workspace");
        addPath(paths, "/v1/workspaces/{workspaceId}/budget-recommendation", "POST",
            "Generate budget recommendation", "Generate AI-powered budget recommendations");
        addPath(paths, "/v1/workspaces/{workspaceId}/budget-recommendation/{id}/submit", "POST",
            "Submit budget for approval", "Submit a budget for approval");
        addPath(paths, "/v1/workspaces/{workspaceId}/budget-recommendation/{id}/approve", "POST",
            "Approve budget", "Approve a budget recommendation");
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

    private static void addCapabilitiesPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/capabilities", "GET",
            "Get workspace capabilities", "Retrieve capability flags for a workspace (P0-002)");
    }

    private static void addAdCopyPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/ad-copy/generate", "POST",
            "Generate ad copy", "Generate AI-powered ad copy drafts");
        addPath(paths, "/v1/workspaces/{workspaceId}/ad-copy/latest-approved", "GET",
            "Get latest approved ad copy", "Retrieve the latest approved ad copy");
    }

    private static void addCompetitorResearchPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/competitor-research", "POST",
            "Generate competitor research", "Generate AI-powered competitor intelligence");
    }

    private static void addContentValidationPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/content-validation", "POST",
            "Validate content", "Validate content against compliance rules");
    }

    private static void addContentVersionPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/content-versions", "GET",
            "List content versions", "List content version history");
        addPath(paths, "/v1/workspaces/{workspaceId}/content-versions/{versionId}", "GET",
            "Get content version", "Retrieve a specific content version");
    }

    private static void addEmailFollowUpPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/email-followup", "POST",
            "Generate email follow-up", "Generate AI-powered email follow-up sequences");
    }

    private static void addIntakeQuestionnairePaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/intake-questionnaire", "POST",
            "Submit intake questionnaire", "Submit workspace intake questionnaire");
    }

    private static void addLandingPagePaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/landing-pages", "POST",
            "Generate landing page", "Generate AI-powered landing page content");
    }

    private static void addLeadScoringPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/lead-scoring", "POST",
            "Score leads", "Score leads using AI-powered lead scoring");
    }

    private static void addProposalPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/proposals", "POST",
            "Generate proposal", "Generate AI-powered marketing proposal");
    }

    private static void addSowPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/sows", "POST",
            "Generate SOW", "Generate AI-powered statement of work");
    }

    private static void addWebsiteAuditPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/workspaces/{workspaceId}/website-audit", "POST",
            "Audit website", "Generate AI-powered website audit report");
    }

    private static void addRouteEntitlementPaths(Map<String, Object> paths) {
        addPath(paths, "/v1/routes/entitlements", "GET",
            "Get route entitlements", "Retrieve route entitlement information");
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
