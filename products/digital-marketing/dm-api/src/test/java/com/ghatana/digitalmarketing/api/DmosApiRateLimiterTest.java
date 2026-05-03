package com.ghatana.digitalmarketing.api;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosApiRateLimiter")
class DmosApiRateLimiterTest extends EventloopTestBase {

    @Test
    @DisplayName("bypasses rate limiting in Gradle/JUnit runtime")
    void shouldBypassRateLimitInTestRuntime() {
        DmosApiRateLimiter.setTestRuntimeOverride(true);

        try {
            AtomicInteger calls = new AtomicInteger();
            AsyncServlet delegate = request -> {
                calls.incrementAndGet();
                return Promise.of(HttpResponse.ok200().build());
            };

            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate);

            for (int i = 0; i < 80; i++) {
                HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns")
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-bypass")
                    .build();
                HttpResponse response = runPromise(() -> wrapped.serve(request));
                assertThat(response.getCode()).isEqualTo(200);
            }

            assertThat(calls.get()).isEqualTo(80);
        } finally {
            DmosApiRateLimiter.setTestRuntimeOverride(null);
        }
    }

    @Test
    @DisplayName("enforces limit when test bypass marker is absent")
    void shouldEnforceRateLimitOutsideTestMarker() {
        DmosApiRateLimiter.setTestRuntimeOverride(false);

        try {
            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate);
            String tenant = "tenant-" + UUID.randomUUID();

            for (int i = 0; i < 60; i++) {
                HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns")
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), tenant)
                    .build();
                HttpResponse response = runPromise(() -> wrapped.serve(request));
                assertThat(response.getCode()).isEqualTo(200);
            }

            HttpRequest throttled = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), tenant)
                .build();
            HttpResponse throttledResponse = runPromise(() -> wrapped.serve(throttled));
            assertThat(throttledResponse.getCode()).isEqualTo(429);
        } finally {
            DmosApiRateLimiter.setTestRuntimeOverride(null);
        }
    }

    @Test
    @DisplayName("resolveClientKey prefers tenant then forwarded-for then remote")
    void shouldResolveClientKeyInPriorityOrder() throws Exception {
        Method resolver = DmosApiRateLimiter.class.getDeclaredMethod("resolveClientKey", HttpRequest.class);
        resolver.setAccessible(true);

        HttpRequest tenantRequest = HttpRequest.get("http://localhost/v1/x")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), " tenant-42 ")
            .build();
        assertThat((String) resolver.invoke(null, tenantRequest)).isEqualTo("tenant:tenant-42");

        HttpRequest forwardedRequest = HttpRequest.get("http://localhost/v1/x")
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1, 10.0.0.2")
            .build();
        assertThat((String) resolver.invoke(null, forwardedRequest)).isEqualTo("ip:10.0.0.1");

        HttpRequest singleIpRequest = HttpRequest.get("http://localhost/v1/x")
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "192.168.1.1")
            .build();
        assertThat((String) resolver.invoke(null, singleIpRequest)).isEqualTo("ip:192.168.1.1");

        HttpRequest noHeadersRequest = HttpRequest.get("http://localhost/v1/x").build();
        assertThat((String) resolver.invoke(null, noHeadersRequest)).isEqualTo("ip:unknown");
    }
}
