/**
 * Platform Integration Client
 * 
 * Client for integrating with Data Cloud+AEP platform.
 * Handles communication with platform services for execution, evidence, memory,
 * telemetry/analytics, policy/guardrails, and execution trace references.
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service interface for platform integration client.
 */
public interface PlatformIntegrationClient {

    /**
     * Executes a platform operation (agent/intelligence execution).
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
     * Searches for evidence (retrieval).
     * 
     * @param query The search query
     * @return List of search results
     */
    List<PlatformEvidence.SearchResult> searchEvidence(PlatformEvidence.SearchQuery query);

    /**
     * Stores memory in the platform (write proposal).
     * 
     * @param memory The memory to store
     * @return true if successful, false otherwise
     */
    boolean storeMemory(PlatformMemory memory);

    /**
     * Retrieves memory summary from the platform.
     * 
     * @param memoryId The memory ID
     * @return PlatformMemory containing the memory summary
     */
    PlatformMemory retrieveMemorySummary(String memoryId);

    /**
     * Retrieves full memory from the platform.
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
     * Records telemetry event (telemetry/analytics).
     * 
     * @param event The telemetry event to record
     * @return true if successful, false otherwise
     */
    boolean recordTelemetry(PlatformTelemetry event);

    /**
     * Gets analytics data for a time range (telemetry/analytics).
     * 
     * @param query The analytics query
     * @return PlatformAnalytics containing the analytics data
     */
    PlatformAnalytics getAnalytics(PlatformAnalytics.AnalyticsQuery query);

    /**
     * Evaluates policy/guardrails for a request (policy/guardrails).
     * 
     * @param request The policy evaluation request
     * @return PlatformPolicy containing the policy decision
     */
    PlatformPolicy evaluatePolicy(PlatformPolicy.PolicyRequest request);

    /**
     * Gets execution trace references (execution trace references).
     * 
     * @param executionId The execution ID
     * @return PlatformTrace containing the trace references
     */
    PlatformTrace getExecutionTrace(String executionId);

    /**
     * Stores execution trace references (execution trace references).
     * 
     * @param trace The execution trace to store
     * @return true if successful, false otherwise
     */
    boolean storeExecutionTrace(PlatformTrace trace);

    /**
     * Gets platform health status.
     * 
     * @return PlatformHealth containing health information
     */
    PlatformHealth getHealth();
}
