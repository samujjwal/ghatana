package com.ghatana.appplatform.audit.retention;

import java.time.Duration;

/**
 * Retention policy configuration for audit logs.
 *
 * <p>Defines how long audit entries are kept before hard deletion or archival.
 * Policy values are typically sourced from the K-02 config engine per tenant.
 *
 * @doc.type record
 * @doc.purpose Audit log retention policy (STORY-K07-011)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RetentionPolicy(
    String tenantId,
    Duration retentionPeriod,    // how long to keep audit logs
    boolean archiveBeforeDelete, // if true, export to cold storage before deleting
    String archiveDestination    // cold storage path (e.g. S3 URI), null if archiveBeforeDelete=false
) {
    public RetentionPolicy {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        if (retentionPeriod == null || retentionPeriod.isNegative()) {
            throw new IllegalArgumentException("retentionPeriod must be positive");
        }
        if (archiveBeforeDelete && (archiveDestination == null || archiveDestination.isBlank())) {
            throw new IllegalArgumentException("archiveDestination required when archiveBeforeDelete=true");
        }
    }

    /** Default 7-year compliance retention without archival. */
    public static RetentionPolicy sevenYear(String tenantId) {
        return new RetentionPolicy(tenantId, Duration.ofDays(365 * 7), false, null);
    }

    /** Archive then delete after the specified period. */
    public static RetentionPolicy withArchive(String tenantId, Duration period, String archiveDest) {
        return new RetentionPolicy(tenantId, period, true, archiveDest);
    }
}
