package com.ghatana.phr.api.routes;

import com.ghatana.phr.api.FhirController;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(PhrFhirRoutes.class);
    private static final String CONTENT_JSON = "application/json";

    private final Eventloop eventloop;
    private final FhirController fhirController;

    public PhrFhirRoutes(Eventloop eventloop, FhirController fhirController) {
        this.eventloop = eventloop;
        this.fhirController = fhirController;
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
        try {
            PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_FHIR_CONTEXT", ex.getMessage());
        }
        String resourceType = request.getPathParameter("resourceType");
        return request.loadBody()
                .then(body -> {
                    String resourceJson = body.getString(StandardCharsets.UTF_8);
                    return fhirController.createResource(resourceType, resourceJson)
                            .map(fhirResponse -> HttpResponse.ofCode(fhirResponse.statusCode())
                                    .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                                    .withJson(fhirResponse.body())
                                    .build());
                });
    }

    private Promise<HttpResponse> handleGetFhirResource(HttpRequest request) {
        try {
            PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_FHIR_CONTEXT", ex.getMessage());
        }
        String resourceType = request.getPathParameter("resourceType");
        String id = request.getPathParameter("id");
        return fhirController.getResource(resourceType, id)
                .map(fhirResponse -> HttpResponse.ofCode(fhirResponse.statusCode())
                        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                        .withJson(fhirResponse.body())
                        .build());
    }

    private Promise<HttpResponse> handleSearchFhirResources(HttpRequest request) {
        try {
            PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_FHIR_CONTEXT", ex.getMessage());
        }
        String resourceType = request.getPathParameter("resourceType");
        var params = request.getQueryParameters();
        return fhirController.searchResources(resourceType, params)
                .map(fhirResponse -> HttpResponse.ofCode(fhirResponse.statusCode())
                        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                        .withJson(fhirResponse.body())
                        .build());
    }
}
