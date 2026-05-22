package com.ghatana.kernel.interaction;

import java.util.Map;

/**
 * Provider contract for writing evidence records to Data Cloud.
 *
 * <p>This interface enables the Kernel to write interaction evidence to Data Cloud
 * without coupling to specific Data Cloud internals. Implementations can use HTTP clients,
 * gRPC, or other protocols to communicate with Data Cloud.</p>
 *
 * @doc.type interface
 * @doc.purpose Provider contract for writing evidence to Data Cloud
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
     * Creates a no-op client that discards all writes.
     *
     * @return a no-op client
     */
    static DataCloudEvidenceClient noop() {
        return (tenantId, workspaceId, evidenceType, evidenceId, evidenceRecord) -> {
            // Discard evidence - useful for development/testing
        };
    }
}
