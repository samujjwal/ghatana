package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    public DmosRouteEntitlementServlet(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/v1/route-entitlements", this::handleRouteEntitlements)
            .build();
    }

    private Promise<HttpResponse> handleRouteEntitlements(HttpRequest request) {
        String role = headerOrDefault(request, "X-Role", "viewer");
        Map<String, Object> entitlement = Map.of(
            "product", "digital-marketing",
            "principalId", headerOrDefault(request, "X-Principal-ID", "anonymous"),
            "tenantId", headerOrDefault(request, "X-Tenant-ID", "default"),
            "role", role,
            "persona", headerOrDefault(request, "X-Persona", "analyst"),
            "tier", headerOrDefault(request, "X-Tier", "core"),
            "routes", routesFor(role),
            "actions", List.of(
                action("view-dashboard", "View dashboard", "/workspaces/:workspaceId/dashboard"),
                action("review-approval", "Review approval", "/workspaces/:workspaceId/approvals"),
                action("view-audit-log", "View AI action log", "/workspaces/:workspaceId/ai-actions")
            ),
            "cards", List.of(
                card("launch-readiness", "Launch readiness", "/workspaces/:workspaceId/dashboard"),
                card("approval-queue", "Approval queue", "/workspaces/:workspaceId/approvals"),
                card("workflow-health", "Workflow health", "/workspaces/:workspaceId/dashboard")
            )
        );
        return jsonResponse(request, 200, entitlement);
    }

    private static List<Map<String, Object>> routesFor(String role) {
        return ROUTE_CONTRACT.routes().stream()
            .filter(route -> isRouteVisibleForRole(route.minimumRole(), role))
            .map(DmosRouteEntitlementServlet::toRouteMap)
            .toList();
    }

    private static boolean isRouteVisibleForRole(String minimumRole, String currentRole) {
        return roleOrder(currentRole) >= roleOrder(minimumRole);
    }

    private static int roleOrder(String role) {
        Integer value = ROUTE_CONTRACT.roleOrder().get(role);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> toRouteMap(RouteDefinition definition) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("path", definition.path());
        route.put("label", definition.label());
        route.put("minimumRole", definition.minimumRole());
        route.put("personas", definition.personas());
        route.put("tiers", definition.tiers());
        route.put("actions", definition.actions());
        route.put("cards", definition.cards());
        if (definition.capabilityKey() != null && !definition.capabilityKey().isBlank()) {
            route.put("capabilityKey", definition.capabilityKey());
        }
        return route;
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

    private static Map<String, Object> action(String id, String label, String routePath) {
        return Map.of("id", id, "label", label, "routePath", routePath);
    }

    private static Map<String, Object> card(String id, String title, String routePath) {
        return Map.of("id", id, "title", title, "routePath", routePath, "surface", "dashboard");
    }

    private static String headerOrDefault(HttpRequest request, String name, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(name));
        return value == null || value.isBlank() ? defaultValue : value.trim();
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
