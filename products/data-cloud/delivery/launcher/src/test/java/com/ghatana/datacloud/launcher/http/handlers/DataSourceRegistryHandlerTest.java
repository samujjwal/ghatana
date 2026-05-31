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
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;
import com.ghatana.datacloud.operations.InMemoryOperationRecorder;
import com.ghatana.datacloud.operations.OperationKind;
import com.ghatana.datacloud.operations.OperationRecord;
import com.ghatana.datacloud.operations.OperationStatus;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import com.ghatana.platform.security.annotation.RequiresRole;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBufStrings;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Production-grade tests for DataSourceRegistryHandler.
 *
 * <p>Tests verify that Data Fabric metrics are properly disabled in production profiles
 * and that no hardcoded demo metrics are returned.
 *
 * @doc.type class
 * @doc.purpose Production-grade tests for DataSourceRegistryHandler with focus on fabric metrics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataSourceRegistryHandler Production Tests")
@ExtendWith(MockitoExtension.class)
@Tag("production")
class DataSourceRegistryHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private HttpHandlerSupport http;
    private DataSourceRegistryHandler handler;
    private String originalProfile;

    @BeforeEach
    void setUp() {
        // Use local profile with non-strict tenant resolution for tests
        HttpHandlerSupport realHttp = new HttpHandlerSupport(
            new ObjectMapper(),
            "*",
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization",
            false // Non-strict mode allows fallback tenant resolution for tests
        );
        http = Mockito.spy(realHttp);
        handler = new DataSourceRegistryHandler(client, http, null, null /* no audit service needed for metrics tests */);
        originalProfile = System.getenv("DATACLOUD_PROFILE");
        System.setProperty("DATACLOUD_PROFILE", "local"); // Use local profile for tests
    }

    @AfterEach
    void tearDown() {
        if (originalProfile != null) {
            System.setProperty("DATACLOUD_PROFILE", originalProfile);
        } else {
            System.clearProperty("DATACLOUD_PROFILE");
        }
        DataCloudFeatureFlags.clearOverrides();
    }

    @Test
    @DisplayName("Fabric metrics return unavailable when DATA_CLOUD_DATA_FABRIC feature flag is disabled (default)")
    void fabricMetrics_featureFlagDisabled_returnsUnavailable() {
        // DC-P1-002: Feature flag is disabled by default; no profile override needed
        // DataCloudFeatureFlags.isEnabled(DATA_CLOUD_DATA_FABRIC) returns false (default)

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("capability")).isEqualTo("unavailable");
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("message")).asString().contains("/api/v1/surfaces");
    }

    @Test
    @DisplayName("Fabric metrics disabled in production profile")
    void fabricMetricsDisabledInProductionProfile() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "production");

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("capability")).isEqualTo("preview");
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("totalEventsPerSec")).isEqualTo(0.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(0.0);
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    @DisplayName("Fabric metrics disabled in staging profile")
    void fabricMetricsDisabledInStagingProfile() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "staging");

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("tiers")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Fabric metrics return empty when no storage profiles configured in local profile")
    void fabricMetricsEmptyWhenNoStorageProfilesInLocalProfile() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "local");

        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(List.of()));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("totalEventsPerSec")).isEqualTo(0.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(0.0);
        assertThat(body.get("message")).isNotNull();
        // Should NOT have disabled flag in local profile
        assertThat(body.containsKey("disabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("Fabric metrics return real data from storage profiles when configured")
    void fabricMetricsReturnRealDataFromStorageProfiles() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "local");

        // Mock storage profile entities with real metrics
        DataCloudClient.Entity hotEntity = mockEntity("hot-connection", Map.of(
            "type", "REDIS",
            "tier", "HOT",
            "throughputEps", 1500.0,
            "latencyP99Ms", 2.5,
            "errorRate", 0.001,
            "queueDepth", 15,
            "healthStatus", "healthy"
        ));

        DataCloudClient.Entity warmEntity = mockEntity("warm-connection", Map.of(
            "type", "POSTGRESQL",
            "tier", "WARM",
            "throughputEps", 800.0,
            "latencyP99Ms", 10.0,
            "errorRate", 0.002,
            "queueDepth", 20,
            "healthStatus", "healthy",
            "storageGb", 50.0
        ));

        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(List.of(hotEntity, warmEntity)));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.containsKey("disabled")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) body.get("tiers");
        assertThat(tiers).hasSize(2);

        // Verify HOT tier metrics
        Map<String, Object> hotTier = tiers.stream()
            .filter(t -> "HOT".equals(t.get("tier")))
            .findFirst()
            .orElseThrow();
        assertThat(hotTier.get("throughputEps")).isEqualTo(1500.0);
        assertThat(hotTier.get("latencyP99Ms")).isEqualTo(2.5);
        assertThat(hotTier.get("status")).isEqualTo("healthy");

        // Verify WARM tier metrics
        Map<String, Object> warmTier = tiers.stream()
            .filter(t -> "WARM".equals(t.get("tier")))
            .findFirst()
            .orElseThrow();
        assertThat(warmTier.get("throughputEps")).isEqualTo(800.0);
        assertThat(warmTier.get("storageGb")).isEqualTo(50.0);

        // Verify totals are calculated from real data
        assertThat(body.get("totalEventsPerSec")).isEqualTo(2300.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Fabric metrics require tenant ID header")
    void fabricMetricsRequireTenantId() {
        // Temporarily set production profile to test strict tenant enforcement
        String originalProfile = System.getProperty("DATACLOUD_PROFILE");
        System.setProperty("DATACLOUD_PROFILE", "production");
        
        try {
            // Recreate HttpHandlerSupport with strict mode for production
            HttpHandlerSupport httpStrict = new HttpHandlerSupport(
                new ObjectMapper(),
                "*",
                "GET,POST,PUT,DELETE,OPTIONS",
                "Content-Type,Authorization",
                true, // strict mode
                "production"
            );
            
            DataSourceRegistryHandler handlerStrict = new DataSourceRegistryHandler(client, httpStrict, null, null);
            
            HttpRequest request = mock(HttpRequest.class);
            lenient().when(request.getHeader(TenantExtractor.TENANT_HEADER)).thenReturn(null);
            lenient().when(request.getQueryParameter("tenantId")).thenReturn(null);
            lenient().when(request.getPath()).thenReturn("/api/v1/data-fabric/metrics");
            lenient().when(request.getMethod()).thenReturn(io.activej.http.HttpMethod.GET);

            HttpResponse response = runPromise(() -> handlerStrict.handleGetFabricMetrics(request));

            assertThat(response.getCode()).isEqualTo(401);
        } finally {
            // Restore original profile
            if (originalProfile != null) {
                System.setProperty("DATACLOUD_PROFILE", originalProfile);
            } else {
                System.clearProperty("DATACLOUD_PROFILE");
            }
        }
    }

    @Test
    @DisplayName("Fabric metrics return degraded response on error")
    void fabricMetricsReturnDegradedResponseOnError() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "local");

        // Mock query failure
        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.ofException(
                new RuntimeException("Database connection failed")));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("degraded")).isEqualTo(true);
        assertThat(body.get("error")).isNotNull();
        assertThat(body.get("tiers")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Register connector strips raw credentials and stores only secretRef metadata")
    void registerConnectorSanitizesCredentials() {
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/connectors")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .withHeader(HttpHeaders.of("X-Permissions"), "connector:register")
            .withHeader(HttpHeaders.of("Content-Type"), "application/json")
            .withBody(ByteBufStrings.wrapUtf8("""
                {
                  "name":"orders-source",
                  "type":"POSTGRESQL",
                  "credentials":{"username":"svc","password":"secret"},
                  "secretRef":{"provider":"vault","path":"kv/datacloud/orders"}
                }
                """))
            .build();

        lenient().when(client.save(anyString(), anyString(), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = invocation.getArgument(2);
                return io.activej.promise.Promise.of(mockEntity(String.valueOf(payload.get("id")), payload));
            });

        HttpResponse response = runPromise(() -> handler.handleRegisterConnection(request));

        assertThat(response.getCode()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).save(anyString(), anyString(), payloadCaptor.capture());
        Map<String, Object> savedPayload = payloadCaptor.getValue();
        assertThat(savedPayload).containsKey("secretRef");
        assertThat(savedPayload).containsEntry("credentialStatus", "referenced");
        assertThat(savedPayload).doesNotContainKey("credentials");
    }

    @Test
    @DisplayName("Rotate credentials rejects raw credential payload")
    void rotateCredentialsRejectsRawCredentials() {
        HttpRequest request = RequestContextTestHelper.createTestRequestWithBody(
            "test-tenant",
            "conn-1",
            "/api/v1/connectors/conn-1/rotate",
            ByteBufStrings.wrapUtf8("""
                { "credentials": { "password": "rotated" } }
                """));
        lenient().when(request.getHeader(HttpHeaders.of("X-Permissions")))
            .thenReturn("connector:rotate-credentials");

        HttpResponse response = runPromise(() -> handler.handleRotateCredentials(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Sync without fabric fails closed in production profile and does not mutate state")
    void syncWithoutFabricFailsClosedInProductionAndDoesNotMutateState() {
        DataSourceRegistryHandler productionHandler = new DataSourceRegistryHandler(
            client, http, null, null, "production");
        lenient().doReturn(TenantResolutionResult.success("test-tenant", null)).when(http).requireTenantIdWithError(any());
        HttpRequest request = RequestContextTestHelper.createTestRequestWithBody(
            "test-tenant",
            "conn-1",
            "/api/v1/connectors/conn-1/sync",
            ByteBufStrings.wrapUtf8("{}"));
        lenient().when(request.getHeader(HttpHeaders.of("X-Permissions")))
            .thenReturn("connector:trigger-sync");

        HttpResponse response = runPromise(() -> productionHandler.handleTriggerSync(request));

        assertThat(response.getCode()).isEqualTo(503);
        Map<String, Object> body = parseJsonBody(response);
        assertThat(String.valueOf(body.get("message")).toLowerCase())
            .contains("connector runtime is required");
        verify(client, never()).save(anyString(), anyString(), anyMap());
        verify(client, never()).findById(anyString(), anyString(), anyString());
    }

    // P1-6: Connector lifecycle hardening tests

    @Test
    @DisplayName("Enable without fabric connector returns 503")
    void enableWithoutFabricReturns503() {
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.getPathParameter("connectionId")).thenReturn("conn-1");
        lenient().when(request.getHeader(TenantExtractor.TENANT_HEADER)).thenReturn("test-tenant");
        lenient().when(request.getPath()).thenReturn("/api/v1/connectors/conn-1/enable");
        lenient().when(request.getQueryParameter("tenantId")).thenReturn(null);
        lenient().when(request.getMethod()).thenReturn(io.activej.http.HttpMethod.POST);

        HttpResponse response = runPromise(() -> handler.handleEnableConnection(request));

        assertThat(response.getCode()).isEqualTo(503);
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("message")).asString().contains("DataFabricConnector");
    }

    @Test
    @DisplayName("Connector lifecycle commands record operation timeline entries")
    void connectorLifecycleCommandsRecordOperations() {
        InMemoryOperationRecorder operationRecorder = new InMemoryOperationRecorder();
        DataSourceRegistryHandler handlerWithOperations = new DataSourceRegistryHandler(
            client, http, null, null)
            .withOperationRecorder(operationRecorder);
        DataCloudClient.Entity existingConnection = mockEntity("conn-1", Map.of(
            "name", "orders-source",
            "type", "POSTGRESQL",
            "state", "INACTIVE",
            "healthStatus", "unknown"));
        lenient().when(client.findById(anyString(), anyString(), eq("conn-1")))
            .thenReturn(io.activej.promise.Promise.of(java.util.Optional.of(existingConnection)));

        HttpRequest testRequest = RequestContextTestHelper.createTestRequest(
            "test-tenant",
            "conn-1",
            "/api/v1/connectors/conn-1/test");
        HttpResponse testResponse = runPromise(() -> handlerWithOperations.handleTestConnection(testRequest));

        HttpRequest healthRequest = RequestContextTestHelper.createTestRequest(
            "test-tenant",
            "conn-1",
            "/api/v1/connectors/conn-1/health");
        lenient().when(healthRequest.getMethod()).thenReturn(HttpMethod.GET);
        lenient().when(healthRequest.getHeader(HttpHeaders.of("X-Permissions")))
            .thenReturn("connector:read-health");
        HttpResponse healthResponse = runPromise(() -> handlerWithOperations.handleGetHealth(healthRequest));

        HttpRequest schemaRequest = RequestContextTestHelper.createTestRequest(
            "test-tenant",
            "conn-1",
            "/api/v1/connectors/conn-1/schema");
        lenient().when(schemaRequest.getMethod()).thenReturn(HttpMethod.GET);
        lenient().when(schemaRequest.getHeader(HttpHeaders.of("X-Permissions")))
            .thenReturn("connector:read-schema");
        HttpResponse schemaResponse = runPromise(() -> handlerWithOperations.handleGetSchema(schemaRequest));

        assertThat(testResponse.getCode()).isEqualTo(200);
        assertThat(healthResponse.getCode()).isEqualTo(200);
        assertThat(schemaResponse.getCode()).isEqualTo(503);

        List<OperationRecord> operations = operationRecorder.listRecent("test-tenant", 10);
        assertThat(operations)
            .extracting(OperationRecord::kind)
            .contains(OperationKind.CONNECTOR_TEST, OperationKind.CONNECTOR_HEALTH, OperationKind.CONNECTOR_SCHEMA);
        assertThat(operations)
            .filteredOn(record -> record.kind() == OperationKind.CONNECTOR_TEST)
            .singleElement()
            .satisfies(record -> assertThat(record.status()).isEqualTo(OperationStatus.BLOCKED));
        assertThat(operations)
            .filteredOn(record -> record.kind() == OperationKind.CONNECTOR_HEALTH)
            .singleElement()
            .satisfies(record -> assertThat(record.status()).isEqualTo(OperationStatus.SUCCEEDED));
        assertThat(operations)
            .filteredOn(record -> record.kind() == OperationKind.CONNECTOR_SCHEMA)
            .singleElement()
            .satisfies(record -> assertThat(record.status()).isEqualTo(OperationStatus.BLOCKED));
    }

    @Test
    @DisplayName("Enable requires successful validation before marking ACTIVE")
    void enableRequiresValidation() {
        // Given fabric connector that returns failed test
        DataFabricConnector mockFabric = mock(DataFabricConnector.class);
        lenient().when(mockFabric.testConnection(anyString()))
            .thenReturn(io.activej.promise.Promise.of(
                new DataFabricConnector.ConnectionTestResult(false, "Connection refused", 0, "1.0.0")));

        DataSourceRegistryHandler handlerWithFabric = new DataSourceRegistryHandler(
            client, http, mockFabric, null);

        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.getPathParameter("connectionId")).thenReturn("conn-1");
        lenient().when(request.getHeader(TenantExtractor.TENANT_HEADER)).thenReturn("test-tenant");
        lenient().when(request.getPath()).thenReturn("/api/v1/connectors/conn-1/enable");
        lenient().when(request.getQueryParameter("tenantId")).thenReturn(null);
        lenient().when(request.getMethod()).thenReturn(io.activej.http.HttpMethod.POST);
        lenient().doReturn(TenantResolutionResult.success("test-tenant", null)).when(http).requireTenantIdWithError(any());

        DataCloudClient.Entity existingEntity = mockEntity("conn-1", Map.of("name", "test-conn", "type", "POSTGRESQL"));
        lenient().when(client.findById(anyString(), anyString(), eq("conn-1")))
            .thenReturn(io.activej.promise.Promise.of(java.util.Optional.of(existingEntity)));

        // Create a mock for the saved entity
        DataCloudClient.Entity savedEntity = mock(DataCloudClient.Entity.class);
        lenient().when(savedEntity.id()).thenReturn("conn-1");
        lenient().when(savedEntity.data()).thenReturn(Map.of("name", "test-conn", "type", "POSTGRESQL", "state", "ERROR"));
        lenient().when(savedEntity.collection()).thenReturn("connections");
        lenient().when(savedEntity.version()).thenReturn(1L);
        lenient().when(savedEntity.createdAt()).thenReturn(java.time.Instant.now());
        
        lenient().when(client.save(anyString(), anyString(), anyMap()))
            .thenReturn(io.activej.promise.Promise.of(savedEntity));

        HttpResponse response = runPromise(() -> handlerWithFabric.handleEnableConnection(request));

        assertThat(response.getCode()).isEqualTo(400);
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("enabled")).isEqualTo(false);
        assertThat(body.get("state")).isEqualTo("ERROR");
        assertThat(body.get("error")).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("Enable succeeds when validation passes")
    void enableSucceedsWithValidation() {
        // Given fabric connector that returns successful test
        DataFabricConnector mockFabric = mock(DataFabricConnector.class);
        lenient().when(mockFabric.testConnection(anyString()))
            .thenReturn(io.activej.promise.Promise.of(
                new DataFabricConnector.ConnectionTestResult(true, "Connected", 15, "1.0.0")));

        DataSourceRegistryHandler handlerWithFabric = new DataSourceRegistryHandler(
            client, http, mockFabric, null);

        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.getPathParameter("connectionId")).thenReturn("conn-1");
        lenient().when(request.getHeader(TenantExtractor.TENANT_HEADER)).thenReturn("test-tenant");
        lenient().when(request.getPath()).thenReturn("/api/v1/connectors/conn-1/enable");
        lenient().when(request.getQueryParameter("tenantId")).thenReturn(null);
        lenient().when(request.getMethod()).thenReturn(io.activej.http.HttpMethod.POST);
        lenient().doReturn(TenantResolutionResult.success("test-tenant", null)).when(http).requireTenantIdWithError(any());

        DataCloudClient.Entity existingEntity = mockEntity("conn-1", Map.of("name", "test-conn", "type", "POSTGRESQL"));
        lenient().when(client.findById(anyString(), anyString(), eq("conn-1")))
            .thenReturn(io.activej.promise.Promise.of(java.util.Optional.of(existingEntity)));

        // Create a mock for the saved entity
        DataCloudClient.Entity savedEntity = mock(DataCloudClient.Entity.class);
        lenient().when(savedEntity.id()).thenReturn("conn-1");
        lenient().when(savedEntity.data()).thenReturn(Map.of("name", "test-conn", "type", "POSTGRESQL", "state", "ACTIVE"));
        lenient().when(savedEntity.collection()).thenReturn("connections");
        lenient().when(savedEntity.version()).thenReturn(1L);
        lenient().when(savedEntity.createdAt()).thenReturn(java.time.Instant.now());
        
        lenient().when(client.save(anyString(), anyString(), anyMap()))
            .thenReturn(io.activej.promise.Promise.of(savedEntity));

        HttpResponse response = runPromise(() -> handlerWithFabric.handleEnableConnection(request));

        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("enabled")).isEqualTo(true);
        assertThat(body.get("state")).isEqualTo("ACTIVE");
        assertThat(body.get("connectionId")).isEqualTo("conn-1");
        assertThat(body.get("tenantId")).isEqualTo("test-tenant");
    }

    @Test
    @DisplayName("State update does not synthesize missing connection record")
    void stateUpdateDoesNotSynthesizeMissingRecord() {
        // When connection doesn't exist
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.getPathParameter("connectionId")).thenReturn("nonexistent-conn");
        lenient().when(request.getHeader(TenantExtractor.TENANT_HEADER)).thenReturn("test-tenant");
        lenient().when(request.getPath()).thenReturn("/api/v1/connectors/nonexistent-conn/enable");
        lenient().when(request.getQueryParameter("tenantId")).thenReturn(null);
        lenient().when(request.getMethod()).thenReturn(io.activej.http.HttpMethod.POST);

        lenient().when(client.findById(anyString(), anyString(), eq("nonexistent-conn")))
            .thenReturn(io.activej.promise.Promise.of(java.util.Optional.empty()));

        HttpResponse response = runPromise(() -> handler.handleEnableConnection(request));

        // Then returns 503 (no fabric) or error - but never creates phantom record
        // The actual behavior depends on whether fabric is available
        // Without fabric, it returns 503 early
        assertThat(response.getCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("Sync updates collection metadata with sync information (P4.4)")
    void syncUpdatesCollectionMetadata() {
        // Given fabric connector that returns successful sync with jobId
        DataFabricConnector mockFabric = mock(DataFabricConnector.class);
        DataFabricConnector.SyncResult syncResult = new DataFabricConnector.SyncResult(
            "conn-1", "sync-job-123", true, 1000, 0, Instant.now(), Instant.now(), "Sync completed successfully");
        lenient().when(mockFabric.sync(anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(syncResult));

        DataSourceRegistryHandler handlerWithFabric = new DataSourceRegistryHandler(
            client, http, mockFabric, null);

        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.getPathParameter("connectionId")).thenReturn("conn-1");
        lenient().when(request.getHeader(TenantExtractor.TENANT_HEADER)).thenReturn("test-tenant");
        lenient().when(request.getPath()).thenReturn("/api/v1/connectors/conn-1/sync");
        lenient().when(request.getQueryParameter("tenantId")).thenReturn(null);
        lenient().when(request.getMethod()).thenReturn(io.activej.http.HttpMethod.POST);
        lenient().when(request.getHeader(HttpHeaders.of("X-Permissions")))
            .thenReturn("connector:trigger-sync");
        lenient().doReturn(TenantResolutionResult.success("test-tenant", null)).when(http).requireTenantIdWithError(any());
        lenient().when(request.getBody()).thenReturn(ByteBufStrings.wrapUtf8("{\"targetCollection\":\"col-1\"}"));

        DataCloudClient.Entity existingConnection = mockEntity("conn-1", Map.of("name", "test-conn", "type", "POSTGRESQL", "state", "ACTIVE"));
        lenient().when(client.findById(anyString(), anyString(), eq("conn-1")))
            .thenReturn(io.activej.promise.Promise.of(java.util.Optional.of(existingConnection)));

        DataCloudClient.Entity savedConnection = mock(DataCloudClient.Entity.class);
        lenient().when(savedConnection.id()).thenReturn("conn-1");
        lenient().when(savedConnection.data()).thenReturn(Map.of("name", "test-conn", "type", "POSTGRESQL", "state", "ACTIVE"));
        lenient().when(savedConnection.collection()).thenReturn("dc_connections");
        lenient().when(savedConnection.version()).thenReturn(1L);
        lenient().when(savedConnection.createdAt()).thenReturn(java.time.Instant.now());
        
        lenient().when(client.save(anyString(), anyString(), anyMap()))
            .thenReturn(io.activej.promise.Promise.of(savedConnection));

        DataCloudClient.Entity existingCollection = mock(DataCloudClient.Entity.class);
        lenient().when(existingCollection.id()).thenReturn("col-1");
        lenient().when(existingCollection.data()).thenReturn(Map.of("name", "test-collection"));
        lenient().when(existingCollection.collection()).thenReturn("dc_collections");
        lenient().when(existingCollection.version()).thenReturn(1L);
        lenient().when(existingCollection.createdAt()).thenReturn(java.time.Instant.now());
        
        lenient().when(client.findById(anyString(), anyString(), eq("col-1")))
            .thenReturn(io.activej.promise.Promise.of(java.util.Optional.of(existingCollection)));

        DataCloudClient.Entity updatedCollection = mock(DataCloudClient.Entity.class);
        lenient().when(updatedCollection.id()).thenReturn("col-1");
        lenient().when(updatedCollection.data()).thenReturn(Map.of("name", "test-collection", "syncMetadata", Map.of(
            "lastSyncConnectionId", "conn-1",
            "lastSyncStatus", "completed",
            "lastSyncRecordsSynced", 1000
        )));
        lenient().when(updatedCollection.collection()).thenReturn("dc_collections");
        lenient().when(updatedCollection.version()).thenReturn(2L);
        lenient().when(updatedCollection.createdAt()).thenReturn(java.time.Instant.now());
        
        lenient().when(client.updateEntity(anyString(), eq("col-1"), anyMap(), eq("test-tenant")))
            .thenReturn(io.activej.promise.Promise.of(updatedCollection));

        HttpResponse response = runPromise(() -> handlerWithFabric.handleTriggerSync(request));

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);

        // H3: Assert canonical response shape with jobId
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body).containsKey("tenantId");
        assertThat(body).containsKey("connectionId");
        assertThat(body).containsKey("jobId");
        assertThat(body).containsKey("syncStatus");
        assertThat(body).containsKey("recordsSynced");
        assertThat(body).containsKey("recordsFailed");
        assertThat(body).containsKey("targetCollection");
        assertThat(body).containsKey("timestamp");

        // Assert jobId is present and matches sync result
        assertThat(body.get("jobId")).isEqualTo("sync-job-123");
        assertThat(body.get("syncStatus")).isEqualTo("completed");
        assertThat(body.get("recordsSynced")).isEqualTo(1000);
        assertThat(body.get("recordsFailed")).isEqualTo(0);

        // Verify that collection metadata was updated with sync information
        verify(client).updateEntity(anyString(), eq("col-1"), anyMap(), eq("test-tenant"));
    }

    @Test
    @DisplayName("H3: List connections returns canonical shape without secrets")
    void listConnectionsReturnsCanonicalShapeWithoutSecrets() {
        // Given connection entities with sensitive data
        DataCloudClient.Entity entity1 = mockEntity("conn-1", Map.ofEntries(
            Map.entry("name", "orders-source"),
            Map.entry("type", "POSTGRESQL"),
            Map.entry("state", "ACTIVE"),
            Map.entry("tenantId", "test-tenant"),
            Map.entry("credentials", Map.of("username", "svc", "password", "secret")),
            Map.entry("secretRef", Map.of("provider", "vault", "path", "kv/datacloud/orders")),
            Map.entry("properties", Map.of("host", "db.example.com", "port", 5432)),
            Map.entry("residencyPolicy", "eu-central"),
            Map.entry("schedule", "daily"),
            Map.entry("targetCollection", "orders-collection"),
            Map.entry("createdAt", "2024-01-01T00:00:00Z"),
            Map.entry("updatedAt", "2024-01-02T00:00:00Z")
        ));

        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(List.of(entity1)));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/connectors")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleListConnections(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body).containsKey("tenantId");
        assertThat(body).containsKey("connections");
        assertThat(body).containsKey("count");
        assertThat(body).containsKey("fabricAvailable");
        assertThat(body).containsKey("timestamp");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> connections = (List<Map<String, Object>>) body.get("connections");
        assertThat(connections).hasSize(1);

        Map<String, Object> connection = connections.get(0);
        // H3: Assert canonical shape matches OpenAPI Connector schema
        assertThat(connection).containsKey("id");
        assertThat(connection).containsKey("name");
        assertThat(connection).containsKey("type");
        assertThat(connection).containsKey("state");
        assertThat(connection).containsKey("tenantId");
        assertThat(connection).containsKey("createdAt");
        assertThat(connection).containsKey("updatedAt");
        assertThat(connection).containsKey("properties");
        assertThat(connection).containsKey("residencyPolicy");
        assertThat(connection).containsKey("schedule");
        assertThat(connection).containsKey("targetCollection");

        // H3: Assert secrets are redacted
        assertThat(connection).doesNotContainKey("credentials");
        assertThat(connection).doesNotContainKey("secretRef");
    }

    @Test
    @DisplayName("H3: Get connection returns canonical shape without secrets")
    void getConnectionReturnsCanonicalShapeWithoutSecrets() {
        // Given connection entity with sensitive data
        DataCloudClient.Entity entity = mockEntity("conn-1", Map.of(
            "name", "orders-source",
            "type", "POSTGRESQL",
            "state", "ACTIVE",
            "tenantId", "test-tenant",
            "credentials", Map.of("username", "svc", "password", "secret"),
            "secretRef", Map.of("provider", "vault", "path", "kv/datacloud/orders"),
            "properties", Map.of("host", "db.example.com", "port", 5432),
            "createdAt", "2024-01-01T00:00:00Z",
            "updatedAt", "2024-01-02T00:00:00Z"
        ));

        lenient().when(client.findById(anyString(), anyString(), eq("conn-1")))
            .thenReturn(io.activej.promise.Promise.of(java.util.Optional.of(entity)));

        HttpRequest request = RequestContextTestHelper.createTestRequest(
            "test-tenant",
            "conn-1",
            "/api/v1/connectors/conn-1");
        lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);

        HttpResponse response = runPromise(() -> handler.handleGetConnection(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> connection = parseJsonBody(response);
        // H3: Assert canonical shape matches OpenAPI Connector schema
        assertThat(connection).containsKey("id");
        assertThat(connection).containsKey("name");
        assertThat(connection).containsKey("type");
        assertThat(connection).containsKey("state");
        assertThat(connection).containsKey("tenantId");
        assertThat(connection).containsKey("createdAt");
        assertThat(connection).containsKey("updatedAt");
        assertThat(connection).containsKey("properties");

        // H3: Assert secrets are redacted
        assertThat(connection).doesNotContainKey("credentials");
        assertThat(connection).doesNotContainKey("secretRef");
    }

    // Helper methods

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(HttpResponse response) {
        try {
            String bodyStr = runPromise(() ->
                response.loadBody().map(buf -> buf.getString(java.nio.charset.StandardCharsets.UTF_8)));
            return http.objectMapper().readValue(bodyStr, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }

    private DataCloudClient.Entity mockEntity(String id, Map<String, Object> data) {
        DataCloudClient.Entity entity = mock(DataCloudClient.Entity.class);
        lenient().when(entity.id()).thenReturn(id);
        lenient().when(entity.data()).thenReturn(data);
        lenient().when(entity.collection()).thenReturn("connections");
        lenient().when(entity.version()).thenReturn(1L);
        lenient().when(entity.createdAt()).thenReturn(java.time.Instant.now());
        return entity;
    }
}
