/**
 * Residual Island Storage Service
 * 
 * Stores residual islands for unmapped areas.
 * Tracks components that could not be mapped to registry contracts.
 * 
 * @doc.type interface
 * @doc.purpose Residual island storage
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import java.util.List;

/**
 * Service interface for storing residual islands.
 */
public interface ResidualIslandStorageService {

    /**
     * Stores a residual island.
     * 
     * @param residualIsland The residual island to store
     * @param projectId The project ID
     * @param importJobId The import job ID
     */
    void storeResidualIsland(ResidualIsland residualIsland, String projectId, String importJobId);

    /**
     * Stores multiple residual islands.
     * 
     * @param residualIslands The residual islands to store
     * @param projectId The project ID
     * @param importJobId The import job ID
     */
    void storeResidualIslands(List<ResidualIsland> residualIslands, String projectId, String importJobId);

    /**
     * Retrieves residual islands for an import job.
     * 
     * @param importJobId The import job ID
     * @return List of residual islands for the import job
     */
    List<ResidualIsland> getResidualIslands(String importJobId);

    /**
     * Retrieves residual islands for a project.
     * 
     * @param projectId The project ID
     * @return List of residual islands for the project
     */
    List<ResidualIsland> getResidualIslandsByProject(String projectId);

    /**
     * Deletes residual islands for an import job.
     * 
     * @param importJobId The import job ID
     */
    void deleteResidualIslands(String importJobId);
}
