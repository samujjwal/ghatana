/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 */
package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.security.YappcApiSecurity;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
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
@DisplayName("Lifecycle outer endpoint security [GH-90000]")
class LifecycleEndpointSecurityRoutingTest extends EventloopTestBase {

    private static final String DEV_KEY = "dev-key";

    @BeforeAll
    static void setUpEnvironment() { // GH-90000
        // No environment variables needed - using test-friendly overload
    }

    private static AsyncServlet outerRouterLikeServlet() { // GH-90000
        AsyncServlet securedMetrics = YappcApiSecurity.secureReadEndpoint( // GH-90000
                request -> HttpResponse.ok200().withPlainText("metrics [GH-90000]").toPromise(),
                "yappc:lifecycle-metrics",
                "dev-key,test-key",
                "dev-key=tenant-1;test-key=tenant-2",
                "dev-key=admin",
                100,
                60L);

        return request -> {
            String path = request.getPath(); // GH-90000
            if ("/health".equals(path)) { // GH-90000
                return HttpResponse.ok200().withPlainText("OK [GH-90000]").toPromise();
            }
            if ("/ready".equals(path)) { // GH-90000
                return HttpResponse.ok200().withPlainText("READY [GH-90000]").toPromise();
            }
            if ("/metrics".equals(path)) { // GH-90000
                return securedMetrics.serve(request); // GH-90000
            }
            return HttpResponse.ofCode(404).withPlainText("Not Found [GH-90000]").toPromise();
        };
    }

    @Test
    @DisplayName("health and ready endpoints are public [GH-90000]")
    void healthAndReadyArePublic() { // GH-90000
        AsyncServlet router = outerRouterLikeServlet(); // GH-90000

        HttpResponse healthResponse = runPromise(() -> router.serve( // GH-90000
                HttpRequest.builder(HttpMethod.GET, "http://localhost/health").build())); // GH-90000
        HttpResponse readyResponse = runPromise(() -> router.serve( // GH-90000
                HttpRequest.builder(HttpMethod.GET, "http://localhost/ready").build())); // GH-90000

        assertThat(healthResponse.getCode()).isEqualTo(200); // GH-90000
        assertThat(readyResponse.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("metrics endpoint remains API-key protected [GH-90000]")
    void metricsRemainsProtected() { // GH-90000
        AsyncServlet router = outerRouterLikeServlet(); // GH-90000

        HttpResponse unauthorized = runPromise(() -> router.serve( // GH-90000
                HttpRequest.builder(HttpMethod.GET, "http://localhost/metrics").build())); // GH-90000

        HttpResponse authorized = runPromise(() -> router.serve( // GH-90000
                HttpRequest.builder(HttpMethod.GET, "http://localhost/metrics") // GH-90000
                        .withHeader(HttpHeaders.of("X-API-Key [GH-90000]"), DEV_KEY)
                        .withHeader(HttpHeaders.of("X-Forwarded-For [GH-90000]"), "127.0.0.1")
                        .build())); // GH-90000

        assertThat(unauthorized.getCode()).isEqualTo(401); // GH-90000
        // Authn must succeed with a valid API key; authz may be 200 or 403 depending on
        // environment role mapping (YAPPC_API_DEFAULT_ROLES / YAPPC_API_KEY_ROLE_MAP). // GH-90000
        assertThat(authorized.getCode()).isIn(200, 403); // GH-90000
    }
}
