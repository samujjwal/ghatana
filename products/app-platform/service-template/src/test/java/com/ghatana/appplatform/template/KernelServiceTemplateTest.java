package com.ghatana.appplatform.template;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link KernelServiceTemplate} — TC-P0-011 through TC-P0-018.
 *
 * <p>Validates that the template service:
 * <ul>
 *   <li>Starts and binds a port (TC-P0-011)</li>
 *   <li>Responds {@code 200 OK} on {@code GET /health} (TC-P0-012)</li>
 *   <li>Responds {@code 200 OK} on {@code GET /ready} when ready (TC-P0-013)</li>
 *   <li>Responds {@code 503} on {@code GET /ready} when dependencies are down (TC-P0-014)</li>
 *   <li>Responds {@code 404} on unknown routes (TC-P0-015)</li>
 *   <li>Health response contains {@code status: UP} JSON (TC-P0-016)</li>
 *   <li>Service stops cleanly (TC-P0-017)</li>
 *   <li>Re-start after stop works (TC-P0-018)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for the kernel service template (TC-P0-011..018)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("KernelServiceTemplate Tests (TC-P0-011 to TC-P0-018)")
class KernelServiceTemplateTest extends EventloopTestBase {

    private KernelServiceTemplate service;

    @BeforeEach
    void setUp() throws Exception {
        // Port 0 — OS assigns a free ephemeral port
        service = new KernelServiceTemplate(getEventloop(), "test-service", 0);
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    @Test
    @DisplayName("TC-P0-011: Service binds to a port after start()")
    void serviceBindsPort() {
        assertThat(service.getPort()).isPositive();
    }

    @Test
    @DisplayName("TC-P0-012: GET /health returns 200 OK")
    void healthReturns200() {
        int status = runPromise(() -> httpGet("/health").map(HttpResponse::getCode));
        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-P0-013: GET /ready returns 200 when readiness check passes")
    void readyReturns200WhenReady() {
        int status = runPromise(() -> httpGet("/ready").map(HttpResponse::getCode));
        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-P0-014: GET /ready returns 503 when readiness check fails")
    void readyReturns503WhenUnhealthy() throws Exception {
        service.stop();
        // Re-create with a failing readiness check
        service = new KernelServiceTemplate(
                getEventloop(), "failing-service", 0,
                () -> io.activej.promise.Promise.of(false));
        service.start();

        int status = runPromise(() -> httpGet("/ready").map(HttpResponse::getCode));
        assertThat(status).isEqualTo(503);
    }

    @Test
    @DisplayName("TC-P0-015: Unknown route returns 404")
    void unknownRouteReturns404() {
        int status = runPromise(() -> httpGet("/unknown").map(HttpResponse::getCode));
        assertThat(status).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-P0-016: Health response contains status:UP JSON")
    void healthResponseContainsStatusUp() {
        String body = runPromise(() ->
                httpGet("/health").map(r -> new String(r.getBody())));
        assertThat(body).contains("\"status\":\"UP\"");
        assertThat(body).contains("\"service\":\"test-service\"");
    }

    @Test
    @DisplayName("TC-P0-017: Service stops cleanly without error")
    void serviceStopsCleanly() {
        service.stop(); // should not throw
        assertThat(true).isTrue(); // stop completed without exception
    }

    @Test
    @DisplayName("TC-P0-018: Re-start after stop works")
    void restartAfterStopWorks() throws Exception {
        service.stop();
        service = new KernelServiceTemplate(getEventloop(), "restarted-service", 0);
        service.start();

        int status = runPromise(() -> httpGet("/health").map(HttpResponse::getCode));
        assertThat(status).isEqualTo(200);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private io.activej.promise.Promise<HttpResponse> httpGet(String path) {
        HttpClient client = HttpClient.create(getEventloop());
        String url = "http://localhost:" + service.getPort() + path;
        return client.request(HttpRequest.get(url).build());
    }
}
