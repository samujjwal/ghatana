package com.ghatana.kernel.interaction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider contract for writing and reading evidence records to/from Data Cloud.
 *
 * <p>This interface enables the Kernel to write interaction evidence to Data Cloud
 * without coupling to specific Data Cloud internals. Implementations can use HTTP clients,
 * gRPC, or other protocols to communicate with Data Cloud.</p>
 *
 * @doc.type interface
 * @doc.purpose Provider contract for writing and reading evidence to/from Data Cloud
 * @doc.layer kernel
 * @doc.pattern Port
 */
public interface DataCloudEvidenceClient {

    /**
     * Writes an evidence record to Data Cloud.
     *
     * @param tenantId the tenant ID for the evidence
     * @param workspaceId the workspace ID for the evidence
     * @param evidenceType the type of evidence (e.g., "interaction-evidence", "lifecycle-evidence")
     * @param evidenceId the unique identifier for the evidence record
     * @param evidenceRecord the evidence record data as a map
     * @throws RuntimeException if the write operation fails
     */
    void writeEvidence(
        String tenantId,
        String workspaceId,
        String evidenceType,
        String evidenceId,
        Map<String, Object> evidenceRecord
    );

    /**
     * Reads an evidence record from Data Cloud.
     *
     * @param tenantId the tenant ID for the evidence
     * @param workspaceId the workspace ID for the evidence
     * @param evidenceType the type of evidence (e.g., "interaction-evidence", "lifecycle-evidence")
     * @param evidenceId the unique identifier for the evidence record
     * @return the evidence record if found, empty otherwise
     * @throws RuntimeException if the read operation fails
     */
    Optional<Map<String, Object>> readEvidence(
        String tenantId,
        String workspaceId,
        String evidenceType,
        String evidenceId
    );

    /**
     * Lists evidence records for a given type within a tenant/workspace.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param evidenceType the type of evidence
     * @param limit maximum number of records to return
     * @return list of evidence record metadata (IDs and timestamps)
     * @throws RuntimeException if the list operation fails
     */
    List<Map<String, String>> listEvidence(
        String tenantId,
        String workspaceId,
        String evidenceType,
        int limit
    );

    /**
     * GOV-001: Validates evidence freshness by checking if the evidence is within the allowed age.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param evidenceType the type of evidence
     * @param evidenceId the unique identifier for the evidence record
     * @param maxAge the maximum allowed age for the evidence
     * @return true if evidence exists and is fresh enough, false otherwise
     * @throws RuntimeException if the validation operation fails
     */
    default boolean isEvidenceFresh(
        String tenantId,
        String workspaceId,
        String evidenceType,
        String evidenceId,
        Duration maxAge
    ) {
        Optional<Map<String, Object>> evidence = readEvidence(tenantId, workspaceId, evidenceType, evidenceId);
        if (evidence.isEmpty()) {
            return false;
        }
        Object timestamp = evidence.get().get("capturedAt");
        if (timestamp instanceof String) {
            try {
                Instant capturedAt = Instant.parse((String) timestamp);
                return Duration.between(capturedAt, Instant.now()).compareTo(maxAge) <= 0;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * GOV-001: Validates that evidence has valid source references.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param evidenceType the type of evidence
     * @param evidenceId the unique identifier for the evidence record
     * @return true if evidence exists and has valid source references, false otherwise
     * @throws RuntimeException if the validation operation fails
     */
    default boolean hasValidSourceRefs(
        String tenantId,
        String workspaceId,
        String evidenceType,
        String evidenceId
    ) {
        Optional<Map<String, Object>> evidence = readEvidence(tenantId, workspaceId, evidenceType, evidenceId);
        if (evidence.isEmpty()) {
            return false;
        }
        Object sourceRefs = evidence.get().get("sourceRefs");
        if (sourceRefs instanceof List) {
            @SuppressWarnings("unchecked")
            List<?> refs = (List<?>) sourceRefs;
            return !refs.isEmpty() && refs.stream().allMatch(ref -> ref instanceof String && !((String) ref).isBlank());
        }
        return false;
    }

    /**
     * Creates a no-op client that discards all writes and returns empty for reads.
     *
     * @return a no-op client
     */
    static DataCloudEvidenceClient noop() {
        return new DataCloudEvidenceClient() {
            @Override
            public void writeEvidence(
                String tenantId,
                String workspaceId,
                String evidenceType,
                String evidenceId,
                Map<String, Object> evidenceRecord
            ) {
                // Discard evidence - useful for development/testing
            }

            @Override
            public Optional<Map<String, Object>> readEvidence(
                String tenantId,
                String workspaceId,
                String evidenceType,
                String evidenceId
            ) {
                return Optional.empty();
            }

            @Override
            public List<Map<String, String>> listEvidence(
                String tenantId,
                String workspaceId,
                String evidenceType,
                int limit
            ) {
                return List.of();
            }
        };
    }
}
