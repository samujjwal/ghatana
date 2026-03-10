package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.ghatana.yappc.api.HttpResponses.*;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for Generation phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class GenerationApiController {
    
    private static final Logger log = LoggerFactory.getLogger(GenerationApiController.class);
    
    private final GenerationService generationService;
    private final YappcArtifactRepository artifactRepository;
    
    public GenerationApiController(GenerationService generationService, YappcArtifactRepository artifactRepository) {
        this.generationService = generationService;
        this.artifactRepository = artifactRepository;
    }
    
    /**
     * POST /api/v1/yappc/generate
     * Generates artifacts from validated specification.
     * 
     * @param request HTTP request with ValidatedSpec JSON body
     * @return Promise of HTTP response with GeneratedArtifacts
     */
    public Promise<HttpResponse> generateArtifacts(HttpRequest request) {
        log.info("Generating artifacts from validated spec");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ValidatedSpec spec = JsonMapper.fromJson(json, ValidatedSpec.class);
                        
                        return generationService.generate(spec)
                                .map(artifacts -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(artifacts));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Error generating artifacts", e));
    }
    
    /**
     * POST /api/v1/yappc/generate/diff
     * Regenerates artifacts and computes diff.
     * 
     * @param request HTTP request with ValidatedSpec and existing artifacts
     * @return Promise of HTTP response with DiffResult
     */
    public Promise<HttpResponse> regenerateWithDiff(HttpRequest request) {
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ValidatedSpec spec = JsonMapper.fromJson(json, ValidatedSpec.class);
                        GeneratedArtifacts existing = JsonMapper.fromJson(json, GeneratedArtifacts.class);
                        
                        return generationService.regenerateWithDiff(spec, existing)
                                .map(diff -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(diff));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Diff generation failed", e));
    }
    
    /**
     * GET /api/v1/yappc/generate/artifacts/{id}
     * Retrieves generated artifacts by ID.
     * 
     * @param request HTTP request with artifacts ID in path
     * @return Promise of HTTP response with GeneratedArtifacts
     */
    public Promise<HttpResponse> getArtifacts(HttpRequest request) {
        String artifactsId = request.getPathParameter("id");
        
        log.info("Retrieving artifacts: {}", artifactsId);
        
        String[] parts = artifactsId.split(":");
        if (parts.length != 2) {
            return Promise.of(badRequest400("{\"error\": \"Invalid artifacts ID format. Expected: productId:version\"}"));
        }
        
        String productId = parts[0];
        String version = parts[1];
        
        return artifactRepository.getArtifact(productId, PhaseType.GENERATE, version)
                .map(content -> {
                    try {
                        GeneratedArtifacts artifacts = JsonMapper.fromJson(new String(content), GeneratedArtifacts.class);
                        return ok200Json(JsonMapper.toJson(artifacts));
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing artifacts", e);
                        return error500("Internal server error");
                    }
                })
                .whenException(e -> log.error("Error retrieving artifacts: {}", artifactsId, e));
    }
}
