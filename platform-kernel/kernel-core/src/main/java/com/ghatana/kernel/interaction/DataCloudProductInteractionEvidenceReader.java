package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.bridge.port.BridgeContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data Cloud-backed implementation of ProductInteractionEvidenceReader.
 *
 * <p>This implementation provides query capabilities for interaction evidence stored in Data Cloud,
 * enabling audit trail inspection, Studio visualization, and schema lifecycle management.</p>
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed evidence reader for query and schema lifecycle management
 * @doc.layer kernel
 * @doc.pattern Repository
 */
public final class DataCloudProductInteractionEvidenceReader implements ProductInteractionEvidenceReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String EVIDENCE_COLLECTION = "interaction-evidence";
    private static final String SCHEMA_VERSIONS_COLLECTION = "interaction-evidence-schema-versions";

    private final DataCloudEvidenceQueryClient queryClient;

    public DataCloudProductInteractionEvidenceReader(DataCloudEvidenceQueryClient queryClient) {
        this.queryClient = Objects.requireNonNull(queryClient, "queryClient must not be null");
    }

    @Override
    public Optional<ProductInteractionEvidenceRecord> get(BridgeContext context, String evidenceId) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");

        try {
            Map<String, Object> record = queryClient.getEvidence(
                    context.getTenantId(),
                    requireWorkspace(context),
                    EVIDENCE_COLLECTION,
                    evidenceId
            );
            if (record == null) {
                return Optional.empty();
            }
            if (!matchesScope(context, record)) {
                return Optional.empty();
            }
            return Optional.of(fromRecord(record));
        } catch (Exception error) {
            throw new RuntimeException(
                    String.format("Failed to retrieve evidence %s from Data Cloud for tenant=%s workspace=%s",
                            evidenceId, context.getTenantId(), requireWorkspace(context)), error);
        }
    }

    @Override
    public List<ProductInteractionEvidenceRecord> queryByTimeRange(
            BridgeContext context,
            long fromTimestampMs,
            long toTimestampMs,
            int limit) {
        Objects.requireNonNull(context, "context must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (fromTimestampMs < 0 || toTimestampMs < 0) {
            throw new IllegalArgumentException("timestamps must be non-negative");
        }
        if (fromTimestampMs > toTimestampMs) {
            throw new IllegalArgumentException("fromTimestampMs must be <= toTimestampMs");
        }

        try {
            List<Map<String, Object>> records = queryClient.queryEvidenceByTimeRange(
                    context.getTenantId(),
                    requireWorkspace(context),
                    EVIDENCE_COLLECTION,
                    fromTimestampMs,
                    toTimestampMs,
                    limit
            );

            List<ProductInteractionEvidenceRecord> results = new ArrayList<>();
            for (Map<String, Object> record : records) {
                if (matchesScope(context, record)) {
                    results.add(fromRecord(record));
                }
            }
            return results;
        } catch (Exception error) {
            throw new RuntimeException(
                    String.format("Failed to query evidence by time range from Data Cloud for tenant=%s workspace=%s",
                            context.getTenantId(), requireWorkspace(context)), error);
        }
    }

    @Override
    public List<ProductInteractionEvidenceRecord> queryByContract(
            BridgeContext context,
            String contractId,
            int limit) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(contractId, "contractId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        try {
            List<Map<String, Object>> records = queryClient.queryEvidenceByContract(
                    context.getTenantId(),
                    requireWorkspace(context),
                    EVIDENCE_COLLECTION,
                    contractId,
                    limit
            );

            List<ProductInteractionEvidenceRecord> results = new ArrayList<>();
            for (Map<String, Object> record : records) {
                if (matchesScope(context, record)) {
                    results.add(fromRecord(record));
                }
            }
            return results;
        } catch (Exception error) {
            throw new RuntimeException(
                    String.format("Failed to query evidence by contract %s from Data Cloud for tenant=%s workspace=%s",
                            contractId, context.getTenantId(), requireWorkspace(context)), error);
        }
    }

    @Override
    public List<ProductInteractionEvidenceRecord> queryByStatus(
            BridgeContext context,
            ProductInteractionStatus status,
            int limit) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        try {
            List<Map<String, Object>> records = queryClient.queryEvidenceByStatus(
                    context.getTenantId(),
                    requireWorkspace(context),
                    EVIDENCE_COLLECTION,
                    status.name(),
                    limit
            );

            List<ProductInteractionEvidenceRecord> results = new ArrayList<>();
            for (Map<String, Object> record : records) {
                if (matchesScope(context, record)) {
                    results.add(fromRecord(record));
                }
            }
            return results;
        } catch (Exception error) {
            throw new RuntimeException(
                    String.format("Failed to query evidence by status %s from Data Cloud for tenant=%s workspace=%s",
                            status, context.getTenantId(), requireWorkspace(context)), error);
        }
    }

    @Override
    public List<ProductInteractionEvidenceRecord> queryByProductUnit(
            BridgeContext context,
            String productUnitId,
            int limit) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(productUnitId, "productUnitId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        try {
            List<Map<String, Object>> records = queryClient.queryEvidenceByProductUnit(
                    context.getTenantId(),
                    requireWorkspace(context),
                    EVIDENCE_COLLECTION,
                    productUnitId,
                    limit
            );

            List<ProductInteractionEvidenceRecord> results = new ArrayList<>();
            for (Map<String, Object> record : records) {
                if (matchesScope(context, record)) {
                    results.add(fromRecord(record));
                }
            }
            return results;
        } catch (Exception error) {
            throw new RuntimeException(
                    String.format("Failed to query evidence by product unit %s from Data Cloud for tenant=%s workspace=%s",
                            productUnitId, context.getTenantId(), requireWorkspace(context)), error);
        }
    }

    @Override
    public Optional<String> getSchemaVersionAt(long timestampMs) {
        if (timestampMs < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }

        try {
            Map<String, Object> versionRecord = queryClient.getSchemaVersionAt(
                    SCHEMA_VERSIONS_COLLECTION,
                    timestampMs
            );
            if (versionRecord == null) {
                return Optional.empty();
            }
            return Optional.ofNullable((String) versionRecord.get("version"));
        } catch (Exception error) {
            throw new RuntimeException("Failed to get schema version at timestamp " + timestampMs, error);
        }
    }

    @Override
    public List<SchemaVersionInfo> getSchemaVersions() {
        try {
            List<Map<String, Object>> versionRecords = queryClient.getAllSchemaVersions(
                    SCHEMA_VERSIONS_COLLECTION
            );

            List<SchemaVersionInfo> versions = new ArrayList<>();
            for (Map<String, Object> record : versionRecords) {
                versions.add(new SchemaVersionInfo(
                        (String) record.get("version"),
                        Instant.parse((String) record.get("effectiveFrom")),
                        record.containsKey("effectiveTo") ? Instant.parse((String) record.get("effectiveTo")) : null,
                        (String) record.get("description")
                ));
            }
            return versions;
        } catch (Exception error) {
            throw new RuntimeException("Failed to get schema versions", error);
        }
    }

    @Override
    public long migrateSchema(String fromVersion, String toVersion, BridgeContext context) {
        Objects.requireNonNull(fromVersion, "fromVersion must not be null");
        Objects.requireNonNull(toVersion, "toVersion must not be null");
        Objects.requireNonNull(context, "context must not be null");

        try {
            return queryClient.migrateEvidenceSchema(
                    context.getTenantId(),
                    requireWorkspace(context),
                    EVIDENCE_COLLECTION,
                    fromVersion,
                    toVersion
            );
        } catch (Exception error) {
            throw new RuntimeException(
                    String.format("Failed to migrate schema from %s to %s for tenant=%s workspace=%s",
                            fromVersion, toVersion, context.getTenantId(), requireWorkspace(context)), error);
        }
    }

    @Override
    public long deleteBefore(BridgeContext context, long beforeTimestampMs) {
        Objects.requireNonNull(context, "context must not be null");
        if (beforeTimestampMs < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }

        try {
            return queryClient.deleteEvidenceBefore(
                    context.getTenantId(),
                    requireWorkspace(context),
                    EVIDENCE_COLLECTION,
                    beforeTimestampMs
            );
        } catch (Exception error) {
            throw new RuntimeException(
                    String.format("Failed to delete evidence before %d from Data Cloud for tenant=%s workspace=%s",
                            beforeTimestampMs, context.getTenantId(), requireWorkspace(context)), error);
        }
    }

    @SuppressWarnings("unchecked")
    private ProductInteractionEvidenceRecord fromRecord(Map<String, Object> record) {
        return new ProductInteractionEvidenceRecord(
                (String) record.get("evidenceId"),
                (String) record.get("schemaVersion"),
                (String) record.get("contractId"),
                (String) record.get("contractVersion"),
                (String) record.get("providerProductId"),
                (String) record.get("consumerProductId"),
                (String) record.get("tenantId"),
                (String) record.get("workspaceId"),
                (String) record.get("productUnitId"),
                (String) record.get("runId"),
                (String) record.get("correlationId"),
                Instant.parse((String) record.get("requestedAt")),
                Instant.parse((String) record.get("completedAt")),
                ProductInteractionStatus.valueOf((String) record.get("status")),
                (String) record.get("reasonCode"),
                (String) record.get("policyDecision"),
                (List<String>) record.get("evidenceRefs"),
                (List<String>) record.get("provenanceRefs"),
                Instant.parse((String) record.get("capturedAt"))
        );
    }

    private static String requireWorkspace(BridgeContext context) {
        String workspaceId = context.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("context workspaceId must not be blank");
        }
        return workspaceId;
    }

    private static boolean matchesScope(BridgeContext context, Map<String, Object> record) {
        return Objects.equals(context.getTenantId(), record.get("tenantId"))
                && Objects.equals(requireWorkspace(context), record.get("workspaceId"));
    }

    /**
     * Data Cloud client interface for evidence query operations.
     */
    public interface DataCloudEvidenceQueryClient {
        Map<String, Object> getEvidence(String tenantId, String workspaceId, String collection, String evidenceId);

        List<Map<String, Object>> queryEvidenceByTimeRange(
                String tenantId,
                String workspaceId,
                String collection,
                long fromTimestampMs,
                long toTimestampMs,
                int limit
        );

        List<Map<String, Object>> queryEvidenceByContract(
                String tenantId,
                String workspaceId,
                String collection,
                String contractId,
                int limit
        );

        List<Map<String, Object>> queryEvidenceByStatus(
                String tenantId,
                String workspaceId,
                String collection,
                String status,
                int limit
        );

        List<Map<String, Object>> queryEvidenceByProductUnit(
                String tenantId,
                String workspaceId,
                String collection,
                String productUnitId,
                int limit
        );

        Map<String, Object> getSchemaVersionAt(String collection, long timestampMs);

        List<Map<String, Object>> getAllSchemaVersions(String collection);

        long migrateEvidenceSchema(
                String tenantId,
                String workspaceId,
                String collection,
                String fromVersion,
                String toVersion
        );

        long deleteEvidenceBefore(
                String tenantId,
                String workspaceId,
                String collection,
                long beforeTimestampMs
        );
    }
}
