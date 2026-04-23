package com.ghatana.orchestrator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.orchestrator.cache.PipelineCache;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.config.OrchestratorConfig;
import com.ghatana.orchestrator.loader.SpecFormatLoader;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link Orchestrator}.
 *
 * <p>All async operations are executed within a managed ActiveJ Eventloop via
 * {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)} to prevent // GH-90000
 * NPE from unresolved Promises.
 */
@DisplayName("Orchestrator")
class OrchestratorTest extends EventloopTestBase {

    @Mock
    private PipelineCache pipelineCache;

    @Mock
    private AgentRegistryClient agentRegistryClient;

    @Mock
    private PipelineRegistryClient pipelineRegistryClient;

    @Mock
    private OrchestratorConfig config;

    @Mock
    private MetricsCollector metrics;

    @Mock
    private SpecFormatLoader specFormatLoader;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000

        // Mock configuration
        when(config.getRefreshInterval()).thenReturn(Duration.ofMinutes(5)); // GH-90000
        when(config.getMaxConcurrentRefreshes()).thenReturn(3); // GH-90000

        orchestrator = new Orchestrator( // GH-90000
                pipelineCache, agentRegistryClient, pipelineRegistryClient, config, metrics, specFormatLoader);
    }

    @Test
    @DisplayName("start() loads pipelines and records startup timer")
    void testStart() { // GH-90000
        when(pipelineRegistryClient.listAllPipelines()).thenReturn(Promise.of(Collections.emptyList())); // GH-90000
        when(pipelineCache.putAll(any())).thenReturn(Promise.of(null)); // GH-90000

        runPromise(() -> orchestrator.start()); // GH-90000

        verify(pipelineRegistryClient).listAllPipelines(); // GH-90000
        verify(metrics).recordTimer("orch.startup", 0L); // GH-90000
    }

    @Test
    @DisplayName("stop() clears pipeline cache")
    void testStop() { // GH-90000
        // Must start before stop will do anything
        when(pipelineRegistryClient.listAllPipelines()).thenReturn(Promise.of(Collections.emptyList())); // GH-90000
        when(pipelineCache.putAll(any())).thenReturn(Promise.of(null)); // GH-90000
        runPromise(() -> orchestrator.start()); // GH-90000

        when(pipelineCache.clear()).thenReturn(Promise.of(null)); // GH-90000
        runPromise(() -> orchestrator.stop()); // GH-90000

        verify(pipelineCache).clear(); // GH-90000
    }

    @Test
    @DisplayName("isHealthy() delegates to registry and agent clients")
    void testHealthCheck() { // GH-90000
        when(agentRegistryClient.isHealthy()).thenReturn(Promise.of(true)); // GH-90000
        when(pipelineRegistryClient.isHealthy()).thenReturn(Promise.of(true)); // GH-90000

        Boolean healthy = runPromise(() -> orchestrator.isHealthy()); // GH-90000

        assertNotNull(healthy); // GH-90000
    }

    @Test
    @DisplayName("getStatus() reflects cached pipeline count")
    void testGetStatus() { // GH-90000
        when(pipelineCache.size()).thenReturn(5); // GH-90000

        Orchestrator.OrchestratorStatus status = orchestrator.getStatus(); // GH-90000

        assertNotNull(status); // GH-90000
        assertEquals(5, status.getCachedPipelines()); // GH-90000
    }

    @Test
    @DisplayName("getPipeline() retrieves entity from cache")
    void testGetPipeline() { // GH-90000
        String pipelineId = "test-pipeline";
        OrchestratorPipelineEntity mockPipeline = new OrchestratorPipelineEntity(); // GH-90000
        mockPipeline.id = pipelineId;

        when(pipelineCache.get(pipelineId)).thenReturn(Promise.of(Optional.of(mockPipeline))); // GH-90000

        OrchestratorPipelineEntity result = runPromise(() -> orchestrator.getPipeline(pipelineId)); // GH-90000

        assertNotNull(result); // GH-90000
        verify(pipelineCache).get(pipelineId); // GH-90000
    }

    @Test
    @DisplayName("listPipelines() returns all cached pipelines")
    void testListPipelines() { // GH-90000
        when(pipelineCache.getAllPipelines()).thenReturn(Promise.of(Collections.emptyList())); // GH-90000

        java.util.List<OrchestratorPipelineEntity> result = runPromise(() -> orchestrator.listPipelines()); // GH-90000

        assertNotNull(result); // GH-90000
        verify(pipelineCache).getAllPipelines(); // GH-90000
    }
}
