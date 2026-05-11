/**
 * Residual Island Storage Service Implementation
 * 
 * Production-grade implementation of residual island storage service.
 * Stores residual islands for unmapped areas.
 * 
 * @doc.type class
 * @doc.purpose Residual island storage implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade implementation of residual island storage service.
 * Uses in-memory storage for demonstration; should be replaced with database persistence.
 */
public final class ResidualIslandStorageServiceImpl implements ResidualIslandStorageService {

    private static final Logger log = LoggerFactory.getLogger(ResidualIslandStorageServiceImpl.class);

    // In-memory storage for demonstration - replace with database persistence
    private final Map<String, List<ResidualIsland>> importJobToIslandsMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> projectToImportJobsMap = new ConcurrentHashMap<>();

    @Override
    public void storeResidualIsland(ResidualIsland residualIsland, String projectId, String importJobId) {
        log.info("Storing residual island: componentId={}, projectId={}, importJobId={}", 
                residualIsland.componentId(), projectId, importJobId);

        importJobToIslandsMap.computeIfAbsent(importJobId, k -> new ArrayList<>()).add(residualIsland);
        projectToImportJobsMap.computeIfAbsent(projectId, k -> new ArrayList<>()).add(importJobId);

        log.info("Residual island stored successfully: componentId={}", residualIsland.componentId());
    }

    @Override
    public void storeResidualIslands(List<ResidualIsland> residualIslands, String projectId, String importJobId) {
        log.info("Storing residual islands: count={}, projectId={}, importJobId={}", 
                residualIslands.size(), projectId, importJobId);

        for (ResidualIsland island : residualIslands) {
            storeResidualIsland(island, projectId, importJobId);
        }

        log.info("Residual islands stored successfully: count={}", residualIslands.size());
    }

    @Override
    public List<ResidualIsland> getResidualIslands(String importJobId) {
        log.debug("Getting residual islands: importJobId={}", importJobId);

        List<ResidualIsland> islands = importJobToIslandsMap.getOrDefault(importJobId, List.of());
        log.debug("Retrieved residual islands: count={}", islands.size());

        return islands;
    }

    @Override
    public List<ResidualIsland> getResidualIslandsByProject(String projectId) {
        log.debug("Getting residual islands by project: projectId={}", projectId);

        List<String> importJobIds = projectToImportJobsMap.getOrDefault(projectId, List.of());
        List<ResidualIsland> allIslands = new ArrayList<>();

        for (String importJobId : importJobIds) {
            allIslands.addAll(getResidualIslands(importJobId));
        }

        log.debug("Retrieved residual islands by project: count={}", allIslands.size());
        return allIslands;
    }

    @Override
    public void deleteResidualIslands(String importJobId) {
        log.info("Deleting residual islands: importJobId={}", importJobId);

        List<ResidualIsland> removed = importJobToIslandsMap.remove(importJobId);

        if (removed != null) {
            log.info("Residual islands deleted successfully: count={}", removed.size());
        } else {
            log.warn("No residual islands found for deletion: importJobId={}", importJobId);
        }
    }

    /**
     * Gets the count of residual islands for an import job.
     * 
     * @param importJobId The import job ID
     * @return Count of residual islands
     */
    public int getResidualIslandCount(String importJobId) {
        return importJobToIslandsMap.getOrDefault(importJobId, List.of()).size();
    }

    /**
     * Checks if an import job has residual islands.
     * 
     * @param importJobId The import job ID
     * @return true if the import job has residual islands, false otherwise
     */
    public boolean hasResidualIslands(String importJobId) {
        return getResidualIslandCount(importJobId) > 0;
    }
}
