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
 * {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)} to prevent
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
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock configuration
        when(config.getRefreshInterval()).thenReturn(Duration.ofMinutes(5));
        when(config.getMaxConcurrentRefreshes()).thenReturn(3);

        orchestrator = new Orchestrator(
                pipelineCache, agentRegistryClient, pipelineRegistryClient, config, metrics, specFormatLoader);
    }

    @Test
    @DisplayName("start() loads pipelines and records startup timer")
    void testStart() {
        when(pipelineRegistryClient.listAllPipelines()).thenReturn(Promise.of(Collections.emptyList()));
        when(pipelineCache.putAll(any())).thenReturn(Promise.of(null));

        runPromise(() -> orchestrator.start());

        verify(pipelineRegistryClient).listAllPipelines();
        verify(metrics).recordTimer("orch.startup", 0L);
    }

    @Test
    @DisplayName("stop() clears pipeline cache")
    void testStop() {
        // Must start before stop will do anything
        when(pipelineRegistryClient.listAllPipelines()).thenReturn(Promise.of(Collections.emptyList()));
        when(pipelineCache.putAll(any())).thenReturn(Promise.of(null));
        runPromise(() -> orchestrator.start());

        when(pipelineCache.clear()).thenReturn(Promise.of(null));
        runPromise(() -> orchestrator.stop());

        verify(pipelineCache).clear();
    }

    @Test
    @DisplayName("isHealthy() delegates to registry and agent clients")
    void testHealthCheck() {
        when(agentRegistryClient.isHealthy()).thenReturn(Promise.of(true));
        when(pipelineRegistryClient.isHealthy()).thenReturn(Promise.of(true));

        Boolean healthy = runPromise(() -> orchestrator.isHealthy());

        assertNotNull(healthy);
    }

    @Test
    @DisplayName("getStatus() reflects cached pipeline count")
    void testGetStatus() {
        when(pipelineCache.size()).thenReturn(5);

        Orchestrator.OrchestratorStatus status = orchestrator.getStatus();

        assertNotNull(status);
        assertEquals(5, status.getCachedPipelines());
    }

    @Test
    @DisplayName("getPipeline() retrieves entity from cache")
    void testGetPipeline() {
        String pipelineId = "test-pipeline";
        OrchestratorPipelineEntity mockPipeline = new OrchestratorPipelineEntity();
        mockPipeline.id = pipelineId;

        when(pipelineCache.get(pipelineId)).thenReturn(Promise.of(Optional.of(mockPipeline)));

        OrchestratorPipelineEntity result = runPromise(() -> orchestrator.getPipeline(pipelineId));

        assertNotNull(result);
        verify(pipelineCache).get(pipelineId);
    }

    @Test
    @DisplayName("listPipelines() returns all cached pipelines")
    void testListPipelines() {
        when(pipelineCache.getAllPipelines()).thenReturn(Promise.of(Collections.emptyList()));

        java.util.List<OrchestratorPipelineEntity> result = runPromise(() -> orchestrator.listPipelines());

        assertNotNull(result);
        verify(pipelineCache).getAllPipelines();
    }
}
