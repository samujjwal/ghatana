/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ConnectorService (Pass 7 connector production workflow).
 *
 * @doc.type class
 * @doc.purpose Validate connector service behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Connector Service Tests")
class ConnectorServiceTest {

    private ConnectorService connectorService;

    @BeforeEach
    void setUp() {
        // Initialize connector service with test dependencies
        connectorService = new ConnectorService();
    }

    @Test
    @DisplayName("Should register connector successfully")
    void shouldRegisterConnector() {
        String name = "Test Connector";
        String type = "POSTGRESQL";
        String connectionString = "jdbc:postgresql://localhost:5432/test";

        var result = connectorService.register(name, type, connectionString);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.connectorId()).isNotNull();
    }

    @Test
    @DisplayName("Should not register connector with invalid type")
    void shouldNotRegisterConnectorWithInvalidType() {
        String name = "Test Connector";
        String type = "INVALID_TYPE";
        String connectionString = "jdbc:postgresql://localhost:5432/test";

        var result = connectorService.register(name, type, connectionString);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should activate connector successfully")
    void shouldActivateConnector() {
        String connectorId = "connector-123";

        var result = connectorService.activate(connectorId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should deactivate connector successfully")
    void shouldDeactivateConnector() {
        String connectorId = "connector-123";

        var result = connectorService.deactivate(connectorId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should get connector status")
    void shouldGetConnectorStatus() {
        String connectorId = "connector-123";

        var status = connectorService.getStatus(connectorId);

        assertThat(status).isNotNull();
        assertThat(status.connectorId()).isEqualTo(connectorId);
    }

    @Test
    @DisplayName("Should test connector connection")
    void shouldTestConnectorConnection() {
        String connectorId = "connector-123";

        var result = connectorService.testConnection(connectorId);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should rotate connector credentials")
    void shouldRotateConnectorCredentials() {
        String connectorId = "connector-123";

        var result = connectorService.rotateCredentials(connectorId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should sync connector data")
    void shouldSyncConnectorData() {
        String connectorId = "connector-123";

        var result = connectorService.sync(connectorId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should get sync status")
    void shouldGetSyncStatus() {
        String connectorId = "connector-123";

        var status = connectorService.getSyncStatus(connectorId);

        assertThat(status).isNotNull();
    }

    @Test
    @DisplayName("Should validate connector schema")
    void shouldValidateConnectorSchema() {
        String connectorId = "connector-123";

        var result = connectorService.validateSchema(connectorId);

        assertThat(result).isNotNull();
    }
}
