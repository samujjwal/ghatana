package com.ghatana.datacloud.entity.media;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;
import com.ghatana.core.database.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * Repository interface for Consent entities.
 *
 * <p>Provides comprehensive query methods for consent management
 * including filtering by consent type, status, giver, and validity
 * checks for compliance and privacy requirements.
 *
 * @see Consent
 * @doc.type interface
 * @doc.purpose Data access layer for media consent records
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ConsentRepository extends Repository<Consent, UUID> {
    /**
     * Finds consent records by tenant and consent ID.
     */
    Optional<Consent> findByTenantIdAndConsentId(String tenantId, String consentId);
    /**
     * Finds consent records by tenant and media artifact ID.
     */
    List<Consent> findByTenantIdAndMediaArtifactId(String tenantId, UUID mediaArtifactId);
    /**
     * Finds consent records by tenant and consent type.
     */
    List<Consent> findByTenantIdAndConsentType(String tenantId, Consent.ConsentType consentType);
    /**
     * Finds consent records by tenant and consent status.
     */
    List<Consent> findByTenantIdAndConsentStatus(String tenantId, Consent.ConsentStatus consentStatus);
    /**
     * Finds consent records by tenant and consent giver.
     */
    List<Consent> findByTenantIdAndConsentGiver(String tenantId, String consentGiver);
    /**
     * Finds consent records by tenant and consent giver type.
     */
    List<Consent> findByTenantIdAndConsentGiverType(String tenantId, String consentGiverType);
    /**
     * Finds consent records by tenant and legal basis.
     */
    List<Consent> findByTenantIdAndLegalBasis(String tenantId, String legalBasis);
    /**
     * Finds consent records that are currently valid.
     */
    List<Consent> findValidConsents(String tenantId, Instant now);
    /**
     * Finds consent records that are expired.
     */
    List<Consent> findExpiredConsents(String tenantId, Instant now);
    /**
     * Finds consent records that are revoked or withdrawn.
     */
    List<Consent> findRevokedConsents(String tenantId);
    /**
     * Finds consent records granted within date range.
     */
    List<Consent> findByGrantedAtBetween(String tenantId, 
                                        Instant startDate, 
                                        Instant endDate);
    /**
     * Finds consent records revoked within date range.
     */
    List<Consent> findByRevokedAtBetween(String tenantId, 
                                        Instant startDate, 
                                        Instant endDate);
    /**
     * Finds consent records that expire within date range.
     */
    List<Consent> findByExpiresAtBetween(String tenantId, 
                                        Instant startDate, 
                                        Instant endDate);
    /**
     * Finds consent records for specific purpose.
     */
    List<Consent> findByPurpose(String tenantId, String purpose);
    /**
     * Finds consent records with multiple purposes.
     */
    List<Consent> findByMultiplePurposes(String tenantId, Integer minPurposes);
    /**
     * Finds consent records by withdrawal method.
     */
    List<Consent> findByTenantIdAndWithdrawalMethod(String tenantId, String withdrawalMethod);
    /**
     * Finds consent records by IP address.
     */
    List<Consent> findByTenantIdAndIpAddress(String tenantId, String ipAddress);
    /**
     * Finds consent records by document reference.
     */
    List<Consent> findByTenantIdAndDocumentReference(String tenantId, String documentReference);
    /**
     * Finds consent records by parent consent ID.
     */
    List<Consent> findByTenantIdAndParentConsentId(String tenantId, String parentConsentId);
    /**
     * Finds consent records that can be modified.
     */
    List<Consent> findModifiableConsents(String tenantId);
    /**
     * Finds consent records that can be withdrawn.
     */
    List<Consent> findWithdrawableConsents(String tenantId);
    /**
     * Finds consent records expiring soon.
     */
    List<Consent> findConsentsExpiringSoon(String tenantId, 
                                          Instant now, 
                                          Integer daysAhead);
    /**
     * Finds consent records with specific metadata.
     */
    List<Consent> findByConsentMetadata(String tenantId, String metadataKey);
    /**
     * Counts consent records by tenant and consent status.
     */
    long countByTenantIdAndConsentStatus(String tenantId, Consent.ConsentStatus consentStatus);
    /**
     * Counts consent records by tenant and consent type.
     */
    long countByTenantIdAndConsentType(String tenantId, Consent.ConsentType consentType);
    /**
     * Counts valid consent records by tenant.
     */
    long countValidConsents(String tenantId, Instant now);
    /**
     * Counts expired consent records by tenant.
     */
    long countExpiredConsents(String tenantId, Instant now);
    /**
     * Finds consent records with pagination support.
     */
    Page<Consent> findByTenantId(String tenantId, PageRequest pageRequest);
    /**
     * Finds consent records by tenant and status with pagination.
     */
    Page<Consent> findByTenantIdAndConsentStatus(String tenantId, 
                                                 Consent.ConsentStatus consentStatus, 
                                                 PageRequest pageRequest);
    /**
     * Finds consent records by tenant and giver with pagination.
     */
    Page<Consent> findByTenantIdAndConsentGiver(String tenantId, 
                                                String consentGiver, 
                                                PageRequest pageRequest);
    /**
     * Gets consent status distribution for tenant.
     */
    List<Object[]> getConsentStatusDistribution(String tenantId);
    /**
     * Gets consent type distribution for tenant.
     */
    List<Object[]> getConsentTypeDistribution(String tenantId);
    /**
     * Gets purpose distribution for tenant.
     */
    List<Object[]> getPurposeDistribution(String tenantId);
    /**
     * Gets consent giver statistics for tenant.
     */
    List<Object[]> getConsentGiverStatistics(String tenantId);
    /**
     * Finds consent records for artifacts created by specific agent.
     */
    List<Consent> findByAgentId(String tenantId, String agentId);
    /**
     * Gets consent compliance metrics for tenant.
     */
    Object[] getComplianceMetrics(String tenantId);
    /**
     * Finds consent records with audit entries by action.
     */
    List<Consent> findByAuditAction(String tenantId, String action);
    /**
     * Finds consent records that need attention (expiring soon, expired, etc.).
     */
    List<Consent> findConsentsNeedingAttention(String tenantId, 
                                               Instant now, 
                                               Integer daysAhead);
    /**
     * Gets consent age distribution for tenant.
     */
    List<Object[]> getAgeDistribution(String tenantId, Instant now);
}
