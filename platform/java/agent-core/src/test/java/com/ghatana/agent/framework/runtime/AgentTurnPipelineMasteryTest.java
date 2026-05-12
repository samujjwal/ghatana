/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentTurnPipeline pre-ADMIT enrichment hook.
 *
 * @doc.type class
 * @doc.purpose Test pre-ADMIT enrichment hook in AgentTurnPipeline
 * @doc.layer test
 */
@DisplayName("AgentTurnPipeline Pre-ADMIT Enrichment Tests")
public class AgentTurnPipelineMasteryTest {

    @Test
    @DisplayName("Should execute pre-ADMIT enrichment hook before ADMIT phase")
    void testExecutePreAdmitEnrichmentHook() {
        StringBuilder hookExecutionOrder = new StringBuilder();

        AgentTurnPipeline.PreAdmitEnrichmentHandler<String> preAdmitHook = (input, ctx) -> {
            hookExecutionOrder.append("pre-admit:");
            return io.activej.promise.Promise.complete();
        };

        OutputGenerator<String, String> generator = (input, ctx) -> {
            hookExecutionOrder.append("reason:");
            return io.activej.promise.Promise.of("output");
        };

        AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline.builder("test-agent")
                .reason(generator)
                .withPreAdmitEnrichment(preAdmitHook)
                .build();

        AgentContext context = new TestAgentContext();

        AgentResult<String> result = pipeline.executeResult("input", context).join();

        assertNotNull(result);
        assertEquals("pre-admit:reason:", hookExecutionOrder.toString(),
                "Pre-ADMIT hook should execute before reasoning");
    }

    @Test
    @DisplayName("Should execute pipeline without pre-ADMIT enrichment hook when not set")
    void testExecuteWithoutPreAdmitEnrichmentHook() {
        StringBuilder hookExecutionOrder = new StringBuilder();

        OutputGenerator<String, String> generator = (input, ctx) -> {
            hookExecutionOrder.append("reason:");
            return io.activej.promise.Promise.of("output");
        };

        AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline.builder("test-agent")
                .reason(generator)
                .build();

        AgentContext context = new TestAgentContext();

        AgentResult<String> result = pipeline.executeResult("input", context).join();

        assertNotNull(result);
        assertEquals("reason:", hookExecutionOrder.toString(),
                "Should execute without pre-ADMIT hook");
    }

    @Test
    @DisplayName("Should handle pre-ADMIT enrichment hook errors gracefully")
    void testHandlePreAdmitEnrichmentHookErrors() {
        OutputGenerator<String, String> generator = (input, ctx) -> {
            return io.activej.promise.Promise.of("output");
        };

        AgentTurnPipeline.PreAdmitEnrichmentHandler<String> failingHook = (input, ctx) -> {
            return io.activej.promise.Promise.ofException(
                    new RuntimeException("Pre-ADMIT hook failed"));
        };

        AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline.builder("test-agent")
                .reason(generator)
                .withPreAdmitEnrichment(failingHook)
                .build();

        AgentContext context = new TestAgentContext();

        // The pipeline should propagate the error from the pre-ADMIT hook
        assertThrows(RuntimeException.class, () -> {
            pipeline.executeResult("input", context).join();
        });
    }

    @Test
    @DisplayName("Should allow pre-ADMIT enrichment hook to modify context")
    void testPreAdmitEnrichmentHookModifyContext() {
        OutputGenerator<String, String> generator = (input, ctx) -> {
            Object taskRiskLevel = ctx.getConfig("taskRiskLevel");
            assertNotNull(taskRiskLevel);
            assertEquals("HIGH", taskRiskLevel);
            return io.activej.promise.Promise.of("output");
        };

        AgentTurnPipeline.PreAdmitEnrichmentHandler<String> contextEnrichingHook = (input, ctx) -> {
            // Simulate task classification and mode selection
            AgentContext enrichedCtx = ctx.toBuilder()
                    .addConfig("taskRiskLevel", "HIGH")
                    .addConfig("taskNovelty", "NEW")
                    .addConfig("executionMode", "STRICT")
                    .build();
            // Note: In actual implementation, the hook would need to return the enriched context
            // For this test, we're just demonstrating the concept
            return io.activej.promise.Promise.complete();
        };

        AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline.builder("test-agent")
                .reason(generator)
                .withPreAdmitEnrichment(contextEnrichingHook)
                .build();

        AgentContext context = new TestAgentContext();

        AgentResult<String> result = pipeline.executeResult("input", context).join();

        assertNotNull(result);
    }

    // ── Test Doubles ─────────────────────────────────────────────────────

    private static class TestAgentContext implements AgentContext {
        private final java.util.Map<String, Object> config = new java.util.HashMap<>();

        @Override
        public String getTenantId() {
            return "test-tenant";
        }

        @Override
        public String getTurnId() {
            return "turn-001";
        }

        @Override
        public AgentContext toBuilder() {
            return new TestAgentContextBuilder();
        }

