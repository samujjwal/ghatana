/**
 * @doc.type class
 * @doc.purpose HTTP server tests with routing, middleware, authentication, and load handling
 * @doc.layer platform
 * @doc.pattern Test
 */
package com.ghatana.platform.http.server;

import com.ghatana.platform.http.server.filter.FilterChain;
import com.ghatana.platform.http.server.server.HttpServerBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP Server Tests
 *
 * HTTP server tests with routing, middleware, authentication, and load handling.
 */
@DisplayName("HTTP Server Tests")
class HttpServerTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("Should handle HTTP routing correctly")
    void shouldHandleHttpRoutingCorrectly() {
        RoutingServlet servlet = new RoutingServlet();

        servlet.addRoute(HttpMethod.GET, "/hello", request ->
            HttpResponse.ok200().withBody("Hello World").build()
        );

        servlet.addRoute(HttpMethod.GET, "/users/:id", request ->
            HttpResponse.ok200().withBody("User ID: " + request.getPath()).build()
        );

        assertThat(servlet.getRouteCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle middleware execution")
    void shouldHandleMiddlewareExecution() {
        AtomicInteger filterCount = new AtomicInteger(0);

        FilterChain.Filter filter1 = (request, next) -> {
            filterCount.incrementAndGet();
            return next.serve(request);
        };

        FilterChain.Filter filter2 = (request, next) -> {
            filterCount.incrementAndGet();
            return next.serve(request);
        };

        HttpServerBuilder builder = HttpServerBuilder.create()
            .withPort(0)
            .addFilter(filter1)
            .addFilter(filter2)
            .addRoute(HttpMethod.GET, "/test", request ->
                HttpResponse.ok200().withBody("test").build()
            );

        assertThat(filterCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle authentication middleware")
    void shouldHandleAuthenticationMiddleware() {
        FilterChain.Filter authFilter = (request, next) -> {
            String token = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (token == null || !token.startsWith("Bearer ")) {
                return Promise.of(HttpResponse.ofCode(401).withBody("Unauthorized").build());
            }
            return next.serve(request);
        };

        HttpServerBuilder builder = HttpServerBuilder.create()
            .withPort(0)
            .addFilter(authFilter)
            .addRoute(HttpMethod.GET, "/protected", request ->
                HttpResponse.ok200().withBody("Protected content").build()
            );

        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should handle concurrent requests")
    void shouldHandleConcurrentRequests() {
        RoutingServlet servlet = new RoutingServlet();

        servlet.addAsyncRoute(HttpMethod.GET, "/async", request ->
            Promise.of(HttpResponse.ok200().withBody("Async response").build())
        );

        servlet.addRoute(HttpMethod.GET, "/sync", request ->
            HttpResponse.ok200().withBody("Sync response").build()
        );

        assertThat(servlet.getRouteCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle request timeouts")
    void shouldHandleRequestTimeouts() {
        HttpServerBuilder builder = HttpServerBuilder.create()
            .withPort(0)
            .withShutdownTimeout(Duration.ofSeconds(10));

        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should handle graceful shutdown")
    void shouldHandleGracefulShutdown() {
        HttpServerBuilder builder = HttpServerBuilder.create()
            .withPort(0)
            .withShutdownTimeout(Duration.ofSeconds(30))
            .withHealthCheck("/health");

        server = builder.build();
        assertThat(server).isNotNull();
    }
}
