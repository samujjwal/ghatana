package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsConnectorReadiness;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsConnectorReadinessService;
import com.ghatana.digitalmarketing.connector.googleads.GoogleAdsConnectorReadinessState;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * @doc.type class
 * @doc.purpose Contract tests for DMOS connector readiness HTTP surface
 * @doc.layer product
 * @doc.pattern Controller Test
 */
@DisplayName("DmosConnectorReadinessServlet")
class DmosConnectorReadinessServletTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private FakeReadinessService readinessService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        readinessService = new FakeReadinessService();
        servlet = new DmosConnectorReadinessServlet(readinessService, Eventloop.create()).getServlet();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosConnectorReadinessServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosConnectorReadinessServlet(readinessService, null));
    }

    @Test
    @DisplayName("GET /connectors/google-ads readiness returns runtime truth")
    void shouldReturnGoogleAdsReadiness() throws Exception {
        HttpResponse response = dispatch();

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(bodyString(response));
        assertThat(body.path("connectorId").asText()).isEqualTo("conn-1");
        assertThat(body.path("provider").asText()).isEqualTo("google-ads");
        assertThat(body.path("readinessState").asText()).isEqualTo("READY");
        assertThat(body.path("connectorStatus").asText()).isEqualTo("ACTIVE");
        assertThat(body.path("ready").asBoolean()).isTrue();
        assertThat(body.path("source").asText()).isEqualTo("DMOS_CONNECTOR_RUNTIME");
        assertThat(readinessService.lastConnectorId).isEqualTo("conn-1");
        assertThat(readinessService.lastContext.tenantId()).isEqualTo("tenant-1");
        assertThat(readinessService.lastContext.getWorkspaceId().getValue()).isEqualTo("ws-1");
    }

    @Test
    @DisplayName("GET /connectors/google-ads readiness requires tenant")
    void shouldRejectMissingTenant() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/connectors/google-ads/conn-1/readiness")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer test-token")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /connectors/google-ads readiness maps authorization denial")
    void shouldMapAuthorizationDenial() {
        readinessService.error = new SecurityException("denied");

        HttpResponse response = dispatch();

        assertThat(response.getCode()).isEqualTo(403);
    }

    private HttpResponse dispatch() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/connectors/google-ads/conn-1/readiness")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer test-token")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Permissions"), "dmos.connectors")
            .build();
        return runPromise(() -> servlet.serve(request));
    }

    private String bodyString(HttpResponse response) throws Exception {
        byte[] bytes = runPromise(response::loadBody).asArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class FakeReadinessService implements DmGoogleAdsConnectorReadinessService {
        private DmOperationContext lastContext;
        private String lastConnectorId;
        private RuntimeException error;

        @Override
        public Promise<DmGoogleAdsConnectorReadiness> checkReadiness(DmOperationContext ctx, String connectorId) {
            lastContext = ctx;
            lastConnectorId = connectorId;
            if (error != null) {
                return Promise.ofException(error);
            }
            return Promise.of(new DmGoogleAdsConnectorReadiness(
                connectorId,
                GoogleAdsConnectorReadinessState.READY,
                DmConnectorStatus.ACTIVE,
                "operational",
                Instant.parse("2026-05-25T12:00:00Z")
            ));
        }
    }
}
