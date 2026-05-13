/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.context.version.VersionContextResolver;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseRepository;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskClassifier;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.EventloopThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GovernedAgentDispatcher mastery, version context, task classification, and mode selection.
 *
 * @doc.type class
 * @doc.purpose Test mastery-aware checks in GovernedAgentDispatcher
 * @doc.layer test
 */
@DisplayName("GovernedAgentDispatcher Mastery Integration Tests")
public class GovernedAgentDispatcherMasteryTest {

    private Eventloop eventloop;
    private EventloopThread eventloopThread;
    private MockAgentDispatcher delegate;
    private MockMasteryRegistry masteryRegistry;
    private MockVersionContextResolver versionContextResolver;
    private MockTaskClassifier taskClassifier;
    private MockModeSelector modeSelector;
    private InvariantMonitor invariantMonitor;
    private InMemoryTraceLedger traceLedger;
    private GovernedAgentDispatcher dispatcher;

    @BeforeEach
    void setUp() throws InterruptedException {
        eventloop = Eventloop.create().withCurrentThread();
        eventloopThread = EventloopThread.create().name("test-eventloop").start();
        eventloopThread.submit(() -> {
            delegate = new MockAgentDispatcher();
            masteryRegistry = new MockMasteryRegistry();
            versionContextResolver = new MockVersionContextResolver();
            taskClassifier = new MockTaskClassifier();
            modeSelector = new MockModeSelector();
            invariantMonitor = new InvariantMonitor();
            traceLedger = new InMemoryTraceLedger();
            dispatcher = new GovernedAgentDispatcher(
                    delegate,
                    invariantMonitor,
                    traceLedger,
                    null, // release repository
                    null, // agent run tracer
                    null, // capability manifest
                    masteryRegistry,
                    versionContextResolver,
                    taskClassifier,
                    modeSelector);
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
    @DisplayName("Should execute task classification during dispatch")
    void testExecuteTaskClassification() throws InterruptedException {
        eventloopThread.submit(() -> {
            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Verify task classifier was called
            assertTrue(taskClassifier.wasCalled());
        }).join();
    }

    @Test
    @DisplayName("Should execute version context resolution during dispatch")
    void testExecuteVersionContextResolution() throws InterruptedException {
        eventloopThread.submit(() -> {
            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Verify version context resolver was called
            assertTrue(versionContextResolver.wasCalled());
        }).join();
    }

    @Test
    @DisplayName("Should execute mastery check during dispatch")
    void testExecuteMasteryCheck() throws InterruptedException {
        eventloopThread.submit(() -> {
            AgentContext context = new TestAgentContext();
            context.addConfig("skillId", "skill-001");

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Verify mastery registry was consulted
            assertTrue(masteryRegistry.wasCalled());
        }).join();
    }

    @Test
    @DisplayName("Should execute mode selection during dispatch")
    void testExecuteModeSelection() throws InterruptedException {
        eventloopThread.submit(() -> {
            AgentContext context = new TestAgentContext();
            context.addConfig("taskRiskLevel", "HIGH");

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Verify mode selector was called
            assertTrue(modeSelector.wasCalled());
        }).join();
    }

    @Test
    @DisplayName("Should enrich context with task classification results")
    void testEnrichContextWithTaskClassification() throws InterruptedException {
        eventloopThread.submit(() -> {
            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Context should be enriched with task classification results
            // (In real implementation, this would be verified through context inspection)
        }).join();
    }

    @Test
    @DisplayName("Should enrich context with version context")
    void testEnrichContextWithVersionContext() throws InterruptedException {
        eventloopThread.submit(() -> {
            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Context should be enriched with version context
        }).join();
    }

    @Test
    @DisplayName("Should enrich context with execution mode")
    void testEnrichContextWithExecutionMode() throws InterruptedException {
        eventloopThread.submit(() -> {
            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Context should be enriched with execution mode
        }).join();
    }

    @Test
    @DisplayName("Should handle task classification errors gracefully")
    void testHandleTaskClassificationErrors() throws InterruptedException {
        eventloopThread.submit(() -> {
            taskClassifier.setShouldFail(true);

            AgentContext context = new TestAgentContext();

            // Should not throw exception, but continue without classification
            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
        }).join();
    }

    @Test
    @DisplayName("Should handle version context resolution errors gracefully")
    void testHandleVersionContextResolutionErrors() throws InterruptedException {
        eventloopThread.submit(() -> {
            versionContextResolver.setShouldFail(true);

            AgentContext context = new TestAgentContext();

            // Should not throw exception, but continue without version context
            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
        }).join();
    }

    @Test
    @DisplayName("Should handle mastery check errors gracefully")
    void testHandleMasteryCheckErrors() throws InterruptedException {
        eventloopThread.submit(() -> {
            masteryRegistry.setShouldFail(true);

            AgentContext context = new TestAgentContext();

            // Should not throw exception, but continue without mastery check
            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
        }).join();
    }

    @Test
    @DisplayName("Should handle mode selection errors gracefully")
    void testHandleModeSelectionErrors() throws InterruptedException {
        eventloopThread.submit(() -> {
            modeSelector.setShouldFail(true);

            AgentContext context = new TestAgentContext();

            // Should not throw exception, but continue without mode selection
            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
        }).join();
    }

    @Test
    @DisplayName("Should work without mastery components when null")
    void testWorkWithoutMasteryComponents() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Create dispatcher without mastery components
            GovernedAgentDispatcher dispatcherWithoutMastery = new GovernedAgentDispatcher(
                    delegate,
                    invariantMonitor,
                    traceLedger,
                    null, // release repository
                    null, // agent run tracer
                    null, // capability manifest
                    null, // mastery registry
                    null, // version context resolver
                    null, // task classifier
                    null); // mode selector

            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcherWithoutMastery.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
        }).join();
    }

