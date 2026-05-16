package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.ghatana.yappc.api.HttpResponses.*;

/**
 * HTTP API controller for artifact graph compiler operations.
 *
 * <p>P1-8: Artifact graph APIs now resolve tenant/workspace/project from authenticated
 * principal and resource registry, not request body. This prevents cross-tenant/cross-project
 * access through payload manipulation.
 *
 * @doc.type class
 * @doc.purpose HTTP API controller for artifact graph compiler operations
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ArtifactGraphController {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphController.class);

    private final ArtifactGraphService artifactGraphService;

    public ArtifactGraphController(ArtifactGraphService artifactGraphService) {
        this.artifactGraphService = artifactGraphService;
    }

    /**
     * POST /api/v1/yappc/artifact/graph/ingest
     * Ingest artifact nodes and edges extracted by the TypeScript scanner.
     *
    * <p>Tenant and project scope are resolved from the authenticated principal.
    * Any tenantId/projectId in the payload that conflicts with the principal is rejected.
     * 
     * <p>P0-7: Workspace/project scope is resolved from principal/resource registry.
     * Graph validation is performed before service call to ensure data integrity.
     */
    public Promise<HttpResponse> ingest(HttpRequest request) {
        // Require authenticated principal — tenant scope resolved from principal, not payload
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            log.warn("Ingest request without authenticated principal");
            return Promise.of(HttpResponse.ofCode(401)
                .withJson("{\"error\":\"Unauthenticated\"}")
                .build());
        }
        String tenantId = principal.getTenantId();

        log.info("Artifact graph ingest request for tenant={}", tenantId);
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ArtifactGraphIngestRequest ingestRequest = JsonMapper.fromJson(json, ArtifactGraphIngestRequest.class);

                        // P0-7: Reject if payload tenantId conflicts with principal tenant
                        if (ingestRequest.tenantId() != null && !ingestRequest.tenantId().equals(tenantId)) {
                            log.warn("Tenant scope mismatch in ingest: principalTenant={}, requestTenant={}",
                                tenantId, ingestRequest.tenantId());
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                                .build());
                        }

                        String scopedWorkspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
                        String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
                        if (scopedWorkspaceId == null || scopedWorkspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
                            log.warn("Missing X-Workspace-ID or X-Project-ID scope header in ingest request");
                            return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                                .build());
                        }

                        if (ingestRequest.projectId() != null && !ingestRequest.projectId().isBlank()
                            && !scopedProjectId.equals(ingestRequest.projectId())) {
                            log.warn("Project scope mismatch in ingest: scopedProjectId={}, requestProjectId={}",
                                scopedProjectId, ingestRequest.projectId());
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: project scope mismatch\"}")
                                .build());
                        }

                        String projectId = scopedProjectId;
                        if (projectId.isBlank()) {
                            log.warn("Missing projectId in ingest request");
                            return Promise.of(HttpResponse.ofCode(400)
                                .withJson("{\"error\":\"Bad Request: missing projectId\"}")
                                .build());
                        }

                        ArtifactGraphIngestRequest scopedRequest = new ArtifactGraphIngestRequest(
                            projectId,
                            tenantId,
                            ingestRequest.nodes(),
                            ingestRequest.edges(),
                            ingestRequest.snapshotRef(),
                            ingestRequest.snapshotId(),
                            ingestRequest.versionId(),
                            ingestRequest.contentChecksum(),
                            ingestRequest.unresolvedEdges(),
                            ingestRequest.edgeResolutionRecords(),
                            ingestRequest.residualIslandIds()
                        );
                        ArtifactRequestScope scope = new ArtifactRequestScope(projectId, tenantId, scopedWorkspaceId);

                        // P0-7: Validate graph structure before service call
                        // Basic validation: ensure node IDs are unique and edge targets exist
                        java.util.Set<String> nodeIds = new java.util.HashSet<>();
                        for (com.ghatana.yappc.domain.artifact.ArtifactNodeDto node : ingestRequest.nodes()) {
                            if (nodeIds.contains(node.id())) {
                                log.warn("Duplicate node ID in ingest request: {}", node.id());
                                return Promise.of(HttpResponse.ofCode(400)
                                    .withJson("{\"error\":\"Bad Request: duplicate node ID " + node.id() + "\"}")
                                    .build());
                            }
                            nodeIds.add(node.id());
                        }

                        for (com.ghatana.yappc.domain.artifact.ArtifactEdgeDto edge : ingestRequest.edges()) {
                            if (edge.sourceNodeId() != null && !nodeIds.contains(edge.sourceNodeId())) {
                                log.warn("Edge source node not found in ingest request: {}", edge.sourceNodeId());
                                return Promise.of(HttpResponse.ofCode(400)
                                    .withJson("{\"error\":\"Bad Request: edge source node not found " + edge.sourceNodeId() + "\"}")
                                    .build());
                            }
                            if (edge.targetNodeId() != null && !nodeIds.contains(edge.targetNodeId())) {
                                log.warn("Edge target node not found in ingest request: {}", edge.targetNodeId());
                                return Promise.of(HttpResponse.ofCode(400)
                                    .withJson("{\"error\":\"Bad Request: edge target node not found " + edge.targetNodeId() + "\"}")
                                    .build());
                            }
                        }

                        return artifactGraphService.ingestGraph(scope, scopedRequest)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing ingest response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing ingest request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Error ingesting artifact graph for tenant={}", tenantId, e));
    }

    /**
     * POST /api/v1/yappc/artifact/graph/analyze
     * Run graph analysis algorithms (centrality, cycles, communities, build-order).
     *
     * <p>Tenant scope resolved from authenticated principal.
     */
    public Promise<HttpResponse> analyze(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            log.warn("Analyze request without authenticated principal");
            return Promise.of(HttpResponse.ofCode(401)
                .withJson("{\"error\":\"Unauthenticated\"}")
                .build());
        }
        String tenantId = principal.getTenantId();

        log.info("Artifact graph analyze request for tenant={}", tenantId);
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ArtifactGraphAnalysisRequest analysisRequest = JsonMapper.fromJson(json, ArtifactGraphAnalysisRequest.class);

                        if (analysisRequest.tenantId() != null && !analysisRequest.tenantId().equals(tenantId)) {
                            log.warn("Tenant scope mismatch in analyze: principalTenant={}, requestTenant={}",
                                tenantId, analysisRequest.tenantId());
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                                .build());
                        }

                        String scopedWorkspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
                        String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
                        if (scopedWorkspaceId == null || scopedWorkspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                                .build());
                        }
                        if (analysisRequest.projectId() != null && !analysisRequest.projectId().isBlank()
                                && !scopedProjectId.equals(analysisRequest.projectId())) {
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: project scope mismatch\"}")
                                .build());
                        }

                        ArtifactGraphAnalysisRequest scopedRequest = new ArtifactGraphAnalysisRequest(
                            scopedProjectId,
                            tenantId,
                            analysisRequest.algorithmTypes(),
                            analysisRequest.nodeIds()
                        );
                        ArtifactRequestScope scope = new ArtifactRequestScope(scopedRequest.projectId(), tenantId, scopedWorkspaceId);

                        return artifactGraphService.analyzeGraph(scope, scopedRequest)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing analyze response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing analyze request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Error analyzing artifact graph for tenant={}", tenantId, e));
    }

    /**
     * POST /api/v1/yappc/artifact/graph/merge
     * Three-way semantic merge of artifact models.
     *
     * <p>Tenant scope resolved from authenticated principal.
     */
    public Promise<HttpResponse> merge(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            log.warn("Merge request without authenticated principal");
            return Promise.of(HttpResponse.ofCode(401)
                .withJson("{\"error\":\"Unauthenticated\"}")
                .build());
        }
        String tenantId = principal.getTenantId();

        log.info("Artifact graph merge request for tenant={}", tenantId);
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ArtifactGraphMergeRequest mergeRequest = JsonMapper.fromJson(json, ArtifactGraphMergeRequest.class);

                        if (mergeRequest.tenantId() != null && !mergeRequest.tenantId().equals(tenantId)) {
                            log.warn("Tenant scope mismatch in merge: principalTenant={}, requestTenant={}",
                                tenantId, mergeRequest.tenantId());
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                                .build());
                        }

                        String scopedWorkspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
                        String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
                        if (scopedWorkspaceId == null || scopedWorkspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                                .build());
                        }
                        if (mergeRequest.projectId() != null && !mergeRequest.projectId().isBlank()
                                && !scopedProjectId.equals(mergeRequest.projectId())) {
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: project scope mismatch\"}")
                                .build());
                        }

                        ArtifactGraphMergeRequest scopedRequest = new ArtifactGraphMergeRequest(
                            scopedProjectId,
                            tenantId,
                            mergeRequest.baseModel(),
                            mergeRequest.leftModel(),
                            mergeRequest.rightModel(),
                            mergeRequest.resolutionStrategy()
                        );
                        ArtifactRequestScope scope = new ArtifactRequestScope(scopedRequest.projectId(), tenantId, scopedWorkspaceId);

                        return artifactGraphService.mergeModels(scope, scopedRequest)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing merge response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing merge request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Error merging artifact models for tenant={}", tenantId, e));
    }

    /**
     * POST /api/v1/yappc/artifact/graph/query
     * Query the artifact graph (orphaned, dependencies, dependents, stats).
     *
     * <p>P1-8: Resolves tenant from authenticated principal, not request body.
     * This prevents cross-tenant access through payload manipulation.
     */
    public Promise<HttpResponse> query(HttpRequest request) {
        log.info("Artifact graph query request");

        // P1-8: Require authenticated principal for scope resolution
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            log.warn("Query request without authenticated principal");
            return Promise.of(HttpResponse.ofCode(401)
                .withJson("{\"error\":\"Unauthenticated\"}")
                .build());
        }

        // P1-8: Extract tenant from principal, not request body
        String tenantId = principal.getTenantId();

        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = JsonMapper.fromJson(json, Map.class);
                        String projectId = (String) payload.get("projectId");
                        String queryType = (String) payload.get("queryType");
                        @SuppressWarnings("unchecked")
                        List<String> seedIds = (List<String>) payload.getOrDefault("seedIds", List.of());

                        if (queryType == null) {
                            return Promise.of(badRequest400("Missing required fields: projectId, queryType"));
                        }

                        String scopedWorkspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
                        String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
                        if (scopedWorkspaceId == null || scopedWorkspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                                .build());
                        }
                        if (projectId != null && !projectId.isBlank() && !scopedProjectId.equals(projectId)) {
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: project scope mismatch\"}")
                                .build());
                        }

                        // P1-8: Validate tenant scope - reject if request body contains tenantId that doesn't match principal
                        String requestTenantId = (String) payload.get("tenantId");
                        if (requestTenantId != null && !requestTenantId.equals(tenantId)) {
                            log.warn("Tenant scope mismatch in query: principalTenant={}, requestTenant={}",
                                tenantId, requestTenantId);
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                                .build());
                        }

                        // P1-8: Parse and validate pagination parameters from payload
                        String cursor = (String) payload.get("cursor");
                        Integer limit = parseInteger(payload.get("limit"), 100);
                        Boolean includeUnresolvedEdges = parseBoolean(payload.get("includeUnresolvedEdges"), false);
                        String snapshotId = (String) payload.get("snapshotId");

                        // Validate limit is within reasonable bounds
                        if (limit < 1 || limit > 1000) {
                            return Promise.of(badRequest400("Invalid limit: must be between 1 and 1000"));
                        }

                        log.info("Querying artifact graph: projectId={}, queryType={}, cursor={}, limit={}, includeUnresolved={}",
                            scopedProjectId, queryType, cursor != null ? "present" : "null", limit, includeUnresolvedEdges);

                        return artifactGraphService.queryGraph(scopedProjectId, tenantId, queryType, seedIds, cursor, limit)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing query response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing query request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Error querying artifact graph", e));
    }

    /**
     * POST /api/v1/yappc/artifact/residual/analyze
     * Analyze residual islands flagged by the TypeScript scanner.
     *
     * <p>P1-8: Resolves tenant from authenticated principal, not request body.
     * This prevents cross-tenant access through payload manipulation.
     */
    public Promise<HttpResponse> analyzeResidual(HttpRequest request) {
        log.info("Artifact residual analyze request");

        // P1-8: Require authenticated principal for scope resolution
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            log.warn("Analyze residual request without authenticated principal");
            return Promise.of(HttpResponse.ofCode(401)
                .withJson("{\"error\":\"Unauthenticated\"}")
                .build());
        }

        // P1-8: Extract tenant from principal, not request body
        String tenantId = principal.getTenantId();

        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = JsonMapper.fromJson(json, Map.class);
                        String projectId = (String) payload.get("projectId");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> islands = (List<Map<String, Object>>) payload.getOrDefault("residualIslands", List.of());

                        String scopedWorkspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
                        String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
                        if (scopedWorkspaceId == null || scopedWorkspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                                .build());
                        }
                        if (projectId != null && !projectId.isBlank() && !scopedProjectId.equals(projectId)) {
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: project scope mismatch\"}")
                                .build());
                        }

                        // P1-8: Validate tenant scope - reject if request body contains tenantId that doesn't match principal
                        String requestTenantId = (String) payload.get("tenantId");
                        if (requestTenantId != null && !requestTenantId.equals(tenantId)) {
                            log.warn("Tenant scope mismatch in analyzeResidual: principalTenant={}, requestTenant={}",
                                tenantId, requestTenantId);
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                                .build());
                        }

                        return artifactGraphService.analyzeResidual(scopedProjectId, tenantId, islands)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing residual response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing residual request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Error analyzing residual islands", e));
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Parse an integer value from the payload with a default fallback.
     */
    private Integer parseInteger(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse a boolean value from the payload with a default fallback.
     */
    private Boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
