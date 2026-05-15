/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.engine.registry;

import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.store.MemoryPlane;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.EventloopThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentMemoryPlaneClient mastery-aware memory retrieval.
 *
 * @doc.type class
 * @doc.purpose Test mastery-aware getMemory in AgentMemoryPlaneClient
 * @doc.layer test
 */
@DisplayName("AgentMemoryPlaneClient Mastery Tests")
public class AgentMemoryPlaneClientMasteryTest {

    private Eventloop eventloop;
    private EventloopThread eventloopThread;
    private InMemoryMemoryPlane memoryPlane;
    private InMemoryMasteryRegistry masteryRegistry;
    private AgentMemoryPlaneClient client;

    @BeforeEach
    void setUp() throws InterruptedException {
        eventloop = Eventloop.create().withCurrentThread();
        eventloopThread = EventloopThread.create().withName("test-eventloop").start();
        eventloopThread.submit(() -> {
            memoryPlane = new InMemoryMemoryPlane();
            masteryRegistry = new InMemoryMasteryRegistry();
            client = new AgentMemoryPlaneClient(memoryPlane, "test-tenant", masteryRegistry);
        }).join();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        eventloopThread.submit(() -> {
            eventloop.breakEventloop();
        }).join();
        eventloopThread.join();
    }

    @Test
    @DisplayName("Should filter out OBSOLETE procedures by default in mastery-aware retrieval")
    void testFilterObsoleteProceduresByDefault() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create an obsolete procedure
            EnhancedProcedure obsoleteProcedure = EnhancedProcedure.builder()
                    .id("proc-obsolete")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Obsolete situation")
                    .action("Obsolete action")
                    .confidence(0.5)
                    .successRate(0.5)
                    .metadata(Map.of("masteryState", "OBSOLETE"))
                    .build();

