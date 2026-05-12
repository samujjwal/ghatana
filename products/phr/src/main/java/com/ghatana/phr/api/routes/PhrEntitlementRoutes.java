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
        // Extract authentication context from request headers
        String principalId = request.getHeader(io.activej.http.HttpHeaders.of("X-Principal-Id"));
        String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
        String role = request.getHeader(io.activej.http.HttpHeaders.of("X-Role"));
        String persona = request.getHeader(io.activej.http.HttpHeaders.of("X-Persona"));
        String tier = request.getHeader(io.activej.http.HttpHeaders.of("X-Tier"));

        // For test scenarios, only use defaults if headers are completely missing
        // If headers are present (even empty), use them as-is
        if (principalId == null) {
            principalId = "anonymous";
        }
        if (tenantId == null) {
            tenantId = "default";
        }
        if (role == null) {
            role = "patient";
        }
        if (persona == null) {
            persona = "patient";
        }
        if (tier == null) {
            tier = "core";
        }

        String cacheKey = "route-entitlements:" + role;

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

        // Compute entitlements
        List<ProductRouteEntitlement.ActionEntitlement> actions = List.of(
            new ProductRouteEntitlement.ActionEntitlement("view-patient-summary", "View patient summary", "/dashboard"),
            new ProductRouteEntitlement.ActionEntitlement("view-records", "View records", "/records"),
            new ProductRouteEntitlement.ActionEntitlement("manage-consent", "Manage consent", "/consents")
        );
        List<ProductRouteEntitlement.CardEntitlement> cards = List.of(
            new ProductRouteEntitlement.CardEntitlement("patient-summary", "Patient summary", "/dashboard", "dashboard"),
            new ProductRouteEntitlement.CardEntitlement("active-consent-grants", "Active consent grants", "/consents", "dashboard")
        );

        ProductRouteEntitlement entitlement = new ProductRouteEntitlement(
            "phr",
            principalId,
            tenantId,
            role,
            persona,
            tier,
            null,  // correlationId can be null
            phrRoutesFor(role),
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

    private List<ProductRouteEntitlement.RouteEntitlement> phrRoutesFor(String role) {
        Map<String, Integer> roleOrder = Map.of(
            "patient", 0,
            "caregiver", 1,
            "clinician", 2,
            "admin", 3
        );

        List<ProductRouteEntitlement.RouteEntitlement> allRoutes = List.of(
            route("/dashboard", "Dashboard", "patient", List.of("view-patient-summary"), List.of("patient-summary")),
            route("/records", "Records", "patient", List.of("view-records"), List.of("record-highlights")),
            route("/consents", "Consents", "patient", List.of("manage-consent"), List.of("active-consent-grants")),
            route("/appointments", "Appointments", "patient", List.of("schedule-visit"), List.of("upcoming-appointments")),
            route("/settings", "Settings", "patient", List.of("manage-profile-settings"), List.of("profile-controls")),
            route("/labs", "Labs", "caregiver", List.of("review-lab-results"), List.of("recent-lab-results")),
            route("/medications", "Medications", "caregiver", List.of("review-medications"), List.of("medication-adherence")),
            route("/emergency", "Emergency", "clinician", List.of("break-glass-review"), List.of("override-audit-timeline"))
        );

        List<ProductRouteEntitlement.RouteEntitlement> filtered = routeEntitlementEvaluator.filterByRole(allRoutes, role, roleOrder);
        
        // Unknown role - fail closed to patient routes (minimum access)
        if (filtered.isEmpty()) {
            filtered = routeEntitlementEvaluator.filterByRole(allRoutes, "patient", roleOrder);
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