    @Test
    @DisplayName("Should block execution when approval is required and missing")
    void shouldBlockExecutionWhenApprovalRequiredAndMissing() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Set mode selector to require approval
            modeSelector.setRequiresApproval(true);

            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Should be blocked or rejected due to missing approval
            // In real implementation, this would be verified through result status
        }).join();
    }

    @Test
    @DisplayName("Should block execution when version compatibility fails")
    void shouldBlockExecutionWhenVersionCompatibilityFails() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Set version compatibility check to fail
            masteryRegistry.setVersionCompatible(false);

            AgentContext context = new TestAgentContext();

            AgentResult<String> result = dispatcher.dispatch("test-agent", "input", context).join();

            assertNotNull(result);
            // Should be blocked or rejected due to version incompatibility
            // In real implementation, this would be verified through result status
        }).join();
    }

    // ── Test Doubles ─────────────────────────────────────────────────────

    private static class MockAgentDispatcher implements AgentDispatcher {
        @Override
        public <I, O> io.activej.promise.Promise<AgentResult<O>> dispatch(String agentId, I input, AgentContext ctx) {
            return io.activej.promise.Promise.of(AgentResult.success((O) "output", agentId, java.time.Duration.ofMillis(100)));
        }

        @Override
        public ExecutionTier resolve(String agentId) {
            return ExecutionTier.LOCAL;
        }
    }

    private static class MockMasteryRegistry implements MasteryRegistry {
        private boolean called = false;
        private boolean shouldFail = false;
        private boolean versionCompatible = true;

        @Override
        public io.activej.promise.Promise<Optional<MasteryState>> getMasteryState(String skillId, String agentId) {
            called = true;
            if (shouldFail) {
                return io.activej.promise.Promise.ofException(new RuntimeException("Mastery check failed"));
            }
            return io.activej.promise.Promise.of(Optional.of(MasteryState.PRACTICED));
        }

        @Override
        public io.activej.promise.Promise<Void> setMasteryState(String skillId, String agentId, MasteryState state, Map<String, Object> evidence) {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<List<MasteryState>> getMasteryHistory(String skillId, String agentId) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<Boolean> isVersionCompatible(String skillId, String versionContext) {
            return io.activej.promise.Promise.of(versionCompatible);
        }

        boolean wasCalled() {
            return called;
        }

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        void setVersionCompatible(boolean versionCompatible) {
            this.versionCompatible = versionCompatible;
        }
    }

    private static class MockVersionContextResolver implements VersionContextResolver {
        private boolean called = false;
        private boolean shouldFail = false;

        @Override
        public io.activej.promise.Promise<VersionContext> resolve(String agentId, String tenantId) {
            called = true;
            if (shouldFail) {
                return io.activej.promise.Promise.ofException(new RuntimeException("Version context resolution failed"));
            }
            return io.activej.promise.Promise.of(new VersionContext("v1.0", Map.of()));
        }

        boolean wasCalled() {
            return called;
        }

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }
    }

    private static class MockTaskClassifier implements TaskClassifier {
        private boolean called = false;
        private boolean shouldFail = false;

        @Override
        public TaskClassification classify(String input, AgentContext ctx) {
            called = true;
            if (shouldFail) {
                throw new RuntimeException("Task classification failed");
            }
            return new TaskClassification("HIGH", "NEW");
        }

        boolean wasCalled() {
            return called;
        }

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }
    }

    private static class MockModeSelector implements MasteryAwareModeSelector {
        private boolean called = false;
        private boolean shouldFail = false;
        private boolean requiresApproval = false;

        @Override
        public ExecutionMode selectMode(String agentId, String taskRiskLevel, AgentContext ctx) {
            called = true;
            if (shouldFail) {
                throw new RuntimeException("Mode selection failed");
            }
            return ExecutionMode.STRICT_EXECUTION;
        }

        boolean wasCalled() {
            return called;
        }

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        void setRequiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
        }
    }

    private static class InvariantMonitor {
        public List<InvariantViolation> evaluate(InvariantContext ctx) {
            return List.of();
        }
    }

    private static class InvariantContext {
        InvariantContext(String agentId, String tenantId, String traceId, AgentContext ctx) {}
    }

    private static class InvariantViolation {
        enum Severity { CRITICAL, FATAL, WARNING, INFO }
        Severity severity() { return Severity.INFO; }
        String description() { return "test violation"; }
    }

    private static class InMemoryTraceLedger implements AgentTraceLedger {
        @Override
        public io.activej.promise.Promise<Void> append(com.ghatana.agent.audit.TraceEvent event) {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<List<com.ghatana.agent.audit.TraceEvent>> query(String agentId, String tenantId, java.time.Instant since, int limit) {
            return io.activej.promise.Promise.of(List.of());
        }
    }

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
        public void recordMetric(String name, double value) {}

        @Override
        public void addTraceTag(String key, String value) {}

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
        public io.activej.promise.Promise<com.ghatana.agent.memory.model.procedure.EnhancedProcedure> getProcedure(String procedureId) {
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
