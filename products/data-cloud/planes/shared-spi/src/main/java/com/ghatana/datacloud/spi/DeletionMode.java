package com.ghatana.datacloud.spi;

/**
 * Deletion modes for data lifecycle management (DC-BE-004).
 *
 * <p>Defines the different ways data can be deleted from the system, ranging from immediate
 * hard deletion to staged soft delete, archival, and automated retention purge.
 *
 * <h2>Mode Descriptions</h2>
 * <ul>
 *   <li>{@link #HARD_DELETE}: Immediate removal from primary storage. Irreversible.</li>
 *   <li>{@link #SOFT_DELETE}: Mark as deleted with tombstone flag. Data remains in storage for audit/restore.</li>
 *   <li>{@link #ARCHIVE}: Move data to cold storage for long-term retention. Not accessible via normal queries.</li>
 *   <li>{@link #RETENTION_PURGE}: Automated deletion based on governance retention policies. Triggered by scheduled jobs.</li>
 * </ul>
 *
 * <h2>DC-BE-004: Deletion Lifecycle Standardization</h2>
 * This enum is the foundation for a standardized deletion lifecycle across all data planes:
 * - Entity plane (collections, entities)
 * - Event plane (event logs, event streams)
 * - Pipeline plane (pipelines, checkpoints)
 * - Governance plane (audit logs, compliance records)
 *
 * @doc.type enum
 * @doc.purpose Deletion modes for standardized data lifecycle management
 * @doc.layer spi
 * @doc.pattern Enumeration
 */
public enum DeletionMode {

    /**
     * Immediate removal from primary storage.
     *
     * <p>Data is permanently deleted and cannot be recovered. This mode is appropriate for:
     * - Development/testing environments
     * - Non-sensitive test data
     * - Explicit user requests for permanent deletion (with consent)
     *
     * <p>WARNING: This operation is irreversible. Audit logging is mandatory.
     */
    HARD_DELETE,

    /**
     * Mark as deleted with tombstone flag.
     *
     * <p>Data remains in storage but is marked as deleted with a tombstone flag and deletedAt timestamp.
     * The data is not returned by normal queries but can be restored if needed.
     * This mode is appropriate for:
     * - Production environments (default)
     * - User-initiated deletions (allow for undo/restore)
     * - Accidental deletions (recovery window)
     *
     * <p>Retention: Typically 30 days before archival or permanent deletion.
     */
    SOFT_DELETE,

    /**
     * Move to cold storage for long-term retention.
     *
     * <p>Data is moved from hot/warm storage to cold storage (e.g., S3, Glacier, archive database).
     * The data is not accessible via normal queries but can be retrieved for compliance or legal holds.
     * This mode is appropriate for:
     * - Compliance requirements (GDPR right to be forgotten with retention)
     * - Legal holds
     * - Historical analysis
     *
     * <p>Retention: Typically 1-7 years depending on governance policy.
     */
    ARCHIVE,

    /**
     * Automated deletion based on governance retention policies.
     *
     * <p>Triggered by scheduled jobs that evaluate data against retention policies.
     * This mode is appropriate for:
     * - Automated data cleanup
     * - Policy-driven retention (e.g., delete after 7 years)
     * - Tiered retention (transient -> short-term -> standard -> compliance)
     *
     * <p>This mode is typically not user-facing and is used by background jobs.
     */
    RETENTION_PURGE;
}
