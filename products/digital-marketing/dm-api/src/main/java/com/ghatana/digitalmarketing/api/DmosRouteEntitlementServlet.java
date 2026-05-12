package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.security.ProductEntitlementContext;
import com.ghatana.platform.http.security.ProductRouteEntitlement;
import com.ghatana.platform.http.security.RoleEvaluator;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Backend route/content entitlement API for DMOS shells.
 *
 * @doc.type class
 * @doc.purpose Exposes ProductRouteEntitlement-shaped navigation, action, and card metadata
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosRouteEntitlementServlet {

    private static final String CONTENT_JSON = "application/json; charset=utf-8";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RouteContract ROUTE_CONTRACT = loadRouteContract();

    private final Eventloop eventloop;
    private final ProductEntitlementContext entitlementContext;
    private final RoleEvaluator roleEvaluator;
    private final RouteEntitlementEvaluator routeEntitlementEvaluator;
    private final IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache;

    public DmosRouteEntitlementServlet(Eventloop eventloop, ProductEntitlementContext entitlementContext, RoleEvaluator roleEvaluator, RouteEntitlementEvaluator routeEntitlementEvaluator, IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.entitlementContext = Objects.requireNonNull(entitlementContext, "entitlementContext must not be null");
        this.roleEvaluator = Objects.requireNonNull(roleEvaluator, "roleEvaluator must not be null");
        this.routeEntitlementEvaluator = Objects.requireNonNull(routeEntitlementEvaluator, "routeEntitlementEvaluator must not be null");
        this.entitlementCache = Objects.requireNonNull(entitlementCache, "entitlementCache must not be null");
    }

    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/v1/route-entitlements", this::handleRouteEntitlements)
            .build();
    }

    private Promise<HttpResponse> handleRouteEntitlements(HttpRequest request) {
        if (!entitlementContext.isAuthenticated()) {
            return jsonResponse(request, 401, Map.of(
                "error", "UNAUTHENTICATED",
                "message", "Request must be authenticated to access route entitlements"
            ));
        }

        String principalId = entitlementContext.getPrincipalId();
        String tenantId = entitlementContext.getTenantId();
        String role = entitlementContext.getRole();
        String cacheKey = "route-entitlements:" + role;

        // Try cache first
        java.util.Optional<Map<String, Object>> cached = entitlementCache.get(
            principalId,
            tenantId,
            "/v1/route-entitlements",
            cacheKey
        );
        if (cached.isPresent()) {
            return jsonResponse(request, 200, cached.get());
        }

        // Compute entitlements
        List<ProductRouteEntitlement.ActionEntitlement> actions = List.of(
            new ProductRouteEntitlement.ActionEntitlement("digital-marketing:view-dashboard", "View dashboard", "/workspaces/:workspaceId/dashboard"),
            new ProductRouteEntitlement.ActionEntitlement("digital-marketing:review-approval", "Review approval", "/workspaces/:workspaceId/approvals"),
            new ProductRouteEntitlement.ActionEntitlement("digital-marketing:view-audit-log", "View AI action log", "/workspaces/:workspaceId/ai-actions")
        );
        List<ProductRouteEntitlement.CardEntitlement> cards = List.of(
            new ProductRouteEntitlement.CardEntitlement("launch-readiness", "Launch readiness", "/workspaces/:workspaceId/dashboard", "dashboard"),
            new ProductRouteEntitlement.CardEntitlement("approval-queue", "Approval queue", "/workspaces/:workspaceId/approvals", "dashboard"),
            new ProductRouteEntitlement.CardEntitlement("workflow-health", "Workflow health", "/workspaces/:workspaceId/dashboard", "dashboard")
        );

        ProductRouteEntitlement entitlement = new ProductRouteEntitlement(
            "digital-marketing",
            principalId,
            tenantId,
            role,
            entitlementContext.getPersona().orElse("analyst"),
            entitlementContext.getTier().orElse("core"),
            entitlementContext.getCorrelationId().orElse(null),
            routesFor(role),
            actions,
            cards
        );

        Map<String, Object> entitlementMap = entitlement.toMap();
        
        // Cache for 5 minutes (300 seconds)
        entitlementCache.put(principalId, tenantId, "/v1/route-entitlements", cacheKey, entitlementMap);
        
        return jsonResponse(request, 200, entitlementMap);
    }

    private List<ProductRouteEntitlement.RouteEntitlement> routesFor(String role) {
        List<ProductRouteEntitlement.RouteEntitlement> routes = ROUTE_CONTRACT.routes().stream()
            .map(DmosRouteEntitlementServlet::toRouteEntitlement)
            .toList();
        return routeEntitlementEvaluator.filterByRole(routes, role, ROUTE_CONTRACT.roleOrder());
    }

    private static ProductRouteEntitlement.RouteEntitlement toRouteEntitlement(RouteDefinition definition) {
        return new ProductRouteEntitlement.RouteEntitlement(
            definition.path(),
            definition.label(),
            definition.minimumRole(),
            definition.personas(),
            definition.tiers(),
            definition.actions(),
            definition.cards(),
            definition.capabilityKey() != null && !definition.capabilityKey().isBlank() ? definition.capabilityKey() : null
        );
    }

    private static RouteContract loadRouteContract() {
        try (InputStream inputStream = DmosRouteEntitlementServlet.class
            .getClassLoader()
            .getResourceAsStream("contracts/dmos-route-capabilities.json")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing contracts/dmos-route-capabilities.json");
            }
            return MAPPER.readValue(inputStream, RouteContract.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load DMOS route-capability contract", e);
        }
    }

    private Promise<HttpResponse> jsonResponse(HttpRequest request, int statusCode, Object body) {
        try {
            return Promise.of(HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson(MAPPER.writeValueAsString(body))
                .build());
        } catch (Exception e) {
            return Promise.of(DmosApiErrorResponses.error(500, "Failed to serialize route entitlements", request));
        }
    }

    private record RouteContract(Map<String, Integer> roleOrder, List<RouteDefinition> routes) {
    }

    private record RouteDefinition(
        String path,
        String label,
        String minimumRole,
        List<String> personas,
        List<String> tiers,
        List<String> actions,
        List<String> cards,
        String capabilityKey
    ) {
    }
}
