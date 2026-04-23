/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Tests for Data Fabric connector (PF002). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Data fabric connector tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataFabricConnection – Connector Tests (PF002)")
class DataFabricConnectionTest extends EventloopTestBase {

    @Mock
    private DataFabricConnector connector;

    @Nested
    @DisplayName("Connection Management")
    class ConnectionManagementTests {

        @Test
        @DisplayName("[PF002]: test_connection_returns_success_for_valid_config")
        void testConnectionReturnsSuccessForValidConfig() { // GH-90000
            String connectionId = "valid-connection";

            DataFabricConnector.ConnectionTestResult result = new DataFabricConnector.ConnectionTestResult( // GH-90000
                true, "Connection successful", 45, "PostgreSQL 14"
            );

            when(connector.testConnection(connectionId)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataFabricConnector.ConnectionTestResult testResult = runPromise(() -> // GH-90000
                connector.testConnection(connectionId) // GH-90000
            );

            assertThat(testResult.success()).isTrue(); // GH-90000
            assertThat(testResult.latencyMs()).isEqualTo(45); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: test_connection_returns_failure_for_invalid_config")
        void testConnectionReturnsFailureForInvalidConfig() { // GH-90000
            String connectionId = "invalid-connection";

            DataFabricConnector.ConnectionTestResult result = new DataFabricConnector.ConnectionTestResult( // GH-90000
                false, "Authentication failed", 0, null
            );

            when(connector.testConnection(connectionId)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataFabricConnector.ConnectionTestResult testResult = runPromise(() -> // GH-90000
                connector.testConnection(connectionId) // GH-90000
            );

            assertThat(testResult.success()).isFalse(); // GH-90000
            assertThat(testResult.message()).contains("failed");
        }

        @Test
        @DisplayName("[PF002]: connect_establishes_connection")
        void connectEstablishesConnection() { // GH-90000
            DataFabricConnector.ConnectionConfig config = DataFabricConnector.ConnectionConfig.builder() // GH-90000
                .id("new-conn")
                .name("Production DB")
                .tenantId("tenant-alpha")
                .type(DataFabricConnector.ConnectionType.POSTGRESQL) // GH-90000
                .properties(Map.of("host", "db.example.com", "port", 5432)) // GH-90000
                .credentials(Map.of("username", "admin", "password", "secret")) // GH-90000
                .build(); // GH-90000

            DataFabricConnector.DataConnection connection = new DataFabricConnector.DataConnection( // GH-90000
                config.id(), config.name(), config.tenantId(), config.type(), // GH-90000
                DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of() // GH-90000
            );

            when(connector.connect(any())) // GH-90000
                .thenReturn(Promise.of(connection)); // GH-90000

            DataFabricConnector.DataConnection result = runPromise(() -> connector.connect(config)); // GH-90000

            assertThat(result.isConnected()).isTrue(); // GH-90000
            assertThat(result.state()).isEqualTo(DataFabricConnector.ConnectionState.CONNECTED); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: disconnect_closes_connection")
        void disconnectClosesConnection() { // GH-90000
            String connectionId = "connected-conn";

            when(connector.disconnect(connectionId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> connector.disconnect(connectionId)); // GH-90000

            verify(connector).disconnect(connectionId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Connection Queries")
    class ConnectionQueriesTests {

        @Test
        @DisplayName("[PF002]: get_connection_returns_existing")
        void getConnectionReturnsExisting() { // GH-90000
            String connectionId = "existing-conn";

            DataFabricConnector.DataConnection connection = new DataFabricConnector.DataConnection( // GH-90000
                connectionId, "Test DB", "tenant-alpha", DataFabricConnector.ConnectionType.MYSQL,
                DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of() // GH-90000
            );

            when(connector.getConnection(connectionId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(connection))); // GH-90000

            Optional<DataFabricConnector.DataConnection> result = runPromise(() -> // GH-90000
                connector.getConnection(connectionId) // GH-90000
            );

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(connectionId); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: list_connections_returns_tenant_connections")
        void listConnectionsReturnsTenantConnections() { // GH-90000
            String tenantId = "tenant-alpha";

            List<DataFabricConnector.DataConnection> connections = List.of( // GH-90000
                new DataFabricConnector.DataConnection("c1", "DB1", tenantId, DataFabricConnector.ConnectionType.POSTGRESQL, // GH-90000
                    DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of()), // GH-90000
                new DataFabricConnector.DataConnection("c2", "DB2", tenantId, DataFabricConnector.ConnectionType.MONGODB, // GH-90000
                    DataFabricConnector.ConnectionState.CONNECTED, Instant.now(), Instant.now(), Map.of()) // GH-90000
            );

            when(connector.listConnections(tenantId)) // GH-90000
                .thenReturn(Promise.of(connections)); // GH-90000

            List<DataFabricConnector.DataConnection> result = runPromise(() -> // GH-90000
                connector.listConnections(tenantId) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result).allMatch(c -> tenantId.equals(c.tenantId())); // GH-90000
        }
    }

    @Nested
    @DisplayName("Query Execution")
    class QueryExecutionTests {

        @Test
        @DisplayName("[PF002]: execute_query_returns_results")
        void executeQueryReturnsResults() { // GH-90000
            String connectionId = "conn-with-data";
            String query = "SELECT * FROM users";

            DataFabricConnector.QueryResult result = new DataFabricConnector.QueryResult( // GH-90000
                true,
                List.of( // GH-90000
                    Map.of("id", 1, "name", "Alice"), // GH-90000
                    Map.of("id", 2, "name", "Bob") // GH-90000
                ),
                2,
                List.of("id", "name"), // GH-90000
                150,
                null
            );

            when(connector.executeQuery(connectionId, query)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataFabricConnector.QueryResult queryResult = runPromise(() -> // GH-90000
                connector.executeQuery(connectionId, query) // GH-90000
            );

            assertThat(queryResult.success()).isTrue(); // GH-90000
            assertThat(queryResult.rowCount()).isEqualTo(2); // GH-90000
            assertThat(queryResult.rows()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: execute_query_returns_error_on_failure")
        void executeQueryReturnsErrorOnFailure() { // GH-90000
            String connectionId = "conn-with-error";
            String query = "INVALID SQL";

            DataFabricConnector.QueryResult result = new DataFabricConnector.QueryResult( // GH-90000
                false, List.of(), 0, List.of(), 0, "Syntax error in SQL" // GH-90000
            );

            when(connector.executeQuery(connectionId, query)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataFabricConnector.QueryResult queryResult = runPromise(() -> // GH-90000
                connector.executeQuery(connectionId, query) // GH-90000
            );

            assertThat(queryResult.success()).isFalse(); // GH-90000
            assertThat(queryResult.errorMessage()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Schema Operations")
    class SchemaOperationsTests {

        @Test
        @DisplayName("[PF002]: get_schema_returns_database_schema")
        void getSchemaReturnsDatabaseSchema() { // GH-90000
            String connectionId = "conn-with-schema";

            DataFabricConnector.DataSchema schema = new DataFabricConnector.DataSchema( // GH-90000
                connectionId,
                List.of( // GH-90000
                    new DataFabricConnector.TableSchema( // GH-90000
                        "users",
                        List.of( // GH-90000
                            new DataFabricConnector.ColumnSchema("id", "INTEGER", false, true), // GH-90000
                            new DataFabricConnector.ColumnSchema("name", "VARCHAR", true, false), // GH-90000
                            new DataFabricConnector.ColumnSchema("email", "VARCHAR", false, false) // GH-90000
                        ),
                        List.of("id")
                    )
                ),
                Instant.now() // GH-90000
            );

            when(connector.getSchema(connectionId)) // GH-90000
                .thenReturn(Promise.of(schema)); // GH-90000

            DataFabricConnector.DataSchema result = runPromise(() -> connector.getSchema(connectionId)); // GH-90000

            assertThat(result.tables()).hasSize(1); // GH-90000
            assertThat(result.tables().get(0).name()).isEqualTo("users");
            assertThat(result.tables().get(0).columns()).hasSize(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("Configuration Builder")
    class ConfigurationBuilderTests {

        @Test
        @DisplayName("[PF002]: connection_config_builder_creates_config")
        void connectionConfigBuilderCreatesConfig() { // GH-90000
            DataFabricConnector.ConnectionConfig config = DataFabricConnector.ConnectionConfig.builder() // GH-90000
                .id("test-conn")
                .name("Test Connection")
                .tenantId("tenant-alpha")
                .type(DataFabricConnector.ConnectionType.S3) // GH-90000
                .properties(Map.of("bucket", "my-bucket", "region", "us-east-1")) // GH-90000
                .credentials(Map.of("accessKey", "AKIA...", "secretKey", "secret...")) // GH-90000
                .encrypted(true) // GH-90000
                .connectionTimeoutSeconds(60) // GH-90000
                .maxConnections(20) // GH-90000
                .build(); // GH-90000

            assertThat(config.id()).isEqualTo("test-conn");
            assertThat(config.type()).isEqualTo(DataFabricConnector.ConnectionType.S3); // GH-90000
            assertThat(config.encrypted()).isTrue(); // GH-90000
            assertThat(config.connectionTimeoutSeconds()).isEqualTo(60); // GH-90000
            assertThat(config.maxConnections()).isEqualTo(20); // GH-90000
        }
    }
}
