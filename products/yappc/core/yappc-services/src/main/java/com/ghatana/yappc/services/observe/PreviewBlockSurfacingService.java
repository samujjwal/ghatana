/**
 * Preview Block Surfacing Service
 * 
 * Surfaces preview blocks in the Observe phase.
 * Makes preview blocks visible and actionable in Observe.
 * 
 * @doc.type interface
 * @doc.purpose Preview block surfacing in Observe
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.observe;

import com.ghatana.yappc.services.import_.PreviewSessionEnforcementService;
import java.util.List;

/**
 * Service interface for surfacing preview blocks in Observe.
 */
public interface PreviewBlockSurfacingService {

    /**
     * Gets preview blocks for a project in Observe.
     * 
     * @param projectId The project ID
     * @return List of preview blocks
     */
    List<PreviewBlock> getPreviewBlocks(String projectId);

    /**
     * Gets preview blocks for a specific import job.
     * 
     * @param importJobId The import job ID
     * @return List of preview blocks
     */
    List<PreviewBlock> getPreviewBlocksByImportJob(String importJobId);

    /**
     * Marks a preview block as reviewed in Observe.
     * 
     * @param blockId The preview block ID
     * @param reviewerId The reviewer ID
     * @param decision The review decision
     */
    void markAsReviewed(String blockId, String reviewerId, String decision);

    /**
     * Gets preview block statistics for a project.
     * 
     * @param projectId The project ID
     * @return Preview block statistics
     */
    PreviewBlockStatistics getStatistics(String projectId);
}

/**
 * Preview block representation.
 */
record PreviewBlock(
    String blockId,
    String projectId,
    String importJobId,
    String blockType,
    String description,
    BlockStatus status,
    String sessionId,
    PreviewSessionEnforcementService.TrustLevel trustLevel,
    java.time.Instant createdAt,
    java.time.Instant updatedAt
) {
    public enum BlockStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        EXPIRED
    }
}

/**
 * Preview block statistics.
 */
record PreviewBlockStatistics(
    int totalBlocks,
    int pendingBlocks,
    int approvedBlocks,
    int rejectedBlocks,
    int expiredBlocks,
    java.util.Map<String, Integer> blocksByType
) {
    public PreviewBlockStatistics {
        if (blocksByType == null) {
            blocksByType = java.util.Map.of();
        }
    }
}
