package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.inject.annotation.Provides;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static com.ghatana.yappc.api.HttpResponses.*;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for Intent phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class IntentApiController {
    
    private static final Logger log = LoggerFactory.getLogger(IntentApiController.class);
    
    private final IntentService intentService;
    private final YappcArtifactRepository artifactRepository;
    
    public IntentApiController(IntentService intentService, YappcArtifactRepository artifactRepository) {
        this.intentService = intentService;
        this.artifactRepository = artifactRepository;
    }
    
    /**
     * POST /api/v1/yappc/intent/capture
     * Captures product intent from raw input.
     * 
     * @param request HTTP request with IntentInput JSON body
     * @return Promise of HTTP response with IntentSpec
     */
    public Promise<HttpResponse> captureIntent(HttpRequest request) {
        log.info("Capturing intent from request");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        IntentInput input = JsonMapper.fromJson(json, IntentInput.class);
                        
                        return intentService.capture(input)
                                .map(spec -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(spec));
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
                .whenException(e -> log.error("Error capturing intent", e);
    }
    
    /**
     * POST /api/v1/yappc/intent/analyze
     * Analyzes an intent specification for feasibility.
     * 
     * @param request HTTP request with IntentSpec JSON body
     * @return Promise of HTTP response with IntentAnalysis
     */
    public Promise<HttpResponse> analyzeIntent(HttpRequest request) {
        log.info("Analyzing intent from request");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        IntentSpec spec = JsonMapper.fromJson(json, IntentSpec.class);
                        
                        return intentService.analyze(spec)
                                .map(analysis -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(analysis));
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
                .whenException(e -> log.error("Error analyzing intent", e);
    }
    
    /**
     * GET /api/v1/yappc/intent/{id}
     * Retrieves an intent specification by ID.
     * 
     * @param request HTTP request with intent ID in path
     * @return Promise of HTTP response with IntentSpec
     */
    public Promise<HttpResponse> getIntent(HttpRequest request) {
        String intentId = request.getPathParameter("id");
        
        log.info("Retrieving intent: {}", intentId);
        
        // Extract productId and version from intentId (format: productId:version)
        String[] parts = intentId.split(":");
        if (parts.length != 2) {
            return Promise.of(badRequest400("{\"error\": \"Invalid intent ID format. Expected: productId:version\"}"));
        }
        
        String productId = parts[0];
        String version = parts[1];
        
        return artifactRepository.getArtifact(productId, PhaseType.INTENT, version)
                .map(content -> {
                    try {
                        IntentSpec spec = JsonMapper.fromJson(new String(content), IntentSpec.class);
                        return ok200Json(JsonMapper.toJson(spec));
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing intent", e);
                        return error500("Internal server error");
                    }
                })
                .whenException(e -> log.error("Error retrieving intent: {}", intentId, e);
    }
}
