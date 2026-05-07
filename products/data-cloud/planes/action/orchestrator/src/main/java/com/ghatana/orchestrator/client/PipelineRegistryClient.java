package com.ghatana.orchestrator.client;

import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;

/**
 * Client interface for communicating with the Pipeline Registry service.
 *
 * Day 24 Implementation: Client for pipeline loading from registry
  * @doc.type interface
 * @doc.purpose Provides pipeline registry client functionality.
 * @doc.layer product
 * @doc.pattern Registry
*/
public interface PipelineRegistryClient {

    /**
     * List all active pipelines from the registry.
     */
    Promise<List<OrchestratorPipelineEntity>> listAllPipelines();

    /**
     * Get a specific pipeline by ID.
     */
    Promise<Optional<OrchestratorPipelineEntity>> getPipeline(String pipelineId);

    /**
     * List pipelines for a specific tenant.
     */
    Promise<List<OrchestratorPipelineEntity>> listPipelinesForTenant(String tenantId);

    /**
     * Check if the client connection is healthy.
     */
    Promise<Boolean> isHealthy();
}
