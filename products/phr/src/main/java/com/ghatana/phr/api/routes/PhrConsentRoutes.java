package com.ghatana.phr.api.routes;

import com.ghatana.phr.api.dto.CreateConsentGrantRequest;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Consent management API routes for the PHR product.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for consent grant, revoke, check, and list journeys
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrConsentRoutes {

    private final Eventloop eventloop;
    private final ConsentManagementService consentService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrConsentRoutes(
            Eventloop eventloop,
            ConsentManagementService consentService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for consent endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/grants", this::handleCreateGrant)
            .with(HttpMethod.POST, "/grants/:grantId/revoke", this::handleRevokeGrant)
            .with(HttpMethod.GET, "/check", this::handleCheckConsent)
            .with(HttpMethod.GET, "/", this::handleListGrants)
            .build();
    }

    private Promise<HttpResponse> handleCreateGrant(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        // If idempotency key is provided, check for existing grant
        if (idempotencyKey != null) {
            return consentService.getGrantByIdempotencyKey(idempotencyKey)
                .then(existing -> {
                    if (existing.isPresent()) {
                        return PhrRouteSupport.jsonResponse(200, existing.get());
                    }
                    // Proceed with creation
                    return createConsentGrant(request, context, idempotencyKey);
                });
        }

        return createConsentGrant(request, context, null);
    }

    private Promise<HttpResponse> createConsentGrant(
            HttpRequest request,
            PhrRouteSupport.PhrRequestContext context,
            String idempotencyKey) {
        return request.loadBody()
            .then(body -> {
                ConsentManagementService.ConsentGrant grant;
                try {
                    grant = parseGrant(body.getString(StandardCharsets.UTF_8), idempotencyKey);
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_CONSENT_GRANT", ex.getMessage());
                }
                PhrPolicyEvaluator.PolicyDecision decision = policyEvaluator.canManageConsent(context, grant.getPatientId());
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.errorResponse(403, decision.getReasonCode(), decision.getReasonMessage());
                }
                return consentService.createGrant(grant)
                    .then(created -> PhrRouteSupport.jsonResponse(201, created));
            });
    }

    private Promise<HttpResponse> handleRevokeGrant(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_REVOKE", ex.getMessage());
        }
        PhrPolicyEvaluator.PolicyDecision decision = policyEvaluator.canManageConsent(context, patientId);
        if (!decision.isAllowed()) {
            return PhrRouteSupport.errorResponse(403, decision.getReasonCode(), decision.getReasonMessage());
        }
        String grantId = request.getPathParameter("grantId");
        return consentService.revokeGrant(grantId)
            .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                "grantId", grantId,
                "status", "REVOKED"
            )));
    }

    private Promise<HttpResponse> handleCheckConsent(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        String accessorId;
        String resourceType;
        String action;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
            accessorId = request.getQueryParameter("accessorId");
            if (accessorId == null || accessorId.isBlank()) {
                accessorId = context.principalId();
            }
            resourceType = PhrRouteSupport.requiredQuery(request, "resourceType");
            action = request.getQueryParameter("action");
            if (action == null || action.isBlank()) {
                action = "READ";
            }
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_CONSENT_CHECK", ex.getMessage());
        }
        String requestedAction = action.toUpperCase();
        PhrPolicyEvaluator.PolicyDecision decision = policyEvaluator.canCheckConsent(context, patientId, accessorId);
        if (!decision.isAllowed()) {
            return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
        }
        return consentService.validateAccess(patientId, accessorId, resourceType, requestedAction)
            .then(result -> {
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("allowed", result.isAllowed());
                response.put("reason", result.getReason());
                response.put("grantId", result.getGrantId());
                response.put("action", requestedAction);
                return PhrRouteSupport.jsonResponse(200, response);
            });
    }

    private Promise<HttpResponse> handleListGrants(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_LIST", ex.getMessage());
        }
        PhrPolicyEvaluator.PolicyDecision decision = policyEvaluator.canManageConsent(context, patientId);
        if (!decision.isAllowed()) {
            return PhrRouteSupport.errorResponse(403, decision.getReasonCode(), decision.getReasonMessage());
        }
        return consentService.getPatientGrants(patientId)
            .then(grants -> PhrRouteSupport.jsonResponse(200, Map.of(
                "patientId", patientId,
                "items", grants,
                "count", grants.size()
            )));
    }

    private static ConsentManagementService.ConsentGrant parseGrant(String json, String idempotencyKey) {
        CreateConsentGrantRequest request;
        try {
            request = PhrRouteSupport.JSON.readValue(json, CreateConsentGrantRequest.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid CreateConsentGrantRequest payload", ex);
        }
        
        // Convert DTO to service model
        CreateConsentGrantRequest.ConsentScopeDto scopeDto = request.getScope();
        ConsentManagementService.ConsentScope scope = new ConsentManagementService.ConsentScope(
            scopeDto.getResourceTypes() != null ? scopeDto.getResourceTypes() : Set.of(),
            scopeDto.isAllDocuments(),
            scopeDto.getSpecificDocumentIds() != null ? scopeDto.getSpecificDocumentIds() : Set.of(),
            scopeDto.getActions() != null ? scopeDto.getActions() : Set.of(),
            scopeDto.getFieldLevelAccess() != null ? scopeDto.getFieldLevelAccess() : Map.of()
        );
        
        return new ConsentManagementService.ConsentGrant(
            null, // id - generated by service
            request.getPatientId(),
            request.getRecipientId(),
            scope,
            request.getStatus() != null ? request.getStatus() : "ACTIVE",
            null, // grantedBy
            Instant.parse(request.getExpiresAt()),
            null, // grantedAt
            idempotencyKey
        );
    }

}