            // Create a normal procedure
            EnhancedProcedure normalProcedure = EnhancedProcedure.builder()
                    .id("proc-normal")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Normal situation")
                    .action("Normal action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of("masteryState", "PRACTICED"))
                    .build();

            // Store both procedures
            memoryPlane.storeProcedure(obsoleteProcedure).join();
            memoryPlane.storeProcedure(normalProcedure).join();

            // Query with mastery-aware retrieval (exclude obsolete)
            AgentExecutionService.AgentMemory memory = client.getMemoryMasteryAware(
                    "test-agent",
                    false, // includeObsolete
                    false, // includeMaintenanceOnly
                    false) // includeNegativeKnowledge
                    .join();

            // Should only return the normal procedure
            assertNotNull(memory);
            Map<String, Object> procedural = memory.procedural();
            assertNotNull(procedural);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> procedures = (List<Map<String, Object>>) procedural.get("procedures");
            assertNotNull(procedures);
            assertEquals(1, procedures.size());
        }).join();
    }

    @Test
    @DisplayName("Should include OBSOLETE procedures when requested")
    void testIncludeObsoleteProceduresWhenRequested() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create an obsolete procedure
            EnhancedProcedure obsoleteProcedure = EnhancedProcedure.builder()
                    .id("proc-obsolete")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Obsolete situation")
                    .action("Obsolete action")
                    .confidence(0.5)
                    .successRate(0.5)
                    .metadata(Map.of("masteryState", "OBSOLETE"))
                    .build();

            // Create a normal procedure
            EnhancedProcedure normalProcedure = EnhancedProcedure.builder()
                    .id("proc-normal")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Normal situation")
                    .action("Normal action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of("masteryState", "PRACTICED"))
                    .build();

            // Store both procedures
            memoryPlane.storeProcedure(obsoleteProcedure).join();
            memoryPlane.storeProcedure(normalProcedure).join();

            // Query with mastery-aware retrieval (include obsolete)
            AgentExecutionService.AgentMemory memory = client.getMemoryMasteryAware(
                    "test-agent",
                    true, // includeObsolete
                    false, // includeMaintenanceOnly
                    false) // includeNegativeKnowledge
                    .join();

            // Should return both procedures
            assertNotNull(memory);
            Map<String, Object> procedural = memory.procedural();
            assertNotNull(procedural);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> procedures = (List<Map<String, Object>>) procedural.get("procedures");
            assertNotNull(procedures);
            assertEquals(2, procedures.size());
        }).join();
    }

    @Test
    @DisplayName("Should filter out MAINTENANCE_ONLY procedures by default")
    void testFilterMaintenanceOnlyProceduresByDefault() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a maintenance-only procedure
            EnhancedProcedure maintenanceProcedure = EnhancedProcedure.builder()
                    .id("proc-maintenance")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Maintenance situation")
                    .action("Maintenance action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of("masteryState", "MAINTENANCE_ONLY"))
                    .build();

            // Create a normal procedure
            EnhancedProcedure normalProcedure = EnhancedProcedure.builder()
                    .id("proc-normal")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Normal situation")
                    .action("Normal action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of("masteryState", "PRACTICED"))
                    .build();

            // Store both procedures
            memoryPlane.storeProcedure(maintenanceProcedure).join();
            memoryPlane.storeProcedure(normalProcedure).join();

            // Query with mastery-aware retrieval (exclude maintenance-only)
            AgentExecutionService.AgentMemory memory = client.getMemoryMasteryAware(
                    "test-agent",
                    false, // includeObsolete
                    false, // includeMaintenanceOnly
                    false) // includeNegativeKnowledge
                    .join();

            // Should only return the normal procedure
            assertNotNull(memory);
            Map<String, Object> procedural = memory.procedural();
            assertNotNull(procedural);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> procedures = (List<Map<String, Object>>) procedural.get("procedures");
            assertNotNull(procedures);
            assertEquals(1, procedures.size());
        }).join();
    }

    @Test
    @DisplayName("Should include MAINTENANCE_ONLY procedures when requested")
    void testIncludeMaintenanceOnlyProceduresWhenRequested() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a maintenance-only procedure
            EnhancedProcedure maintenanceProcedure = EnhancedProcedure.builder()
                    .id("proc-maintenance")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Maintenance situation")
                    .action("Maintenance action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of("masteryState", "MAINTENANCE_ONLY"))
                    .build();

            // Create a normal procedure
            EnhancedProcedure normalProcedure = EnhancedProcedure.builder()
                    .id("proc-normal")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Normal situation")
                    .action("Normal action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of("masteryState", "PRACTICED"))
                    .build();

            // Store both procedures
            memoryPlane.storeProcedure(maintenanceProcedure).join();
            memoryPlane.storeProcedure(normalProcedure).join();

            // Query with mastery-aware retrieval (include maintenance-only)
            AgentExecutionService.AgentMemory memory = client.getMemoryMasteryAware(
                    "test-agent",
                    false, // includeObsolete
                    true, // includeMaintenanceOnly
                    false) // includeNegativeKnowledge
                    .join();

            // Should return both procedures
            assertNotNull(memory);
            Map<String, Object> procedural = memory.procedural();
            assertNotNull(procedural);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> procedures = (List<Map<String, Object>>) procedural.get("procedures");
            assertNotNull(procedures);
            assertEquals(2, procedures.size());
        }).join();
    }

    @Test
    @DisplayName("Should work without MasteryRegistry when null")
    void testWorkWithoutMasteryRegistry() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a client without mastery registry
            AgentMemoryPlaneClient clientWithoutRegistry = new AgentMemoryPlaneClient(
                    memoryPlane, "test-tenant", null);

            // Create a procedure
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id("proc-001")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Test situation")
                    .action("Test action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of())
                    .build();

            // Store the procedure
            memoryPlane.storeProcedure(procedure).join();

            // Query with mastery-aware retrieval (should still work)
            AgentExecutionService.AgentMemory memory = clientWithoutRegistry.getMemoryMasteryAware(
                    "test-agent",
                    false, // includeObsolete
                    false, // includeMaintenanceOnly
                    false) // includeNegativeKnowledge
                    .join();

            // Should return the procedure
            assertNotNull(memory);
        }).join();
    }

    @Test
    @DisplayName("Should use regular getMemory when mastery filtering not needed")
    void testUseRegularGetMemory() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a procedure
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id("proc-001")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Test situation")
                    .action("Test action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of())
                    .build();

            // Store the procedure
            memoryPlane.storeProcedure(procedure).join();

            // Query with regular getMemory
            AgentExecutionService.AgentMemory memory = client.getMemory("test-agent").join();

            // Should return the procedure
            assertNotNull(memory);
            Map<String, Object> procedural = memory.procedural();
            assertNotNull(procedural);
        }).join();
    }

    @Test
    @DisplayName("Should preserve explicit tenant in canonical memory item query")
    void testCanonicalMemoryQueryUsesExplicitTenant() throws InterruptedException {
        eventloopThread.submit(() -> {
            client.queryMemoryItemsMasteryAware(
                    "tenant-b",
                    "test-agent",
                    "skill-1",
                    10,
                    false,
                    false,
                    true)
                .join();

            assertEquals("tenant-b", memoryPlane.lastTenantIdForItemQuery());
        }).join();
    }

    // ── Test Doubles ─────────────────────────────────────────────────────

    private static class InMemoryMemoryPlane implements MemoryPlane {
        private final java.util.Map<String, EnhancedProcedure> procedures = new java.util.HashMap<>();
        private final java.util.Map<String, EnhancedFact> facts = new java.util.HashMap<>();
        private final java.util.Map<String, EnhancedEpisode> episodes = new java.util.HashMap<>();
        private String lastTenantIdForItemQuery;

        String lastTenantIdForItemQuery() {
            return lastTenantIdForItemQuery;
        }

        @Override
        public com.io.activej.promise.Promise<EnhancedEpisode> storeEpisode(EnhancedEpisode episode) {
            episodes.put(episode.getId(), episode);
            return com.io.activej.promise.Promise.of(episode);
        }

        @Override
        public com.io.activej.promise.Promise<List<EnhancedEpisode>> queryEpisodes(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return com.io.activej.promise.Promise.of(episodes.values().stream().toList());
        }

        @Override
        public com.io.activej.promise.Promise<EnhancedFact> storeFact(EnhancedFact fact) {
            facts.put(fact.getId(), fact);
            return com.io.activej.promise.Promise.of(fact);
        }

        @Override
        public com.io.activej.promise.Promise<List<EnhancedFact>> queryFacts(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return com.io.activej.promise.Promise.of(facts.values().stream().toList());
        }

        @Override
        public com.io.activej.promise.Promise<EnhancedProcedure> storeProcedure(EnhancedProcedure procedure) {
            procedures.put(procedure.getId(), procedure);
            return com.io.activej.promise.Promise.of(procedure);
        }

        @Override
        public com.io.activej.promise.Promise<List<EnhancedProcedure>> queryProcedures(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return com.io.activej.promise.Promise.of(procedures.values().stream().toList());
        }

        @Override
        public com.io.activej.promise.Promise<EnhancedProcedure> getProcedure(String procedureId) {
            return com.io.activej.promise.Promise.of(procedures.get(procedureId));
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.artifact.TypedArtifact> writeArtifact(
                com.ghatana.agent.memory.model.artifact.TypedArtifact artifact) {
            return com.io.activej.promise.Promise.of(artifact);
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.MemoryItem> store(
                com.ghatana.agent.memory.model.MemoryItem item) {
            return com.io.activej.promise.Promise.of(item);
        }

        @Override
        public com.io.activej.promise.Promise<List<com.ghatana.agent.memory.model.MemoryItem>> query(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            lastTenantIdForItemQuery = query.tenantId();
            return com.io.activej.promise.Promise.of(List.of());
        }

        @Override
        public com.io.activej.promise.Promise<List<com.ghatana.agent.memory.model.MemoryItem>> readItems(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return com.io.activej.promise.Promise.of(List.of());
        }

        @Override
        public com.io.activej.promise.Promise<List<com.ghatana.agent.memory.store.ScoredMemoryItem>> searchSemantic(
                String query, List<com.ghatana.agent.memory.model.MemoryItemType> itemTypes,
                int k, java.time.Instant startTime, java.time.Instant endTime) {
            return com.io.activej.promise.Promise.of(List.of());
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
        public com.io.activej.promise.Promise<String> checkpoint(String taskId) {
            return com.io.activej.promise.Promise.of(taskId);
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.store.MemoryPlaneStats> getStats() {
            return com.io.activej.promise.Promise.of(
                    com.ghatana.agent.memory.store.MemoryPlaneStats.builder().build());
        }
    }

    private static class InMemoryMasteryRegistry implements MasteryRegistry {
        private final java.util.Map<String, MasteryState> masteryStates = new java.util.HashMap<>();

        @Override
        public com.io.activej.promise.Promise<Optional<MasteryState>> getMasteryState(String skillId, String agentId) {
            return com.io.activej.promise.Promise.of(Optional.ofNullable(masteryStates.get(skillId)));
        }

        @Override
        public com.io.activej.promise.Promise<Void> setMasteryState(String skillId, String agentId, MasteryState state,
                Map<String, Object> evidence) {
            masteryStates.put(skillId, state);
            return com.io.activej.promise.Promise.complete();
        }

        @Override
        public com.io.activej.promise.Promise<List<MasteryState>> getMasteryHistory(String skillId, String agentId) {
            return com.io.activej.promise.Promise.of(List.of());
        }

        @Override
        public com.io.activej.promise.Promise<Boolean> isVersionCompatible(String skillId, String versionContext) {
            return com.io.activej.promise.Promise.of(true);
        }
    }

    private static class NoopTaskStateStore implements com.ghatana.agent.memory.store.taskstate.TaskStateStore {
        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskState> createTask(
                com.ghatana.agent.memory.model.taskstate.TaskState task) {
            return com.io.activej.promise.Promise.of(task);
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskState> getTask(String taskId) {
            return com.io.activej.promise.Promise.of(null);
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskState> updatePhase(
                String taskId, String phaseId, String status) {
            return com.io.activej.promise.Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskCheckpoint> addCheckpoint(
                String taskId, com.ghatana.agent.memory.model.taskstate.TaskCheckpoint checkpoint) {
            return com.io.activej.promise.Promise.of(checkpoint);
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskBlocker> reportBlocker(
                String taskId, com.ghatana.agent.memory.model.taskstate.TaskBlocker blocker) {
            return com.io.activej.promise.Promise.of(blocker);
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.model.taskstate.TaskBlocker> resolveBlocker(
                String taskId, String blockerId, String resolution) {
            return com.io.activej.promise.Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public com.io.activej.promise.Promise<com.ghatana.agent.memory.store.taskstate.ReconcileResult> reconcileOnResume(
                String taskId) {
            return com.io.activej.promise.Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public com.io.activej.promise.Promise<Void> archiveTask(String taskId) {
            return com.io.activej.promise.Promise.complete();
        }

        @Override
        public com.io.activej.promise.Promise<List<com.ghatana.agent.memory.model.taskstate.TaskState>> listActiveTasks(
                String agentId) {
            return com.io.activej.promise.Promise.of(List.of());
        }

        @Override
        public com.io.activej.promise.Promise<Integer> garbageCollect(java.time.Instant inactiveSince) {
            return com.io.activej.promise.Promise.of(0);
        }
    }
}
