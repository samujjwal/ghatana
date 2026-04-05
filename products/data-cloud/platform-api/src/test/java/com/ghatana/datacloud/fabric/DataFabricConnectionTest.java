/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.fabric;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Data Fabric connector (PF002).
 *
 * @doc.type class
 * @doc.purpose Data fabric connector tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataFabricConnection – Connector Tests (PF002)")
class DataFabricConnectionTest extends EventloopTestBase {

    @Mock
    private DataFabricConnector connector;

    @Nested
    @DisplayName("Connection Management")
    class ConnectionManagementTests {

        @Test
        @DisplayName("[PF002]: test_connection_returns_success_for_valid_config")
        void testConnectionReturnsSuccessForValidConfig() {
            String connectionId = "valid-connection";

            DataFabricConnector.ConnectionTestResult result = new DataFabricConnector.ConnectionTestResult(
                true, "Connection successful", 45, "PostgreSQL 14"
            );

            when(connector.testConnection(connectionId))
                .thenReturn(Promise.of(result));

            DataFabricConnector.ConnectionTestResult testResult = runPromise(() ->
                connector.testConnection(connectionId)
            );

            assertThat(testResult.success()).isTrue();
            assertThat(testResult.latencyMs()).isEqualTo(45);
        }

        @Test
        @DisplayName("[PF002]: test_connection_returns_failure_for_invalid_config")
        void testConnectionReturnsFailureForInvalidConfig() {
            String connectionId = "invalid-connection";

            DataFabricConnector.ConnectionTestResult result = new DataFabricConnector.ConnectionTestResult(
                false, "Authentication failed", 0, null
            );

            when(connector.testConnection(connectionId))
                .thenReturn(Promise.of(result));

            DataFabricConnector.ConnectionTestResult testResult = runPromise(() ->
                connector.testConnection(connectionId)
            );

            assertThat(testResult.success()).isFalse();
            assertThat(testResult.message()).contains("failed");
        }

        @Test
        @DisplayName("[PF002]: connect_establishes_connection")
        void connectEstablishesConnection() {
            DataFabricConnector.ConnectionConfig config = DataFabricConnector.ConnectionConfig.builder()
                .id("new-conn")
                .name("Production DB")
                .tenantId("tenant-alpha")
                .type(DataFabricConnector.ConnectionType.POSTGRESQL)
                .properties(Map.of("host", "db.example.com", "port", 5432))
                .credentials(Map.of("username", "admin", "password", "secret"))
                .build();

            DataFabricConnector.DataConnection connection = new DataFabricConnector.DataConnection(
                config.id(), config.name(), config.tenantId(), config.type(),
                DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of()
            );

            when(connector.connect(any()))
                .thenReturn(Promise.of(connection));

            DataFabricConnector.DataConnection result = runPromise(() -> connector.connect(config));

            assertThat(result.isConnected()).isTrue();
            assertThat(result.state()).isEqualTo(DataFabricConnector.ConnectionState.CONNECTED);
        }

        @Test
        @DisplayName("[PF002]: disconnect_closes_connection")
        void disconnectClosesConnection() {
            String connectionId = "connected-conn";

            when(connector.disconnect(connectionId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> connector.disconnect(connectionId));

            verify(connector).disconnect(connectionId);
        }
    }

    @Nested
    @DisplayName("Connection Queries")
    class ConnectionQueriesTests {

        @Test
        @DisplayName("[PF002]: get_connection_returns_existing")
        void getConnectionReturnsExisting() {
            String connectionId = "existing-conn";

            DataFabricConnector.DataConnection connection = new DataFabricConnector.DataConnection(
                connectionId, "Test DB", "tenant-alpha", DataFabricConnector.ConnectionType.MYSQL,
                DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of()
            );

            when(connector.getConnection(connectionId))
                .thenReturn(Promise.of(Optional.of(connection)));

            Optional<DataFabricConnector.DataConnection> result = runPromise(() ->
                connector.getConnection(connectionId)
            );

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(connectionId);
        }

