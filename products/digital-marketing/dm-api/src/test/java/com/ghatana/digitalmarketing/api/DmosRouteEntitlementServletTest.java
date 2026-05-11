package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Behavioral contract tests for DMOS route entitlement responses.
 * @doc.layer product
 * @doc.pattern Contract Test
 */
@DisplayName("DmosRouteEntitlementServlet")
class DmosRouteEntitlementServletTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new DmosRouteEntitlementServlet(Eventloop.create()).getServlet();
    }

    @Test
    @DisplayName("returns ProductRouteEntitlement-shaped payload")
    void returnsProductRouteEntitlementShape() throws Exception {
        HttpResponse response = dispatch(Map.of());

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(bodyString(response));
        assertThat(body.path("product").asText()).isEqualTo("digital-marketing");
        assertThat(body.path("principalId").asText()).isEqualTo("anonymous");
        assertThat(body.path("tenantId").asText()).isEqualTo("default");
        assertThat(body.path("role").asText()).isEqualTo("viewer");
        assertThat(body.path("routes").isArray()).isTrue();
        assertThat(body.path("actions").isArray()).isTrue();
        assertThat(body.path("cards").isArray()).isTrue();
    }

    @Test
    @DisplayName("viewer role is denied brand-manager routes")
    void viewerRoleDoesNotReceiveBrandManagerRoutes() throws Exception {
        HttpResponse response = dispatch(Map.of("X-Role", "viewer"));

        JsonNode routes = JSON.readTree(bodyString(response)).path("routes");
        assertThat(routePaths(routes)).contains("/workspaces/:workspaceId/dashboard");
        assertThat(routePaths(routes)).doesNotContain("/workspaces/:workspaceId/campaigns");
    }

    @Test
    @DisplayName("brand-manager role receives campaign routes but not admin-only agency route")
    void brandManagerReceivesCampaignRoutesButNotAdminRoutes() throws Exception {
        HttpResponse response = dispatch(Map.of("X-Role", "brand-manager"));

        JsonNode routes = JSON.readTree(bodyString(response)).path("routes");
        assertThat(routePaths(routes)).contains("/workspaces/:workspaceId/campaigns");
        assertThat(routePaths(routes)).doesNotContain("/workspaces/:workspaceId/agency");
    }

    @Test
    @DisplayName("unknown role fails closed to viewer-scoped routes")
    void unknownRoleFailsClosedToViewerRoutes() throws Exception {
        HttpResponse response = dispatch(Map.of("X-Role", "owner"));

        JsonNode routes = JSON.readTree(bodyString(response)).path("routes");
        assertThat(routePaths(routes)).contains("/workspaces/:workspaceId/dashboard");
        assertThat(routePaths(routes)).doesNotContain("/workspaces/:workspaceId/campaigns");
    }

    @Test
    @DisplayName("propagates tenant, principal, persona, and tier headers")
    void propagatesEntitlementContextHeaders() throws Exception {
        HttpResponse response = dispatch(Map.of(
            "X-Tenant-ID", "tenant-dm-1",
            "X-Principal-ID", "principal-1",
            "X-Persona", "planner",
            "X-Tier", "growth"
        ));

        JsonNode body = JSON.readTree(bodyString(response));
        assertThat(body.path("tenantId").asText()).isEqualTo("tenant-dm-1");
        assertThat(body.path("principalId").asText()).isEqualTo("principal-1");
        assertThat(body.path("persona").asText()).isEqualTo("planner");
        assertThat(body.path("tier").asText()).isEqualTo("growth");
    }

    private HttpResponse dispatch(Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.get("http://localhost/v1/route-entitlements");
        headers.forEach((name, value) -> builder.withHeader(HttpHeaders.of(name), value));
        return runPromise(() -> servlet.serve(builder.build()));
    }

    private String bodyString(HttpResponse response) throws Exception {
        byte[] bytes = runPromise(response::loadBody).asArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> routePaths(JsonNode routes) {
        List<String> paths = new ArrayList<>();
        routes.forEach(route -> paths.add(route.path("path").asText()));
        return paths;
    }
}
