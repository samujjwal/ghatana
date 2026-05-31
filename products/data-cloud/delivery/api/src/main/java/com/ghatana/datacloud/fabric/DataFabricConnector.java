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
     * Tests the connection to the data source (SPI-compatible signature).
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing connection test result
     */
    Promise<ConnectionTestResult> testConnection(String tenantId, String connectorId);

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
     * Infers schema from the data source (SPI-compatible signature).
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param options schema inference options (optional)
     * @return Promise containing inferred schema
     */
    Promise<SchemaSnapshot> inferSchema(String tenantId, String connectorId, Map<String, Object> options);

    /**
     * Sync data from external source.
     *
     * @param connectionId connection identifier
     * @param syncConfig sync configuration
     * @return promise of sync result
     */
    Promise<SyncResult> sync(String connectionId, SyncConfig syncConfig);

    /**
     * Synchronizes data from source to target dataset (SPI-compatible signature).
     *
     * @param tenantId the tenant ID (required)
     * @param connectionId the connector ID (required)
     * @param request sync request configuration (required)
     * @return Promise containing sync operation result
     */
    Promise<SyncResult> sync(String tenantId, String connectionId, SyncRequest request);

    /**
     * Get sync status.
     *
     * @param connectionId connection identifier
     * @return promise of sync status
     */
    Promise<SyncStatus> getSyncStatus(String connectionId);

    /**
     * Gets the current synchronization status (SPI-compatible signature).
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing sync status
     */
    Promise<SyncStatus> getSyncStatus(String tenantId, String connectorId);

    /**
     * Links connector to a dataset.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param datasetId the dataset ID to link (required)
     * @param userId the user performing the link (for audit)
     * @return promise containing link result
     */
    Promise<DatasetLink> linkDataset(String tenantId, String connectorId, String datasetId, String userId);

    /**
     * Gets dataset link information.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return promise containing dataset link or null if not linked
     */
    Promise<DatasetLink> getDatasetLink(String tenantId, String connectorId);

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
    ) {
        /**
         * Returns true if the connection test succeeded.
         */
        public boolean success() {
            return success;
        }

        /**
         * Returns the status string (for compatibility).
         */
        public String status() {
            return success ? "CONNECTED" : "FAILED";
        }
    }

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
     * Schema snapshot from inference (SPI-compatible).
     */
    record SchemaSnapshot(
        String snapshotId,
        String connectorId,
        String tenantId,
        List<SchemaField> fields,
        String schemaVersion,
        Instant capturedAt,
        Map<String, Object> metadata
    ) {
        public SchemaSnapshot {
            if (fields == null) {
                fields = List.of();
            }
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    /**
     * Schema field definition (SPI-compatible).
     */
    record SchemaField(
        String name,
        String type,
        boolean nullable,
        String description,
        Map<String, Object> constraints
    ) {
        public SchemaField {
            if (constraints == null) {
                constraints = Map.of();
            }
        }
    }

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
     * Synchronization request (SPI-compatible).
     */
    record SyncRequest(
        String datasetId,
        String mode,
        Map<String, Object> filters,
        Map<String, Object> mapping,
        boolean incremental,
        String idempotencyKey
    ) {
        public SyncRequest {
            if (mode == null) {
                mode = "FULL";
            }
            if (filters == null) {
                filters = Map.of();
            }
            if (mapping == null) {
                mapping = Map.of();
            }
        }
    }

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
    ) {
        /**
         * Returns true if the sync operation succeeded.
         */
        public boolean success() {
            return success;
        }

        /**
         * Returns the sync ID (alias for jobId).
         */
        public String syncId() {
            return jobId;
        }

        /**
         * Returns the number of records processed (alias for recordsSynced).
         */
        public int recordsProcessed() {
            return recordsSynced;
        }

        /**
         * Returns the number of records inserted (0 for compatibility).
         */
        public int recordsInserted() {
            return 0;
        }

        /**
         * Returns the number of records updated (0 for compatibility).
         */
        public int recordsUpdated() {
            return 0;
        }

        /**
         * Returns the error message.
         */
        public String errorMessage() {
            return errorMessage;
        }

        /**
         * Returns the status string (for compatibility).
         */
        public String status() {
            return success ? "COMPLETED" : "FAILED";
        }

        /**
         * Returns the dataset version (null for compatibility).
         */
        public String datasetVersion() {
            return null;
        }
    }

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

    /**
     * Dataset link information.
     */
    record DatasetLink(
        String linkId,
        String connectorId,
        String datasetId,
        String tenantId,
        String syncDirection,
        String lastSyncVersion,
        Instant linkedAt,
        String linkedBy
    ) {}
}