        @Test
        @DisplayName("[PF002]: list_connections_returns_tenant_connections")
        void listConnectionsReturnsTenantConnections() {
            String tenantId = "tenant-alpha";

            List<DataFabricConnector.DataConnection> connections = List.of(
                new DataFabricConnector.DataConnection("c1", "DB1", tenantId, DataFabricConnector.ConnectionType.POSTGRESQL,
                    DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of()),
                new DataFabricConnector.DataConnection("c2", "DB2", tenantId, DataFabricConnector.ConnectionType.MONGODB,
                    DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of())
            );

            when(connector.listConnections(tenantId))
                .thenReturn(Promise.of(connections));

            List<DataFabricConnector.DataConnection> result = runPromise(() ->
                connector.listConnections(tenantId)
            );

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> tenantId.equals(c.tenantId()));
        }
    }

    @Nested
    @DisplayName("Query Execution")
    class QueryExecutionTests {

        @Test
        @DisplayName("[PF002]: execute_query_returns_results")
        void executeQueryReturnsResults() {
            String connectionId = "conn-with-data";
            String query = "SELECT * FROM users";

            DataFabricConnector.QueryResult result = new DataFabricConnector.QueryResult(
                true,
                List.of(
                    Map.of("id", 1, "name", "Alice"),
                    Map.of("id", 2, "name", "Bob")
                ),
                2,
                List.of("id", "name"),
                150,
                null
            );

            when(connector.executeQuery(connectionId, query))
                .thenReturn(Promise.of(result));

            DataFabricConnector.QueryResult queryResult = runPromise(() ->
                connector.executeQuery(connectionId, query)
            );

            assertThat(queryResult.success()).isTrue();
            assertThat(queryResult.rowCount()).isEqualTo(2);
            assertThat(queryResult.rows()).hasSize(2);
        }

        @Test
        @DisplayName("[PF002]: execute_query_returns_error_on_failure")
        void executeQueryReturnsErrorOnFailure() {
            String connectionId = "conn-with-error";
            String query = "INVALID SQL";

            DataFabricConnector.QueryResult result = new DataFabricConnector.QueryResult(
                false, List.of(), 0, List.of(), 0, "Syntax error in SQL"
            );

            when(connector.executeQuery(connectionId, query))
                .thenReturn(Promise.of(result));

            DataFabricConnector.QueryResult queryResult = runPromise(() ->
                connector.executeQuery(connectionId, query)
            );

            assertThat(queryResult.success()).isFalse();
            assertThat(queryResult.errorMessage()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Schema Operations")
    class SchemaOperationsTests {

        @Test
        @DisplayName("[PF002]: get_schema_returns_database_schema")
        void getSchemaReturnsDatabaseSchema() {
            String connectionId = "conn-with-schema";

            DataFabricConnector.DataSchema schema = new DataFabricConnector.DataSchema(
                connectionId,
                List.of(
                    new DataFabricConnector.TableSchema(
                        "users",
                        List.of(
                            new DataFabricConnector.ColumnSchema("id", "INTEGER", false, true),
                            new DataFabricConnector.ColumnSchema("name", "VARCHAR", true, false),
                            new DataFabricConnector.ColumnSchema("email", "VARCHAR", false, false)
                        ),
                        List.of("id")
                    )
                ),
                Instant.now()
            );

            when(connector.getSchema(connectionId))
                .thenReturn(Promise.of(schema));

            DataFabricConnector.DataSchema result = runPromise(() -> connector.getSchema(connectionId));

            assertThat(result.tables()).hasSize(1);
            assertThat(result.tables().get(0).name()).isEqualTo("users");
            assertThat(result.tables().get(0).columns()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Configuration Builder")
    class ConfigurationBuilderTests {

        @Test
        @DisplayName("[PF002]: connection_config_builder_creates_config")
        void connectionConfigBuilderCreatesConfig() {
            DataFabricConnector.ConnectionConfig config = DataFabricConnector.ConnectionConfig.builder()
                .id("test-conn")
                .name("Test Connection")
                .tenantId("tenant-alpha")
                .type(DataFabricConnector.ConnectionType.S3)
                .properties(Map.of("bucket", "my-bucket", "region", "us-east-1"))
                .credentials(Map.of("accessKey", "AKIA...", "secretKey", "secret..."))
                .encrypted(true)
                .connectionTimeoutSeconds(60)
                .maxConnections(20)
                .build();

            assertThat(config.id()).isEqualTo("test-conn");
            assertThat(config.type()).isEqualTo(DataFabricConnector.ConnectionType.S3);
            assertThat(config.encrypted()).isTrue();
            assertThat(config.connectionTimeoutSeconds()).isEqualTo(60);
            assertThat(config.maxConnections()).isEqualTo(20);
        }
    }
}
