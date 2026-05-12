/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
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
 * Tests for GovernedMemoryPlane mastery-aware read/write logic.
 *
 * @doc.type class
 * @doc.purpose Test mastery-aware filtering and validation in GovernedMemoryPlane
 * @doc.layer test
 */
@DisplayName("GovernedMemoryPlane Mastery Integration Tests")
public class GovernedMemoryPlaneMasteryTest {

    private Eventloop eventloop;
    private EventloopThread eventloopThread;
    private InMemoryMemoryPlane memoryPlane;
    private InMemoryMasteryRegistry masteryRegistry;
    private GovernedMemoryPlane governedMemoryPlane;

    @BeforeEach
    void setUp() throws InterruptedException {
        eventloop = Eventloop.create().withCurrentThread();
        eventloopThread = EventloopThread.create().withName("test-eventloop").start();
        eventloopThread.submit(() -> {
            memoryPlane = new InMemoryMemoryPlane();
            masteryRegistry = new InMemoryMasteryRegistry();
            governedMemoryPlane = new GovernedMemoryPlane(
                    memoryPlane,
                    new NoopDataAccessBroker(),
                    "test-tenant",
                    "test-subject",
                    masteryRegistry);
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
    @DisplayName("Should filter out OBSOLETE procedures by default")
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
            governedMemoryPlane.storeProcedure(obsoleteProcedure).join();
            governedMemoryPlane.storeProcedure(normalProcedure).join();

            // Query without includeObsolete flag
            MemoryQuery query = MemoryQuery.builder()
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .includeObsolete(false)
                    .build();

            List<EnhancedProcedure> procedures = governedMemoryPlane.queryProcedures(query).join();

            // Should only return the normal procedure
            assertEquals(1, procedures.size());
            assertEquals("proc-normal", procedures.get(0).getId());
            assertFalse(procedures.stream()
                    .anyMatch(p -> "OBSOLETE".equals(p.getMetadata().get("masteryState"))));
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
            governedMemoryPlane.storeProcedure(obsoleteProcedure).join();
            governedMemoryPlane.storeProcedure(normalProcedure).join();

            // Query with includeObsolete flag
            MemoryQuery query = MemoryQuery.builder()
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .includeObsolete(true)
                    .build();

            List<EnhancedProcedure> procedures = governedMemoryPlane.queryProcedures(query).join();

            // Should return both procedures
            assertEquals(2, procedures.size());
            assertTrue(procedures.stream()
                    .anyMatch(p -> "OBSOLETE".equals(p.getMetadata().get("masteryState"))));
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
            governedMemoryPlane.storeProcedure(maintenanceProcedure).join();
            governedMemoryPlane.storeProcedure(normalProcedure).join();

            // Query without includeMaintenanceOnly flag
            MemoryQuery query = MemoryQuery.builder()
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .includeMaintenanceOnly(false)
                    .build();

            List<EnhancedProcedure> procedures = governedMemoryPlane.queryProcedures(query).join();

            // Should only return the normal procedure
            assertEquals(1, procedures.size());
            assertEquals("proc-normal", procedures.get(0).getId());
            assertFalse(procedures.stream()
                    .anyMatch(p -> "MAINTENANCE_ONLY".equals(p.getMetadata().get("masteryState"))));
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
            governedMemoryPlane.storeProcedure(maintenanceProcedure).join();
            governedMemoryPlane.storeProcedure(normalProcedure).join();

            // Query with includeMaintenanceOnly flag
            MemoryQuery query = MemoryQuery.builder()
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .includeMaintenanceOnly(true)
                    .build();

            List<EnhancedProcedure> procedures = governedMemoryPlane.queryProcedures(query).join();

            // Should return both procedures
            assertEquals(2, procedures.size());
            assertTrue(procedures.stream()
                    .anyMatch(p -> "MAINTENANCE_ONLY".equals(p.getMetadata().get("masteryState"))));
        }).join();
    }

