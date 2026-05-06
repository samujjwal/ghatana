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
        return jsonResponse(200, entitlement);
    }

    private static List<Map<String, Object>> routesFor(String role) {
        List<Map<String, Object>> base = List.of(
            route("/workspaces/:workspaceId/dashboard", "Dashboard", "viewer", List.of("view-dashboard"), List.of("launch-readiness")),
            route("/workspaces/:workspaceId/approvals", "Approvals", "viewer", List.of("review-approval"), List.of("pending-approvals")),
            route("/workspaces/:workspaceId/ai-actions", "AI Action Log", "viewer", List.of("view-audit-log"), List.of("recent-ai-actions"))
        );
        if ("marketing-director".equals(role) || "exec-sponsor".equals(role) || "admin".equals(role)) {
            return List.of(
                base.get(0),
                base.get(1),
                base.get(2),
                route("/workspaces/:workspaceId/campaigns", "Campaigns", "brand-manager", List.of("launch-campaign"), List.of("campaign-summary")),
                route("/workspaces/:workspaceId/strategy", "Strategy", "brand-manager", List.of("submit-strategy"), List.of("strategy-brief")),
                route("/workspaces/:workspaceId/budget", "Budget", "marketing-director", List.of("submit-budget", "approve-budget"), List.of("budget-recommendations"))
            );
        }
        if ("brand-manager".equals(role)) {
            return List.of(
                base.get(0),
                base.get(1),
                base.get(2),
                route("/workspaces/:workspaceId/campaigns", "Campaigns", "brand-manager", List.of("launch-campaign"), List.of("campaign-summary")),
                route("/workspaces/:workspaceId/strategy", "Strategy", "brand-manager", List.of("submit-strategy"), List.of("strategy-brief"))
            );
        }
        return base;
    }

    private static Map<String, Object> route(
            String path,
            String label,
            String minimumRole,
            List<String> actions,
            List<String> cards) {
        return Map.of(
            "path", path,
            "label", label,
            "minimumRole", minimumRole,
            "personas", List.of("analyst", "approver", "executive"),
            "tiers", List.of("core", "growth"),
            "actions", actions,
            "cards", cards
        );
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

    private static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        try {
            return Promise.of(HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson(MAPPER.writeValueAsString(body))
                .build());
        } catch (Exception e) {
            return Promise.of(HttpResponse.ofCode(500)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson("{\"error\":\"SERIALIZATION_ERROR\"}")
                .build());
        }
    }
}
