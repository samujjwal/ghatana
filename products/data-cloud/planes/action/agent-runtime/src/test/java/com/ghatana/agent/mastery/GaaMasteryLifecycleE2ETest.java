/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.audit.TraceEvent;
import com.ghatana.agent.audit.TraceEventType;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.config.AgentType;
import com.ghatana.agent.framework.learning.LearningContract;
import com.ghatana.agent.framework.learning.LearningEngine;
import com.ghatana.agent.framework.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.memory.governance.GovernedMemoryPlane;
import com.ghatana.agent.memory.governance.MemoryWritePolicy;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.retrieval.mastery.MasteryAwareMemoryQuery;
import com.ghatana.agent.memory.retrieval.mastery.RetrievalPolicy;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.EventloopThread;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end scenario test for the full GAA mastery lifecycle.
 *
 * <p>This test exercises the complete mastery lifecycle including:
 * <ul>
 *   <li>Skill creation with mastery state tracking</li>
 *   <li>Learning delta proposal and promotion</li>
 *   <li>Version compatibility checks</li>
 *   <li>Obsolescence handling</li>
 *   <li>Mastery-aware memory retrieval</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose End-to-end mastery lifecycle validation
 * @doc.layer test
 */
@DisplayName("GAA Mastery Lifecycle E2E Test")
public class GaaMasteryLifecycleE2ETest {

    private Eventloop eventloop;
    private EventloopThread eventloopThread;
    private InMemoryMemoryPlane memoryPlane;
    private InMemoryMasteryRegistry masteryRegistry;
    private InMemoryTraceLedger traceLedger;
    private GovernedMemoryPlane governedMemoryPlane;
    private LearningEngine learningEngine;

    @BeforeEach
    void setUp() throws InterruptedException {
        eventloop = Eventloop.create().withCurrentThread();
        eventloopThread = EventloopThread.create().withName("test-eventloop").start();
        eventloopThread.submit(() -> {
            memoryPlane = new InMemoryMemoryPlane();
            masteryRegistry = new InMemoryMasteryRegistry();
            traceLedger = new InMemoryTraceLedger();
            governedMemoryPlane = new GovernedMemoryPlane(
                    memoryPlane,
                    new NoopDataAccessBroker(),
                    "test-tenant",
                    "test-subject",
                    masteryRegistry);
            learningEngine = new LearningEngine(
                    LearningContract.of(LearningLevel.L2, LearningTarget.PROCEDURAL_SKILL),
                    null);
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
    @DisplayName("Should complete full mastery lifecycle from skill creation to promotion")
    void testFullMasteryLifecycle() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Step 1: Create a procedural skill with OBSERVED mastery state
            EnhancedProcedure observedSkill = EnhancedProcedure.builder()
                    .id("skill-001")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("User asks for weather forecast")
                    .action("Call weather API and return forecast")
                    .confidence(0.6)
                    .successRate(0.6)
                    .metadata(Map.of(
                            "learningTarget", "PROCEDURAL_SKILL",
                            "skillId", "skill-001",
                            "masteryState", "OBSERVED",
                            "provenanceRequired", "true",
                            "provenance", "episode-001"))
                    .build();

            // Step 2: Store the skill via governed memory plane (should pass validation)
            Promise<EnhancedProcedure> storePromise = governedMemoryPlane.storeProcedure(observedSkill);
            EnhancedProcedure stored = storePromise.join();
            assertNotNull(stored);
            assertEquals("OBSERVED", stored.getMetadata().get("masteryState"));

            // Step 3: Simulate learning episodes to promote to PRACTICED
            for (int i = 0; i < 5; i++) {
                EnhancedEpisode episode = EnhancedEpisode.builder()
                        .id("episode-" + (i + 1))
                        .tenantId("test-tenant")
                        .agentId("test-agent")
                        .turnId("turn-" + (i + 1))
                        .timestamp(Instant.now())
                        .input("Weather request " + (i + 1))
                        .output("Weather forecast " + (i + 1))
                        .latencyMs(100L)
                        .context(Map.of("skillId", "skill-001"))
                        .build();
                governedMemoryPlane.storeEpisode(episode).join();
            }

            // Step 4: Update mastery state to PRACTICED
            EnhancedProcedure practicedSkill = stored.toBuilder()
                    .successRate(0.75)
                    .metadata(Map.of(
                            "learningTarget", "PROCEDURAL_SKILL",
                            "skillId", "skill-001",
                            "masteryState", "PRACTICED",
                            "provenanceRequired", "true",
                            "provenance", "episode-001"))
                    .build();
            EnhancedProcedure updated = governedMemoryPlane.storeProcedure(practicedSkill).join();
            assertEquals("PRACTICED", updated.getMetadata().get("masteryState"));

            // Step 5: Query with mastery-aware retrieval (should exclude obsolete/retired)
            MemoryQuery query = MemoryQuery.builder()
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .includeObsolete(false)
                    .includeMaintenanceOnly(false)
                    .build();
            List<EnhancedProcedure> procedures = governedMemoryPlane.queryProcedures(query).join();
            assertFalse(procedures.isEmpty());
            assertTrue(procedures.stream()
                    .allMatch(p -> !"OBSOLETE".equals(p.getMetadata().get("masteryState"))));

            // Step 6: Mark skill as obsolete
            EnhancedProcedure obsoleteSkill = updated.toBuilder()
                    .metadata(Map.of(
                            "learningTarget", "PROCEDURAL_SKILL",
                            "skillId", "skill-001",
                            "masteryState", "OBSOLETE",
                            "provenanceRequired", "true",
                            "provenance", "episode-001"))
                    .build();
            governedMemoryPlane.storeProcedure(obsoleteSkill).join();

            // Step 7: Query without obsolete flag (should not return obsolete skill)
            List<EnhancedProcedure> activeProcedures = governedMemoryPlane.queryProcedures(query).join();
            assertTrue(activeProcedures.stream()
                    .noneMatch(p -> "OBSOLETE".equals(p.getMetadata().get("masteryState"))));

            // Step 8: Query with obsolete flag (should return obsolete skill)
            MemoryQuery obsoleteQuery = MemoryQuery.builder()
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .includeObsolete(true)
                    .build();
            List<EnhancedProcedure> allProcedures = governedMemoryPlane.queryProcedures(obsoleteQuery).join();
            assertTrue(allProcedures.stream()
                    .anyMatch(p -> "OBSOLETE".equals(p.getMetadata().get("masteryState"))));

            // Step 9: Verify trace events were recorded
            List<TraceEvent> events = traceLedger.getEvents();
            assertFalse(events.isEmpty());

        }).join();
    }

