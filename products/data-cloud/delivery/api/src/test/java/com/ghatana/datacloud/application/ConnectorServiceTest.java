/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.DataSourceRepository;
import com.ghatana.datacloud.spi.ConnectorSecretService;
import com.ghatana.datacloud.spi.ConnectorRegistry;
import com.ghatana.datacloud.spi.ConnectorHealthMonitor;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for ConnectorService (Pass 7 connector production workflow).
 *
 * @doc.type class
 * @doc.purpose Validate connector service behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Connector Service Tests")
class ConnectorServiceTest extends EventloopTestBase {

    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private ConnectorRegistry connectorRegistry;
    @Mock
    private ConnectorSecretService secretService;
    @Mock
    private ConnectorHealthMonitor healthMonitor;
    @Mock
    private MetricsCollector metrics;

    private ConnectorService connectorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        connectorService = new ConnectorService(
            dataSourceRepository, connectorRegistry, secretService, healthMonitor, metrics);
    }

    @Test
    @DisplayName("Should activate connector successfully")
    void shouldActivateConnector() {
        String connectorId = "connector-123";
        when(connectorRegistry.activateConnector(anyString(), anyString()))
            .thenReturn(Promise.of(true));

        Promise<Void> result = connectorService.activate(connectorId);
        runPromise(() -> result);

        // Test completed successfully if no exception thrown
    }

    @Test
    @DisplayName("Should deactivate connector successfully")
    void shouldDeactivateConnector() {
        String connectorId = "connector-123";
        when(connectorRegistry.deactivateConnector(anyString(), anyString()))
            .thenReturn(Promise.of(true));

        Promise<Void> result = connectorService.deactivate(connectorId);
        runPromise(() -> result);

        // Test completed successfully if no exception thrown
    }

    @Test
    @DisplayName("Should get connector status")
    void shouldGetConnectorStatus() {
        String connectorId = "connector-123";
        when(connectorRegistry.getConnectorStatus(anyString(), anyString()))
            .thenReturn(Promise.of(java.util.Map.of(
                "status", "ACTIVE",
                "lastHealthCheck", "2026-05-31T00:00:00Z"
            )));

        var status = runPromise(() -> connectorService.getStatus(connectorId));

        assertThat(status).isNotNull();
        // The status map doesn't include connectorId in the mock response
        assertThat(status.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should test connector connection")
    void shouldTestConnectorConnection() {
        String connectorId = "connector-123";
        when(healthMonitor.performHealthCheck(anyString(), anyString()))
            .thenReturn(Promise.of(true));

        var result = runPromise(() -> connectorService.testConnection(connectorId));

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should rotate connector credentials")
    void shouldRotateConnectorCredentials() {
        String connectorId = "connector-123";

        var result = runPromise(() -> connectorService.rotateCredentials(connectorId));

        assertThat(result).isNotNull();
        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should sync connector data")
    void shouldSyncConnectorData() {
        String connectorId = "connector-123";

        var result = runPromise(() -> connectorService.sync(connectorId));

        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("SYNCING");
    }

    @Test
    @DisplayName("Should get sync status")
    void shouldGetSyncStatus() {
        String connectorId = "connector-123";

        var status = runPromise(() -> connectorService.getSyncStatus(connectorId));

        assertThat(status).isNotNull();
        assertThat(status.get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should validate connector schema")
    void shouldValidateConnectorSchema() {
        String connectorId = "connector-123";

        var result = runPromise(() -> connectorService.validateSchema(connectorId));

        assertThat(result).isNotNull();
        assertThat(result.get("valid")).isEqualTo(true);
    }
}
