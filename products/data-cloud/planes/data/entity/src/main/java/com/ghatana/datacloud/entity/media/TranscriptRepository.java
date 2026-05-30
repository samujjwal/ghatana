package com.ghatana.datacloud.entity.media;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;
import com.ghatana.core.database.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * Repository interface for Transcript entities.
 *
 * <p>Provides comprehensive query methods for transcript management
 * including filtering by language, confidence score, speaker count,
 * and content search capabilities.
 *
 * @see Transcript
 * @doc.type interface
 * @doc.purpose Data access layer for media transcripts
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface TranscriptRepository extends Repository<Transcript, UUID> {
    /**
     * Finds transcripts by tenant and transcript ID.
     */
    Optional<Transcript> findByTenantIdAndTranscriptId(String tenantId, String transcriptId);
    /**
     * Finds transcripts by tenant and media artifact ID.
     */
    List<Transcript> findByTenantIdAndMediaArtifactId(String tenantId, UUID mediaArtifactId);
    /**
     * Finds transcripts by tenant and processing job ID.
     */
    List<Transcript> findByTenantIdAndProcessingJobId(String tenantId, UUID processingJobId);
    /**
     * Finds transcripts by tenant and language code.
     */
    List<Transcript> findByTenantIdAndLanguageCode(String tenantId, String languageCode);
    /**
     * Finds transcripts by tenant and detected language.
     */
    List<Transcript> findByTenantIdAndDetectedLanguage(String tenantId, String detectedLanguage);
    /**
     * Finds transcripts by tenant with confidence score above threshold.
     */
    List<Transcript> findByTenantIdAndConfidenceScoreGreaterThanEqual(String tenantId, 
                                                                      Double threshold);
    /**
     * Finds transcripts by tenant and speaker count.
     */
    List<Transcript> findByTenantIdAndSpeakerCount(String tenantId, Integer speakerCount);
    /**
     * Finds transcripts by tenant with word count in range.
     */
    List<Transcript> findByTenantIdAndWordCountBetween(String tenantId, 
                                                      Integer minWords, 
                                                      Integer maxWords);
    /**
     * Finds transcripts created within date range.
     */
    List<Transcript> findByCreatedAtBetween(String tenantId, 
                                           Instant startDate, 
                                           Instant endDate);
    /**
     * Searches transcripts by text content.
     */
    List<Transcript> searchByText(String tenantId, String searchText);
    /**
     * Searches transcripts by text content using full-text search.
     */
    List<Transcript> fullTextSearch(String tenantId, String searchText);
    /**
     * Finds transcripts by language with minimum confidence.
     */
    List<Transcript> findByLanguageAndConfidence(String tenantId, 
                                                String languageCode, 
                                                Double confidence);
    /**
     * Finds transcripts with multiple speakers.
     */
    List<Transcript> findByMultipleSpeakers(String tenantId, 
                                           Integer minSpeakers);
    /**
     * Finds transcripts by duration range.
     */
    List<Transcript> findByDurationRange(String tenantId, 
                                        Long minDuration, 
                                        Long maxDuration);
    /**
     * Finds transcripts containing specific speaker ID.
     */
    List<Transcript> findBySpeakerId(String tenantId, String speakerId);
    /**
     * Finds transcripts with alternatives for specific language.
     */
    List<Transcript> findByAlternativeLanguage(String tenantId, 
                                               String languageCode);
    /**
     * Finds high-quality transcripts.
     */
    List<Transcript> findHighQualityTranscripts(String tenantId);
    /**
     * Finds transcripts with processing metadata.
     */
    List<Transcript> findWithProcessingMetadata(String tenantId);
    /**
     * Counts transcripts by tenant and language code.
     */
    long countByTenantIdAndLanguageCode(String tenantId, String languageCode);
    /**
     * Counts transcripts by tenant and detected language.
     */
    long countByTenantIdAndDetectedLanguage(String tenantId, String detectedLanguage);
    /**
     * Counts transcripts by tenant with confidence above threshold.
     */
    long countByTenantIdAndConfidenceScoreGreaterThanEqual(String tenantId, 
                                                          Double threshold);
    /**
     * Calculates average confidence score by language.
     */
    Double calculateAverageConfidenceByLanguage(String tenantId, 
                                               String languageCode);
    /**
     * Calculates average word count by language.
     */
    Double calculateAverageWordCountByLanguage(String tenantId, 
                                              String languageCode);
    /**
     * Finds transcripts with pagination support.
     */
    Page<Transcript> findByTenantId(String tenantId, PageRequest pageRequest);
    /**
     * Finds transcripts by tenant and language with pagination.
     */
    Page<Transcript> findByTenantIdAndLanguageCode(String tenantId, 
                                                   String languageCode, 
                                                   PageRequest pageRequest);
    /**
     * Finds transcripts by tenant and confidence range with pagination.
     */
    Page<Transcript> findByConfidenceRange(String tenantId, 
                                          Double minConfidence, 
                                          Double maxConfidence, 
                                          PageRequest pageRequest);
    /**
     * Gets language distribution for tenant.
     */
    List<Object[]> getLanguageDistribution(String tenantId);
    /**
     * Gets confidence score distribution for tenant.
     */
    List<Object[]> getConfidenceDistribution(String tenantId);
    /**
     * Gets speaker count distribution for tenant.
     */
    List<Object[]> getSpeakerCountDistribution(String tenantId);
    /**
     * Finds transcripts for artifacts with specific metadata.
     */
    List<Transcript> findByArtifactMetadata(String tenantId, 
                                           String metadataKey);
    /**
     * Finds transcripts with specific processing parameters.
     */
    List<Transcript> findByProcessingParameter(String tenantId, 
                                              String parameterKey);
    /**
     * Gets transcript quality metrics for tenant.
     */
    Object[] getQualityMetrics(String tenantId);
    /**
     * Finds transcripts created from specific agent.
     */
    List<Transcript> findByAgentId(String tenantId, String agentId);
    /**
     * Finds transcripts with alternatives above confidence threshold.
     */
    List<Transcript> findByAlternativeConfidence(String tenantId, 
                                                Double threshold);
}
