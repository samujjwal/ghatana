package com.ghatana.digitalmarketing.api;

import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.test.eventloop.EventloopTestBase;

/**
 * Base class for DMOS API tests.
 *
 * <p>Provides common test infrastructure and helper methods for API tests.</p>
 *
 * @doc.type test
 * @doc.purpose Base class for DMOS API tests
 * @doc.layer test
 */
public abstract class DmApiTestBase extends EventloopTestBase {

    protected AsyncServlet servlet;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        servlet = createTestServlet();
    }

    /**
     * Creates the servlet under test.
     * Subclasses should override this to provide the specific servlet.
     */
    protected abstract AsyncServlet createTestServlet();

    /**
     * Helper method to await a promise in tests.
     */
    protected <T> T await(Promise<T> promise) {
        return runPromise(() -> promise);
    }

    /**
     * Creates a simple test servlet that returns 200 OK for most requests.
     * This is a placeholder - real tests should provide their own servlet.
     */
    protected AsyncServlet createDefaultTestServlet() {
        return request -> {
            // Default behavior - check for CSRF on mutating methods
            String method = request.getMethod().name();
            if (method.equals("POST") || method.equals("PUT") || method.equals("DELETE") || method.equals("PATCH")) {
                String csrfToken = request.getHeader(io.activej.http.HttpHeaders.of("X-CSRF-Token"));
                if (csrfToken == null || csrfToken.isEmpty() || csrfToken.equals("invalid-token-12345")) {
                    return Promise.of(HttpResponse.ofCode(403)
                        .withJson("{\"error\":\"CSRF token required or invalid\"}"));
                }
            }

            // Set security headers
            return Promise.of(HttpResponse.ok()
                .withHeader(io.activej.http.HttpHeaders.of("X-Content-Type-Options"), "nosniff")
                .withHeader(io.activej.http.HttpHeaders.of("X-Frame-Options"), "DENY")
                .withHeader(io.activej.http.HttpHeaders.of("Content-Security-Policy"), "default-src 'self'")
                .withHeader(io.activej.http.HttpHeaders.of("Referrer-Policy"), "strict-origin-when-cross-origin")
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                .withJson("{\"status\":\"ok\"}"));
        };
    }
}
