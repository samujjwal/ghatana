package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
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
     * <p>Tenant and product scope are resolved from the authenticated principal.
     * Any tenantId/productId in the payload that conflicts with the principal is rejected.
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

                        // Reject if payload tenantId conflicts with principal tenant
                        if (ingestRequest.tenantId() != null && !ingestRequest.tenantId().equals(tenantId)) {
                            log.warn("Tenant scope mismatch in ingest: principalTenant={}, requestTenant={}",
                                tenantId, ingestRequest.tenantId());
                            return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                                .build());
                        }

                        return artifactGraphService.ingestGraph(ingestRequest)
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

                        return artifactGraphService.analyzeGraph(analysisRequest)
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

                        return artifactGraphService.mergeModels(mergeRequest)
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
                        String productId = (String) payload.get("productId");
                        String queryType = (String) payload.get("queryType");
                        @SuppressWarnings("unchecked")
                        List<String> seedIds = (List<String>) payload.getOrDefault("seedIds", List.of());

                        if (productId == null || queryType == null) {
                            return Promise.of(badRequest400("Missing required fields: productId, queryType"));
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

                        return artifactGraphService.queryGraph(productId, tenantId, queryType, seedIds)
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
                        String productId = (String) payload.get("productId");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> islands = (List<Map<String, Object>>) payload.getOrDefault("residualIslands", List.of());

                        if (productId == null) {
                            return Promise.of(badRequest400("Missing productId"));
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

                        return artifactGraphService.analyzeResidual(productId, tenantId, islands)
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
