package com.ghatana.datacloud.governance;

/**
 * Service interface for checking tenant-level quotas.
 * Implementations may be in-memory, database-backed, or distributed.
 */
public interface TenantQuotaService {

    /**
     * Check whether a tenant operation is within quota.
     *
     * @param tenantId        the tenant identifier
     * @param operationType   the type of operation (e.g., "ENTITY", "STORAGE", "REQUEST", "EVENT")
     * @param resourceAmount  the amount of resource being consumed
     * @return a {@link QuotaCheckResult} indicating allowance or denial
     */
    QuotaCheckResult checkQuota(String tenantId, String operationType, int resourceAmount);
}
