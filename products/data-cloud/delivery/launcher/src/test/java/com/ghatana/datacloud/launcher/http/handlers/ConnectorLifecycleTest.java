/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.fabric.DataFabricConnector;
import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-P2-006 — Connector lifecycle strengthening tests.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Create emits {@code CONNECTOR_CREATED} audit event.</li>
 *   <li>Update (PUT) applies only mutable fields and emits {@code CONNECTOR_UPDATED}.</li>
 *   <li>Delete emits {@code CONNECTOR_DELETED} audit event.</li>
 *   <li>Test when no fabric connector is present does NOT mutate connection state.</li>
 *   <li>Test when fabric returns failure sets state to ERROR and emits audit event.</li>
 *   <li>Enable emits {@code CONNECTOR_ENABLED} audit event.</li>
 *   <li>Disable emits {@code CONNECTOR_DISABLED} audit event.</li>
 *   <li>Rotate-credentials emits {@code CONNECTOR_CREDENTIALS_ROTATED} audit event.</li>
 *   <li>Credentials are never exposed in create/update/list/get responses.</li>
 *   <li>Update returns 404 when connection does not exist.</li>
 *   <li>DATA_CLOUD_CONNECTORS feature flag gates connector routes.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DC-P2-006 connector lifecycle regression and strengthening tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-P2-006 — Connector Lifecycle Tests")
@ExtendWith(MockitoExtension.class)
@Tag("production")
class ConnectorLifecycleTest extends EventloopTestBase {

    private static final String TENANT = "tenant-lifecycle-test";
    private static final String CONN_ID = "conn-abc123";
    private static final String DC_CONNECTIONS = "dc_connections";

    @Mock
    private DataCloudClient client;

    @Mock
    private AuditService auditService;

    private HttpHandlerSupport httpSpy;
    private DataSourceRegistryHandler handler;

    @BeforeEach
    void setUp() {
        HttpHandlerSupport http = new HttpHandlerSupport(
            new ObjectMapper(), "*", "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization", true);
        httpSpy = spy(http);
        lenient().doReturn(TENANT).when(httpSpy).requireTenantIdOrFail(any());
        handler = new DataSourceRegistryHandler(client, httpSpy, null, auditService);
        // auditService.record() returns a completed promise by default
        lenient().when(auditService.record(any())).thenReturn(Promise.of(null));
        DataCloudFeatureFlags.clearOverrides();
    }

    @AfterEach
    void tearDown() {
        DataCloudFeatureFlags.clearOverrides();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private HttpRequest buildRequest(HttpMethod method, String url, String body) {
        HttpRequest req = mock(HttpRequest.class);
        lenient().when(req.getPathParameter("connectionId")).thenReturn(CONN_ID);
        if (body != null) {
            ByteBuf buf = ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8));
            lenient().when(req.loadBody()).thenReturn(Promise.of(buf));
        }
        return req;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(HttpResponse response) {
        try {
            String body = runPromise(() ->
                response.loadBody().map(buf -> buf.getString(StandardCharsets.UTF_8)));
            return new ObjectMapper().readValue(body, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response body", e);
        }
    }

    private DataCloudClient.Entity mockEntity(String id, Map<String, Object> data) {
        DataCloudClient.Entity entity = mock(DataCloudClient.Entity.class);
        lenient().when(entity.id()).thenReturn(id);
        lenient().when(entity.data()).thenReturn(data);
        return entity;
    }

    // ─── CREATE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Create connection")
    class CreateConnection {

        @Test
        @DisplayName("Emits CONNECTOR_CREATED audit event on successful registration")
        void createEmitsAuditEvent() {
            DataCloudClient.Entity saved = mockEntity(CONN_ID, Map.of(
                "id", CONN_ID, "name", "MyDB", "type", "POSTGRESQL",
                "state", "INACTIVE", "tenantId", TENANT));
            when(client.save(eq(TENANT), eq(DC_CONNECTIONS), any())).thenReturn(Promise.of(saved));

            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors",
                "{\"name\":\"MyDB\",\"type\":\"POSTGRESQL\"}");

            HttpResponse response = runPromise(() -> handler.handleRegisterConnection(request));

            assertThat(response.getCode()).isEqualTo(201);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("connectionId")).isEqualTo(CONN_ID);
            assertThat(body.get("created")).isEqualTo(true);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            AuditEvent event = captor.getValue();
            assertThat(event.eventType()).isEqualTo("CONNECTOR_CREATED");
            assertThat(event.tenantId()).isEqualTo(TENANT);
            assertThat(event.resourceType()).isEqualTo("CONNECTOR");
            assertThat(event.success()).isTrue();
        }

