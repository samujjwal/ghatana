package com.ghatana.datacloud.entity.media;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;
import com.ghatana.core.database.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * Repository interface for MediaArtifact entities.
 *
 * <p>Provides comprehensive query methods for media artifact management
 * including filtering by media type, classification, consent status,
 * and retention policies.
 *
 * @see MediaArtifact
 * @doc.type interface
 * @doc.purpose Data access layer for media artifacts
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface MediaArtifactRepository extends Repository<MediaArtifact, UUID> {
    /**
     * Finds media artifacts by tenant and artifact ID.
     */
    Optional<MediaArtifact> findByTenantIdAndArtifactId(String tenantId, String artifactId);
    /**
     * Finds media artifacts by tenant and agent ID.
     */
    List<MediaArtifact> findByTenantIdAndAgentId(String tenantId, String agentId);
    /**
     * Finds media artifacts by tenant and media type.
     */
    List<MediaArtifact> findByTenantIdAndMediaType(String tenantId, String mediaType);
    /**
     * Finds media artifacts by tenant and classification.
     */
    List<MediaArtifact> findByTenantIdAndClassification(String tenantId, MediaArtifact.Classification classification);
    /**
     * Finds media artifacts by tenant and consent status.
     */
    List<MediaArtifact> findByTenantIdAndConsentStatus(String tenantId, MediaArtifact.ConsentStatus consentStatus);
    /**
     * Finds media artifacts by tenant and owner ID.
     */
    List<MediaArtifact> findByTenantIdAndOwnerId(String tenantId, String ownerId);
    /**
     * Finds media artifacts by tenant and source system.
     */
    List<MediaArtifact> findByTenantIdAndSourceSystem(String tenantId, String sourceSystem);
    /**
     * Finds media artifacts that are expired.
     */
    List<MediaArtifact> findExpiredArtifacts(Instant now);
    /**
     * Finds media artifacts that require consent.
     */
    List<MediaArtifact> findArtifactsRequiringConsent(String tenantId);
    /**
     * Finds media artifacts with valid consent.
     */
    List<MediaArtifact> findArtifactsWithValidConsent(String tenantId);
    /**
     * Finds media artifacts by media type pattern.
     */
    List<MediaArtifact> findByMediaTypePattern(String tenantId, String mediaTypePattern);
    /**
     * Finds media artifacts created within date range.
     */
    List<MediaArtifact> findByCreatedAtBetween(String tenantId, 
                                               Instant startDate, 
                                               Instant endDate);
    /**
     * Finds media artifacts by size range.
     */
    List<MediaArtifact> findBySizeRange(String tenantId, 
                                        Long minSize, 
                                        Long maxSize);
    /**
     * Finds media artifacts by duration range.
     */
    List<MediaArtifact> findByDurationRange(String tenantId, 
                                           Long minDuration, 
                                           Long maxDuration);
    /**
     * Finds media artifacts by origin tool ID.
     */
    List<MediaArtifact> findByTenantIdAndOriginToolId(String tenantId, String originToolId);
    /**
     * Finds media artifacts by correlation ID.
     */
    List<MediaArtifact> findByTenantIdAndCorrelationId(String tenantId, String correlationId);
    /**
     * Finds media artifacts with processing jobs.
     */
    List<MediaArtifact> findArtifactsWithProcessingJobs(String tenantId);
    /**
     * Finds media artifacts by processing job type.
     */
    List<MediaArtifact> findByProcessingJobType(String tenantId, 
                                                MediaProcessingJob.JobType jobType);
    /**
     * Finds media artifacts with completed processing jobs.
     */
    List<MediaArtifact> findArtifactsWithCompletedJobs(String tenantId);
    /**
     * Finds media artifacts with failed processing jobs.
     */
    List<MediaArtifact> findArtifactsWithFailedJobs(String tenantId);
    /**
     * Finds media artifacts by retention policy.
     */
    List<MediaArtifact> findByRetentionPolicy(String tenantId, 
                                              String retentionPolicy);
    /**
     * Counts media artifacts by tenant and classification.
     */
    long countByTenantIdAndClassification(String tenantId, MediaArtifact.Classification classification);
    /**
     * Counts media artifacts by tenant and media type.
     */
    long countByTenantIdAndMediaType(String tenantId, String mediaType);
    /**
     * Counts media artifacts by tenant and consent status.
     */
    long countByTenantIdAndConsentStatus(String tenantId, MediaArtifact.ConsentStatus consentStatus);
    /**
     * Counts expired artifacts by tenant.
     */
    long countExpiredArtifacts(String tenantId, Instant now);
    /**
     * Calculates total storage used by tenant.
     */
    Long calculateTotalStorageUsed(String tenantId);
    /**
     * Calculates storage used by media type for tenant.
     */
    Long calculateStorageUsedByMediaType(String tenantId, String mediaType);
    /**
     * Finds artifacts with pagination support.
     */
    Page<MediaArtifact> findByTenantId(String tenantId, PageRequest pageRequest);
    /**
     * Finds artifacts by tenant and classification with pagination.
     */
    Page<MediaArtifact> findByTenantIdAndClassification(String tenantId, 
                                                        MediaArtifact.Classification classification, 
                                                        PageRequest pageRequest);
    /**
     * Finds artifacts by tenant and media type with pagination.
     */
    Page<MediaArtifact> findByTenantIdAndMediaType(String tenantId, 
                                                   String mediaType, 
                                                   PageRequest pageRequest);
    /**
     * Searches artifacts by text in metadata.
     */
    List<MediaArtifact> searchByText(String tenantId, String searchText);
    /**
     * Finds artifacts with lineage containing parent artifact ID.
     */
    List<MediaArtifact> findByLineageParent(String tenantId, 
                                           String parentArtifactId);
    /**
     * Finds artifacts created by specific agent within date range.
     */
    List<MediaArtifact> findByAgentAndDateRange(String tenantId, 
                                                String agentId, 
                                                Instant startDate, 
                                                Instant endDate);
    /**
     * Finds artifacts that need attention (expired consent, failed jobs, etc.).
     */
    List<MediaArtifact> findArtifactsNeedingAttention(String tenantId, 
                                                      Instant now);
}