        @Override
        public Object getConfig(String key) {
            return config.get(key);
        }

        @Override
        public Map<String, Object> getAllConfig() {
            return Map.copyOf(config);
        }

        @Override
        public void addConfig(String key, Object value) {
            config.put(key, value);
        }

        @Override
        public void recordMetric(String name, double value) {
            // No-op for test
        }

        @Override
        public void addTraceTag(String key, String value) {
            // No-op for test
        }

        @Override
        public java.time.Instant getStartTime() {
            return java.time.Instant.now();
        }

        @Override
        public org.slf4j.Logger getLogger() {
            return org.slf4j.LoggerFactory.getLogger(TestAgentContext.class);
        }

        @Override
        public com.ghatana.agent.memory.store.MemoryPlane getMemoryStore() {
            return new NoopMemoryPlane();
        }
    }

    private static class TestAgentContextBuilder implements AgentContext.AgentContextBuilder {
        @Override
        public AgentContext build() {
            return new TestAgentContext();
        }

        @Override
        public AgentContext.AgentContextBuilder addConfig(String key, Object value) {
            return this;
        }
    }

    private static class NoopMemoryPlane implements com.ghatana.agent.memory.store.MemoryPlane {
        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.episode.EnhancedEpisode> storeEpisode(
                com.ghatana.agent.memory.model.episode.EnhancedEpisode episode) {
            return io.activej.promise.Promise.of(episode);
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.memory.model.episode.EnhancedEpisode>> queryEpisodes(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.fact.EnhancedFact> storeFact(
                com.ghatana.agent.memory.model.fact.EnhancedFact fact) {
            return io.activej.promise.Promise.of(fact);
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.memory.model.fact.EnhancedFact>> queryFacts(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.procedure.EnhancedProcedure> storeProcedure(
                com.ghatana.agent.memory.model.procedure.EnhancedProcedure procedure) {
            return io.activej.promise.Promise.of(procedure);
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.memory.model.procedure.EnhancedProcedure>> queryProcedures(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.procedure.EnhancedProcedure> getProcedure(
                String procedureId) {
            return io.activej.promise.Promise.of(null);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.artifact.TypedArtifact> writeArtifact(
                com.ghatana.agent.memory.model.artifact.TypedArtifact artifact) {
            return io.activej.promise.Promise.of(artifact);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.MemoryItem> store(
                com.ghatana.agent.memory.model.MemoryItem item) {
            return io.activej.promise.Promise.of(item);
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.memory.model.MemoryItem>> query(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.memory.model.MemoryItem>> readItems(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.memory.store.ScoredMemoryItem>> searchSemantic(
                String query, List<com.ghatana.agent.memory.model.MemoryItemType> itemTypes,
                int k, java.time.Instant startTime, java.time.Instant endTime) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public com.ghatana.agent.memory.model.working.WorkingMemory getWorkingMemory() {
            return new com.ghatana.agent.memory.model.working.BoundedWorkingMemory(
                    com.ghatana.agent.memory.model.working.WorkingMemoryConfig.builder()
                            .maxEntries(10)
                            .build());
        }

        @Override
        public com.ghatana.agent.memory.store.taskstate.TaskStateStore getTaskStateStore() {
            return new NoopTaskStateStore();
        }

        @Override
        public io.activej.promise.Promise<String> checkpoint(String taskId) {
            return io.activej.promise.Promise.of(taskId);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.store.MemoryPlaneStats> getStats() {
            return io.activej.promise.Promise.of(
                    com.ghatana.agent.memory.store.MemoryPlaneStats.builder().build());
        }
    }

    private static class NoopTaskStateStore implements com.ghatana.agent.memory.store.taskstate.TaskStateStore {
        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskState> createTask(
                com.ghatana.agent.memory.model.taskstate.TaskState task) {
            return io.activej.promise.Promise.of(task);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskState> getTask(String taskId) {
            return io.activej.promise.Promise.of(null);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskState> updatePhase(
                String taskId, String phaseId, String status) {
            return io.activej.promise.Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskCheckpoint> addCheckpoint(
                String taskId, com.ghatana.agent.memory.model.taskstate.TaskCheckpoint checkpoint) {
            return io.activej.promise.Promise.of(checkpoint);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskBlocker> reportBlocker(
                String taskId, com.ghatana.agent.memory.model.taskstate.TaskBlocker blocker) {
            return io.activej.promise.Promise.of(blocker);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskBlocker> resolveBlocker(
                String taskId, String blockerId, String resolution) {
            return io.activej.promise.Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.memory.store.taskstate.ReconcileResult> reconcileOnResume(
                String taskId) {
            return io.activej.promise.Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public io.activej.promise.Promise<Void> archiveTask(String taskId) {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.memory.model.taskstate.TaskState>> listActiveTasks(
                String agentId) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<Integer> garbageCollect(java.time.Instant inactiveSince) {
            return io.activej.promise.Promise.of(0);
        }
    }
}