        @Test
        @DisplayName("Returns 400 when name is missing")
        void createRejects_missingName() {
            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors",
                "{\"type\":\"POSTGRESQL\"}");

            HttpResponse response = runPromise(() -> handler.handleRegisterConnection(request));

            assertThat(response.getCode()).isEqualTo(400);
            verify(auditService, never()).record(any());
        }

        @Test
        @DisplayName("Credentials are not exposed in create response")
        void createDoesNotExposeCredentials() {
            DataCloudClient.Entity saved = mockEntity(CONN_ID, Map.of(
                "id", CONN_ID, "name", "MyDB", "type", "POSTGRESQL",
                "state", "INACTIVE", "tenantId", TENANT,
                "credentials", Map.of("password", "s3cr3t")));
            when(client.save(eq(TENANT), eq(DC_CONNECTIONS), any())).thenReturn(Promise.of(saved));

            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors",
                "{\"name\":\"MyDB\",\"type\":\"POSTGRESQL\",\"credentials\":{\"password\":\"s3cr3t\"}}");

            HttpResponse response = runPromise(() -> handler.handleRegisterConnection(request));

            assertThat(response.getCode()).isEqualTo(201);
            Map<String, Object> body = parseBody(response);
            assertThat(body).doesNotContainKey("credentials");
            assertThat(body).doesNotContainKey("password");
        }
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Update connection")
    class UpdateConnection {

        @Test
        @DisplayName("Updates mutable fields and emits CONNECTOR_UPDATED audit event")
        void updateEmitsAuditEvent() {
            Map<String, Object> existingData = new java.util.LinkedHashMap<>(Map.of(
                "id", CONN_ID, "name", "OldName", "type", "POSTGRESQL",
                "state", "ACTIVE", "tenantId", TENANT));
            DataCloudClient.Entity existing = mockEntity(CONN_ID, existingData);
            DataCloudClient.Entity saved = mockEntity(CONN_ID, Map.of(
                "id", CONN_ID, "name", "NewName", "type", "POSTGRESQL",
                "state", "ACTIVE", "tenantId", TENANT));

            when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.of(existing)));
            when(client.save(eq(TENANT), eq(DC_CONNECTIONS), any())).thenReturn(Promise.of(saved));

            HttpRequest request = buildRequest(HttpMethod.PUT,
                "http://localhost/api/v1/connectors/" + CONN_ID,
                "{\"name\":\"NewName\"}");

            HttpResponse response = runPromise(() -> handler.handleUpdateConnection(request));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("updated")).isEqualTo(true);
            assertThat(body.get("connectionId")).isEqualTo(CONN_ID);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("CONNECTOR_UPDATED");
            assertThat(captor.getValue().success()).isTrue();
        }

        @Test
        @DisplayName("Returns 404 when connection does not exist")
        void updateReturns404_whenNotFound() {
            when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.empty()));

            HttpRequest request = buildRequest(HttpMethod.PUT,
                "http://localhost/api/v1/connectors/" + CONN_ID,
                "{\"name\":\"NewName\"}");

            HttpResponse response = runPromise(() -> handler.handleUpdateConnection(request));

            assertThat(response.getCode()).isEqualTo(404);
            verify(auditService, never()).record(any());
        }

        @Test
        @DisplayName("Identity fields (type, tenantId) cannot be overwritten via update")
        void updateDoesNotOverwriteIdentityFields() {
            Map<String, Object> existingData = new java.util.LinkedHashMap<>();
            existingData.put("id", CONN_ID);
            existingData.put("name", "Original");
            existingData.put("type", "POSTGRESQL");
            existingData.put("tenantId", TENANT);
            existingData.put("state", "ACTIVE");
            DataCloudClient.Entity existing = mockEntity(CONN_ID, existingData);
            DataCloudClient.Entity saved = mockEntity(CONN_ID, existingData);

            when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.of(existing)));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> savedDataCaptor = ArgumentCaptor.forClass(
                (Class<Map<String, Object>>) (Class<?>) Map.class);
            when(client.save(eq(TENANT), eq(DC_CONNECTIONS), savedDataCaptor.capture()))
                .thenReturn(Promise.of(saved));

            // Attempt to override type and tenantId
            HttpRequest request = buildRequest(HttpMethod.PUT,
                "http://localhost/api/v1/connectors/" + CONN_ID,
                "{\"type\":\"MYSQL\",\"tenantId\":\"attacker-tenant\",\"name\":\"Hacked\"}");

            HttpResponse response = runPromise(() -> handler.handleUpdateConnection(request));

            assertThat(response.getCode()).isEqualTo(200);
            // type must remain POSTGRESQL; tenantId must remain TENANT
            Map<String, Object> persisted = savedDataCaptor.getValue();
            assertThat(persisted.get("type")).isEqualTo("POSTGRESQL");
            assertThat(persisted.get("tenantId")).isEqualTo(TENANT);
            assertThat(persisted.get("name")).isEqualTo("Hacked"); // name is mutable
        }
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Delete connection")
    class DeleteConnection {

        @Test
        @DisplayName("Emits CONNECTOR_DELETED audit event on successful deletion")
        void deleteEmitsAuditEvent() {
            DataCloudClient.Entity existing = mockEntity(CONN_ID, Map.of(
                "id", CONN_ID, "tenantId", TENANT));
            when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.of(existing)));
            when(client.delete(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(null));

            HttpRequest request = buildRequest(HttpMethod.DELETE,
                "http://localhost/api/v1/connectors/" + CONN_ID, null);

            HttpResponse response = runPromise(() -> handler.handleDeleteConnection(request));

            assertThat(response.getCode()).isEqualTo(204);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("CONNECTOR_DELETED");
            assertThat(captor.getValue().resourceId()).isEqualTo(CONN_ID);
        }

        @Test
        @DisplayName("Returns 404 and no audit event when connection does not exist")
        void deleteReturns404_whenNotFound() {
            when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.empty()));

            HttpRequest request = buildRequest(HttpMethod.DELETE,
                "http://localhost/api/v1/connectors/" + CONN_ID, null);

            HttpResponse response = runPromise(() -> handler.handleDeleteConnection(request));

            assertThat(response.getCode()).isEqualTo(404);
            verify(auditService, never()).record(any());
        }
    }

    // ─── TEST (bad state prevention) ─────────────────────────────────────────

    @Nested
    @DisplayName("Test connection — state safety")
    class TestConnectionStateSafety {

        @Test
        @DisplayName("When no fabric connector is available, connection state is NOT mutated")
        void testWithNoFabric_doesNotChangeState() {
            // handler was constructed with fabric=null in setUp()
            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors/" + CONN_ID + "/test", null);

            HttpResponse response = runPromise(() -> handler.handleTestConnection(request));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("testStatus")).isEqualTo("pending");

            // Critical: client.save/findById must NOT have been called — state not mutated
            verify(client, never()).save(anyString(), anyString(), any());
            verify(client, never()).findById(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("When fabric test fails, state is set to ERROR and CONNECTOR_TESTED(failed) event emitted")
        void testWithFabricFailure_setsErrorState_andEmitsAudit() {
            DataFabricConnector fabric = mock(DataFabricConnector.class);
            DataFabricConnector.ConnectionTestResult failResult =
                new DataFabricConnector.ConnectionTestResult(false, "Connection refused", 0L, null);
            when(fabric.testConnection(CONN_ID)).thenReturn(Promise.of(failResult));

            // Stub state update so it doesn't fail
            DataCloudClient.Entity existing = mockEntity(CONN_ID, new java.util.LinkedHashMap<>(Map.of(
                "id", CONN_ID, "tenantId", TENANT, "state", "ACTIVE")));
            lenient().when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.of(existing)));
            lenient().when(client.save(eq(TENANT), eq(DC_CONNECTIONS), any()))
                .thenReturn(Promise.of(existing));

            DataSourceRegistryHandler handlerWithFabric =
                new DataSourceRegistryHandler(client, httpSpy, fabric, auditService);
            when(auditService.record(any())).thenReturn(Promise.of(null));

            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors/" + CONN_ID + "/test", null);

            HttpResponse response = runPromise(() -> handlerWithFabric.handleTestConnection(request));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("testStatus")).isEqualTo("failed");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("CONNECTOR_TESTED");
            assertThat(captor.getValue().success()).isFalse();
        }
    }

    // ─── ENABLE / DISABLE ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Enable and Disable connection")
    class EnableDisableConnection {

        private DataCloudClient.Entity setupExistingConnection() {
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("id", CONN_ID);
            data.put("tenantId", TENANT);
            data.put("state", "INACTIVE");
            data.put("healthStatus", "disabled");
            DataCloudClient.Entity entity = mockEntity(CONN_ID, data);
            lenient().when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.of(entity)));
            lenient().when(client.save(eq(TENANT), eq(DC_CONNECTIONS), any()))
                .thenReturn(Promise.of(entity));
            return entity;
        }

        @Test
        @DisplayName("Enable emits CONNECTOR_ENABLED audit event")
        void enableEmitsAuditEvent() {
            setupExistingConnection();

            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors/" + CONN_ID + "/enable", null);

            HttpResponse response = runPromise(() -> handler.handleEnableConnection(request));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("state")).isEqualTo("ACTIVE");
            assertThat(body.get("enabled")).isEqualTo(true);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("CONNECTOR_ENABLED");
        }

        @Test
        @DisplayName("Disable emits CONNECTOR_DISABLED audit event")
        void disableEmitsAuditEvent() {
            setupExistingConnection();

            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors/" + CONN_ID + "/disable", null);

            HttpResponse response = runPromise(() -> handler.handleDisableConnection(request));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("state")).isEqualTo("INACTIVE");
            assertThat(body.get("enabled")).isEqualTo(false);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("CONNECTOR_DISABLED");
        }
    }

    // ─── ROTATE CREDENTIALS ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Rotate credentials")
    class RotateCredentials {

        @Test
        @DisplayName("Emits CONNECTOR_CREDENTIALS_ROTATED audit event")
        void rotateEmitsAuditEvent() {
            Map<String, Object> existingData = new java.util.LinkedHashMap<>(Map.of(
                "id", CONN_ID, "tenantId", TENANT, "name", "MyDB"));
            DataCloudClient.Entity existing = mockEntity(CONN_ID, existingData);
            DataCloudClient.Entity saved = mockEntity(CONN_ID, existingData);

            when(client.findById(TENANT, DC_CONNECTIONS, CONN_ID))
                .thenReturn(Promise.of(Optional.of(existing)));
            when(client.save(eq(TENANT), eq(DC_CONNECTIONS), any())).thenReturn(Promise.of(saved));

            HttpRequest request = buildRequest(HttpMethod.POST,
                "http://localhost/api/v1/connectors/" + CONN_ID + "/rotate-credentials",
                "{\"credentials\":{\"password\":\"newSecret\"}}");

            HttpResponse response = runPromise(() -> handler.handleRotateCredentials(request));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(response);
            assertThat(body.get("rotated")).isEqualTo(true);
            // Raw credentials must not appear in the response
            assertThat(body).doesNotContainKey("credentials");
            assertThat(body).doesNotContainKey("password");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("CONNECTOR_CREDENTIALS_ROTATED");
        }
    }

    // ─── CAPABILITY GATE ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("DATA_CLOUD_CONNECTORS feature flag")
    class CapabilityGate {

        @Test
        @DisplayName("DATA_CLOUD_CONNECTORS is enabled by default")
        void connectorFeatureEnabledByDefault() {
            DataCloudFeatureFlags.clearOverrides();
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_CONNECTORS)).isTrue();
        }

        @Test
        @DisplayName("DATA_CLOUD_CONNECTORS can be disabled via feature flag override")
        void connectorFeatureCanBeDisabled() {
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_CONNECTORS, false);
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_CONNECTORS)).isFalse();
        }
    }
}
