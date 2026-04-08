package com.ghatana.orchestrator.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.agent.framework.governance.ActionClass;
import com.ghatana.agent.planning.PlanGraph;
import com.ghatana.agent.planning.PlannedAction;
import com.ghatana.orchestrator.cache.PipelineCache;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.config.OrchestratorConfig;
import com.ghatana.orchestrator.loader.SpecFormatLoader;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.planning.PlanCompiler;
import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Mock
    private PlanCompiler planCompiler;

    private Orchestrator orchestrator;
    private Orchestrator orchestratorWithPlanning;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PipelineCache pipelineCache = new PipelineCache(Duration.ofMinutes(10), metricsCollector);
        OrchestratorConfig config = new OrchestratorConfig();
        config.setRefreshInterval(Duration.ofMinutes(5));

        orchestrator = new Orchestrator(
                pipelineCache, agentRegistryClient, pipelineRegistryClient, config, metricsCollector, specFormatLoader);

        PipelineCache pipelineCachePlanning = new PipelineCache(Duration.ofMinutes(10), metricsCollector);
        orchestratorWithPlanning = new Orchestrator(
                pipelineCachePlanning,
                agentRegistryClient,
                pipelineRegistryClient,
                config,
                metricsCollector,
                specFormatLoader,
                planCompiler);
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

    // ── Planning integration ───────────────────────────────────────────────

    @Nested
    @DisplayName("Planning agent integration via PlanCompiler")
    class PlanningIntegration {

        @Test
        @DisplayName("compilePlan delegates to PlanCompiler")
        void compilePlanDelegatesToCompiler() {
            PlanGraph expectedGraph = PlanGraph.of(
                    "p1", "agent-1", "Do something", List.of(PlannedAction.simple("a1", "Step 1", ActionClass.READ)));
            when(planCompiler.compile(eq("agent-1"), eq("tenant-1"), eq("Do something"), any()))
                    .thenReturn(Promise.of(expectedGraph));

            PlanGraph graph =
                    runPromise(() -> orchestratorWithPlanning.compilePlan("agent-1", "tenant-1", "Do something"));

            assertThat(graph.planId()).isEqualTo("p1");
            assertThat(graph.actions()).hasSize(1);
            verify(planCompiler).compile(eq("agent-1"), eq("tenant-1"), eq("Do something"), any());
        }

        @Test
        @DisplayName("compilePlan throws when PlanCompiler is not configured")
        void compilePlanThrowsWhenNoPlanCompiler() {
            assertThatThrownBy(() -> runPromise(() -> orchestrator.compilePlan("a", "t", "Obj")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PlanCompiler is not configured");
        }

        @Test
        @DisplayName("hasPlanCompiler returns true when compiler is set")
        void hasPlanCompilerTrueWhenSet() {
            assertThat(orchestratorWithPlanning.hasPlanCompiler()).isTrue();
        }

        @Test
        @DisplayName("hasPlanCompiler returns false when no compiler is set")
        void hasPlanCompilerFalseWhenNotSet() {
            assertThat(orchestrator.hasPlanCompiler()).isFalse();
        }
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
