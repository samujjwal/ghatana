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

@DisplayName("Orchestrator Pipeline Integration [GH-90000]")
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000

        PipelineCache pipelineCache = new PipelineCache(Duration.ofMinutes(10), metricsCollector); // GH-90000
        OrchestratorConfig config = new OrchestratorConfig(); // GH-90000
        config.setRefreshInterval(Duration.ofMinutes(5)); // GH-90000

        orchestrator = new Orchestrator( // GH-90000
                pipelineCache, agentRegistryClient, pipelineRegistryClient, config, metricsCollector, specFormatLoader);

        PipelineCache pipelineCachePlanning = new PipelineCache(Duration.ofMinutes(10), metricsCollector); // GH-90000
        orchestratorWithPlanning = new Orchestrator( // GH-90000
                pipelineCachePlanning,
                agentRegistryClient,
                pipelineRegistryClient,
                config,
                metricsCollector,
                specFormatLoader,
                planCompiler);
    }

    @Test
    @DisplayName("deployPipeline succeeds when all referenced agents are active [GH-90000]")
    void shouldDeployPipelineWhenAgentsAreActive() { // GH-90000
        OrchestratorPipelineEntity pipeline = pipeline("pipe-1", "agent-1"); // GH-90000
        when(pipelineRegistryClient.getPipeline("pipe-1 [GH-90000]")).thenReturn(Promise.of(Optional.of(pipeline)));
        when(agentRegistryClient.getAgent("agent-1 [GH-90000]"))
                .thenReturn(Promise.of(Optional.of(new AgentRegistryClient.AgentInfo("agent-1", "Agent 1", "active")))); // GH-90000

        OrchestratorPipelineEntity deployed = runPromise(() -> orchestrator.deployPipeline("pipe-1 [GH-90000]"));

        assertEquals("pipe-1", deployed.id); // GH-90000
        verify(metricsCollector).incrementCounter("orch.pipeline.deployed [GH-90000]");
    }

    @Test
    @DisplayName("deployPipeline returns null when referenced agent is missing [GH-90000]")
    void shouldRejectPipelineWhenAgentIsMissing() { // GH-90000
        OrchestratorPipelineEntity pipeline = pipeline("pipe-missing", "missing-agent"); // GH-90000
        when(pipelineRegistryClient.getPipeline("pipe-missing [GH-90000]")).thenReturn(Promise.of(Optional.of(pipeline)));
        when(agentRegistryClient.getAgent("missing-agent [GH-90000]")).thenReturn(Promise.of(Optional.empty()));

        OrchestratorPipelineEntity deployed = runPromise(() -> orchestrator.deployPipeline("pipe-missing [GH-90000]"));

        assertNull(deployed); // GH-90000
        verify(metricsCollector).incrementCounter("orch.pipeline.validation.failed [GH-90000]");
    }

    @Test
    @DisplayName("undeployPipeline removes deployed pipeline [GH-90000]")
    void shouldUndeployPipeline() { // GH-90000
        OrchestratorPipelineEntity pipeline = pipeline("pipe-2", "agent-2"); // GH-90000
        when(pipelineRegistryClient.getPipeline("pipe-2 [GH-90000]")).thenReturn(Promise.of(Optional.of(pipeline)));
        when(agentRegistryClient.getAgent("agent-2 [GH-90000]"))
                .thenReturn(Promise.of(Optional.of(new AgentRegistryClient.AgentInfo("agent-2", "Agent 2", "active")))); // GH-90000

        runPromise(() -> orchestrator.deployPipeline("pipe-2 [GH-90000]"));
        Boolean removed = runPromise(() -> orchestrator.undeployPipeline("pipe-2 [GH-90000]"));

        assertTrue(removed); // GH-90000
        verify(metricsCollector).incrementCounter("orch.pipeline.undeployed [GH-90000]");
    }

    // ── Planning integration ───────────────────────────────────────────────

    @Nested
    @DisplayName("Planning agent integration via PlanCompiler [GH-90000]")
    class PlanningIntegration {

        @Test
        @DisplayName("compilePlan delegates to PlanCompiler [GH-90000]")
        void compilePlanDelegatesToCompiler() { // GH-90000
            PlanGraph expectedGraph = PlanGraph.of( // GH-90000
                    "p1", "agent-1", "Do something", List.of(PlannedAction.simple("a1", "Step 1", ActionClass.READ))); // GH-90000
            when(planCompiler.compile(eq("agent-1 [GH-90000]"), eq("tenant-1 [GH-90000]"), eq("Do something [GH-90000]"), any()))
                    .thenReturn(Promise.of(expectedGraph)); // GH-90000

            PlanGraph graph =
                    runPromise(() -> orchestratorWithPlanning.compilePlan("agent-1", "tenant-1", "Do something")); // GH-90000

            assertThat(graph.planId()).isEqualTo("p1 [GH-90000]");
            assertThat(graph.actions()).hasSize(1); // GH-90000
            verify(planCompiler).compile(eq("agent-1 [GH-90000]"), eq("tenant-1 [GH-90000]"), eq("Do something [GH-90000]"), any());
        }

        @Test
        @DisplayName("compilePlan throws when PlanCompiler is not configured [GH-90000]")
        void compilePlanThrowsWhenNoPlanCompiler() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> orchestrator.compilePlan("a", "t", "Obj"))) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("PlanCompiler is not configured [GH-90000]");
        }

        @Test
        @DisplayName("hasPlanCompiler returns true when compiler is set [GH-90000]")
        void hasPlanCompilerTrueWhenSet() { // GH-90000
            assertThat(orchestratorWithPlanning.hasPlanCompiler()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("hasPlanCompiler returns false when no compiler is set [GH-90000]")
        void hasPlanCompilerFalseWhenNotSet() { // GH-90000
            assertThat(orchestrator.hasPlanCompiler()).isFalse(); // GH-90000
        }
    }

    private static OrchestratorPipelineEntity pipeline(String id, String agentId) { // GH-90000
        OrchestratorPipelineEntity entity = new OrchestratorPipelineEntity(); // GH-90000
        entity.id = id;
        entity.name = "Pipeline " + id;
        entity.description = "test pipeline";
        entity.version = "1.0.0";
        entity.createdAt = Instant.now(); // GH-90000
        entity.updatedAt = Instant.now(); // GH-90000
        entity.createdBy = "test";
        entity.status = "active";
        entity.config = "{\"steps\":[{\"agentId\":\"" + agentId + "\"}]}";
        return entity;
    }
}
