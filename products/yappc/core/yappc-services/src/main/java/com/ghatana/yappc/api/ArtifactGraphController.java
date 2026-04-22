package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
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
     */
    public Promise<HttpResponse> ingest(HttpRequest request) {
        log.info("Artifact graph ingest request");
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ArtifactGraphIngestRequest ingestRequest = JsonMapper.fromJson(json, ArtifactGraphIngestRequest.class);
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
                .whenException(e -> log.error("Error ingesting artifact graph", e));
    }

    /**
     * POST /api/v1/yappc/artifact/graph/analyze
     * Run graph analysis algorithms (centrality, cycles, communities, build-order).
     */
    public Promise<HttpResponse> analyze(HttpRequest request) {
        log.info("Artifact graph analyze request");
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ArtifactGraphAnalysisRequest analysisRequest = JsonMapper.fromJson(json, ArtifactGraphAnalysisRequest.class);
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
                .whenException(e -> log.error("Error analyzing artifact graph", e));
    }

    /**
     * POST /api/v1/yappc/artifact/graph/merge
     * Three-way semantic merge of artifact models.
     */
    public Promise<HttpResponse> merge(HttpRequest request) {
        log.info("Artifact graph merge request");
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ArtifactGraphMergeRequest mergeRequest = JsonMapper.fromJson(json, ArtifactGraphMergeRequest.class);
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
                .whenException(e -> log.error("Error merging artifact models", e));
    }

    /**
     * POST /api/v1/yappc/artifact/graph/query
     * Query the artifact graph (orphaned, dependencies, dependents, stats).
     */
    public Promise<HttpResponse> query(HttpRequest request) {
        log.info("Artifact graph query request");
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = JsonMapper.fromJson(json, Map.class);
                        String productId = (String) payload.get("productId");
                        String tenantId = (String) payload.get("tenantId");
                        String queryType = (String) payload.get("queryType");
                        @SuppressWarnings("unchecked")
                        List<String> seedIds = (List<String>) payload.getOrDefault("seedIds", List.of());

                        if (productId == null || tenantId == null || queryType == null) {
                            return Promise.of(badRequest400("Missing required fields: productId, tenantId, queryType"));
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
     */
    public Promise<HttpResponse> analyzeResidual(HttpRequest request) {
        log.info("Artifact residual analyze request");
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = JsonMapper.fromJson(json, Map.class);
                        String productId = (String) payload.get("productId");
                        String tenantId = (String) payload.get("tenantId");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> islands = (List<Map<String, Object>>) payload.getOrDefault("residualIslands", List.of());

                        if (productId == null || tenantId == null) {
                            return Promise.of(badRequest400("Missing productId or tenantId"));
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
