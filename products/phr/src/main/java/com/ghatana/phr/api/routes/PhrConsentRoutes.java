package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.api.dto.CreateConsentGrantRequest;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
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

    public PhrConsentRoutes(Eventloop eventloop, ConsentManagementService consentService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
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
                if (!mayManagePatientConsent(context, grant.getPatientId())) {
                    return PhrRouteSupport.errorResponse(403, "CONSENT_OWNER_REQUIRED", "Only the patient or an admin can create consent grants");
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
        if (!mayManagePatientConsent(context, patientId)) {
            return PhrRouteSupport.errorResponse(403, "CONSENT_OWNER_REQUIRED", "Only the patient or an admin can revoke consent grants");
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
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
            accessorId = request.getQueryParameter("accessorId");
            if (accessorId == null || accessorId.isBlank()) {
                accessorId = context.principalId();
            }
            resourceType = PhrRouteSupport.requiredQuery(request, "resourceType");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_CONSENT_CHECK", ex.getMessage());
        }
        return consentService.validateAccess(patientId, accessorId, resourceType)
            .then(result -> {
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("allowed", result.isAllowed());
                response.put("reason", result.getReason());
                response.put("grantId", result.getGrantId());
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
        if (!mayManagePatientConsent(context, patientId)) {
            return PhrRouteSupport.errorResponse(403, "CONSENT_OWNER_REQUIRED", "Only the patient or an admin can list consent grants");
        }
        return consentService.getPatientGrants(patientId)
            .then(grants -> PhrRouteSupport.jsonResponse(200, Map.of(
                "patientId", patientId,
                "items", grants,
                "count", grants.size()
            )));
    }

    private static boolean mayManagePatientConsent(PhrRouteSupport.PhrRequestContext context, String patientId) {
        return context.principalId().equals(patientId) || "admin".equalsIgnoreCase(context.role());
    }

    private static ConsentManagementService.ConsentGrant parseGrant(String json, String idempotencyKey) {
        // B-018: Use DTO validation instead of manual JSON parsing
        CreateConsentGrantRequest request = PhrRouteSupport.parseAndValidateJson(
            json, CreateConsentGrantRequest.class, "CreateConsentGrantRequest"
        );
        
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

    private static Set<String> stringSet(JsonNode node) {
        if (!node.isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        node.forEach(value -> {
            if (!value.asText().isBlank()) {
                values.add(value.asText());
            }
        });
        return Set.copyOf(values);
    }
}
