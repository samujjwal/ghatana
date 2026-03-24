package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.PolicySpec;
import com.ghatana.yappc.domain.validate.ValidationConfig;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.validate.ValidationService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.ghatana.yappc.api.HttpResponses.*;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for Validation phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ValidationApiController {
    
    private static final Logger log = LoggerFactory.getLogger(ValidationApiController.class);
    
    private final ValidationService validationService;
    
    public ValidationApiController(ValidationService validationService) {
        this.validationService = validationService;
    }
    
    /**
     * POST /api/v1/yappc/validate
     * Validates a shape specification.
     * 
     * @param request HTTP request with ShapeSpec JSON body
     * @return Promise of HTTP response with ValidationResult
     */
    public Promise<HttpResponse> validate(HttpRequest request) {
        log.info("Validating shape specification");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ShapeSpec spec = JsonMapper.fromJson(json, ShapeSpec.class);
                        
                        return validationService.validate(spec)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
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
                .whenException(e -> log.error("Error validating shape", e));
    }
    
    /**
     * POST /api/v1/yappc/validate/with-config
     * Validates with custom configuration.
     * 
     * @param request HTTP request with ShapeSpec and ValidationConfig
     * @return Promise of HTTP response with ValidationResult
     */
    public Promise<HttpResponse> validateWithConfig(HttpRequest request) {
        log.info("Validating shape specification with custom configuration");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ShapeSpec spec = JsonMapper.fromJson(json, ShapeSpec.class);
                        ValidationConfig config = JsonMapper.fromJson(json, ValidationConfig.class);
                        
                        return validationService.validate(spec, config)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
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
                .whenException(e -> log.error("Error validating shape with config", e));
    }
    
    /**
     * POST /api/v1/yappc/validate/with-policy
     * Validates against a specific policy.
     * 
     * @param request HTTP request with ShapeSpec and PolicySpec
     * @return Promise of HTTP response with ValidationResult
     */
    public Promise<HttpResponse> validateWithPolicy(HttpRequest request) {
        log.info("Validating shape specification against a policy");
        
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ShapeSpec spec = JsonMapper.fromJson(json, ShapeSpec.class);
                        PolicySpec policy = JsonMapper.fromJson(json, PolicySpec.class);
                        
                        return validationService.validateWithPolicy(spec, policy)
                                .map(result -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(result));
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
                .whenException(e -> log.error("Error validating shape with policy", e));
    }
}
