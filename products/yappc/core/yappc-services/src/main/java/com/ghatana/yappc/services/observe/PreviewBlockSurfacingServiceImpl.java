/**
 * Preview Block Surfacing Service Implementation
 * 
 * Production-grade implementation of preview block surfacing service.
 * Surfaces preview blocks in the Observe phase.
 * 
 * @doc.type class
 * @doc.purpose Preview block surfacing implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.observe;

import com.ghatana.yappc.services.import_.PreviewSessionEnforcementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-grade implementation of preview block surfacing service.
 * Uses in-memory storage for demonstration; should be replaced with database persistence.
 */
public final class PreviewBlockSurfacingServiceImpl implements PreviewBlockSurfacingService {

    private static final Logger log = LoggerFactory.getLogger(PreviewBlockSurfacingServiceImpl.class);

    private final PreviewSessionEnforcementService previewSessionService;

    // In-memory storage for demonstration - replace with database persistence
    private final Map<String, List<PreviewBlock>> projectToBlocksMap = new ConcurrentHashMap<>();
    private final Map<String, PreviewBlock> blocks = new ConcurrentHashMap<>();

    public PreviewBlockSurfacingServiceImpl(PreviewSessionEnforcementService previewSessionService) {
        this.previewSessionService = previewSessionService;
    }

    @Override
    public List<PreviewBlock> getPreviewBlocks(String projectId) {
        log.debug("Getting preview blocks: projectId={}", projectId);

        List<PreviewBlock> projectBlocks = projectToBlocksMap.getOrDefault(projectId, List.of());

        // Filter out expired blocks
        List<PreviewBlock> activeBlocks = projectBlocks.stream()
                .filter(block -> block.status() != PreviewBlock.BlockStatus.EXPIRED)
                .collect(Collectors.toList());

        log.debug("Preview blocks retrieved: total={}, active={}", projectBlocks.size(), activeBlocks.size());
        return activeBlocks;
    }

    @Override
    public List<PreviewBlock> getPreviewBlocksByImportJob(String importJobId) {
        log.debug("Getting preview blocks by import job: importJobId={}", importJobId);

        List<PreviewBlock> jobBlocks = blocks.values().stream()
                .filter(block -> block.importJobId().equals(importJobId))
                .collect(Collectors.toList());

        log.debug("Preview blocks retrieved: count={}", jobBlocks.size());
        return jobBlocks;
    }

    @Override
    public void markAsReviewed(String blockId, String reviewerId, String decision) {
        log.info("Marking preview block as reviewed: blockId={}, reviewerId={}, decision={}", 
                blockId, reviewerId, decision);

        PreviewBlock block = blocks.get(blockId);

        if (block == null) {
            log.warn("Preview block not found: blockId={}", blockId);
            throw new IllegalArgumentException("Preview block not found: " + blockId);
        }

        PreviewBlock.BlockStatus status = switch (decision.toLowerCase()) {
            case "approve" -> PreviewBlock.BlockStatus.APPROVED;
            case "reject" -> PreviewBlock.BlockStatus.REJECTED;
            default -> PreviewBlock.BlockStatus.PENDING_REVIEW;
        };

        PreviewBlock updatedBlock = new PreviewBlock(
                block.blockId(),
                block.projectId(),
                block.importJobId(),
                block.blockType(),
                block.description(),
                status,
                block.sessionId(),
                block.trustLevel(),
                block.createdAt(),
                Instant.now()
        );

        blocks.put(blockId, updatedBlock);

        // Update project map
        List<PreviewBlock> projectBlocks = projectToBlocksMap.get(block.projectId());
        if (projectBlocks != null) {
            for (int i = 0; i < projectBlocks.size(); i++) {
                if (projectBlocks.get(i).blockId().equals(blockId)) {
                    projectBlocks.set(i, updatedBlock);
                    break;
                }
            }
        }

        log.info("Preview block marked as reviewed successfully: blockId={}, status={}", blockId, status);
    }

    @Override
    public PreviewBlockStatistics getStatistics(String projectId) {
        log.debug("Getting preview block statistics: projectId={}", projectId);

        List<PreviewBlock> projectBlocks = projectToBlocksMap.getOrDefault(projectId, List.of());

        int totalBlocks = projectBlocks.size();
        int pendingBlocks = (int) projectBlocks.stream()
                .filter(block -> block.status() == PreviewBlock.BlockStatus.PENDING_REVIEW)
                .count();
        int approvedBlocks = (int) projectBlocks.stream()
                .filter(block -> block.status() == PreviewBlock.BlockStatus.APPROVED)
                .count();
        int rejectedBlocks = (int) projectBlocks.stream()
                .filter(block -> block.status() == PreviewBlock.BlockStatus.REJECTED)
                .count();
        int expiredBlocks = (int) projectBlocks.stream()
                .filter(block -> block.status() == PreviewBlock.BlockStatus.EXPIRED)
                .count();

        Map<String, Integer> blocksByType = projectBlocks.stream()
                .collect(Collectors.groupingBy(PreviewBlock::blockType, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        return new PreviewBlockStatistics(
                totalBlocks,
                pendingBlocks,
                approvedBlocks,
                rejectedBlocks,
                expiredBlocks,
                blocksByType
        );
    }

    /**
     * Creates a preview block from an import job.
     * 
     * @param importJobId The import job ID
     * @param projectId The project ID
     * @param sessionId The preview session ID
     * @param blockType The block type
     * @param description The block description
     */
    public void createPreviewBlock(String importJobId, String projectId, String sessionId, 
            String blockType, String description) {
        String blockId = "block-" + java.util.UUID.randomUUID().toString();

        log.info("Creating preview block: blockId={}, importJobId={}, blockType={}", 
                blockId, importJobId, blockType);

        PreviewSessionEnforcementService.TrustLevel trustLevel = 
                previewSessionService.getTrustLevel(sessionId);

        PreviewBlock block = new PreviewBlock(
                blockId,
                projectId,
                importJobId,
                blockType,
                description,
                PreviewBlock.BlockStatus.PENDING_REVIEW,
                sessionId,
                trustLevel,
                Instant.now(),
                Instant.now()
        );

        blocks.put(blockId, block);
        projectToBlocksMap.computeIfAbsent(projectId, k -> new ArrayList<>()).add(block);

        log.info("Preview block created successfully: blockId={}", blockId);
    }
}