    @Test
    @DisplayName("Should validate procedural skill metadata on write")
    void testValidateProceduralSkillMetadataOnWrite() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a procedural skill without required metadata
            EnhancedProcedure invalidProcedure = EnhancedProcedure.builder()
                    .id("proc-invalid")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Test situation")
                    .action("Test action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of(
                            "learningTarget", "PROCEDURAL_SKILL"))
                    // Missing skillId and masteryState
                    .build();

            // Should throw exception when storing
            assertThrows(IllegalStateException.class, () -> {
                governedMemoryPlane.storeProcedure(invalidProcedure).join();
            });
        }).join();
    }

    @Test
    @DisplayName("Should validate negative knowledge justification on write")
    void testValidateNegativeKnowledgeJustificationOnWrite() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a negative knowledge fact without justification
            EnhancedFact invalidFact = EnhancedFact.builder()
                    .id("fact-invalid")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .subject("API endpoint")
                    .predicate("is not available")
                    .object("/api/weather")
                    .confidence(0.9)
                    .metadata(Map.of(
                            "learningTarget", "NEGATIVE_KNOWLEDGE"))
                    // Missing justification
                    .build();

            // Should throw exception when storing
            assertThrows(IllegalStateException.class, () -> {
                governedMemoryPlane.storeFact(invalidFact).join();
            });
        }).join();
    }

    @Test
    @DisplayName("Should accept valid procedural skill with all metadata")
    void testAcceptValidProceduralSkill() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a valid procedural skill
            EnhancedProcedure validProcedure = EnhancedProcedure.builder()
                    .id("proc-valid")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Test situation")
                    .action("Test action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of(
                            "learningTarget", "PROCEDURAL_SKILL",
                            "skillId", "skill-001",
                            "masteryState", "PRACTICED",
                            "provenance", "episode-001"))
                    .build();

            // Should not throw exception when storing
            assertDoesNotThrow(() -> {
                governedMemoryPlane.storeProcedure(validProcedure).join();
            });
        }).join();
    }

    @Test
    @DisplayName("Should work without MasteryRegistry when null")
    void testWorkWithoutMasteryRegistry() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a governed memory plane without mastery registry
            GovernedMemoryPlane planeWithoutRegistry = new GovernedMemoryPlane(
                    memoryPlane,
                    new NoopDataAccessBroker(),
                    "test-tenant",
                    "test-subject",
                    null);

            // Create a normal procedure
            EnhancedProcedure normalProcedure = EnhancedProcedure.builder()
                    .id("proc-normal")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Normal situation")
                    .action("Normal action")
                    .confidence(0.8)
                    .successRate(0.8)
                    .metadata(Map.of())
                    .build();

            // Should still work without mastery registry
            assertDoesNotThrow(() -> {
                planeWithoutRegistry.storeProcedure(normalProcedure).join();
            });

            // Query should still work
            MemoryQuery query = MemoryQuery.builder()
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .build();

            List<EnhancedProcedure> procedures = planeWithoutRegistry.queryProcedures(query).join();
            assertEquals(1, procedures.size());
        }).join();
    }

    // ── Test Doubles ─────────────────────────────────────────────────────

    private static class InMemoryMemoryPlane implements MemoryPlane {
        private final java.util.Map<String, EnhancedProcedure> procedures = new java.util.HashMap<>();
        private final java.util.Map<String, EnhancedFact> facts = new java.util.HashMap<>();
        private final java.util.Map<String, EnhancedEpisode> episodes = new java.util.HashMap<>();

        @Override
        public Promise<EnhancedEpisode> storeEpisode(EnhancedEpisode episode) {
            episodes.put(episode.getId(), episode);
            return Promise.of(episode);
        }

        @Override
        public Promise<List<EnhancedEpisode>> queryEpisodes(MemoryQuery query) {
            return Promise.of(episodes.values().stream().toList());
        }

        @Override
        public Promise<EnhancedFact> storeFact(EnhancedFact fact) {
            facts.put(fact.getId(), fact);
            return Promise.of(fact);
        }

        @Override
        public Promise<List<EnhancedFact>> queryFacts(MemoryQuery query) {
            return Promise.of(facts.values().stream().toList());
        }

        @Override
        public Promise<EnhancedProcedure> storeProcedure(EnhancedProcedure procedure) {
            procedures.put(procedure.getId(), procedure);
            return Promise.of(procedure);
        }

        @Override
        public Promise<List<EnhancedProcedure>> queryProcedures(MemoryQuery query) {
            return Promise.of(procedures.values().stream().toList());
        }

        @Override
        public Promise<EnhancedProcedure> getProcedure(String procedureId) {
            return Promise.of(procedures.get(procedureId));
        }

        @Override
        public Promise<com.ghatana.agent.memory.model.artifact.TypedArtifact> writeArtifact(
                com.ghatana.agent.memory.model.artifact.TypedArtifact artifact) {
            return Promise.of(artifact);
        }

        @Override
        public Promise<com.ghatana.agent.memory.model.MemoryItem> store(
                com.ghatana.agent.memory.model.MemoryItem item) {
            return Promise.of(item);
        }

        @Override
        public Promise<List<com.ghatana.agent.memory.model.MemoryItem>> query(MemoryQuery query) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<com.ghatana.agent.memory.model.MemoryItem>> readItems(MemoryQuery query) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<com.ghatana.agent.memory.store.ScoredMemoryItem>> searchSemantic(
                String query, List<com.ghatana.agent.memory.model.MemoryItemType> itemTypes,
                int k, java.time.Instant startTime, java.time.Instant endTime) {
            return Promise.of(List.of());
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
        public Promise<String> checkpoint(String taskId) {
            return Promise.of(taskId);
        }

        @Override
        public Promise<com.ghatana.agent.memory.store.MemoryPlaneStats> getStats() {
            return Promise.of(com.ghatana.agent.memory.store.MemoryPlaneStats.builder().build());
        }
    }

    private static class InMemoryMasteryRegistry implements MasteryRegistry {
        private final java.util.Map<String, MasteryState> masteryStates = new java.util.HashMap<>();

        @Override
        public Promise<Optional<MasteryState>> getMasteryState(String skillId, String agentId) {
            return Promise.of(Optional.ofNullable(masteryStates.get(skillId)));
        }

        @Override
        public Promise<Void> setMasteryState(String skillId, String agentId, MasteryState state,
                Map<String, Object> evidence) {
            masteryStates.put(skillId, state);
            return Promise.complete();
        }

        @Override
        public Promise<List<MasteryState>> getMasteryHistory(String skillId, String agentId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Boolean> isVersionCompatible(String skillId, String versionContext) {
            return Promise.of(true);
        }
    }

    private static class NoopDataAccessBroker implements com.ghatana.data.governance.DataAccessBroker {
        @Override
        public Promise<Void> checkAccess(String tenantId, String subjectId, String dataId, String purpose) {
            return Promise.complete();
        }
    }

    private static class NoopTaskStateStore implements com.ghatana.agent.memory.store.taskstate.TaskStateStore {
        @Override
        public Promise<com.ghatana.agent.memory.model.taskstate.TaskState> createTask(
                com.ghatana.agent.memory.model.taskstate.TaskState task) {
            return Promise.of(task);
        }

        @Override
        public Promise<com.ghatana.agent.memory.model.taskstate.TaskState> getTask(String taskId) {
            return Promise.of(null);
        }

        @Override
        public Promise<com.ghatana.agent.memory.model.taskstate.TaskState> updatePhase(
                String taskId, String phaseId, String status) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<com.ghatana.agent.memory.model.taskstate.TaskCheckpoint> addCheckpoint(
                String taskId, com.ghatana.agent.memory.model.taskstate.TaskCheckpoint checkpoint) {
            return Promise.of(checkpoint);
        }

        @Override
        public Promise<com.ghatana.agent.memory.model.taskstate.TaskBlocker> reportBlocker(
                String taskId, com.ghatana.agent.memory.model.taskstate.TaskBlocker blocker) {
            return Promise.of(blocker);
        }

        @Override
        public Promise<com.ghatana.agent.memory.model.taskstate.TaskBlocker> resolveBlocker(
                String taskId, String blockerId, String resolution) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<com.ghatana.agent.memory.store.taskstate.ReconcileResult> reconcileOnResume(
                String taskId) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<Void> archiveTask(String taskId) {
            return Promise.complete();
        }

        @Override
        public Promise<List<com.ghatana.agent.memory.model.taskstate.TaskState>> listActiveTasks(
                String agentId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Integer> garbageCollect(java.time.Instant inactiveSince) {
            return Promise.of(0);
        }
    }
}
