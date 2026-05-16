package com.ghatana.datacloud.entity.kernel;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Record for Kernel lifecycle provider data storage.
 *
 * <p><b>Purpose</b><br>
 * Stores Kernel provider records (events, artifacts, health snapshots, approvals,
 * provenance, memory, runtime truth) with tenant isolation, privacy classification,
 * and retention support.
 *
 * <p><b>Multi-Tenancy</b><br>
 * All records are tenant-scoped. Cross-tenant access is prevented at the repository level.
 *
 * <p><b>Privacy & Retention</b><br>
 * Supports privacy classification (public, internal, restricted) and retention
 * expiration for compliance with data governance policies.
 *
 * @doc.type record
 * @doc.purpose Kernel provider data storage record
 * @doc.layer product
 * @doc.pattern Entity Record
 */
public record KernelProviderRecord(
    /**
     * Unique identifier for the provider record.
     */
    UUID id,

    /**
     * Tenant identifier for multi-tenancy isolation.
     */
    String tenantId,

    /**
     * Workspace identifier for scope isolation.
     */
    String workspaceId,

    /**
     * Project identifier for scope isolation.
     */
    String projectId,

    /**
     * Provider type (events, artifacts, health, approvals, provenance, memory, runtime-truth).
     */
    String providerType,

    /**
     * Reference ID for the provider record (external identifier).
     */
    String providerRef,

    /**
     * The actual provider data payload.
     */
    Map<String, Object> data,

    /**
     * Privacy classification for the record (public, internal, restricted).
     */
    String privacyClassification,

    /**
     * Retention expiration timestamp (null for no expiration).
     */
    Instant expiresAt,

    /**
     * Record creation timestamp.
     */
    Instant createdAt,

    /**
     * User ID who created the record.
     */
    String createdBy
) {
    /**
     * Creates a new KernelProviderRecord with generated ID and timestamp.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param projectId project identifier
     * @param providerType provider type
     * @param providerRef provider reference
     * @param data provider data payload
     * @param privacyClassification privacy classification
     * @param expiresAt retention expiration
     * @param createdBy user who created the record
     * @return new KernelProviderRecord
     */
    public static KernelProviderRecord create(
        String tenantId,
        String workspaceId,
        String projectId,
        String providerType,
        String providerRef,
        Map<String, Object> data,
        String privacyClassification,
        Instant expiresAt,
        String createdBy
    ) {
        return new KernelProviderRecord(
            UUID.randomUUID(),
            tenantId,
            workspaceId,
            projectId,
            providerType,
            providerRef,
            data,
            privacyClassification,
            expiresAt,
            Instant.now(),
            createdBy
        );
    }

    /**
     * Checks if the record has expired based on retention policy.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the record is restricted privacy.
     *
     * @return true if restricted, false otherwise
     */
    public boolean isRestricted() {
        return "restricted".equals(privacyClassification);
    }
}
