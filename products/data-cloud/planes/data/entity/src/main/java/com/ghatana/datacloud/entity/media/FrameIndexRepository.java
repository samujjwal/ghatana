package com.ghatana.datacloud.entity.media;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;
import com.ghatana.core.database.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * Repository interface for FrameIndex entities.
 *
 * <p>Provides comprehensive query methods for frame index management
 * including filtering by extraction method, quality score, frame count,
 * and visual feature search capabilities.
 *
 * @see FrameIndex
 * @doc.type interface
 * @doc.purpose Data access layer for video frame indices
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface FrameIndexRepository extends Repository<FrameIndex, UUID> {
    /**
     * Finds frame indices by tenant and frame index ID.
     */
    Optional<FrameIndex> findByTenantIdAndFrameIndexId(String tenantId, String frameIndexId);
    /**
     * Finds frame indices by tenant and media artifact ID.
     */
    List<FrameIndex> findByTenantIdAndMediaArtifactId(String tenantId, UUID mediaArtifactId);
    /**
     * Finds frame indices by tenant and processing job ID.
     */
    List<FrameIndex> findByTenantIdAndProcessingJobId(String tenantId, UUID processingJobId);
    /**
     * Finds frame indices by tenant and extraction method.
     */
    List<FrameIndex> findByTenantIdAndExtractionMethod(String tenantId, FrameIndex.ExtractionMethod extractionMethod);
    /**
     * Finds frame indices by tenant with quality score above threshold.
     */
    List<FrameIndex> findByTenantIdAndQualityScoreGreaterThanEqual(String tenantId, 
                                                                    Double threshold);
    /**
     * Finds frame indices by tenant with frame count in range.
     */
    List<FrameIndex> findByTenantIdAndTotalFramesBetween(String tenantId, 
                                                         Integer minFrames, 
                                                         Integer maxFrames);
    /**
     * Finds frame indices by tenant and resolution.
     */
    List<FrameIndex> findByTenantIdAndResolution(String tenantId, 
                                                 Integer width, 
                                                 Integer height);
    /**
     * Finds frame indices by tenant with minimum resolution.
     */
    List<FrameIndex> findByTenantIdAndMinResolution(String tenantId, 
                                                    Integer minWidth, 
                                                    Integer minHeight);
    /**
     * Finds frame indices created within date range.
     */
    List<FrameIndex> findByCreatedAtBetween(String tenantId, 
                                           Instant startDate, 
                                           Instant endDate);
    /**
     * Finds frame indices containing specific objects.
     */
    List<FrameIndex> findByDetectedObject(String tenantId, String objectClass);
    /**
     * Finds frame indices with scenes.
     */
    List<FrameIndex> findWithScenes(String tenantId);
    /**
     * Finds frame indices with visual features.
     */
    List<FrameIndex> findWithVisualFeatures(String tenantId);
    /**
     * Finds frame indices by frame interval.
     */
    List<FrameIndex> findByFrameInterval(String tenantId, Long intervalMs);
    /**
     * Finds frame indices with frame interval in range.
     */
    List<FrameIndex> findByFrameIntervalRange(String tenantId, 
                                              Long minInterval, 
                                              Long maxInterval);
    /**
     * Finds frame indices with specific scene type.
     */
    List<FrameIndex> findBySceneType(String tenantId, String sceneType);
    /**
     * Finds frame indices with multiple scenes.
     */
    List<FrameIndex> findByMultipleScenes(String tenantId, Integer minScenes);
    /**
     * Finds high-quality frame indices.
     */
    List<FrameIndex> findHighQualityIndices(String tenantId);
    /**
     * Finds frame indices with processing metadata.
     */
    List<FrameIndex> findWithProcessingMetadata(String tenantId);
    /**
     * Counts frame indices by tenant and extraction method.
     */
    long countByTenantIdAndExtractionMethod(String tenantId, FrameIndex.ExtractionMethod extractionMethod);
    /**
     * Counts frame indices by tenant with quality above threshold.
     */
    long countByTenantIdAndQualityScoreGreaterThanEqual(String tenantId, 
                                                        Double threshold);
    /**
     * Calculates average quality score by extraction method.
     */
    Double calculateAverageQualityByMethod(String tenantId, 
                                          FrameIndex.ExtractionMethod method);
    /**
     * Calculates average frame count by extraction method.
     */
    Double calculateAverageFrameCountByMethod(String tenantId, 
                                             FrameIndex.ExtractionMethod method);
    /**
     * Finds frame indices with pagination support.
     */
    Page<FrameIndex> findByTenantId(String tenantId, PageRequest pageRequest);
    /**
     * Finds frame indices by tenant and extraction method with pagination.
     */
    Page<FrameIndex> findByTenantIdAndExtractionMethod(String tenantId, 
                                                       FrameIndex.ExtractionMethod extractionMethod, 
                                                       PageRequest pageRequest);
    /**
     * Finds frame indices by tenant and quality range with pagination.
     */
    Page<FrameIndex> findByQualityRange(String tenantId, 
                                        Double minQuality, 
                                        Double maxQuality, 
                                        PageRequest pageRequest);
    /**
     * Gets extraction method distribution for tenant.
     */
    List<Object[]> getExtractionMethodDistribution(String tenantId);
    /**
     * Gets quality score distribution for tenant.
     */
    List<Object[]> getQualityDistribution(String tenantId);
    /**
     * Gets resolution distribution for tenant.
     */
    List<Object[]> getResolutionDistribution(String tenantId);
    /**
     * Finds frame indices for artifacts with specific metadata.
     */
    List<FrameIndex> findByArtifactMetadata(String tenantId, 
                                           String metadataKey);
    /**
     * Finds frame indices with specific processing parameters.
     */
    List<FrameIndex> findByProcessingParameter(String tenantId, 
                                              String parameterKey);
    /**
     * Gets frame index quality metrics for tenant.
     */
    Object[] getQualityMetrics(String tenantId);
    /**
     * Finds frame indices created from specific agent.
     */
    List<FrameIndex> findByAgentId(String tenantId, String agentId);
    /**
     * Finds frame indices with specific visual features.
     */
    List<FrameIndex> findByVisualFeature(String tenantId, String featureKey);
    /**
     * Finds frame indices with scene boundaries in time range.
     */
    List<FrameIndex> findBySceneTimeRange(String tenantId, 
                                         Long startTime, 
                                         Long endTime);
    /**
     * Gets object detection statistics for tenant.
     */
    Object[] getObjectDetectionStats(String tenantId);
    /**
     * Finds frame indices with good coverage.
     */
    List<FrameIndex> findWithGoodCoverage(String tenantId);
}
