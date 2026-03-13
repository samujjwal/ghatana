/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.ai.impl;

import com.ghatana.aep.agent.AepContextBridge;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager.AgentDefinition;
import com.ghatana.orchestrator.core.Orchestrator;
import com.ghatana.orchestrator.executor.AgentStepRunner;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AIAgentOrchestrationManagerImpl}.
 *
 * <p>Extends {@link EventloopTestBase} because the manager's public methods
 * return {@link io.activej.promise.Promise}. All async assertions use {@code runPromise()}.
 *
 * <p>The manager is created with {@code null} for the optional {@link
 * com.ghatana.datacloud.spi.EventLogStore} parameter so event-sourcing is
 * skipped (dev/test mode), keeping tests focused on orchestration logic.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AI agent orchestration manager
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIAgentOrchestrationManagerImpl")
class AIAgentOrchestrationManagerImplTest extends EventloopTestBase {

    @Mock AgentRegistryService agentRegistryService;
    @Mock Orchestrator orchestrator;
    @Mock ExecutionQueue executionQueue;
    @Mock AgentStepRunner agentStepRunner;
    @Mock MetricsCollector metrics;

    private AIAgentOrchestrationManagerImpl manager;

    /**
     * Tests in this class intentionally trigger {@link io.activej.promise.Promise#ofBlocking}
     * failures, which the event loop captures as "fatal" errors. Override teardown to clear the
     * fatal before {@link com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner#close()}
     * re-throws it and causes a spurious test failure.
     */
    @Override
    protected void tearDownEventloop() {
        if (runner != null) {
            runner.clearFatalError();
        }
        super.tearDownEventloop();
    }

    @BeforeEach
    void setUpManager() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AepContextBridge contextBridge = new AepContextBridge(MemoryStore.noOp());
        // Use backwards-compatible constructor (null eventLogStore = in-memory mode)
        manager = new AIAgentOrchestrationManagerImpl(
                agentRegistryService,
                orchestrator,
                executionQueue,
                agentStepRunner,
                metrics,
                executor,
                contextBridge);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static AgentDefinition agentDef(String id) {
        return new AgentDefinition(id, "Agent " + id, null, null, null, null, null, null, null);
    }

    // ── registerAgent ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("registerAgent()")
    class RegisterAgent {

        @Test
        @DisplayName("returns agent ID when registration succeeds")
        void returnsAgentId() {
            String result = runPromise(() -> manager.registerAgent(agentDef("agent-001")));

            assertThat(result).isEqualTo("agent-001");
        }

        @Test
        @DisplayName("fails when definition is null")
        void failsOnNullDefinition() {
            assertThatThrownBy(() -> runPromise(() -> manager.registerAgent(null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Agent definition and ID are required");
        }

        @Test
        @DisplayName("fails when agent ID is blank")
        void failsOnBlankId() {
            assertThatThrownBy(() -> runPromise(() -> manager.registerAgent(agentDef("  "))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Agent definition and ID are required");
        }

        @Test
        @DisplayName("re-registering the same agent ID overwrites the existing registration")
        void overwritesExistingRegistration() {
            runPromise(() -> manager.registerAgent(agentDef("dup-agent")));
            String second = runPromise(() -> manager.registerAgent(agentDef("dup-agent")));

            assertThat(second).isEqualTo("dup-agent");
        }
    }

    // ── chainAgents ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("chainAgents()")
    class ChainAgents {

        @Test
        @DisplayName("fails when chain name is null")
        void failsOnNullChainName() {
            assertThatThrownBy(() -> runPromise(() -> manager.chainAgents(null, List.of(agentDef("a1")))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chain name is required");
        }

        @Test
        @DisplayName("fails when pipeline is empty")
        void failsOnEmptyPipeline() {
            assertThatThrownBy(() -> runPromise(() -> manager.chainAgents("my-chain", List.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Pipeline cannot be null or empty");
        }

        @Test
        @DisplayName("fails when pipeline contains an unregistered agent")
        void failsOnUnregisteredAgent() {
            assertThatThrownBy(() -> runPromise(() ->
                    manager.chainAgents("my-chain", List.of(agentDef("not-registered")))))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create agent chain")
                    .cause()
                    .hasMessageContaining("not-registered");
        }

        @Test
        @DisplayName("returns a chain ID when all agents are registered")
        void returnsChainIdForRegisteredAgents() {
            runPromise(() -> manager.registerAgent(agentDef("step-1")));
            runPromise(() -> manager.registerAgent(agentDef("step-2")));

            String chainId = runPromise(() ->
                    manager.chainAgents("test-chain", List.of(agentDef("step-1"), agentDef("step-2"))));

            assertThat(chainId).startsWith("chain_");
        }
    }

    // ── executeChain ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeChain()")
    class ExecuteChain {

        @Test
        @DisplayName("fails when chain ID is null")
        void failsOnNullChainId() {
            assertThatThrownBy(() -> runPromise(() -> manager.executeChain(null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chain ID is required");
        }

        @Test
        @DisplayName("fails with 'Input event is required' when inputEvent is null for valid chain ID")
        void failsWhenInputEventIsNull() {
            assertThatThrownBy(() -> runPromise(() -> manager.executeChain("ghost-chain", null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Input event is required");
        }

        @Test
        @DisplayName("fails with 'Chain ID is required' when chain ID is blank")
        void failsOnBlankChainId() {
            assertThatThrownBy(() -> runPromise(() -> manager.executeChain(" ", null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chain ID is required");
        }
    }

    // ── rebuildFromEventLog ─────────────────────────────────────────────────

    @Nested
    @DisplayName("rebuildFromEventLog()")
    class RebuildFromEventLog {

        @Test
        @DisplayName("completes successfully when no event store is configured (in-memory mode)")
        void completesWithoutErrorInInMemoryMode() {
            // manager was created without EventLogStore (null) — rebuild is a no-op
            Void result = runPromise(() -> manager.rebuildFromEventLog());

            assertThat(result).isNull(); // Promise<Void> resolves to null
        }
    }

    // ── cancelExecution ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelExecution()")
    class CancelExecution {

        @Test
        @DisplayName("returns false for an unknown execution ID")
        void returnsFalseForUnknownId() {
            Boolean result = runPromise(() -> manager.cancelExecution("no-such-exec"));

            assertThat(result).isFalse();
        }
    }
}
