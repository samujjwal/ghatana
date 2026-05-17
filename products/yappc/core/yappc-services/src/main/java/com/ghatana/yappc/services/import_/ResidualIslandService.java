/**
 * Residual Island Service
 * 
 * Service for persisting and querying residual islands with full schema support.
 * Provides database-backed persistence for residual islands with source fidelity tracking.
 * 
 * @doc.type class
 * @doc.purpose Residual island persistence with full schema
 * @doc.layer service
 * @doc.pattern Service
 * 
 * P1-20: Created database-backed residual island service with full schema support.
 * Replaces in-memory storage with proper database persistence via ArtifactGraphRepository.
 */

package com.ghatana.yappc.services.import_;

import com.ghatana.yappc.storage.ArtifactGraphRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Service for persisting residual islands with full schema support.
 * Uses database-backed storage via ArtifactGraphRepository for production-grade persistence.
 */
public final class ResidualIslandService {

    private static final Logger log = LoggerFactory.getLogger(ResidualIslandService.class);

    private final ArtifactGraphRepository repository;
    private final Executor blockingExecutor;

    public ResidualIslandService(ArtifactGraphRepository repository, Executor blockingExecutor) {
        this.repository = repository;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * Stores a residual island with full schema.
     * 
     * @param island The residual island record with full schema
     * @return Promise that completes when the island is stored
     */
    public Promise<Void> storeResidualIsland(ArtifactGraphRepository.ResidualIslandRecord island) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            repository.saveResidualIslands(
                island.projectId(),
                island.tenantId(),
                island.snapshotId(),
                List.of(island)
            ).getResult();
            log.info("Stored residual island: id={}, islandType={}, snapshotId={}", 
                island.id(), island.islandType(), island.snapshotId());
            return null;
        });
    }

    /**
     * Stores multiple residual islands with full schema.
     * 
     * @param islands The residual island records with full schema
     * @return Promise that completes when all islands are stored
     */
    public Promise<Void> storeResidualIslands(List<ArtifactGraphRepository.ResidualIslandRecord> islands) {
        if (islands == null || islands.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(blockingExecutor, () -> {
            ArtifactGraphRepository.ResidualIslandRecord first = islands.get(0);
            repository.saveResidualIslands(
                first.projectId(),
                first.tenantId(),
                first.snapshotId(),
                islands
            ).getResult();
            log.info("Stored {} residual islands", islands.size());
            return null;
        });
    }

    /**
     * Creates a ResidualIslandRecord with full schema.
     * 
     * @param id Island ID
     * @param islandType Type of the residual island
     * @param summary Summary of the island
     * @param sourceSpan Source span information
     * @param checksum Content checksum
     * @param rawFragmentRef Reference to raw fragment
     * @param reason Reason for residual classification
     * @param confidence Confidence score (0.0 to 1.0)
     * @param reviewRequired Whether review is required
     * @param riskScore Risk score (0.0 to 1.0)
     * @param fileCount Number of files in the island
     * @param metadata Additional metadata
     * @param tenantId Tenant ID
     * @param projectId Project ID
     * @param workspaceId Workspace ID
     * @param snapshotId Snapshot ID
     * @return ResidualIslandRecord with full schema
     */
    public static ArtifactGraphRepository.ResidualIslandRecord createResidualIslandRecord(
        String id,
        String islandType,
        String summary,
        String sourceSpan,
        String checksum,
        String rawFragmentRef,
        String reason,
        double confidence,
        boolean reviewRequired,
        double riskScore,
        int fileCount,
        Map<String, Object> metadata,
        String tenantId,
        String projectId,
        String workspaceId,
        String snapshotId
    ) {
        return new ArtifactGraphRepository.ResidualIslandRecord(
            id,
            islandType,
            summary,
            sourceSpan,
            checksum,
            rawFragmentRef,
            reason,
            confidence,
            reviewRequired,
            riskScore,
            fileCount,
            metadata,
            tenantId,
            projectId,
            workspaceId,
            snapshotId
        );
    }
}
