/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.fabric;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Pass 7: Production-ready connector runtime contract for Data Cloud.
 *
 * <p>Provides comprehensive connector lifecycle management including:
 * <ul>
 *   <li>Connection testing</li>
 *   <li>Schema inference</li>
 *   <li>Data synchronization</li>
 *   <li>Health monitoring</li>
 *   <li>Credential rotation</li>
 *   <li>Dataset linkage</li>
 *   <li>Configuration redaction</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Production connector runtime contract
 * @doc.layer product
 * @doc.pattern Service Provider Interface
 */
public interface DataFabricConnector {

    /**
     * Tests the connection to the data source.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing connection test result
     */
    Promise<ConnectionTestResult> testConnection(String tenantId, String connectorId);

    /**
     * Infers schema from the data source.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param options schema inference options (optional)
     * @return Promise containing inferred schema
     */
    Promise<SchemaSnapshot> inferSchema(String tenantId, String connectorId, Map<String, Object> options);

    /**
     * Synchronizes data from source to target dataset.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param request sync request configuration (required)
     * @return Promise containing sync operation result
     */
    Promise<SyncResult> sync(String tenantId, String connectorId, SyncRequest request);

    /**
     * Gets the current synchronization status.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing sync status
     */
    Promise<SyncStatus> getSyncStatus(String tenantId, String connectorId);

    /**
     * Gets the current health status of the connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing health status
     */
    Promise<ConnectorHealth> getHealth(String tenantId, String connectorId);

    /**
     * Rotates credentials for the connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param request credential rotation request (required)
     * @return Promise containing rotation result
     */
    Promise<RotationResult> rotateCredentials(String tenantId, String connectorId, RotationRequest request);

    /**
     * Links connector to a dataset.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param datasetId the dataset ID to link (required)
     * @param userId the user performing the link (for audit)
     * @return Promise containing link result
     */
    Promise<DatasetLink> linkDataset(String tenantId, String connectorId, String datasetId, String userId);

    /**
     * Gets dataset link information.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing dataset link or null if not linked
     */
    Promise<DatasetLink> getDatasetLink(String tenantId, String connectorId);

    /**
     * Redacts sensitive configuration values.
     *
     * @param configuration the configuration map (required)
     * @return Map with sensitive values redacted
     */
    Map<String, Object> redactConfig(Map<String, Object> configuration);

    /**
     * Gets connector capabilities.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing connector capabilities
     */
    Promise<ConnectorCapabilities> getCapabilities(String tenantId, String connectorId);

    // -------------------------------------------------------------------------
    // Record Definitions
    // -------------------------------------------------------------------------

    /**
     * Connection test result.
     */
    record ConnectionTestResult(
            boolean success,
            String status,
            String message,
            long responseTimeMs,
            Map<String, Object> details,
            Instant timestamp
    ) {
        public ConnectionTestResult {
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (details == null) {
                details = Map.of();
            }
        }
    }

    /**
     * Schema snapshot from inference.
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
     * Schema field definition.
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
     * Synchronization request.
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
     * Synchronization result.
     */
    record SyncResult(
            String syncId,
            String connectorId,
            String datasetId,
            String status,
            long recordsProcessed,
            long recordsInserted,
            long recordsUpdated,
            long recordsFailed,
            String errorMessage,
            Instant startedAt,
            Instant completedAt,
            String datasetVersion
    ) {
        /**
         * Returns true if the sync operation succeeded.
         */
        public boolean success() {
            return "COMPLETED".equals(status) || "SUCCESS".equals(status);
        }
    }

    /**
     * Synchronization status.
     */
    record SyncStatus(
            String syncId,
            String connectorId,
            SyncState state,
            long recordsProcessed,
            long recordsTotal,
            double progressPercent,
            String currentOperation,
            String errorMessage,
            Instant startedAt,
            Instant lastUpdatedAt
    ) {}

    /**
     * Sync state enumeration.
     */
    enum SyncState {
        IDLE,
        QUEUED,
        RUNNING,
        FAILED,
        COMPLETED
    }

    /**
     * Connector health information.
     */
    record ConnectorHealth(
            String connectorId,
            String tenantId,
            HealthStatus status,
            String lastError,
            Instant lastHealthCheck,
            Instant lastSuccessfulSync,
            long totalSyncs,
            long failedSyncs,
            double averageSyncTimeMs,
            boolean credentialsValid,
            boolean credentialsExpiringSoon
    ) {}

    /**
     * Health status enumeration.
     */
    enum HealthStatus {
        UNKNOWN,
        HEALTHY,
        DEGRADED,
        FAILED,
        CREDENTIALS_EXPIRED
    }

    /**
     * Credential rotation request.
     */
    record RotationRequest(
            String rotationType,
            String newCredentialValue,
            boolean testBeforeApply,
            String scheduledFor
    ) {}

    /**
     * Credential rotation result.
     */
    record RotationResult(
            boolean success,
            String operationId,
            String previousCredentialId,
            String newCredentialId,
            Instant rotatedAt,
            String rotatedBy,
            String message
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

    /**
     * Connector capabilities.
     */
    record ConnectorCapabilities(
            String connectorId,
            String connectorType,
            boolean supportsStreaming,
            boolean supportsBatch,
            boolean supportsIncremental,
            boolean supportsSchemaInference,
            boolean supportsTwoWaySync,
            List<String> supportedSyncModes,
            List<String> supportedAuthMethods,
            Map<String, Object> limits
    ) {
        public ConnectorCapabilities {
            if (supportedSyncModes == null) {
                supportedSyncModes = List.of();
            }
            if (supportedAuthMethods == null) {
                supportedAuthMethods = List.of();
            }
            if (limits == null) {
                limits = Map.of();
            }
        }
    }
}
