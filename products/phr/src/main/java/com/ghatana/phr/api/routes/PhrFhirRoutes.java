package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.api.FhirController;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;

/**
 * FHIR R4 API routes for the PHR product.
 * <p>
 * Handles FHIR resource creation, retrieval, and search operations.
 * </p>
 *
 * @doc.type class
 * @doc.purpose FHIR R4 route handlers for PHR
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrFhirRoutes {

    private static final String CONTENT_JSON = "application/json";

    private final Eventloop eventloop;
    private final FhirController fhirController;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrFhirRoutes(Eventloop eventloop, FhirController fhirController, PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = eventloop;
        this.fhirController = fhirController;
        this.policyEvaluator = policyEvaluator;
    }

    /**
     * Returns the routing servlet for FHIR endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/:resourceType", this::handleCreateFhirResource)
            .with(HttpMethod.GET, "/:resourceType/:id", this::handleGetFhirResource)
            .with(HttpMethod.GET, "/:resourceType", this::handleSearchFhirResources)
            .build();
    }

    private Promise<HttpResponse> handleCreateFhirResource(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_FHIR_CONTEXT", ex.getMessage());
        }
        String resourceType = request.getPathParameter("resourceType");
        return request.loadBody()
                .then(body -> {
                    String resourceJson = body.getString(StandardCharsets.UTF_8);
                    String patientId;
                    try {
                        patientId = patientIdFromResource(context, resourceType, resourceJson);
                    } catch (IllegalArgumentException ex) {
                        return PhrRouteSupport.errorResponse(400, "INVALID_FHIR_PATIENT_SCOPE", ex.getMessage(), correlationId);
                    }
                    return authorizeFhir(context, patientId, resourceType, "WRITE")
                        .then(decision -> decision.isAllowed()
                            ? fhirController.createResource(resourceType, resourceJson)
                                .map(response -> fhirHttpResponse(response, context.correlationId()))
                            : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
                });
    }

    private Promise<HttpResponse> handleGetFhirResource(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_FHIR_CONTEXT", ex.getMessage());
        }
        String resourceType = request.getPathParameter("resourceType");
        String id = request.getPathParameter("id");
        String patientId;
        try {
            patientId = patientIdFromReadRequest(request, resourceType, id);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_FHIR_PATIENT_SCOPE", ex.getMessage(), correlationId);
        }
        return authorizeFhir(context, patientId, resourceType, "READ")
            .then(decision -> decision.isAllowed()
                ? fhirController.getResource(resourceType, id).map(response -> fhirHttpResponse(response, context.correlationId()))
                : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
    }

    private Promise<HttpResponse> handleSearchFhirResources(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_FHIR_CONTEXT", ex.getMessage());
        }
        String resourceType = request.getPathParameter("resourceType");
        String patientId;
        try {
            patientId = patientIdFromSearchRequest(request, resourceType);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_FHIR_PATIENT_SCOPE", ex.getMessage(), correlationId);
        }
        var params = request.getQueryParameters();
        return authorizeFhir(context, patientId, resourceType, "SEARCH")
            .then(decision -> decision.isAllowed()
                ? fhirController.searchResources(resourceType, params).map(response -> fhirHttpResponse(response, context.correlationId()))
                : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> authorizeFhir(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String resourceType,
            String action) {
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "fhir-" + resourceType,
            action,
            context.tenantId(),
            context.facilityId()
        );
    }

    private static HttpResponse fhirHttpResponse(com.ghatana.phr.api.PhrApiResponse fhirResponse, String correlationId) {
        return HttpResponse.ofCode(fhirResponse.statusCode())
            .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
            .withJson(fhirResponse.body())
            .build();
    }

    private static String patientIdFromReadRequest(HttpRequest request, String resourceType, String id) {
        if ("Patient".equals(resourceType)) {
            return requirePatientId(id);
        }
        return requirePatientId(firstNonBlank(
            request.getQueryParameter("patient"),
            request.getQueryParameter("patientId"),
            patientIdFromReference(request.getQueryParameter("subject"))
        ));
    }

    private static String patientIdFromSearchRequest(HttpRequest request, String resourceType) {
        if ("Patient".equals(resourceType)) {
            return requirePatientId(firstNonBlank(
                request.getQueryParameter("_id"),
                request.getQueryParameter("identifier"),
                request.getQueryParameter("patientId")
            ));
        }
        return requirePatientId(firstNonBlank(
            request.getQueryParameter("patient"),
            request.getQueryParameter("patientId"),
            patientIdFromReference(request.getQueryParameter("subject"))
        ));
    }

    private static String patientIdFromResource(PhrRouteSupport.PhrRequestContext context, String resourceType, String resourceJson) {
        try {
            JsonNode node = PhrRouteSupport.JSON.readTree(resourceJson);
            if (!resourceType.equals(node.path("resourceType").asText(resourceType))) {
                throw new IllegalArgumentException("FHIR resourceType must match route resourceType");
            }
            if ("Patient".equals(resourceType)) {
                return requirePatientId(firstNonBlank(text(node, "id"), ownPatientScope(context)));
            }
            return requirePatientId(firstNonBlank(
                patientIdFromReference(text(node.path("subject"), "reference")),
                patientIdFromReference(text(node.path("patient"), "reference")),
                text(node, "patientId")
            ));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid FHIR JSON payload", ex);
        }
    }

    private static String requirePatientId(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("FHIR request must include a patient scope");
        }
        return patientId.strip();
    }

    private static String patientIdFromReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String stripped = reference.strip();
        return stripped.startsWith("Patient/") ? stripped.substring("Patient/".length()) : stripped;
    }

    private static String ownPatientScope(PhrRouteSupport.PhrRequestContext context) {
        return "patient".equals(context.role()) ? context.principalId() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
