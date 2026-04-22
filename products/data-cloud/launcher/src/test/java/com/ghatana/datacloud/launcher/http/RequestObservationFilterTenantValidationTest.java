package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies strict tenant validation happens at the outer request observation boundary
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RequestObservationFilter tenant validation [GH-90000]")
class RequestObservationFilterTenantValidationTest extends EventloopTestBase {

    private static final String BASE_URL = "http://localhost";
    private static final String ORIGIN = "http://localhost:3000";

    @Test
    @DisplayName("strict mode rejects missing tenant with 401 before delegate execution [GH-90000]")
    void strictModeRejectsMissingTenant() { // GH-90000
        HttpHandlerSupport support = new HttpHandlerSupport( // GH-90000
            new ObjectMapper(), // GH-90000
            ORIGIN,
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,X-Tenant-Id",
            true
        );
        RequestObservationFilter filter = new RequestObservationFilter(support, DataCloudBusinessMetrics.noop(), null, 0.0); // GH-90000
        AtomicBoolean delegateCalled = new AtomicBoolean(false); // GH-90000
        AsyncServlet delegate = request -> {
            delegateCalled.set(true); // GH-90000
            return io.activej.promise.Promise.of(support.jsonResponse(java.util.Map.of("status", "ok"))); // GH-90000
        };

        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders").build(); // GH-90000

        HttpResponse response = runPromise(() -> filter.apply(delegate).serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        assertThat(delegateCalled.get()).isFalse(); // GH-90000
        assertThat(response.getHeader(HttpHeaders.of("X-Request-ID [GH-90000]"))).isNotBlank();
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"))).isNotBlank();
        assertThat(response.getHeader(HttpHeaders.of("traceparent [GH-90000]"))).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01] [GH-90000]");
    }

    @Test
    @DisplayName("strict mode rejects malformed tenant with 400 before delegate execution [GH-90000]")
    void strictModeRejectsMalformedTenant() { // GH-90000
        HttpHandlerSupport support = new HttpHandlerSupport( // GH-90000
            new ObjectMapper(), // GH-90000
            ORIGIN,
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,X-Tenant-Id",
            true
        );
        RequestObservationFilter filter = new RequestObservationFilter(support, DataCloudBusinessMetrics.noop(), null, 0.0); // GH-90000
        AtomicBoolean delegateCalled = new AtomicBoolean(false); // GH-90000
        AsyncServlet delegate = request -> {
            delegateCalled.set(true); // GH-90000
            return io.activej.promise.Promise.of(support.jsonResponse(java.util.Map.of("status", "ok"))); // GH-90000
        };

        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders") // GH-90000
            .withHeader(HttpHeaders.of("X-Tenant-Id [GH-90000]"), "tenant invalid")
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> filter.apply(delegate).serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        assertThat(delegateCalled.get()).isFalse(); // GH-90000
        assertThat(response.getHeader(HttpHeaders.of("X-Request-ID [GH-90000]"))).isNotBlank();
        assertThat(response.getHeader(HttpHeaders.of("traceparent [GH-90000]"))).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01] [GH-90000]");
    }

    @Test
    @DisplayName("strict mode allows valid tenant for protected API routes [GH-90000]")
    void strictModeAllowsValidTenant() { // GH-90000
        HttpHandlerSupport support = new HttpHandlerSupport( // GH-90000
            new ObjectMapper(), // GH-90000
            ORIGIN,
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,X-Tenant-Id",
            true
        );
        RequestObservationFilter filter = new RequestObservationFilter(support, DataCloudBusinessMetrics.noop(), null, 0.0); // GH-90000
        AtomicBoolean delegateCalled = new AtomicBoolean(false); // GH-90000
        AsyncServlet delegate = request -> {
            delegateCalled.set(true); // GH-90000
            return io.activej.promise.Promise.of(support.jsonResponse(java.util.Map.of("status", "ok"))); // GH-90000
        };

        HttpRequest requestWithHeaders = HttpRequest.get(BASE_URL + "/api/v1/entities/orders") // GH-90000
            .withHeader(HttpHeaders.of("X-Tenant-Id [GH-90000]"), "tenant-001")
            .withHeader(HttpHeaders.of("X-Request-Id [GH-90000]"), "req-dc-001")
            .withHeader(HttpHeaders.of("traceparent [GH-90000]"), "00-0123456789abcdef0123456789abcdef-1111222233334444-01")
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> filter.apply(delegate).serve(requestWithHeaders)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getHeader(HttpHeaders.of("X-Request-ID [GH-90000]"))).isEqualTo("req-dc-001 [GH-90000]");
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"))).isEqualTo("req-dc-001 [GH-90000]");
        assertThat(response.getHeader(HttpHeaders.of("traceparent [GH-90000]"))).startsWith("00-0123456789abcdef0123456789abcdef- [GH-90000]");
        assertThat(delegateCalled.get()).isTrue(); // GH-90000
    }
}