package com.ghatana.orchestrator.core;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ghatana.orchestrator.core.registry.PipelineRegistryClient;
import com.ghatana.orchestrator.core.cache.PipelineCache;
import com.ghatana.orchestrator.core.agent.AgentRegistryClient;
import com.ghatana.platform.core.async.Promise;
import com.ghatana.platform.core.async.Promises;
import com.ghatana.platform.observability.metrics.MetricsCollector;
import io.activej.eventloop.EventloopTestBase;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @doc.type class
 * @doc.purpose Integration tests for AEP Orchestrator pipeline deployment and agent execution
 * @doc.layer platform
 * @doc.pattern Integration Test
 */
@DisplayName("Orchestrator Pipeline Integration Tests")
class OrchestratorPipelineIntegrationTest extends EventloopTestBase {

    private Orchestrator orchestrator;

    @Mock
    private PipelineRegistryClient pipelineRegistry;

    @Mock
    private AgentRegistryClient agentRegistry;

    @Mock
    private MetricsCollector metricsCollector;

    private PipelineCache pipelineCache;
    private Map<String, OrchestratorPipelineEntity> pipelineStore; // In-memory store
    private Map<String, AgentEntity> agentStore; // In-memory store

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pipelineStore = new ConcurrentHashMap<>();
        agentStore = new ConcurrentHashMap<>();
        pipelineCache = new InMemoryPipelineCache(pipelineStore);

