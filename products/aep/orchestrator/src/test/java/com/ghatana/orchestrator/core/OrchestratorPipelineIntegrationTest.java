package com.ghatana.orchestrator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("Orchestrator Pipeline Integration")
class OrchestratorPipelineIntegrationTest extends EventloopTestBase {

    @Mock
    private PipelineRegistryClient pipelineRegistryClient;

    @Mock
    private AgentRegistryClient agentRegistryClient;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private SpecFormatLoader specFormatLoader;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PipelineCache pipelineCache = new PipelineCache(Duration.ofMinutes(10), metricsCollector);
        OrchestratorConfig config = new OrchestratorConfig();
        config.setRefreshInterval(Duration.ofMinutes(5));

        orchestrator = new Orchestrator(
                pipelineCache, agentRegistryClient, pipelineRegistryClient, config, metricsCollector, specFormatLoader);
    }

    @Test
    @DisplayName("deployPipeline succeeds when all referenced agents are active")
    void shouldDeployPipelineWhenAgentsAreActive() {
        OrchestratorPipelineEntity pipeline = pipeline("pipe-1", "agent-1");
        when(pipelineRegistryClient.getPipeline("pipe-1")).thenReturn(Promise.of(Optional.of(pipeline)));
        when(agentRegistryClient.getAgent("agent-1"))
                .thenReturn(Promise.of(Optional.of(new AgentRegistryClient.AgentInfo("agent-1", "Agent 1", "active"))));

        OrchestratorPipelineEntity deployed = runPromise(() -> orchestrator.deployPipeline("pipe-1"));

        assertEquals("pipe-1", deployed.id);
        verify(metricsCollector).incrementCounter("orch.pipeline.deployed");
    }

    @Test
    @DisplayName("deployPipeline returns null when referenced agent is missing")
    void shouldRejectPipelineWhenAgentIsMissing() {
        OrchestratorPipelineEntity pipeline = pipeline("pipe-missing", "missing-agent");
        when(pipelineRegistryClient.getPipeline("pipe-missing")).thenReturn(Promise.of(Optional.of(pipeline)));
        when(agentRegistryClient.getAgent("missing-agent")).thenReturn(Promise.of(Optional.empty()));

        OrchestratorPipelineEntity deployed = runPromise(() -> orchestrator.deployPipeline("pipe-missing"));

        assertNull(deployed);
        verify(metricsCollector).incrementCounter("orch.pipeline.validation.failed");
    }

    @Test
    @DisplayName("undeployPipeline removes deployed pipeline")
    void shouldUndeployPipeline() {
        OrchestratorPipelineEntity pipeline = pipeline("pipe-2", "agent-2");
        when(pipelineRegistryClient.getPipeline("pipe-2")).thenReturn(Promise.of(Optional.of(pipeline)));
        when(agentRegistryClient.getAgent("agent-2"))
                .thenReturn(Promise.of(Optional.of(new AgentRegistryClient.AgentInfo("agent-2", "Agent 2", "active"))));

        runPromise(() -> orchestrator.deployPipeline("pipe-2"));
        Boolean removed = runPromise(() -> orchestrator.undeployPipeline("pipe-2"));

        assertTrue(removed);
        verify(metricsCollector).incrementCounter("orch.pipeline.undeployed");
    }

    private static OrchestratorPipelineEntity pipeline(String id, String agentId) {
        OrchestratorPipelineEntity entity = new OrchestratorPipelineEntity();
        entity.id = id;
        entity.name = "Pipeline " + id;
        entity.description = "test pipeline";
        entity.version = "1.0.0";
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        entity.createdBy = "test";
        entity.status = "active";
        entity.config = "{\"steps\":[{\"agentId\":\"" + agentId + "\"}]}";
        return entity;
    }
}
