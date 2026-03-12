package com.ghatana.tutorputor.contentgeneration.api;

import com.ghatana.tutorputor.contentgeneration.*;
import com.ghatana.tutorputor.contentgeneration.domain.*;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpMethod;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST API controller for content generation operations.
 *
 * <p>Provides HTTP endpoints for generating educational content including claims,
 * examples, simulations, animations, and assessments. Supports complete package
 * generation with quality validation.
 *
 * @doc.type class
 * @doc.purpose REST API for content generation
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ContentGenerationController {
    
    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationController.class);
    
    private final PlatformContentGenerator contentGenerator;
    
    @Inject
    public ContentGenerationController(PlatformContentGenerator contentGenerator) {
        this.contentGenerator = contentGenerator;
    }
    
    /**
     * Generates a complete content package for a topic.
     */
    public Promise<HttpResponse> generateCompletePackage(HttpRequest request) {
        LOG.info("POST /api/content/generate - Generating complete content package");
        
        try {
            ContentGenerationRequest genRequest = parseRequest(request);
            
            return contentGenerator.generateCompletePackage(genRequest)
                .map(package_ -> {
                    Map<String, Object> response = Map.of(
                        "success", true,
                        "data", Map.of(
                            "claims", package_.getClaims(),
                            "examples", package_.getExamples(),
                            "simulations", package_.getSimulations(),
                            "animations", package_.getAnimations(),
                            "assessments", package_.getAssessments(),
                            "qualityReport", package_.getQualityReport()
                        ),
                        "meta", Map.of(
                            "totalItems", package_.getClaims().size() + package_.getExamples().size() + 
                                         package_.getSimulations().size() + package_.getAnimations().size() + 
                                         package_.getAssessments().size()
                        )
                    );
                    
                    return HttpResponse.ok200()
                        .withJson(response);
                })
                .mapException(e -> {
                    LOG.error("Failed to generate complete package", e);
                    return HttpResponse.ofCode(500)
                        .withJson(Map.of(
                            "success", false,
                            "error", Map.of(
                                "code", "GENERATION_ERROR",
                                "message", e.getMessage()
                            )
                        ));
                });
                
        } catch (Exception e) {
            LOG.error("Invalid request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withJson(Map.of(
                    "success", false,
                    "error", Map.of(
                        "code", "INVALID_REQUEST",
                        "message", e.getMessage()
                    )
                )));
        }
    }
    
    /**
     * Generates educational claims for a topic.
     */
    public Promise<HttpResponse> generateClaims(HttpRequest request) {
        LOG.info("POST /api/content/claims - Generating claims");
        
        try {
            Map<String, String> params = parseQueryParams(request);
            ClaimsRequest claimsRequest = new ClaimsRequest(
                params.get("topic"),
                params.getOrDefault("gradeLevel", "HIGH_SCHOOL"),
                params.getOrDefault("domain", "PHYSICS"),
                Integer.parseInt(params.getOrDefault("maxClaims", "10"))
            );
            
            return contentGenerator.generateClaims(claimsRequest)
                .map(response -> HttpResponse.ok200()
                    .withJson(Map.of(
                        "success", true,
                        "data", Map.of(
                            "claims", response.getClaims(),
                            "validation", response.getValidation()
                        )
                    )))
                .mapException(e -> {
                    LOG.error("Failed to generate claims", e);
                    return HttpResponse.ofCode(500)
                        .withJson(Map.of("success", false, "error", e.getMessage()));
                });
                
        } catch (Exception e) {
            LOG.error("Invalid claims request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withJson(Map.of("success", false, "error", e.getMessage())));
        }
    }
    
    /**
     * Generates worked examples for claims.
     */
    public Promise<HttpResponse> generateExamples(HttpRequest request) {
        LOG.info("POST /api/content/examples - Generating examples");
        
        try {
            Map<String, String> params = parseQueryParams(request);
            // Example generation requires claims as input
            // For API simplicity, we'll generate claims first if not provided
            
            return HttpResponse.ok200()
                .withJson(Map.of(
                    "success", true,
                    "message", "Examples generation endpoint - requires claims input"
                ));
                
        } catch (Exception e) {
            LOG.error("Invalid examples request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withJson(Map.of("success", false, "error", e.getMessage())));
        }
    }
    
    /**
     * Generates simulation manifests.
     */
    public Promise<HttpResponse> generateSimulations(HttpRequest request) {
        LOG.info("POST /api/content/simulations - Generating simulations");
        
        try {
            Map<String, String> params = parseQueryParams(request);
            SimulationRequest simRequest = new SimulationRequest(
                Collections.emptyList(), // Would need claims input in real implementation
                params.getOrDefault("gradeLevel", "HIGH_SCHOOL"),
                params.getOrDefault("domain", "PHYSICS"),
                Integer.parseInt(params.getOrDefault("maxSimulations", "3"))
            );
            
            return contentGenerator.generateSimulation(simRequest)
                .map(response -> HttpResponse.ok200()
                    .withJson(Map.of(
                        "success", true,
                        "data", Map.of(
                            "simulations", response.getSimulations(),
                            "validation", response.getValidation()
                        )
                    )))
                .mapException(e -> {
                    LOG.error("Failed to generate simulations", e);
                    return HttpResponse.ofCode(500)
                        .withJson(Map.of("success", false, "error", e.getMessage()));
                });
                
        } catch (Exception e) {
            LOG.error("Invalid simulations request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withJson(Map.of("success", false, "error", e.getMessage())));
        }
    }
    
    /**
     * Generates animations for claims.
     */
    public Promise<HttpResponse> generateAnimations(HttpRequest request) {
        LOG.info("POST /api/content/animations - Generating animations");
        
        try {
            Map<String, String> params = parseQueryParams(request);
            AnimationRequest animRequest = new AnimationRequest(
                Collections.emptyList(),
                params.getOrDefault("gradeLevel", "HIGH_SCHOOL"),
                params.getOrDefault("domain", "PHYSICS"),
                Integer.parseInt(params.getOrDefault("maxAnimations", "3"))
            );
            
            return contentGenerator.generateAnimation(animRequest)
                .map(response -> HttpResponse.ok200()
                    .withJson(Map.of(
                        "success", true,
                        "data", Map.of(
                            "animations", response.getAnimations(),
                            "validation", response.getValidation()
                        )
                    )))
                .mapException(e -> {
                    LOG.error("Failed to generate animations", e);
                    return HttpResponse.ofCode(500)
                        .withJson(Map.of("success", false, "error", e.getMessage()));
                });
                
        } catch (Exception e) {
            LOG.error("Invalid animations request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withJson(Map.of("success", false, "error", e.getMessage())));
        }
    }
    
    /**
     * Generates assessments for claims.
     */
    public Promise<HttpResponse> generateAssessments(HttpRequest request) {
        LOG.info("POST /api/content/assessments - Generating assessments");
        
        try {
            Map<String, String> params = parseQueryParams(request);
            AssessmentRequest assessmentRequest = new AssessmentRequest(
                Collections.emptyList(),
                params.getOrDefault("gradeLevel", "HIGH_SCHOOL"),
                params.getOrDefault("domain", "PHYSICS"),
                Integer.parseInt(params.getOrDefault("maxAssessments", "10"))
            );
            
            return contentGenerator.generateAssessment(assessmentRequest)
                .map(response -> HttpResponse.ok200()
                    .withJson(Map.of(
                        "success", true,
                        "data", Map.of(
                            "assessments", response.getAssessments(),
                            "validation", response.getValidation()
                        )
                    )))
                .mapException(e -> {
                    LOG.error("Failed to generate assessments", e);
                    return HttpResponse.ofCode(500)
                        .withJson(Map.of("success", false, "error", e.getMessage()));
                });
                
        } catch (Exception e) {
            LOG.error("Invalid assessments request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withJson(Map.of("success", false, "error", e.getMessage())));
        }
    }
    
    /**
     * Gets service configuration.
     */
    public Promise<HttpResponse> getConfiguration(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) {
            tenantId = "default";
        }
        
        return contentGenerator.getConfiguration(tenantId)
            .map(config -> HttpResponse.ok200()
                .withJson(Map.of(
                    "success", true,
                    "data", config
                )));
    }
    
    /**
     * Gets supported models/providers.
     */
    public Promise<HttpResponse> getSupportedModels(HttpRequest request) {
        return contentGenerator.getSupportedModels()
            .map(models -> HttpResponse.ok200()
                .withJson(Map.of(
                    "success", true,
                    "data", Map.of("models", models)
                )));
    }
    
    /**
     * Health check endpoint.
     */
    public Promise<HttpResponse> healthCheck(HttpRequest request) {
        return Promise.of(HttpResponse.ok200()
            .withJson(Map.of(
                "status", "healthy",
                "service", "content-generation",
                "version", "1.0.0",
                "timestamp", System.currentTimeMillis()
            )));
    }
    
    // Helper methods
    private ContentGenerationRequest parseRequest(HttpRequest request) throws Exception {
        String body = request.getBodyUtf8();
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Request body is required");
        }
        
        // Simple parsing - production would use JSON parser
        Map<String, String> params = Map.of(
            "topic", extractValue(body, "topic"),
            "gradeLevel", extractValue(body, "gradeLevel"),
            "domain", extractValue(body, "domain")
        );
        
        return ContentGenerationRequest.builder()
            .topic(params.get("topic"))
            .gradeLevel(params.get("gradeLevel"))
            .domain(params.get("domain"))
            .tenantId(extractValue(body, "tenantId"))
            .build();
    }
    
    private String extractValue(String body, String key) {
        // Simplified extraction - production would use JSON parser
        int start = body.indexOf("\"" + key + "\":");
        if (start == -1) return null;
        start = body.indexOf("\"", start + key.length() + 3);
        if (start == -1) return null;
        int end = body.indexOf("\"", start + 1);
        if (end == -1) return null;
        return body.substring(start + 1, end);
    }
    
    private Map<String, String> parseQueryParams(HttpRequest request) {
        Map<String, String> params = new java.util.HashMap<>();
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] parts = pair.split("=");
                if (parts.length == 2) {
                    params.put(parts[0], java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        return params;
    }
}
