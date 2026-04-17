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
@DisplayName("RequestObservationFilter tenant validation")
class RequestObservationFilterTenantValidationTest extends EventloopTestBase {

    private static final String BASE_URL = "http://localhost";
    private static final String ORIGIN = "http://localhost:3000";

    @Test
    @DisplayName("strict mode rejects missing tenant with 401 before delegate execution")
    void strictModeRejectsMissingTenant() {
        HttpHandlerSupport support = new HttpHandlerSupport(
            new ObjectMapper(),
            ORIGIN,
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,X-Tenant-Id",
            true
        );
        RequestObservationFilter filter = new RequestObservationFilter(support, DataCloudBusinessMetrics.noop(), null, 0.0);
        AtomicBoolean delegateCalled = new AtomicBoolean(false);
        AsyncServlet delegate = request -> {
            delegateCalled.set(true);
            return io.activej.promise.Promise.of(HttpResponse.ok200().build());
        };

        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders").build();

        HttpResponse response = runPromise(() -> filter.apply(delegate).serve(request));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(delegateCalled.get()).isFalse();
    }

    @Test
    @DisplayName("strict mode rejects malformed tenant with 400 before delegate execution")
    void strictModeRejectsMalformedTenant() {
        HttpHandlerSupport support = new HttpHandlerSupport(
            new ObjectMapper(),
            ORIGIN,
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,X-Tenant-Id",
            true
        );
        RequestObservationFilter filter = new RequestObservationFilter(support, DataCloudBusinessMetrics.noop(), null, 0.0);
        AtomicBoolean delegateCalled = new AtomicBoolean(false);
        AsyncServlet delegate = request -> {
            delegateCalled.set(true);
            return io.activej.promise.Promise.of(HttpResponse.ok200().build());
        };

        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant invalid")
            .build();

        HttpResponse response = runPromise(() -> filter.apply(delegate).serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(delegateCalled.get()).isFalse();
    }

    @Test
    @DisplayName("strict mode allows valid tenant for protected API routes")
    void strictModeAllowsValidTenant() {
        HttpHandlerSupport support = new HttpHandlerSupport(
            new ObjectMapper(),
            ORIGIN,
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,X-Tenant-Id",
            true
        );
        RequestObservationFilter filter = new RequestObservationFilter(support, DataCloudBusinessMetrics.noop(), null, 0.0);
        AtomicBoolean delegateCalled = new AtomicBoolean(false);
        AsyncServlet delegate = request -> {
            delegateCalled.set(true);
            return io.activej.promise.Promise.of(HttpResponse.ok200().build());
        };

        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-001")
            .build();

        HttpResponse response = runPromise(() -> filter.apply(delegate).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(delegateCalled.get()).isTrue();
    }
}