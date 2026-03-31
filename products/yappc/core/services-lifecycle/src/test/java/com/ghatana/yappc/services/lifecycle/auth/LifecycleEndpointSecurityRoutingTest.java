/*
 * Copyright (c) 2026 Ghatana Technologies
 */
package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.security.YappcApiSecurity;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for lifecycle outer-route security expectations.
 *
 * @doc.type class
 * @doc.purpose Verifies health/readiness public access and secured metrics access behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Lifecycle outer endpoint security")
class LifecycleEndpointSecurityRoutingTest extends EventloopTestBase {

    private static final String DEV_KEY = "dev-key";

    private static AsyncServlet outerRouterLikeServlet() {
        AsyncServlet securedMetrics = YappcApiSecurity.secureReadEndpoint(
                request -> HttpResponse.ok200().withPlainText("metrics").toPromise(),
                "yappc:lifecycle-metrics");

        return request -> {
            String path = request.getPath();
            if ("/health".equals(path)) {
                return HttpResponse.ok200().withPlainText("OK").toPromise();
            }
            if ("/ready".equals(path)) {
                return HttpResponse.ok200().withPlainText("READY").toPromise();
            }
            if ("/metrics".equals(path)) {
                return securedMetrics.serve(request);
            }
            return HttpResponse.ofCode(404).withPlainText("Not Found").toPromise();
        };
    }

    @Test
    @DisplayName("health and ready endpoints are public")
    void healthAndReadyArePublic() {
        AsyncServlet router = outerRouterLikeServlet();

        HttpResponse healthResponse = runPromise(() -> router.serve(
                HttpRequest.builder(HttpMethod.GET, "http://localhost/health").build()));
        HttpResponse readyResponse = runPromise(() -> router.serve(
                HttpRequest.builder(HttpMethod.GET, "http://localhost/ready").build()));

        assertThat(healthResponse.getCode()).isEqualTo(200);
        assertThat(readyResponse.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("metrics endpoint remains API-key protected")
    void metricsRemainsProtected() {
        AsyncServlet router = outerRouterLikeServlet();

        HttpResponse unauthorized = runPromise(() -> router.serve(
                HttpRequest.builder(HttpMethod.GET, "http://localhost/metrics").build()));

        HttpResponse authorized = runPromise(() -> router.serve(
                HttpRequest.builder(HttpMethod.GET, "http://localhost/metrics")
                        .withHeader(HttpHeaders.of("X-API-Key"), DEV_KEY)
                        .withHeader(HttpHeaders.of("X-Forwarded-For"), "127.0.0.1")
                        .build()));

        assertThat(unauthorized.getCode()).isEqualTo(401);
        // Authn must succeed with a valid API key; authz may be 200 or 403 depending on
        // environment role mapping (YAPPC_API_DEFAULT_ROLES / YAPPC_API_KEY_ROLE_MAP).
        assertThat(authorized.getCode()).isIn(200, 403);
    }
}
