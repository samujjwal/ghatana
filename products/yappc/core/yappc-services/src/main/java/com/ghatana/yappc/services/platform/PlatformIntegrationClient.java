/**
 * Platform Integration Client
 * 
 * Client for integrating with Data Cloud+AEP platform.
 * Handles communication with platform services for execution, evidence, and memory.
 * 
 * @doc.type interface
 * @doc.purpose Platform integration client
 * @doc.layer product
 * @doc.pattern Client
 */

package com.ghatana.yappc.services.platform;

import com.ghatana.yappc.api.PlatformExecution;
import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.api.PlatformMemory;

import java.util.List;
import java.util.Map;

/**
 * Service interface for platform integration client.
 */
public interface PlatformIntegrationClient {

    /**
     * Executes a platform operation.
     * 
     * @param request The execution request
     * @return PlatformExecution containing the execution result
     */
    PlatformExecution execute(PlatformExecution.ExecutionRequest request);

    /**
     * Gets execution status.
     * 
     * @param executionId The execution ID
     * @return PlatformExecution containing the current status
     */
    PlatformExecution getExecutionStatus(String executionId);

    /**
     * Stores evidence from a platform execution.
     * 
     * @param evidence The evidence to store
     * @return true if successful, false otherwise
     */
    boolean storeEvidence(PlatformEvidence evidence);

    /**
     * Searches for evidence.
     * 
     * @param query The search query
     * @return List of search results
     */
    List<PlatformEvidence.SearchResult> searchEvidence(PlatformEvidence.SearchQuery query);

    /**
     * Stores memory in the platform.
     * 
     * @param memory The memory to store
     * @return true if successful, false otherwise
     */
    boolean storeMemory(PlatformMemory memory);

    /**
     * Retrieves memory from the platform.
     * 
     * @param memoryId The memory ID
     * @return PlatformMemory containing the memory data
     */
    PlatformMemory retrieveMemory(String memoryId);

    /**
     * Deletes memory from the platform.
     * 
     * @param memoryId The memory ID
     * @return true if successful, false otherwise
     */
    boolean deleteMemory(String memoryId);

    /**
     * Gets platform health status.
     * 
     * @return PlatformHealth containing health information
     */
    PlatformHealth getHealth();
}

/**
 * Platform health status.
 */
record PlatformHealth(
    boolean isHealthy,
    String status,
    Map<String, String> components,
    String version
) {}
