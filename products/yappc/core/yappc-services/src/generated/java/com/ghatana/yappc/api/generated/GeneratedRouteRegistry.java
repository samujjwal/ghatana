package com.ghatana.yappc.api.generated;

/**
 * AUTO-GENERATED - DO NOT EDIT
 * Generated from docs/api/route-manifest.yaml
 * Run: python scripts/generate-route-registry.py
 */
import com.ghatana.yappc.governance.route.AuthMode;
import com.ghatana.yappc.governance.route.Boundary;
import com.ghatana.yappc.governance.route.PrivacyClassification;
import com.ghatana.yappc.governance.route.RouteEntry;
import com.ghatana.yappc.governance.route.RouteManifest;
import java.util.List;
import java.util.Set;

public final class GeneratedRouteRegistry {
    private static final RouteManifest MANIFEST = new RouteManifest();
    
    static {
        initializeManifest();
    }
    
    private static void initializeManifest() {
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "GET",
            "/health",
            AuthMode.PUBLIC,
            Set.of(),
            "yappc-services",
            Boundary.YAPPC,
            "liveness",
            "LIVENESS_CHECK",
            PrivacyClassification.PUBLIC
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "GET",
            "/api/v1/yappc/info",
            AuthMode.PUBLIC,
            Set.of(),
            "yappc-services",
            Boundary.YAPPC,
            "serviceInfo",
            "SERVICE_INFO_READ",
            PrivacyClassification.PUBLIC
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/intent/capture",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "captureIntent",
            "INTENT_CAPTURE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/intent/analyze",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "analyzeIntent",
            "INTENT_ANALYZE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "GET",
            "/api/v1/yappc/intent/{id}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "getIntent",
            "INTENT_READ",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/shape/derive",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "deriveShape",
            "SHAPE_DERIVE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/shape/model",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "modelShape",
            "SHAPE_MODEL",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "GET",
            "/api/v1/yappc/shape/{id}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "getShape",
            "SHAPE_READ",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/validate",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "validateArtifacts",
            "VALIDATE_ARTIFACTS",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/validate/with-config",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "validateWithConfig",
            "VALIDATE_WITH_CONFIG",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/validate/with-policy",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "validateWithPolicy",
            "VALIDATE_WITH_POLICY",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/generate",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "generateArtifacts",
            "GENERATE_ARTIFACTS",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/generate/diff",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "generateDiff",
            "GENERATE_DIFF",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "GET",
            "/api/v1/yappc/generate/artifacts/{id}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "getArtifacts",
            "ARTIFACTS_READ",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/run",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "runArtifacts",
            "RUN_ARTIFACTS",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/run/with-observation",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "runWithObservation",
            "RUN_WITH_OBSERVATION",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/run/rollback",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "rollbackRun",
            "RUN_ROLLBACK",
            PrivacyClassification.RESTRICTED
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/run/promote",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "promoteRun",
            "RUN_PROMOTE",
            PrivacyClassification.RESTRICTED
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/observe",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "observeRun",
            "OBSERVE_RUN",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/learn",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "learnFromRun",
            "LEARN_FROM_RUN",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/learn/with-context",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "learnWithContext",
            "LEARN_WITH_CONTEXT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/evolve",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "evolveSystem",
            "EVOLVE_SYSTEM",
            PrivacyClassification.RESTRICTED
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/evolve/with-constraints",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "evolveWithConstraints",
            "EVOLVE_WITH_CONSTRAINTS",
            PrivacyClassification.RESTRICTED
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/lifecycle/execute",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "executeLifecycle",
            "LIFECYCLE_EXECUTE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/artifact/graph/ingest",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "ingestArtifactGraph",
            "ARTIFACT_GRAPH_INGEST",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/artifact/graph/analyze",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "analyzeArtifactGraph",
            "ARTIFACT_GRAPH_ANALYZE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/artifact/graph/merge",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "mergeArtifactGraph",
            "ARTIFACT_GRAPH_MERGE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/artifact/graph/query",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "queryArtifactGraph",
            "ARTIFACT_GRAPH_QUERY",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/artifact/residual/analyze",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "analyzeResidual",
            "RESIDUAL_ANALYZE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/yappc/artifact/import-source",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "importSource",
            "SOURCE_IMPORT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/preview/session/create",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-services",
            Boundary.YAPPC,
            "createPreviewSession",
            "PREVIEW_SESSION_CREATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/preview/session/validate",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "validatePreviewSession",
            "PREVIEW_SESSION_VALIDATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/capabilities",
            AuthMode.REQUIRED,
            Set.of("admin"),
            "yappc-services",
            Boundary.YAPPC,
            "queryCapabilities",
            "CAPABILITIES_QUERY",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "GET",
            "/api/v1/capabilities",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-services",
            Boundary.YAPPC,
            "getCapabilities",
            "CAPABILITIES_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/phase/packet",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "requestPhasePacket",
            "PHASE_PACKET_REQUEST",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "GET",
            "/api/v1/phase/packet",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-services",
            Boundary.YAPPC,
            "getPhasePacket",
            "PHASE_PACKET_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-services", new RouteEntry(
            "POST",
            "/api/v1/dashboard/actions",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-services",
            Boundary.YAPPC,
            "requestDashboardActions",
            "DASHBOARD_ACTIONS_REQUEST",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/dashboard/actions",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-services",
            Boundary.YAPPC,
            "getDashboardActions",
            "DASHBOARD_ACTIONS_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/vector/search",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorSearch",
            "VECTOR_SEARCH",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/vector/search/hybrid",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorSearchHybrid",
            "VECTOR_SEARCH_HYBRID",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/vector/similar/{id}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorSimilar",
            "VECTOR_SIMILAR",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/vector/index",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorIndex",
            "VECTOR_INDEX",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/vector/index/batch",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorIndexBatch",
            "VECTOR_INDEX_BATCH",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "DELETE",
            "/api/v1/vector/index/{id}",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorIndexDelete",
            "VECTOR_INDEX_DELETE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/vector/rag",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorRag",
            "VECTOR_RAG",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/vector/rag/chat",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "vectorRagChat",
            "VECTOR_RAG_CHAT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/agents",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "listAgents",
            "AGENTS_LIST",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/agents/health",
            AuthMode.PUBLIC,
            Set.of(),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "agentsHealth",
            "AGENTS_HEALTH",
            PrivacyClassification.PUBLIC
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/agents/capabilities",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "agentsCapabilities",
            "AGENTS_CAPABILITIES",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/agents/by-capability/{capability}",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "agentsByCapability",
            "AGENTS_BY_CAPABILITY",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/agents/{name}",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "getAgent",
            "AGENT_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/agents/{name}/health",
            AuthMode.PUBLIC,
            Set.of(),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "agentHealth",
            "AGENT_HEALTH",
            PrivacyClassification.PUBLIC
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/agents/{name}/execute",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "executeAgent",
            "AGENTS_EXECUTE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/agents/copilot/chat",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "copilotChat",
            "COPILOT_CHAT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/agents/search",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "searchAgents",
            "AGENTS_SEARCH",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/agents/predict",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "predictAgent",
            "AGENTS_PREDICT",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "createWorkflow",
            "WORKFLOW_CREATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/workflows",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "listWorkflows",
            "WORKFLOW_LIST",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "GET",
            "/api/v1/workflows/{id}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "getWorkflow",
            "WORKFLOW_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "DELETE",
            "/api/v1/workflows/{id}",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "deleteWorkflow",
            "WORKFLOW_DELETE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/start",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "startWorkflow",
            "WORKFLOW_START",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/pause",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "pauseWorkflow",
            "WORKFLOW_PAUSE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/resume",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "resumeWorkflow",
            "WORKFLOW_RESUME",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/cancel",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "cancelWorkflow",
            "WORKFLOW_CANCEL",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/steps/advance",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "advanceWorkflowStep",
            "WORKFLOW_STEP_ADVANCE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/steps/{stepId}/goto",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "gotoWorkflowStep",
            "WORKFLOW_STEP_GOTO",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/plans/generate",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "generateWorkflowPlan",
            "WORKFLOW_PLAN_GENERATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{workflowId}/plans/{planId}/approve",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "approveWorkflowPlan",
            "WORKFLOW_PLAN_APPROVE",
            PrivacyClassification.RESTRICTED
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{workflowId}/plans/{planId}/reject",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "rejectWorkflowPlan",
            "WORKFLOW_PLAN_REJECT",
            PrivacyClassification.RESTRICTED
        ));
        MANIFEST.addRoute("yappc-api", new RouteEntry(
            "PUT",
            "/api/v1/workflows/{workflowId}/plans/{planId}/steps",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "updateWorkflowPlanSteps",
            "WORKFLOW_PLAN_STEPS_UPDATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/workflows/{id}/route",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "yappc-api",
            Boundary.DATA_CLOUD_AEP,
            "routeWorkflow",
            "WORKFLOW_ROUTE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "listPacks",
            "PACK_LIST",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs/languages",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getPackLanguages",
            "PACK_LANGUAGES_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs/categories",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getPackCategories",
            "PACK_CATEGORIES_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs/platforms",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getPackPlatforms",
            "PACK_PLATFORMS_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/packs/refresh",
            AuthMode.REQUIRED,
            Set.of("admin"),
            "scaffold-api",
            Boundary.YAPPC,
            "refreshPacks",
            "PACKS_REFRESH",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs/{name}",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getPack",
            "PACK_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs/{name}/validate",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "validatePack",
            "PACK_VALIDATE",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs/{name}/templates",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getPackTemplates",
            "PACK_TEMPLATES_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/packs/{name}/variables",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getPackVariables",
            "PACK_VARIABLES_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/scaffold/projects",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "createScaffoldProject",
            "SCAFFOLD_CREATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/scaffold/projects/add-feature",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "addFeatureToProject",
            "SCAFFOLD_ADD_FEATURE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/scaffold/projects/update",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "updateScaffoldProject",
            "SCAFFOLD_UPDATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/scaffold/projects/info",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getScaffoldProjectInfo",
            "SCAFFOLD_INFO_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/scaffold/projects/state",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getScaffoldProjectState",
            "SCAFFOLD_STATE_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/scaffold/projects/validate",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "validateScaffoldProject",
            "SCAFFOLD_VALIDATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/scaffold/projects/check-updates",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "checkScaffoldUpdates",
            "SCAFFOLD_CHECK_UPDATES",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/scaffold/projects/preview-update",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "previewScaffoldUpdate",
            "SCAFFOLD_PREVIEW_UPDATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/scaffold/projects/features",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getScaffoldFeatures",
            "SCAFFOLD_FEATURES_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/scaffold/projects/export",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "exportScaffoldProject",
            "SCAFFOLD_EXPORT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/scaffold/projects/import",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "importScaffoldProject",
            "SCAFFOLD_IMPORT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/templates/render",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "renderTemplate",
            "TEMPLATE_RENDER",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/templates/helpers",
            AuthMode.REQUIRED,
            Set.of("workspace:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "getTemplateHelpers",
            "TEMPLATE_HELPERS_GET",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/templates/validate",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "validateTemplate",
            "TEMPLATE_VALIDATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "GET",
            "/api/v1/dependencies/analyze/pack/{name}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "analyzePackDependencies",
            "DEPENDENCY_ANALYZE_PACK",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/dependencies/analyze/project",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "analyzeProjectDependencies",
            "DEPENDENCY_ANALYZE_PROJECT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("scaffold-api", new RouteEntry(
            "POST",
            "/api/v1/dependencies/conflicts",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "scaffold-api",
            Boundary.YAPPC,
            "detectDependencyConflicts",
            "DEPENDENCY_CONFLICTS_DETECT",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "POST",
            "/api/v1/dependencies/add-conflicts",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "scaffold-api",
            Boundary.YAPPC,
            "addDependencyConflicts",
            "DEPENDENCY_CONFLICTS_ADD",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "POST",
            "/api/v1/jobs",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "refactorer-api",
            Boundary.YAPPC,
            "createRefactorJob",
            "REFACTOR_JOB_CREATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "GET",
            "/api/v1/jobs/{jobId}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "refactorer-api",
            Boundary.YAPPC,
            "getRefactorJob",
            "REFACTOR_JOB_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "DELETE",
            "/api/v1/jobs/{jobId}",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "refactorer-api",
            Boundary.YAPPC,
            "deleteRefactorJob",
            "REFACTOR_JOB_DELETE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "GET",
            "/api/v1/jobs/{jobId}/report",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "refactorer-api",
            Boundary.YAPPC,
            "getRefactorJobReport",
            "REFACTOR_JOB_REPORT_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "POST",
            "/api/v1/jobs/{jobId}/start",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "refactorer-api",
            Boundary.YAPPC,
            "startRefactorJob",
            "REFACTOR_JOB_START",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "POST",
            "/api/v1/jobs/{jobId}/stop",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "refactorer-api",
            Boundary.YAPPC,
            "stopRefactorJob",
            "REFACTOR_JOB_STOP",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "POST",
            "/api/v1/jobs/{jobId}/runs",
            AuthMode.REQUIRED,
            Set.of("project:write"),
            "refactorer-api",
            Boundary.YAPPC,
            "createRefactorJobRun",
            "REFACTOR_JOB_RUN_CREATE",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "GET",
            "/api/v1/jobs/{jobId}/runs",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "refactorer-api",
            Boundary.YAPPC,
            "listRefactorJobRuns",
            "REFACTOR_JOB_RUNS_LIST",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "GET",
            "/api/v1/jobs/{jobId}/runs/{runId}",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "refactorer-api",
            Boundary.YAPPC,
            "getRefactorJobRun",
            "REFACTOR_JOB_RUN_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "GET",
            "/api/v1/jobs/{jobId}/runs/{runId}/logs",
            AuthMode.REQUIRED,
            Set.of("project:read"),
            "refactorer-api",
            Boundary.YAPPC,
            "getRefactorJobRunLogs",
            "REFACTOR_JOB_RUN_LOGS_GET",
            PrivacyClassification.CONFIDENTIAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "POST",
            "/v1/diagnose",
            AuthMode.REQUIRED,
            Set.of("admin"),
            "refactorer-api",
            Boundary.YAPPC,
            "diagnoseRefactorer",
            "REFACTORER_DIAGNOSE",
            PrivacyClassification.INTERNAL
        ));
        MANIFEST.addRoute("refactorer-api", new RouteEntry(
            "GET",
            "/v1/config",
            AuthMode.PUBLIC,
            Set.of(),
            "refactorer-api",
            Boundary.YAPPC,
            "getRefactorerConfig",
            "REFACTORER_CONFIG_GET",
            PrivacyClassification.PUBLIC
        ));    }
    
    public static RouteManifest getManifest() {
        return MANIFEST;
    }
}
