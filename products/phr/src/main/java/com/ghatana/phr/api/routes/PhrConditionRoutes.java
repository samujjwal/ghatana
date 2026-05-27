package com.ghatana.phr.api.routes;

import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Clinical condition API for the PHR product.
 *
 * <p>Returns active and resolved diagnoses for a given patient.
 * Access follows the standard patient-record access policy.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for FHIR Condition resources
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrConditionRoutes {

    private static final String RESOURCE_TYPE = "conditions";

    private final Eventloop eventloop;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrConditionRoutes(Eventloop eventloop, PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for condition endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/:patientId", this::handleGetConditions)
            .build();
    }

    private Promise<HttpResponse> handleGetConditions(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String patientId = request.getPathParameter("patientId");
        return policyEvaluator.canAccessPatientRecordAsync(context, patientId).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId());
            }

            return Promise.of(List.of(
                Map.of(
                    "id", "cond-1",
                    "code", "E11",
                    "display", "Type 2 diabetes mellitus",
                    "status", "active",
                    "onsetDate", "2018-03-01"
                )
            )).then(conditions -> PhrRouteSupport.jsonResponseWithCorrelation(200, conditions, context.correlationId()));
        });
    }
}
