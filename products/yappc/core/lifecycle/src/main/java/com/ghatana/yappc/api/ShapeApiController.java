package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
import com.ghatana.yappc.services.shape.ShapeService;
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
 * @doc.purpose HTTP API controller for Shape phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ShapeApiController {
    
    private static final Logger log = LoggerFactory.getLogger(ShapeApiController.class);
    
    private final ShapeService shapeService;
    private final YappcArtifactRepository artifactRepository;
    
    public ShapeApiController(ShapeService shapeService, YappcArtifactRepository artifactRepository) {
        this.shapeService = shapeService;
        this.artifactRepository = artifactRepository;
    }
    
    /**
     * POST /api/v1/yappc/shape/derive
     * Derives system design from intent specification.
     * 
     * @param request HTTP request with IntentSpec JSON body
     * @return Promise of HTTP response with ShapeSpec
     */
    public Promise<HttpResponse> deriveShape(HttpRequest request) {
        log.info("Deriving shape from intent");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        IntentSpec intent = JsonMapper.fromJson(json, IntentSpec.class);
                        
                        return shapeService.derive(intent)
                                .map(shape -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(shape));
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
                .whenException(e -> log.error("Error deriving shape", e);
    }
    
    /**
     * POST /api/v1/yappc/shape/model
     * Generates detailed system model from shape specification.
     * 
     * @param request HTTP request with ShapeSpec JSON body
     * @return Promise of HTTP response with SystemModel
     */
    public Promise<HttpResponse> generateSystemModel(HttpRequest request) {
        log.info("Generating system model");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ShapeSpec spec = JsonMapper.fromJson(json, ShapeSpec.class);
                        
                        return shapeService.generateModel(spec)
                                .map(model -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(model));
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
                .whenException(e -> log.error("Error generating system model", e);
    }
    
    /**
     * GET /api/v1/yappc/shape/{id}
     * Retrieves a shape specification by ID.
     * 
     * @param request HTTP request with shape ID in path
     * @return Promise of HTTP response with ShapeSpec
     */
    public Promise<HttpResponse> getShape(HttpRequest request) {
        String shapeId = request.getPathParameter("id");
        
        log.info("Retrieving shape: {}", shapeId);
        
        String[] parts = shapeId.split(":");
        if (parts.length != 2) {
            return Promise.of(badRequest400("{\"error\": \"Invalid shape ID format. Expected: productId:version\"}"));
        }
        
        String productId = parts[0];
        String version = parts[1];
        
        return artifactRepository.getArtifact(productId, PhaseType.SHAPE, version)
                .map(content -> {
                    try {
                        ShapeSpec spec = JsonMapper.fromJson(new String(content), ShapeSpec.class);
                        return ok200Json(JsonMapper.toJson(spec));
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing shape", e);
                        return error500("Internal server error");
                    }
                })
                .whenException(e -> log.error("Error retrieving shape: {}", shapeId, e);
    }
}
