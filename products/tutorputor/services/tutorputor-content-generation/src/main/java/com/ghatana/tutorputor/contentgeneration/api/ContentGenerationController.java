package com.ghatana.tutorputor.contentgeneration.api;

import com.ghatana.tutorputor.contentgeneration.PlatformContentGenerator;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.domain.GenerationConfig;
import com.ghatana.tutorputor.contentgeneration.domain.UnifiedContentGenerator;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for content generation operations.
 *
 * @doc.type class
 * @doc.purpose Minimal HTTP controller for content generation endpoints
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ContentGenerationController {

    private final PlatformContentGenerator contentGenerator;

    @Inject
    public ContentGenerationController(PlatformContentGenerator contentGenerator) {
        this.contentGenerator = contentGenerator;
    }

    public Promise<HttpResponse> generateCompletePackage(HttpRequest request) {
        try {
            ContentGenerationRequest genRequest = parseRequest(request);
            return contentGenerator.generateCompletePackage(genRequest)
                    .map(pkg -> HttpResponse.ok200()
                            .withJson(String.format(
                                    "{\"success\":true,\"data\":{\"claims\":%d,\"examples\":%d,\"simulations\":%d,\"animations\":%d,\"assessments\":%d}}",
                                    pkg.claims().size(),
                                    pkg.examples().size(),
                                    pkg.simulations().size(),
                                    pkg.animations().size(),
                                    pkg.assessments().size()))
                            .build())
                                .then((response, error) -> error == null
                                    ? Promise.of(response)
                                    : Promise.of(errorResponse(500, "GENERATION_ERROR", error.getMessage())));
        } catch (Exception e) {
            return Promise.of(errorResponse(400, "INVALID_REQUEST", e.getMessage()));
        }
    }

    public Promise<HttpResponse> generateClaims(HttpRequest request) {
        try {
            Map<String, String> params = parseQueryParams(request);
            UnifiedContentGenerator.ClaimsRequest claimsRequest = new UnifiedContentGenerator.ClaimsRequest(
                    params.getOrDefault("topic", "General topic"),
                    params.getOrDefault("gradeLevel", "HIGH_SCHOOL"),
                    params.getOrDefault("domain", Domain.PHYSICS.name()),
                    Integer.parseInt(params.getOrDefault("maxClaims", "5"))
            );
            return contentGenerator.generateClaims(claimsRequest)
                    .map(response -> HttpResponse.ok200()
                            .withJson(String.format(
                                    "{\"success\":true,\"data\":{\"claims\":%d,\"passed\":%s}}",
                                    response.claims().size(),
                                    response.validation().passed()))
                            .build())
                    .then((response, error) -> error == null
                        ? Promise.of(response)
                        : Promise.of(errorResponse(500, "CLAIMS_ERROR", error.getMessage())));
        } catch (Exception e) {
            return Promise.of(errorResponse(400, "INVALID_REQUEST", e.getMessage()));
        }
    }

    public Promise<HttpResponse> generateExamples(HttpRequest request) {
        return Promise.of(HttpResponse.ok200().withJson("{\"success\":true,\"message\":\"Examples require claims input\"}").build());
    }

    public Promise<HttpResponse> generateSimulations(HttpRequest request) {
        return Promise.of(HttpResponse.ok200().withJson("{\"success\":true,\"message\":\"Simulations require claims input\"}").build());
    }

    public Promise<HttpResponse> generateAnimations(HttpRequest request) {
        return Promise.of(HttpResponse.ok200().withJson("{\"success\":true,\"message\":\"Animations require claims input\"}").build());
    }

    public Promise<HttpResponse> generateAssessments(HttpRequest request) {
        return Promise.of(HttpResponse.ok200().withJson("{\"success\":true,\"message\":\"Assessments require claims input\"}").build());
    }

    public Promise<HttpResponse> getConfiguration(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId") == null ? "default" : request.getQueryParameter("tenantId");
        return contentGenerator.getConfiguration(tenantId)
                .map(config -> HttpResponse.ok200()
                        .withJson(String.format("{\"success\":true,\"data\":{\"tenantId\":\"%s\"}}", tenantId))
                        .build());
    }

    public Promise<HttpResponse> getSupportedModels(HttpRequest request) {
        return contentGenerator.getSupportedModels()
                .map(models -> HttpResponse.ok200()
                        .withJson(String.format("{\"success\":true,\"data\":{\"count\":%d}}", models.size()))
                        .build());
    }

    public Promise<HttpResponse> healthCheck(HttpRequest request) {
        return Promise.of(HttpResponse.ok200()
                .withJson("{\"status\":\"healthy\",\"service\":\"content-generation\"}")
                .build());
    }

    private ContentGenerationRequest parseRequest(HttpRequest request) {
        Map<String, String> params = parseQueryParams(request);
        String domainName = params.getOrDefault("domain", Domain.PHYSICS.name());
        return ContentGenerationRequest.builder()
                .topic(params.getOrDefault("topic", "General topic"))
                .gradeLevel(params.getOrDefault("gradeLevel", "HIGH_SCHOOL"))
                .domain(Domain.valueOf(domainName))
                .tenantId(params.getOrDefault("tenantId", "default"))
                .config(GenerationConfig.defaultConfig())
                .build();
    }

    private Map<String, String> parseQueryParams(HttpRequest request) {
        Map<String, String> params = new HashMap<>();
        for (String key : new String[] {
                "topic",
                "gradeLevel",
                "domain",
                "tenantId",
                "maxClaims"
        }) {
            String value = request.getQueryParameter(key);
            if (value != null) {
                params.put(key, value);
            }
        }
        return params;
    }

    private HttpResponse errorResponse(int statusCode, String code, String message) {
        return HttpResponse.ofCode(statusCode)
                .withJson(String.format("{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}", code, sanitize(message)))
                .build();
    }

    private String sanitize(String message) {
        return message == null ? "unknown" : message.replace("\"", "'");
    }
}
