package com.ghatana.pipeline.registry.web;

import com.ghatana.pipeline.registry.model.ConnectorInstance;
import com.ghatana.pipeline.registry.service.ConnectorAdminService;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConnectorAdminController}.
 *
 * @doc.type test
 * @doc.purpose Verify HTTP responses for connector admin CRUD endpoints
 * @doc.layer product
 * @doc.pattern ControllerTest
 */
@DisplayName("ConnectorAdminController tests")
@ExtendWith(MockitoExtension.class)
class ConnectorAdminControllerTest extends EventloopTestBase {

    @Mock
    private ConnectorAdminService connectorAdminService;

    private ConnectorAdminController controller;
    private static final TenantId TENANT = TenantId.of("tenant-1");

    @BeforeEach
    void setUp() {
        controller = new ConnectorAdminController(connectorAdminService);
    }

    @Test
    @DisplayName("listConnectors: returns 200 with connector list")
    void listConnectors_returns200() {
        ConnectorInstance conn = ConnectorInstance.builder()
                .id("conn-1")
                .name("my-kafka")
                .type("KAFKA")
                .build();
        when(connectorAdminService.listByTenant(TENANT)).thenReturn(List.of(conn));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/connectors").build();
        HttpResponse response = runPromise(() -> controller.listConnectors(request, TENANT, "user-1"));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("getConnector: found → 200 OK")
    void getConnector_found_returns200() {
        ConnectorInstance conn = ConnectorInstance.builder()
                .id("conn-1")
                .name("my-kafka")
                .type("KAFKA")
                .build();
        when(connectorAdminService.get("conn-1")).thenReturn(Optional.of(conn));

        HttpResponse response = runPromise(() -> controller.getConnector(TENANT, "user-1", "conn-1"));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("getConnector: not found → 404 Not Found")
    void getConnector_notFound_returns404() {
        when(connectorAdminService.get("missing")).thenReturn(Optional.empty());

        HttpResponse response = runPromise(() -> controller.getConnector(TENANT, "user-1", "missing"));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("createConnector: valid body → 201 Created")
    void createConnector_valid_returns201() {
        ConnectorInstance created = ConnectorInstance.builder()
                .id("conn-2")
                .name("new-connector")
                .type("RABBITMQ")
                .build();
        when(connectorAdminService.create(any(ConnectorInstance.class))).thenReturn(created);

        String json = "{\"name\":\"new-connector\",\"type\":\"RABBITMQ\",\"templateId\":\"tmpl-1\",\"config\":{}}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/connectors")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> controller.createConnector(request, TENANT, "user-1"));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("deleteConnector: exists → 204 No Content")
    void deleteConnector_returns204() {
        when(connectorAdminService.delete("conn-1")).thenReturn(true);

        HttpResponse response = runPromise(() -> controller.deleteConnector(TENANT, "user-1", "conn-1"));

        assertThat(response.getCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("deleteConnector: not found → 404 Not Found")
    void deleteConnector_notFound_returns404() {
        when(connectorAdminService.delete("missing")).thenReturn(false);

        HttpResponse response = runPromise(() -> controller.deleteConnector(TENANT, "user-1", "missing"));

        assertThat(response.getCode()).isEqualTo(404);
    }
}
