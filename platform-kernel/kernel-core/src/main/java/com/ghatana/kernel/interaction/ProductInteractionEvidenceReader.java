package com.ghatana.kernel.interaction;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Evidence reader for querying product interaction evidence.
 *
 * <p>Provides readback capabilities for interaction evidence stored by the evidence writer.
 * Supports querying by time range, contract, status, and provides schema lifecycle management
 * for evidence records.</p>
 *
 * @doc.type interface
 * @doc.purpose Query and read back product interaction evidence for audit and Studio visualization
 * @doc.layer kernel
 * @doc.pattern Repository
 */
public interface ProductInteractionEvidenceReader {

    /**
     * Retrieves a specific evidence record by ID.
     *
     * @param context trusted tenant/workspace context
     * @param evidenceId the evidence ID
     * @return the evidence record if found
     */
    Optional<ProductInteractionEvidenceRecord> get(BridgeContext context, String evidenceId);

    /**
     * Queries evidence records within a time range.
     *
     * @param context trusted tenant/workspace context
     * @param fromTimestampMs start timestamp in milliseconds
     * @param toTimestampMs end timestamp in milliseconds
     * @param limit maximum number of records to retrieve
     * @return list of evidence records
     */
    List<ProductInteractionEvidenceRecord> queryByTimeRange(
            BridgeContext context,
            long fromTimestampMs,
            long toTimestampMs,
            int limit);

    /**
     * Queries evidence records by contract ID.
     *
     * @param context trusted tenant/workspace context
     * @param contractId the contract ID
     * @param limit maximum number of records to retrieve
     * @return list of evidence records
     */
    List<ProductInteractionEvidenceRecord> queryByContract(
            BridgeContext context,
            String contractId,
            int limit);

    /**
     * Queries evidence records by status.
     *
     * @param context trusted tenant/workspace context
     * @param status the interaction status
     * @param limit maximum number of records to retrieve
     * @return list of evidence records
     */
    List<ProductInteractionEvidenceRecord> queryByStatus(
            BridgeContext context,
            ProductInteractionStatus status,
            int limit);

    /**
     * Queries evidence records for a specific product unit.
     *
     * @param context trusted tenant/workspace context
     * @param productUnitId the product unit ID
     * @param limit maximum number of records to retrieve
     * @return list of evidence records
     */
    List<ProductInteractionEvidenceRecord> queryByProductUnit(
            BridgeContext context,
            String productUnitId,
            int limit);

    /**
     * Returns the schema version for evidence records at a given timestamp.
     *
     * @param timestampMs the timestamp to check
     * @return the schema version, or null if unknown
     */
    Optional<String> getSchemaVersionAt(long timestampMs);

    /**
     * Returns all schema versions with their effective date ranges.
     *
     * @return list of schema version info
     */
    List<SchemaVersionInfo> getSchemaVersions();

    /**
     * Migrates evidence records from an old schema version to the current version.
     *
     * @param fromVersion the source schema version
     * @param toVersion the target schema version
     * @param context trusted tenant/workspace context
     * @return number of records migrated
     */
    long migrateSchema(String fromVersion, String toVersion, BridgeContext context);

    /**
     * Deletes evidence records older than the specified timestamp (cleanup).
     *
     * @param context trusted tenant/workspace context
     * @param beforeTimestampMs delete records older than this timestamp
     * @return number of records deleted
     */
    long deleteBefore(BridgeContext context, long beforeTimestampMs);

    /**
     * Evidence record representing a product interaction.
     */
    record ProductInteractionEvidenceRecord(
            String evidenceId,
            String schemaVersion,
            String contractId,
            String contractVersion,
            String providerProductId,
            String consumerProductId,
            String tenantId,
            String workspaceId,
            String productUnitId,
            String runId,
            String correlationId,
            Instant requestedAt,
            Instant completedAt,
            ProductInteractionStatus status,
            String reasonCode,
            String policyDecision,
            List<String> evidenceRefs,
            List<String> provenanceRefs,
            Instant capturedAt
    ) {
        public ProductInteractionEvidenceRecord {
            if (evidenceId == null || evidenceId.isBlank()) {
                throw new IllegalArgumentException("evidenceId must not be blank");
            }
            if (schemaVersion == null || schemaVersion.isBlank()) {
                throw new IllegalArgumentException("schemaVersion must not be blank");
            }
        }
    }

    /**
     * Schema version information with effective date range.
     */
    record SchemaVersionInfo(
            String version,
            Instant effectiveFrom,
            Instant effectiveTo,
            String description
    ) {
        public SchemaVersionInfo {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("version must not be blank");
            }
            if (effectiveFrom == null) {
                throw new IllegalArgumentException("effectiveFrom must not be null");
            }
        }
    }
}
