package com.ghatana.datacloud.entity.media;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;
import com.ghatana.core.database.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * Repository interface for RetentionPolicy entities.
 *
 * <p>Provides comprehensive query methods for retention policy management
 * including filtering by status, priority, classification, and policy
 * application tracking for compliance and storage optimization.
 *
 * @see RetentionPolicy
 * @doc.type interface
 * @doc.purpose Data access layer for media retention policies
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface RetentionPolicyRepository extends Repository<RetentionPolicy, UUID> {
    /**
     * Finds retention policies by tenant and policy ID.
     */
    Optional<RetentionPolicy> findByTenantIdAndPolicyId(String tenantId, String policyId);
    /**
     * Finds retention policies by tenant and policy name.
     */
    Optional<RetentionPolicy> findByTenantIdAndPolicyName(String tenantId, String policyName);
    /**
     * Finds retention policies by tenant and status.
     */
    List<RetentionPolicy> findByTenantIdAndStatus(String tenantId, RetentionPolicy.PolicyStatus status);
    /**
     * Finds retention policies by tenant and priority.
     */
    List<RetentionPolicy> findByTenantIdAndPriority(String tenantId, RetentionPolicy.PolicyPriority priority);
    /**
     * Finds retention policies by tenant and classification.
     */
    List<RetentionPolicy> findByTenantIdAndClassification(String tenantId, MediaArtifact.Classification classification);
    /**
     * Finds retention policies by tenant and created by.
     */
    List<RetentionPolicy> findByTenantIdAndCreatedBy(String tenantId, String createdBy);
    /**
     * Finds retention policies by tenant and approved by.
     */
    List<RetentionPolicy> findByTenantIdAndApprovedBy(String tenantId, String approvedBy);
    /**
     * Finds retention policies that are currently active.
     */
    List<RetentionPolicy> findActivePolicies(String tenantId, Instant now);
    /**
     * Finds retention policies that are expired.
     */
    List<RetentionPolicy> findExpiredPolicies(String tenantId, Instant now);
    /**
     * Finds retention policies with auto-delete enabled.
     */
    List<RetentionPolicy> findAutoDeletePolicies(String tenantId);
    /**
     * Finds retention policies with legal hold.
     */
    List<RetentionPolicy> findLegalHoldPolicies(String tenantId);
    /**
     * Finds retention policies for specific media type.
     */
    List<RetentionPolicy> findByMediaType(String tenantId, String mediaType);
    /**
     * Finds retention policies with specific retention period.
     */
    List<RetentionPolicy> findByRetentionDays(String tenantId, Integer days);
    /**
     * Finds retention policies with retention period in range.
     */
    List<RetentionPolicy> findByRetentionDaysRange(String tenantId, 
                                                  Integer minDays, 
                                                  Integer maxDays);
    /**
     * Finds retention policies created within date range.
     */
    List<RetentionPolicy> findByCreatedAtBetween(String tenantId, 
                                               Instant startDate, 
                                               Instant endDate);
    /**
     * Finds retention policies effective within date range.
     */
    List<RetentionPolicy> findByEffectiveDateBetween(String tenantId, 
                                                   Instant startDate, 
                                                   Instant endDate);
    /**
     * Finds retention policies expiring within date range.
     */
    List<RetentionPolicy> findByExpiryDateBetween(String tenantId, 
                                                 Instant startDate, 
                                                 Instant endDate);
    /**
     * Finds retention policies by parent policy ID.
     */
    List<RetentionPolicy> findByTenantIdAndParentPolicyId(String tenantId, String parentPolicyId);
    /**
     * Finds retention policies that match a specific media artifact.
     */
    List<RetentionPolicy> findMatchingPolicies(String tenantId, 
                                              Instant now, 
                                              MediaArtifact.Classification classification, 
                                              String mediaType);
    /**
     * Finds retention policies that need attention (expiring soon, etc.).
     */
    List<RetentionPolicy> findPoliciesNeedingAttention(String tenantId, 
                                                      Instant now, 
                                                      Integer daysAhead);
    /**
     * Finds retention policies with specific metadata.
     */
    List<RetentionPolicy> findByPolicyMetadata(String tenantId, String metadataKey);
    /**
     * Finds retention policies with audit entries by action.
     */
    List<RetentionPolicy> findByAuditAction(String tenantId, String action);
    /**
     * Counts retention policies by tenant and status.
     */
    long countByTenantIdAndStatus(String tenantId, RetentionPolicy.PolicyStatus status);
    /**
     * Counts retention policies by tenant and priority.
     */
    long countByTenantIdAndPriority(String tenantId, RetentionPolicy.PolicyPriority priority);
    /**
     * Counts active retention policies by tenant.
     */
    long countActivePolicies(String tenantId, Instant now);
    /**
     * Finds retention policies with pagination support.
     */
    Page<RetentionPolicy> findByTenantId(String tenantId, PageRequest pageRequest);
    /**
     * Finds retention policies by tenant and status with pagination.
     */
    Page<RetentionPolicy> findByTenantIdAndStatus(String tenantId, 
                                                  RetentionPolicy.PolicyStatus status, 
                                                  PageRequest pageRequest);
    /**
     * Finds retention policies by tenant and priority with pagination.
     */
    Page<RetentionPolicy> findByTenantIdAndPriority(String tenantId, 
                                                    RetentionPolicy.PolicyPriority priority, 
                                                    PageRequest pageRequest);
    /**
     * Gets policy status distribution for tenant.
     */
    List<Object[]> getPolicyStatusDistribution(String tenantId);
    /**
     * Gets policy priority distribution for tenant.
     */
    List<Object[]> getPolicyPriorityDistribution(String tenantId);
    /**
     * Gets classification distribution for tenant.
     */
    List<Object[]> getClassificationDistribution(String tenantId);
    /**
     * Gets retention period distribution for tenant.
     */
    List<Object[]> getRetentionPeriodDistribution(String tenantId);
    /**
     * Gets policy application statistics for tenant.
     */
    Object[] getApplicationStatistics(String tenantId);
    /**
     * Finds most recently applied policies.
     */
    List<RetentionPolicy> findRecentlyAppliedPolicies(String tenantId, PageRequest pageRequest);
    /**
     * Finds policies with high application counts.
     */
    List<RetentionPolicy> findHighUsagePolicies(String tenantId, 
                                               Long minApplications);
    /**
     * Gets policy creator statistics for tenant.
     */
    List<Object[]> getCreatorStatistics(String tenantId);
    /**
     * Finds policies with specific consent requirements.
     */
    List<RetentionPolicy> findByConsentRequirements(String tenantId, 
                                                    Boolean requireExplicit);
    /**
     * Gets policy compliance metrics for tenant.
     */
    Object[] getComplianceMetrics(String tenantId);
    /**
     * Finds policies that will expire in the future.
     */
    List<RetentionPolicy> findFutureExpiringPolicies(String tenantId, Instant now);
    /**
     * Calculates average retention period by classification.
     */
    List<Object[]> getAverageRetentionByClassification(String tenantId);
}
