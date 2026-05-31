/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.fabric;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Connector for external data sources in Data Fabric.
 *
 * <p>Manages connections to external databases, APIs, and services.
 *
 * @doc.type interface
 * @doc.purpose External data source connector
 * @doc.layer product
 * @doc.pattern Connector, Data Access
 */
public interface DataFabricConnector {

    /**
     * Test connection to data source.
     *
     * @param connectionId connection identifier
     * @return promise of connection test result
     */
    Promise<ConnectionTestResult> testConnection(String connectionId);

    /**
     * Establish connection to data source.
     *
     * @param config connection configuration
     * @return promise of established connection
     */
    Promise<DataConnection> connect(ConnectionConfig config);

    /**
     * Disconnect from data source.
     *
     * @param connectionId connection identifier
     * @return promise completing when disconnected
     */
    Promise<Void> disconnect(String connectionId);

    /**
     * Get connection by ID.
     *
     * @param connectionId connection identifier
     * @return promise of connection if found
     */
    Promise<Optional<DataConnection>> getConnection(String connectionId);

    /**
     * List all connections for tenant.
     *
     * @param tenantId tenant identifier
     * @return promise of connection list
     */
    Promise<List<DataConnection>> listConnections(String tenantId);

    /**
     * Execute query on connected data source.
     *
     * @param connectionId connection identifier
     * @param query query to execute
     * @return promise of query result
     */
    Promise<QueryResult> executeQuery(String connectionId, String query);

    /**
     * Get schema from data source.
     *
     * @param connectionId connection identifier
     * @return promise of schema
     */
    Promise<DataSchema> getSchema(String connectionId);

    /**
     * Sync data from external source.
     *
     * @param connectionId connection identifier
     * @param syncConfig sync configuration
     * @return promise of sync result
     */
    Promise<SyncResult> sync(String connectionId, SyncConfig syncConfig);

    /**
     * Get sync status.
     *
     * @param connectionId connection identifier
     * @return promise of sync status
     */
    Promise<SyncStatus> getSyncStatus(String connectionId);

    /**
     * Connection types.
     */
    enum ConnectionType {
        POSTGRESQL, MYSQL, MONGODB, S3, REST_API, KAFKA, SNOWFLAKE, BIGQUERY, CUSTOM
    }

    /**
     * Connection configuration.
     */
    record ConnectionConfig(
        String id,
        String name,
        String tenantId,
        ConnectionType type,
        Map<String, Object> properties,
        Map<String, String> credentials,
        boolean encrypted,
        int connectionTimeoutSeconds,
        int maxConnections
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String name;
            private String tenantId;
            private ConnectionType type;
            private Map<String, Object> properties = Map.of();
            private Map<String, String> credentials = Map.of();
            private boolean encrypted = true;
            private int connectionTimeoutSeconds = 30;
            private int maxConnections = 10;

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder type(ConnectionType type) { this.type = type; return this; }
            public Builder properties(Map<String, Object> props) { this.properties = props; return this; }
            public Builder credentials(Map<String, String> creds) { this.credentials = creds; return this; }
            public Builder encrypted(boolean encrypted) { this.encrypted = encrypted; return this; }
            public Builder connectionTimeoutSeconds(int seconds) { this.connectionTimeoutSeconds = seconds; return this; }
            public Builder maxConnections(int max) { this.maxConnections = max; return this; }

            public ConnectionConfig build() {
                return new ConnectionConfig(id, name, tenantId, type, properties, credentials,
                    encrypted, connectionTimeoutSeconds, maxConnections);
            }
        }
    }

    /**
     * Data connection.
     */
    record DataConnection(
        String id,
        String name,
        String tenantId,
        ConnectionType type,
        ConnectionState state,
        Instant connectedAt,
        Instant lastActivityAt,
        Map<String, Object> metadata
    ) {
        public boolean isConnected() {
            return state == ConnectionState.CONNECTED;
        }
    }

    /**
     * Connection state.
     */
    enum ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    /**
     * Connection test result.
     */
    record ConnectionTestResult(
        boolean success,
        String message,
        long latencyMs,
        String version
    ) {}

    /**
     * Query result.
     */
    record QueryResult(
        boolean success,
        List<Map<String, Object>> rows,
        int rowCount,
        List<String> columns,
        long executionTimeMs,
        String errorMessage
    ) {}

    /**
     * Data schema.
     */
    record DataSchema(
        String connectionId,
        List<TableSchema> tables,
        Instant fetchedAt
    ) {}

    /**
     * Table schema.
     */
    record TableSchema(
        String name,
        List<ColumnSchema> columns,
        List<String> primaryKeys
    ) {}

    /**
     * Column schema.
     */
    record ColumnSchema(
        String name,
        String type,
        boolean nullable,
        boolean primaryKey
    ) {}

    /**
     * Sync configuration.
     */
    record SyncConfig(
        String syncMode,
        String targetCollection,
        String schedule,
        Map<String, Object> filters,
        boolean incremental,
        List<String> columns
    ) {}

    /**
     * Sync result.
     */
    record SyncResult(
        String connectionId,
        String jobId,
        boolean success,
        int recordsSynced,
        int recordsFailed,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
    ) {}

    /**
     * Sync status.
     */
    record SyncStatus(
        String connectionId,
        String state,
        int totalRecords,
        int syncedRecords,
        int failedRecords,
        double progressPercent,
        Instant startedAt,
        Instant estimatedCompletionAt
    ) {}
}
