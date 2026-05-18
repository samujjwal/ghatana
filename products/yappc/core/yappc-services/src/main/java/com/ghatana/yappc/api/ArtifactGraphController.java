package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphQueryRequest;
import com.ghatana.yappc.domain.artifact.ResidualAnalysisRequest;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import com.ghatana.yappc.services.artifact.ArtifactGraphValidator;
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
     * P0: Safe JSON error serialization to prevent injection attacks and malformed JSON.
     * Properly escapes special characters in error messages.
     */
    private String toJsonError(String errorType, String message) {
        try {
            Map<String, String> errorMap = Map.of("error", errorType + ": " + message);
            return JsonMapper.toJson(errorMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error JSON", e);
            return "{\"error\":\"Internal error\"}";
        }
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
                            ingestRequest.residualIslands()
                        );
                        ArtifactRequestScope scope = new ArtifactRequestScope(projectId, tenantId, scopedWorkspaceId);

                        // P0: Use centralized ArtifactGraphValidator for comprehensive validation
                        ArtifactGraphValidator.ValidationResult validationResult =
                            ArtifactGraphValidator.validateIngestRequest(scopedRequest, scope);

                        if (!validationResult.valid()) {
                            log.warn("Graph validation failed with {} errors", validationResult.errors().size());
                            StringBuilder errorMsg = new StringBuilder("Validation failed:");
                            for (ArtifactGraphValidator.ValidationError error : validationResult.errors()) {
                                errorMsg.append(" ").append(error.code()).append("-").append(error.message()).append(";");
                            }
                            String errorJson = toJsonError("Validation failed", errorMsg.toString());
                            return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson)
                                .build());
                        }

                        // P0: Validate residual references have full residual records
                        ArtifactGraphValidator.ValidationResult residualValidation =
                            ArtifactGraphValidator.validateResidualReferences(
                                scopedRequest.nodes(),
                                scopedRequest.residualIslands()
                            );

                        if (!residualValidation.valid()) {
                            log.warn("Residual reference validation failed with {} errors", residualValidation.errors().size());
                            StringBuilder errorMsg = new StringBuilder("Residual validation failed:");
                            for (ArtifactGraphValidator.ValidationError error : residualValidation.errors()) {
                                errorMsg.append(" ").append(error.code()).append("-").append(error.message()).append(";");
                            }
                            String errorJson = toJsonError("Residual validation failed", errorMsg.toString());
                            return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson)
                                .build());
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
     * P0: Uses typed ArtifactGraphQueryRequest instead of raw map parsing.
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
                        // P0: Use typed request DTO instead of raw map parsing
                        ArtifactGraphQueryRequest queryRequest = JsonMapper.fromJson(json, ArtifactGraphQueryRequest.class);

                        String scopedWorkspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
                        String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
                        if (scopedWorkspaceId == null || scopedWorkspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                                .build());
                        }

                        // P1-8: Validate tenant scope - reject if request body contains tenantId that doesn't match principal
                        // (This is handled by not allowing tenantId in the typed request DTO)

                        log.info("Querying artifact graph: projectId={}, workspaceId={}, queryType={}, cursor={}, limit={}, includeUnresolved={}",
                            scopedProjectId, scopedWorkspaceId, queryRequest.queryType(), 
                            queryRequest.cursor() != null ? "present" : "null", queryRequest.limit(), queryRequest.includeUnresolvedEdges());

                        ArtifactRequestScope scope = new ArtifactRequestScope(scopedProjectId, tenantId, scopedWorkspaceId);
                        return artifactGraphService.queryGraph(scope, queryRequest.queryType(), queryRequest.seedNodeIds(), 
                                queryRequest.cursor(), queryRequest.limit(), queryRequest.snapshotId(), queryRequest.includeUnresolvedEdges())
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
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid query request parameters", e);
                        return Promise.of(badRequest400("Invalid request parameters: " + e.getMessage()));
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
     * P0: Uses typed ResidualAnalysisRequest instead of raw map parsing.
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
                        // P0: Use typed request DTO instead of raw map parsing
                        ResidualAnalysisRequest analysisRequest = JsonMapper.fromJson(json, ResidualAnalysisRequest.class);

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

                        // P1-8: Validate tenant scope - reject if request body contains tenantId that doesn't match principal
                        if (analysisRequest.tenantId() != null && !analysisRequest.tenantId().equals(tenantId)) {
                            log.warn("Tenant scope mismatch in analyzeResidual: principalTenant={}, requestTenant={}",
                                tenantId, analysisRequest.tenantId());
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                                .build());
                        }

                        // Create scoped request
                        ResidualAnalysisRequest scopedRequest = new ResidualAnalysisRequest(
                            scopedProjectId,
                            tenantId,
                            scopedWorkspaceId,
                            analysisRequest.residualIslands()
                        );
                        ArtifactRequestScope scope = new ArtifactRequestScope(scopedProjectId, tenantId, scopedWorkspaceId);

                        return artifactGraphService.analyzeResidual(scope, scopedRequest)
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

}
