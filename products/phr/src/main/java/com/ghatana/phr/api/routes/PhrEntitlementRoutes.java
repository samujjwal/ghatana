package com.ghatana.phr.api.routes;

import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.security.ProductRouteEntitlement;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Route entitlement API for the PHR product.
 * <p>
 * Handles route/content entitlement requests with identity-aware caching.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Route entitlement handlers for PHR
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrEntitlementRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(PhrEntitlementRoutes.class);
    private static final String CONTENT_JSON = "application/json";

    private final Eventloop eventloop;
    private final RouteEntitlementEvaluator routeEntitlementEvaluator;
    private final IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache;

    public PhrEntitlementRoutes(
            Eventloop eventloop,
            RouteEntitlementEvaluator routeEntitlementEvaluator,
            IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache) {
        this.eventloop = eventloop;
        this.routeEntitlementEvaluator = routeEntitlementEvaluator;
        this.entitlementCache = entitlementCache;
    }

    /**
     * Returns the routing servlet for entitlement endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleRouteEntitlements)
            .build();
    }

    private Promise<HttpResponse> handleRouteEntitlements(io.activej.http.HttpRequest request) {
        // Extract and validate authentication context from request headers
        // Fail closed: require explicit identity headers for production route entitlement
        String principalId = request.getHeader(io.activej.http.HttpHeaders.of("X-Principal-Id"));
        String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
        String role = request.getHeader(io.activej.http.HttpHeaders.of("X-Role"));
        String persona = request.getHeader(io.activej.http.HttpHeaders.of("X-Persona"));
        String tier = request.getHeader(io.activej.http.HttpHeaders.of("X-Tier"));

        // Validate required headers - fail closed for missing identity
        if (principalId == null || principalId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_PRINCIPAL", "X-Principal-Id header is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_TENANT", "X-Tenant-Id header is required");
        }
        if (role == null || role.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_ROLE", "X-Role header is required");
        }

        // Normalize role to lower-case
        String normalizedRole = role.strip().toLowerCase();
        if (!PhrRouteSupport.ALLOWED_ROLES.contains(normalizedRole)) {
            return PhrRouteSupport.errorResponse(400, "INVALID_ROLE", "Unrecognised role: " + role);
        }

        // Set sensible defaults for optional headers
        if (persona == null || persona.isBlank()) {
            persona = normalizedRole;
        }
        if (tier == null || tier.isBlank()) {
            tier = "core";
        }

        String cacheKey = "route-entitlements:" + normalizedRole;

        // Try cache first
        java.util.Optional<Map<String, Object>> cached = entitlementCache.get(
            principalId,
            tenantId,
            "/route-entitlements",
            cacheKey
        );
        if (cached.isPresent()) {
            return jsonResponse(200, cached.get());
        }

        Map<String, Integer> roleOrder = phrRoleOrder();
        List<ProductRouteEntitlement.RouteEntitlement> routes = phrRoutesFor(normalizedRole, roleOrder);
        List<ProductRouteEntitlement.ActionEntitlement> actions =
            routeEntitlementEvaluator.filterActionsByRole(routes, normalizedRole, roleOrder);
        List<ProductRouteEntitlement.CardEntitlement> cards =
            routeEntitlementEvaluator.filterCardsByRole(routes, normalizedRole, roleOrder);

        ProductRouteEntitlement entitlement = new ProductRouteEntitlement(
            "phr",
            principalId,
            tenantId,
            normalizedRole,
            persona,
            tier,
            null,  // correlationId can be null
            routes,
            actions,
            cards
        );

        // Build map manually to handle null correlationId
        // (ProductRouteEntitlement.toMap() doesn't handle null values well)
        Map<String, Object> entitlementMap = new java.util.HashMap<>();
        entitlementMap.put("product", entitlement.product());
        entitlementMap.put("principalId", entitlement.principalId());
        entitlementMap.put("tenantId", entitlement.tenantId());
        entitlementMap.put("role", entitlement.role());
        entitlement.persona().ifPresent(p -> entitlementMap.put("persona", p));
        entitlement.tier().ifPresent(t -> entitlementMap.put("tier", t));
        entitlement.correlationId().ifPresent(c -> entitlementMap.put("correlationId", c));
        entitlementMap.put("routes", entitlement.routes().stream().map(ProductRouteEntitlement.RouteEntitlement::toMap).toList());
        entitlementMap.put("actions", entitlement.actions().stream().map(ProductRouteEntitlement.ActionEntitlement::toMap).toList());
        entitlementMap.put("cards", entitlement.cards().stream().map(ProductRouteEntitlement.CardEntitlement::toMap).toList());
        
        // Cache for 5 minutes (300 seconds)
        entitlementCache.put(principalId, tenantId, "/route-entitlements", cacheKey, entitlementMap);
        
        return jsonResponse(200, entitlementMap);
    }

    private static Map<String, Integer> phrRoleOrder() {
        return Map.of(
            "patient", 0,
            "caregiver", 1,
            "clinician", 2,
            "admin", 3
        );
    }

    private List<ProductRouteEntitlement.RouteEntitlement> phrRoutesFor(String role, Map<String, Integer> roleOrder) {
        List<ProductRouteEntitlement.RouteEntitlement> allRoutes = List.of(
            // Core patient routes
            route("/dashboard", "Dashboard", "patient", List.of("view-patient-summary"), List.of("patient-summary", "care-plan", "emergency-readiness")),
            route("/records", "Records", "patient", List.of("view-records"), List.of("record-highlights", "interop-status")),
            route("/consents", "Consents", "patient", List.of("manage-consent"), List.of("active-consent-grants", "expiring-consents")),
            route("/appointments", "Appointments", "patient", List.of("schedule-visit"), List.of("upcoming-appointments")),
            route("/settings", "Settings", "patient", List.of("manage-profile-settings"), List.of("profile-controls", "integration-status")),
            
            // Clinical routes
            route("/labs", "Labs", "caregiver", List.of("review-lab-results"), List.of("recent-lab-results")),
            route("/medications", "Medications", "caregiver", List.of("review-medications"), List.of("medication-adherence")),
            route("/conditions", "Conditions", "patient", List.of("view-conditions"), List.of("condition-list")),
            route("/observations", "Observations", "caregiver", List.of("view-observations"), List.of("observation-trends")),
            route("/immunizations", "Immunizations", "patient", List.of("view-immunizations"), List.of("immunization-schedule")),
            
            // Document routes
            route("/documents", "Documents", "patient", List.of("view-documents", "upload-document"), List.of("document-list")),
            route("/documents/upload", "Document Upload", "patient", List.of("upload-document"), List.of()),
            route("/documents/:docId/ocr", "OCR Review", "patient", List.of("review-ocr"), List.of()),
            
            // Timeline and profile
            route("/timeline", "Timeline", "patient", List.of("view-timeline"), List.of("health-timeline")),
            route("/profile", "Profile", "patient", List.of("view-profile", "edit-profile"), List.of("profile-summary")),
            route("/records/:recordId", "Record Detail", "patient", List.of("view-records"), List.of()),
            
            // Notifications
            route("/notifications", "Notifications", "patient", List.of("view-notifications"), List.of("notification-feed")),
            
            // Error pages
            route("/forbidden", "Forbidden", "patient", List.of(), List.of()),
            route("/not-found", "Not Found", "patient", List.of(), List.of()),
            
            // Emergency and governance
            route("/emergency", "Emergency", "clinician", List.of("break-glass-review"), List.of("override-audit-timeline")),
            route("/emergency/reviews", "Emergency Reviews", "admin", List.of("review-emergency-access"), List.of("pending-reviews", "overdue-reviews")),
            route("/release-readiness", "Release Readiness", "admin", List.of("view-release-readiness"),
                List.of("evidence-freshness", "fhir-runtime", "consent-cache-proof", "rollback-proof")),
            route("/audit", "Audit", "admin", List.of("view-audit-trail"), List.of("audit-trail")),
            
            // Feature-flagged provider routes
            route("/provider/dashboard", "Provider Dashboard", "clinician", List.of("view-provider-dashboard"), List.of("provider-panel")),
            route("/provider/patients", "Provider Patients", "clinician", List.of("view-patient-list"), List.of("patient-roster")),
            
            // Feature-flagged caregiver routes
            route("/caregiver/dependents", "Caregiver Dependents", "caregiver", List.of("view-dependents"), List.of("dependent-summaries")),
            
            // Feature-flagged FCHV routes
            route("/fchv/dashboard", "FCHV Dashboard", "caregiver", List.of("view-fchv-dashboard"), List.of("community-health-summary"))
        );

        List<ProductRouteEntitlement.RouteEntitlement> filtered = routeEntitlementEvaluator.filterByRole(allRoutes, role, roleOrder);
        
        // Unknown role - fail closed with empty route list
        // Do not silently fall back to patient routes for security
        if (filtered.isEmpty()) {
            LOG.warn("Unknown role '{}' requested - returning empty route list (fail closed)", normalizedRole);
        }
        
        return filtered;
    }

    private static ProductRouteEntitlement.RouteEntitlement route(
            String path,
            String label,
            String minimumRole,
            List<String> actions,
            List<String> cards) {
        return new ProductRouteEntitlement.RouteEntitlement(
            path,
            label,
            minimumRole,
            List.of("patient", "caregiver", "clinician", "admin"),
            List.of("core"),
            actions,
            cards,
            null
        );
    }

    private static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        String json = com.ghatana.platform.core.util.JsonUtils.toJsonSafe(body);
        if (json == null) {
            json = "{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}";
            statusCode = 500;
        }
        return Promise.of(io.activej.http.HttpResponse.ofCode(statusCode)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson(json)
                .build());
    }
}
