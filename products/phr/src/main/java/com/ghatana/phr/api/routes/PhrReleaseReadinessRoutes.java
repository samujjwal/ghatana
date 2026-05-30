package com.ghatana.phr.api.routes;

import com.ghatana.kernel.release.ReleaseReadinessRuntimeService;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-truth release readiness endpoint for the PHR cockpit.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter exposing PHR release evidence via Kernel runtime API
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrReleaseReadinessRoutes {

    private static final List<String> REQUIRED_SECTIONS = List.of(
        "evidenceFreshness",
        "fhirRuntime",
        "consentCache",
        "deployment",
        "rollback",
        "dataCloudRuntime"
    );

    private final Eventloop eventloop;
    private final ReleaseReadinessRuntimeService releaseReadinessService;

    public PhrReleaseReadinessRoutes(Eventloop eventloop, ReleaseReadinessRuntimeService releaseReadinessService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.releaseReadinessService = Objects.requireNonNull(releaseReadinessService, "releaseReadinessService must not be null");
    }

    /**
     * Returns the routing servlet for release readiness endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleReadiness)
            .with(HttpMethod.GET, "/section/:sectionId", this::handleSectionDrillDown)
            .build();
    }

    private Promise<HttpResponse> handleReadiness(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }
        if (!"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "PHR_RELEASE_READINESS_FORBIDDEN",
                "Release readiness evidence requires an admin principal");
        }

        String environment = normalizeEnvironment(request.getQueryParameter("environment"));
        return releaseReadinessService.getReleaseReadiness("phr", environment)
            .then(evidence -> PhrRouteSupport.jsonResponse(200, buildResponse(context, environment, evidence, correlationId)))
            .whenException(ex -> PhrRouteSupport.errorResponse(503, "PHR_RELEASE_READINESS_UNAVAILABLE", ex.getMessage(, correlationId)));
    }

    private Promise<HttpResponse> handleSectionDrillDown(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }
        if (!"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "PHR_RELEASE_READINESS_FORBIDDEN",
                "Release readiness evidence requires an admin principal");
        }

        String sectionId = request.getPathParameter("sectionId");
        String environment = normalizeEnvironment(request.getQueryParameter("environment"));
        
        return releaseReadinessService.getReleaseReadinessSection("phr", environment, sectionId)
            .then(sectionData -> {
                if (sectionData == null) {
                    return PhrRouteSupport.errorResponse(404, "SECTION_NOT_FOUND",
                        "Release readiness section not found: " + sectionId);
                }
                
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("product", "phr");
                response.put("tenantId", context.tenantId());
                response.put("principalId", context.principalId());
                response.put("role", context.role());
                response.putAll(sectionData);
                
                return PhrRouteSupport.jsonResponse(200, response);
            })
            .whenException(ex -> PhrRouteSupport.errorResponse(503, "PHR_RELEASE_READINESS_UNAVAILABLE", ex.getMessage(, correlationId)));
    }

    private Map<String, Object> buildResponse(
            PhrRouteSupport.PhrRequestContext context,
            String environment,
            Map<String, Object> evidence) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("product", "phr");
        response.put("tenantId", context.tenantId());
        response.put("principalId", context.principalId());
        response.put("role", context.role());
        response.put("environment", environment);
        response.putAll(evidence);
        response.put("requiredSections", REQUIRED_SECTIONS);
        return response;
    }

    private static String normalizeEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return "staging";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "local", "dev", "staging", "prod" -> normalized;
            default -> "staging";
        };
    }
}