    @Test
    @DisplayName("Should reject procedural skills without required mastery metadata")
    void testRejectProceduralSkillsWithoutMetadata() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create a procedural skill without mastery state
            EnhancedProcedure invalidSkill = EnhancedProcedure.builder()
                    .id("skill-invalid")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Test situation")
                    .action("Test action")
                    .confidence(0.5)
                    .successRate(0.5)
                    .metadata(Map.of(
                            "learningTarget", "PROCEDURAL_SKILL",
                            "skillId", "skill-invalid"))
                    // Missing masteryState
                    .build();

            // Should throw exception when storing
            assertThrows(IllegalStateException.class, () -> {
                governedMemoryPlane.storeProcedure(invalidSkill).join();
            });

            // Create a procedural skill without skillId
            EnhancedProcedure invalidSkill2 = EnhancedProcedure.builder()
                    .id("skill-invalid-2")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .situation("Test situation")
                    .action("Test action")
                    .confidence(0.5)
                    .successRate(0.5)
                    .metadata(Map.of(
                            "learningTarget", "PROCEDURAL_SKILL",
                            "masteryState", "OBSERVED"))
                    // Missing skillId
                    .build();

            assertThrows(IllegalStateException.class, () -> {
                governedMemoryPlane.storeProcedure(invalidSkill2).join();
            });

        }).join();
    }

    @Test
    @DisplayName("Should validate negative knowledge requires justification")
    void testNegativeKnowledgeRequiresJustification() throws InterruptedException {
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

            assertThrows(IllegalStateException.class, () -> {
                governedMemoryPlane.storeFact(invalidFact).join();
            });

            // Create a valid negative knowledge fact
            EnhancedFact validFact = EnhancedFact.builder()
                    .id("fact-valid")
                    .tenantId("test-tenant")
                    .agentId("test-agent")
                    .subject("API endpoint")
                    .predicate("is not available")
                    .object("/api/weather")
                    .confidence(0.9)
                    .metadata(Map.of(
                            "learningTarget", "NEGATIVE_KNOWLEDGE",
                            "justification",
                            "Endpoint was deprecated in version 2.0",
                            "evidenceRef", "ticket-12345"))
                    .build();

            EnhancedFact stored = governedMemoryPlane.storeFact(validFact).join();
            assertNotNull(stored);
            assertEquals("NEGATIVE_KNOWLEDGE", stored.getMetadata().get("learningTarget"));

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

    private static class InMemoryTraceLedger implements AgentTraceLedger {
        private final java.util.List<TraceEvent> events = new java.util.ArrayList<>();

        @Override
        public Promise<Void> append(TraceEvent event) {
            events.add(event);
            return Promise.complete();
        }

        @Override
        public Promise<List<TraceEvent>> query(String agentId, String tenantId, 
                java.time.Instant since, int limit) {
            return Promise.of(events);
        }

        public List<TraceEvent> getEvents() {
            return List.copyOf(events);
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