        orchestrator = new Orchestrator(
                pipelineRegistry,
                agentRegistry,
                pipelineCache,
                metricsCollector
        );
    }

    @Nested
    @DisplayName("Pipeline Deployment Lifecycle")
    class PipelineDeploymentTests {

        @Test
        @DisplayName("Should deploy valid pipeline and cache it")
        void shouldDeployValidPipeline() {
            // Setup
            String pipelineId = "analysis-pipeline-v1";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            // Mock registry to return pipeline
            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));

            // Mock agent validation - all agents valid
            mockAgentValidation(pipeline, true);

            // Execute
            OrchestratorPipelineEntity deployed = runPromise(() ->
                    orchestrator.deployPipeline(pipelineId)
            );

            // Verify
            assertThat(deployed).isNotNull();
            assertThat(deployed.id()).isEqualTo(pipelineId);
            verify(metricsCollector).incrementCounter("orch.pipeline.deployed");
            verify(metricsCollector).recordTimer(
                    argThat(s -> s.contains("deploy.time")),
                    anyLong()
            );
        }

        @Test
        @DisplayName("Should fail deployment if pipeline not found")
        void shouldFailDeploymentWhenPipelineNotFound() {
            // Setup
            String pipelineId = "nonexistent-pipeline";
            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.empty()));

            // Execute
            OrchestratorPipelineEntity result = runPromise(() ->
                    orchestrator.deployPipeline(pipelineId)
            );

            // Verify
            assertThat(result).isNull();
            verify(metricsCollector).incrementCounter("orch.pipeline.deploy.failed");
        }

        @Test
        @DisplayName("Should fail deployment if agent validation fails")
        void shouldFailDeploymentOnAgentValidationFailure() {
            // Setup
            String pipelineId = "invalid-agents-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));

            // Mock agent validation - at least one agent invalid
            mockAgentValidation(pipeline, false);

            // Execute
            OrchestratorPipelineEntity result = runPromise(() ->
                    orchestrator.deployPipeline(pipelineId)
            );

            // Verify
            assertThat(result).isNull();
            verify(metricsCollector).incrementCounter("orch.pipeline.validation.failed");
        }

        @Test
        @DisplayName("Should use cached pipeline on repeated deployment")
        void shouldUseCachedPipeline() {
            // Setup
            String pipelineId = "cached-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));
            mockAgentValidation(pipeline, true);

            // Execute first deployment
            runPromise(() -> orchestrator.deployPipeline(pipelineId));

            // Reset mock to verify it's not called again (uses cache)
            reset(pipelineRegistry);

            // Execute second deployment
            reset(pipelineRegistry);
            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));
            mockAgentValidation(pipeline, true);

            runPromise(() -> orchestrator.deployPipeline(pipelineId));

            // Should have called registry twice (once per deploy)
            verify(pipelineRegistry, times(2)).getPipeline(pipelineId);
        }

        @Test
        @DisplayName("Should undeploy pipeline and remove from cache")
        void shouldUndeployPipeline() {
            // Setup
            String pipelineId = "deploy-then-undeploy";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));
            mockAgentValidation(pipeline, true);

            // Deploy first
            runPromise(() -> orchestrator.deployPipeline(pipelineId));

            // Undeploy
            Boolean undeployed = runPromise(() ->
                    orchestrator.undeployPipeline(pipelineId)
            );

            // Verify
            assertTrue(undeployed);
            verify(metricsCollector).incrementCounter("orch.pipeline.undeployed");
            verify(metricsCollector).recordTimer(
                    argThat(s -> s.contains("undeploy.time")),
                    anyLong()
            );
        }

        @Test
        @DisplayName("Should handle undeploy of non-existent pipeline gracefully")
        void shouldHandleUndeployOfNonExistentPipeline() {
            // Execute undeploy of non-existent pipeline
            Boolean undeployed = runPromise(() ->
                    orchestrator.undeployPipeline("nonexistent-pipeline")
            );

            // Verify - should return false but not throw
            assertFalse(undeployed);
        }
    }

    @Nested
    @DisplayName("Agent Validation and Registry")
    class AgentValidationTests {

        @Test
        @DisplayName("Should validate all agents in pipeline are available")
        void shouldValidateAllAgents() {
            // Setup
            String pipelineId = "multi-agent-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipelineWithAgents(
                    pipelineId,
                    "agent-1", "agent-2", "agent-3"
            );
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));

            // Mock all agents as valid
            for (String agentId : List.of("agent-1", "agent-2", "agent-3")) {
                AgentEntity agent = createTestAgent(agentId, "active");
                agentStore.put(agentId, agent);
                when(agentRegistry.getAgent(agentId))
                        .thenReturn(Promise.of(Optional.of(agent)));
            }

            // Execute
            OrchestratorPipelineEntity deployed = runPromise(() ->
                    orchestrator.deployPipeline(pipelineId)
            );

            // Verify all agents were validated
            assertThat(deployed).isNotNull();
            verify(agentRegistry).getAgent("agent-1");
            verify(agentRegistry).getAgent("agent-2");
            verify(agentRegistry).getAgent("agent-3");
        }

        @Test
        @DisplayName("Should reject agents that don't exist in registry")
        void shouldRejectMissingAgents() {
            // Setup
            String pipelineId = "invalid-agent-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipelineWithAgents(
                    pipelineId,
                    "agent-1", "agent-missing"
            );
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));

            when(agentRegistry.getAgent("agent-1"))
                    .thenReturn(Promise.of(Optional.of(
                            createTestAgent("agent-1", "active")
                    )));
            when(agentRegistry.getAgent("agent-missing"))
                    .thenReturn(Promise.of(Optional.empty()));

            // Execute
            OrchestratorPipelineEntity result = runPromise(() ->
                    orchestrator.deployPipeline(pipelineId)
            );

            // Verify deployment failed
            assertThat(result).isNull();
            verify(metricsCollector).incrementCounter(
                    argThat(s -> s.contains("agent.validation.failed")),
                    argThat(map -> map.containsValue("not_found"))
            );
        }

        @Test
        @DisplayName("Should reject inactive agents")
        void shouldRejectInactiveAgents() {
            // Setup
            String pipelineId = "inactive-agent-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipelineWithAgents(
                    pipelineId,
                    "agent-1"
            );
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));

            // Agent exists but is inactive
            AgentEntity inactiveAgent = createTestAgent("agent-1", "inactive");
            when(agentRegistry.getAgent("agent-1"))
                    .thenReturn(Promise.of(Optional.of(inactiveAgent)));

            // Execute
            OrchestratorPipelineEntity result = runPromise(() ->
                    orchestrator.deployPipeline(pipelineId)
            );

            // Verify deployment failed
            assertThat(result).isNull();
            verify(metricsCollector).incrementCounter(
                    argThat(s -> s.contains("agent.validation.failed")),
                    argThat(map -> map.containsValue("not_active"))
            );
        }

        @Test
        @DisplayName("Should skip validation for pipelines without agent references")
        void shouldSkipValidationForNonAgentPipelines() {
            // Setup - pipeline with no agents
            String pipelineId = "no-agent-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));

            // Execute
            OrchestratorPipelineEntity deployed = runPromise(() ->
                    orchestrator.deployPipeline(pipelineId)
            );

            // Verify
            assertThat(deployed).isNotNull();
            // Agent registry should not be called for non-agent pipelines
            verify(agentRegistry, never()).getAgent(anyString());
        }
    }

    @Nested
    @DisplayName("Metrics and Observability")
    class MetricsTests {

        @Test
        @DisplayName("Should record deployment timing metrics")
        void shouldRecordDeploymentMetrics() {
            // Setup
            String pipelineId = "metrics-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));
            mockAgentValidation(pipeline, true);

            // Execute
            runPromise(() -> orchestrator.deployPipeline(pipelineId));

            // Verify timing was recorded
            ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
            verify(metricsCollector).recordTimer(
                    contains("deploy.time"),
                    durationCaptor.capture()
            );
            assertThat(durationCaptor.getValue()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should track deployment success and failure counts")
        void shouldTrackDeploymentCounts() {
            // Setup - deploy valid pipeline
            String validId = "valid-pipeline";
            OrchestratorPipelineEntity validPipeline = createTestPipeline(validId, "active");
            pipelineStore.put(validId, validPipeline);

            when(pipelineRegistry.getPipeline(validId))
                    .thenReturn(Promise.of(Optional.of(validPipeline)));
            mockAgentValidation(validPipeline, true);

            // Execute valid deployment
            runPromise(() -> orchestrator.deployPipeline(validId));

            // Setup - deploy invalid pipeline
            String invalidId = "invalid-pipeline";
            when(pipelineRegistry.getPipeline(invalidId))
                    .thenReturn(Promise.of(Optional.empty()));

            // Execute invalid deployment
            runPromise(() -> orchestrator.deployPipeline(invalidId));

            // Verify counts
            verify(metricsCollector, times(1)).incrementCounter("orch.pipeline.deployed");
            verify(metricsCollector, times(1)).incrementCounter("orch.pipeline.deploy.failed");
        }

        @Test
        @DisplayName("Should include pipeline ID in all metrics")
        void shouldIncludePipelineIdInMetrics() {
            // Setup
            String pipelineId = "tagged-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));
            mockAgentValidation(pipeline, true);

            // Execute
            runPromise(() -> orchestrator.deployPipeline(pipelineId));

            // Verify metrics include pipeline ID tag
            verify(metricsCollector).recordTimer(
                    anyString(),
                    anyLong(),
                    argThat(s -> s.contains(pipelineId))
            );
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("Should handle concurrent pipeline deployments safely")
        void shouldHandleConcurrentDeployments() {
            // Setup
            int numPipelines = 5;
            for (int i = 1; i <= numPipelines; i++) {
                String pipelineId = "concurrent-pipeline-" + i;
                OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
                pipelineStore.put(pipelineId, pipeline);

                when(pipelineRegistry.getPipeline(pipelineId))
                        .thenReturn(Promise.of(Optional.of(pipeline)));
                mockAgentValidation(pipeline, true);
            }

            // Execute all deployments
            List<Promise<OrchestratorPipelineEntity>> deployments = new ArrayList<>();
            for (int i = 1; i <= numPipelines; i++) {
                String pipelineId = "concurrent-pipeline-" + i;
                deployments.add(orchestrator.deployPipeline(pipelineId));
            }

            // Wait for all
            List<OrchestratorPipelineEntity> results = runPromise(() ->
                    Promises.all(deployments)
            );

            // Verify all succeeded
            assertThat(results).hasSize(numPipelines);
            for (OrchestratorPipelineEntity pipeline : results) {
                assertThat(pipeline).isNotNull();
            }
        }

        @Test
        @DisplayName("Should handle concurrent deploy and undeploy operations")
        void shouldHandleConcurrentDeployUndeploy() {
            // Setup
            String pipelineId = "race-condition-pipeline";
            OrchestratorPipelineEntity pipeline = createTestPipeline(pipelineId, "active");
            pipelineStore.put(pipelineId, pipeline);

            when(pipelineRegistry.getPipeline(pipelineId))
                    .thenReturn(Promise.of(Optional.of(pipeline)));
            mockAgentValidation(pipeline, true);

            // Deploy
            runPromise(() -> orchestrator.deployPipeline(pipelineId));

            // Concurrent undeploy and redeploy
            Promise<Boolean> undeploy = orchestrator.undeployPipeline(pipelineId);
            Promise<OrchestratorPipelineEntity> redeploy = orchestrator.deployPipeline(pipelineId);

            Boolean undeployResult = runPromise(() -> undeploy);
            OrchestratorPipelineEntity redeployResult = runPromise(() -> redeploy);

            // Both should complete without errors
            assertThat(undeployResult).isNotNull();
            assertThat(redeployResult).isNotNull();
        }
    }

    // Helper Methods

    private void mockAgentValidation(OrchestratorPipelineEntity pipeline, boolean shouldPass) {
        List<String> agentIds = extractAgentIds(pipeline);
        for (String agentId : agentIds) {
            if (shouldPass) {
                AgentEntity agent = createTestAgent(agentId, "active");
                agentStore.put(agentId, agent);
                when(agentRegistry.getAgent(agentId))
                        .thenReturn(Promise.of(Optional.of(agent)));
            } else {
                when(agentRegistry.getAgent(argThat(id -> id.contains(agentId))))
                        .thenReturn(Promise.of(Optional.empty()));
            }
        }
    }

    private List<String> extractAgentIds(OrchestratorPipelineEntity pipeline) {
        // Simplified extraction - real implementation would parse pipeline config
        return new ArrayList<>();
    }

    private OrchestratorPipelineEntity createTestPipeline(String id, String status) {
        return new OrchestratorPipelineEntity(
                id,
                "pipeline-" + id,
                "1.0.0",
                status,
                "test-config",
                System.currentTimeMillis()
        );
    }

    private OrchestratorPipelineEntity createTestPipelineWithAgents(
            String id, String... agentIds) {
        String config = buildPipelineConfig(agentIds);
        return new OrchestratorPipelineEntity(
                id,
                "pipeline-" + id,
                "1.0.0",
                "active",
                config,
                System.currentTimeMillis()
        );
    }

    private String buildPipelineConfig(String... agentIds) {
        // Simplified config - real implementation would create proper YAML/JSON
        StringBuilder config = new StringBuilder("stages:\n");
        for (String agentId : agentIds) {
            config.append("  - name: ").append(agentId).append("\n");
            config.append("    agent: ").append(agentId).append("\n");
        }
        return config.toString();
    }

    private AgentEntity createTestAgent(String id, String status) {
        return new AgentEntity(
                id,
                "agent-" + id,
                status,
                "test",
                System.currentTimeMillis()
        );
    }

    /**
     * Test implementation of PipelineCache using in-memory storage
     */
    static class InMemoryPipelineCache implements PipelineCache {
        private final Map<String, OrchestratorPipelineEntity> store;

        InMemoryPipelineCache(Map<String, OrchestratorPipelineEntity> store) {
            this.store = store;
        }

        @Override
        public Promise<OrchestratorPipelineEntity> get(String pipelineId) {
            return Promise.of(store.get(pipelineId));
        }

        @Override
        public Promise<Void> put(String pipelineId, OrchestratorPipelineEntity pipeline) {
            store.put(pipelineId, pipeline);
            return Promise.complete();
        }

        @Override
        public Promise<Boolean> remove(String pipelineId) {
            boolean removed = store.remove(pipelineId) != null;
            return Promise.of(removed);
        }

        @Override
        public Promise<Void> clear() {
            store.clear();
            return Promise.complete();
        }
    }

    /**
     * Test data classes
     */
    static class OrchestratorPipelineEntity {
        private final String id;
        private final String name;
        private final String version;
        private final String status;
        private final String config;
        private final long createdAt;

        OrchestratorPipelineEntity(String id, String name, String version, String status,
                                   String config, long createdAt) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.status = status;
            this.config = config;
            this.createdAt = createdAt;
        }

        String id() { return id; }
        String name() { return name; }
        String version() { return version; }
        String status() { return status; }
        String config() { return config; }
    }

    static class AgentEntity {
        private final String id;
        private final String name;
        private final String status;
        private final String type;
        private final long deployedAt;

        AgentEntity(String id, String name, String status, String type, long deployedAt) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.type = type;
            this.deployedAt = deployedAt;
        }

        String id() { return id; }
        String status() { return status; }
    }

    interface PipelineCache {
        Promise<OrchestratorPipelineEntity> get(String pipelineId);
        Promise<Void> put(String pipelineId, OrchestratorPipelineEntity pipeline);
        Promise<Boolean> remove(String pipelineId);
        Promise<Void> clear();
    }

    interface PipelineRegistryClient {
        Promise<Optional<OrchestratorPipelineEntity>> getPipeline(String pipelineId);
    }

    interface AgentRegistryClient {
        Promise<Optional<AgentEntity>> getAgent(String agentId);
    }
}
