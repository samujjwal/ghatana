package com.ghatana.plugin.audit;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Audit Trail Plugin - Immutable, tamper-evident audit logging.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Hash chain verification</li>
 *   <li>Cryptographic signing</li>
 *   <li>Regulatory compliance (SOX, HIPAA, PCI-DSS)</li>
 *   <li>Cross-product audit trails</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Audit trail plugin interface
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.0.0
 */
public interface AuditTrailPlugin extends Plugin {

    /**
     * Logs an audit event.
     *
     * @param entityId the entity identifier
     * @param action the action performed
     * @param details the event details
     * @return Promise containing the audit entry
     */
    Promise<AuditEntry> logEvent(String entityId, String action, Map<String, Object> details);

    /**
     * Gets the audit trail for an entity.
     *
     * @param entityId the entity identifier
     * @return Promise containing audit entries
     */
    Promise<List<AuditEntry>> getTrail(String entityId);

    /**
     * Verifies the integrity of an audit trail.
     *
     * @param entityId the entity identifier
     * @return Promise containing verification result
     */
    Promise<VerificationResult> verifyIntegrity(String entityId);

    /**
     * Exports an audit trail.
     *
     * @param entityId the entity identifier
     * @param format the export format
     * @param out the output stream
     * @return Promise completing when export is done
     */
    Promise<Void> exportTrail(String entityId, ExportFormat format, OutputStream out);

    /**
     * Purges audit entries older than the given cutoff timestamp (retention policy enforcement).
     *
     * <p>Entries whose {@code timestamp} is strictly before {@code cutoffEpochMs} are deleted.
     * This method is intended for regulatory retention policy enforcement and should only be
     * called after confirming the retention period has elapsed.
     *
     * @param cutoffEpochMs epoch milliseconds; entries older than this are removed
     * @return Promise containing the number of entries deleted
     */
    Promise<Integer> purgeEntriesOlderThan(long cutoffEpochMs);

    /**
     * Export formats.
     */
    enum ExportFormat {
        JSON,
        CSV,
        PDF,
        XML
    }

    /**
     * Audit entry.
     */
    record AuditEntry(
        String entryId,
        String entityId,
        String action,
        Map<String, Object> details,
        String actorId,
        String hash,
        String previousHash,
        Instant timestamp
    ) {}

    /**
     * Verification result.
     */
    record VerificationResult(
        String entityId,
        boolean valid,
        int entryCount,
        List<String> violations,
        Instant verifiedAt
    ) {}
}
