/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.registry;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for AgentRegistry + InMemoryAgentRegistry
 * and AgentOperatorFactory.
 */
@DisplayName("Registry & Operator Factory")
class RegistryAndFactoryTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("registry-test")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        AtomicReference<T> result = new AtomicReference<>(); // GH-90000
        AtomicReference<Exception> err = new AtomicReference<>(); // GH-90000
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(result::set) // GH-90000
                .whenException(err::set)); // GH-90000
        eventloop.run(); // GH-90000
        if (err.get() != null) throw new RuntimeException(err.get()); // GH-90000
        return result.get(); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stub Agent
    // ═══════════════════════════════════════════════════════════════════════════

    static class StubTypedAgent extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor desc;
        private final Map<String, Object> output;
        private final AtomicBoolean initialized = new AtomicBoolean(false); // GH-90000
        private final AtomicBoolean shutdown = new AtomicBoolean(false); // GH-90000

        StubTypedAgent(String id, AgentType type, Map<String, Object> output, String... capabilities) { // GH-90000
            this.desc = AgentDescriptor.builder() // GH-90000
                    .agentId(id).name(id).version("1.0")
                    .type(type) // GH-90000
                    .capabilities(capabilities.length > 0 ? // GH-90000
                            new HashSet<>(Arrays.asList(capabilities)) : Set.of()) // GH-90000
                    .build(); // GH-90000
            this.output = output;
        }

        @Override public @NotNull AgentDescriptor descriptor() { return desc; } // GH-90000

        @Override protected @NotNull Promise<Void> doInitialize(@NotNull AgentConfig config) { // GH-90000
            initialized.set(true); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override protected @NotNull Promise<Void> doShutdown() { // GH-90000
            shutdown.set(true); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess( // GH-90000
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            return Promise.of(AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(output) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId(desc.getAgentId()) // GH-90000
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build()); // GH-90000
        }

        boolean wasInitialized() { return initialized.get(); } // GH-90000
        boolean wasShutdown() { return shutdown.get(); } // GH-90000
    }

    private AgentConfig configFor(String id, AgentType type) { // GH-90000
        return AgentConfig.builder().agentId(id).type(type).build(); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // InMemoryAgentRegistry
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("InMemoryAgentRegistry")
    class RegistryTests {

        @Test void registerAndResolve() { // GH-90000
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent agent = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("ok", true)); // GH-90000

            runOnEventloop(() -> registry.register(agent, configFor("a1", AgentType.DETERMINISTIC))); // GH-90000

            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolvedOpt =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(resolvedOpt).isPresent(); // GH-90000
            TypedAgent<Map<String, Object>, Map<String, Object>> resolved = resolvedOpt.get(); // GH-90000
            assertThat(resolved.descriptor().getAgentId()).isEqualTo("a1");
        }

        @Test void resolveReturnsRegisteredAgent() { // GH-90000
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of()); // GH-90000
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of()); // GH-90000

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); // GH-90000
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC))); // GH-90000

            // Assert that registered agents can be resolved
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolvedA1 =
                    runOnEventloop(() -> registry.resolve("a1"));
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolvedA2 =
                    runOnEventloop(() -> registry.resolve("a2"));
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolvedA3 =
                    runOnEventloop(() -> registry.resolve("a3"));

            assertThat(resolvedA1).isPresent().hasValueSatisfying(a ->
                assertThat(a.descriptor().getAgentId()).isEqualTo("a1"));
            assertThat(resolvedA2).isPresent().hasValueSatisfying(a ->
                assertThat(a.descriptor().getAgentId()).isEqualTo("a2"));
            assertThat(resolvedA3).isEmpty();
        }

        @Test void duplicateRegistrationReplaces() { // GH-90000
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("v1", true)); // GH-90000
            StubTypedAgent a2 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("v2", true)); // GH-90000

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); // GH-90000
            runOnEventloop(() -> registry.register(a2, configFor("a1", AgentType.DETERMINISTIC))); // GH-90000

            // InMemoryAgentRegistry silently replaces duplicate registrations
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolved =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(resolved).isPresent(); // GH-90000
            // Should be the second agent (replacement) // GH-90000
            assertThat(resolved.get()).isSameAs(a2); // GH-90000
        }

        @Test void agentLifecycleViaDirectInitializeAndShutdown() { // GH-90000
            // Test actual SPI: agents have lifecycle methods directly, not via registry
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of()); // GH-90000

            // Initialize directly via agent's SPI method
            runOnEventloop(() -> a1.initialize(configFor("a1", AgentType.DETERMINISTIC))); // GH-90000
            assertThat(a1.wasInitialized()).isTrue(); // GH-90000

            // Shutdown directly via agent's SPI method
            runOnEventloop(() -> a1.shutdown()); // GH-90000
            assertThat(a1.wasShutdown()).isTrue(); // GH-90000
        }

        @Test void resolveUnknownReturnsEmpty() { // GH-90000
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolved =
                    runOnEventloop(() -> registry.resolve("unknown"));
            assertThat(resolved).isEmpty(); // GH-90000
        }

        @Test void findByCapabilityReturnsAgentIds() { // GH-90000
            // Test actual SPI: findByCapability returns List<String> of agent IDs
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of(), "fraud-detection"); // GH-90000
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of(), "fraud-detection", "scoring"); // GH-90000
            StubTypedAgent a3 = new StubTypedAgent("a3", AgentType.REACTIVE, Map.of(), "monitoring"); // GH-90000

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); // GH-90000
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC))); // GH-90000
            runOnEventloop(() -> registry.register(a3, configFor("a3", AgentType.REACTIVE))); // GH-90000

            // SPI returns List<String> of agent IDs, not List<AgentDescriptor>
            List<String> fraudIds = runOnEventloop(() -> registry.findByCapability("fraud-detection"));
            assertThat(fraudIds).hasSize(2).containsExactlyInAnyOrder("a1", "a2"); // GH-90000

            List<String> scoringIds = runOnEventloop(() -> registry.findByCapability("scoring"));
            assertThat(scoringIds).hasSize(1).containsExactly("a2"); // GH-90000

            List<String> monitoringIds = runOnEventloop(() -> registry.findByCapability("monitoring"));
            assertThat(monitoringIds).hasSize(1).containsExactly("a3"); // GH-90000

            List<String> unknownIds = runOnEventloop(() -> registry.findByCapability("unknown"));
            assertThat(unknownIds).isEmpty(); // GH-90000
        }

        @Test void listAgentIdsReturnsAllRegisteredAgentIds() { // GH-90000
            // Test actual SPI: listAgentIds returns Set<String> of all registered agent IDs
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of()); // GH-90000
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of()); // GH-90000

            // Initially empty
            Set<String> emptySet = runOnEventloop(() -> registry.listAgentIds());
            assertThat(emptySet).isEmpty(); // GH-90000

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); // GH-90000
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC))); // GH-90000

            Set<String> allIds = runOnEventloop(() -> registry.listAgentIds());
            assertThat(allIds).hasSize(2).containsExactlyInAnyOrder("a1", "a2"); // GH-90000
        }

        @Test void agentReconfigurationReplacesInstance() { // GH-90000
            // Test actual SPI: re-registering with same ID replaces the agent instance
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent originalAgent = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("version", 1)); // GH-90000
            StubTypedAgent replacementAgent = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("version", 2)); // GH-90000

            AgentConfig originalConfig = configFor("a1", AgentType.DETERMINISTIC); // GH-90000
            AgentConfig updatedConfig = AgentConfig.builder() // GH-90000
                    .agentId("a1")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .timeout(Duration.ofSeconds(10)) // GH-90000
                    .build(); // GH-90000

            // Register original
            runOnEventloop(() -> registry.register(originalAgent, originalConfig)); // GH-90000
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> firstResolve =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(firstResolve).isPresent().hasValue(originalAgent); // GH-90000

            // Re-register with same ID (hot-reload pattern via re-registration)
            runOnEventloop(() -> registry.register(replacementAgent, updatedConfig)); // GH-90000
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> secondResolve =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(secondResolve).isPresent().hasValue(replacementAgent); // GH-90000

            // Verify replacement happened
            assertThat(secondResolve.get()).isNotEqualTo(originalAgent); // GH-90000
            assertThat(secondResolve.get()).isEqualTo(replacementAgent); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentOperatorFactory
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentOperatorFactory")
    class FactoryTests {

        @Test void createTreeFromRegistryRegisteredAgent() { // GH-90000
            // Test actual SPI: create tree from agent registered in registry
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent agent = new StubTypedAgent("op-agent", AgentType.DETERMINISTIC, // GH-90000
                    Map.of("result", "processed")); // GH-90000

            // Register in registry (no initialize needed for registration)
            runOnEventloop(() -> registry.register(agent, configFor("op-agent", AgentType.DETERMINISTIC))); // GH-90000

            AgentOperatorFactory factory = new AgentOperatorFactory(registry); // GH-90000
            // Factory resolves agent from registry by ID
            AgentOperatorFactory.OperatorTree tree = runOnEventloop(() -> // GH-90000
                    factory.createOperatorTree("op-agent"));

            assertThat(tree).isNotNull(); // GH-90000
            assertThat(tree.getOperators()).hasSize(1); // GH-90000
        }

        @Test void executeTreeWithPreInitializedAgent() { // GH-90000
            // Test actual SPI: execute tree when agent is pre-initialized
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent agent = new StubTypedAgent("exec-agent", AgentType.DETERMINISTIC, // GH-90000
                    Map.of("data", "hello")); // GH-90000

            // Register in registry
            runOnEventloop(() -> registry.register(agent, configFor("exec-agent", AgentType.DETERMINISTIC))); // GH-90000
            // Initialize agent directly (actual SPI)
            runOnEventloop(() -> agent.initialize(configFor("exec-agent", AgentType.DETERMINISTIC))); // GH-90000

            AgentOperatorFactory factory = new AgentOperatorFactory(registry); // GH-90000
            AgentOperatorFactory.OperatorTree tree = runOnEventloop(() -> // GH-90000
                    factory.createOperatorTree("exec-agent"));

            AgentResult<Map<String, Object>> result = runOnEventloop(() -> // GH-90000
                    tree.execute(ctx, Map.of("input", "test"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("data", "hello"); // GH-90000
        }

        @Test void directTreeCreation() { // GH-90000
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent agent = new StubTypedAgent("direct", AgentType.DETERMINISTIC, // GH-90000
                    Map.of("ok", true)); // GH-90000

            // Initialize the agent first
            runOnEventloop(() -> agent.initialize(configFor("direct", AgentType.DETERMINISTIC))); // GH-90000

            AgentOperatorFactory factory = new AgentOperatorFactory(registry); // GH-90000
            AgentOperatorFactory.OperatorTree tree = factory.createOperatorTree( // GH-90000
                    agent, configFor("direct", AgentType.DETERMINISTIC)); // GH-90000

            assertThat(tree.getOperators()).hasSize(1); // GH-90000

            var result = runOnEventloop(() -> tree.execute(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test void customMappingWithPreInitializedAgent() { // GH-90000
            // Test actual SPI: custom mapping with pre-initialized agent
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000
            StubTypedAgent agent = new StubTypedAgent("custom", AgentType.DETERMINISTIC, // GH-90000
                    Map.of("raw", true)); // GH-90000

            // Register and initialize agent directly (actual SPI)
            runOnEventloop(() -> registry.register(agent, configFor("custom", AgentType.DETERMINISTIC))); // GH-90000
            runOnEventloop(() -> agent.initialize(configFor("custom", AgentType.DETERMINISTIC))); // GH-90000

            AgentOperatorFactory factory = new AgentOperatorFactory(registry); // GH-90000

            // Register custom mapping that adds a post-processor
            factory.registerMapping("custom", (a, config) -> { // GH-90000
                @SuppressWarnings("unchecked")
                TypedAgent<Map<String, Object>, Map<String, Object>> typed =
                        (TypedAgent<Map<String, Object>, Map<String, Object>>) a; // GH-90000
                AgentOperatorFactory.AgentOperator op = AgentOperatorFactory.AgentOperator.builder() // GH-90000
                        .name("custom-op")
                        .agentType(a.descriptor().getType()) // GH-90000
                        .agent(typed) // GH-90000
                        .postProcessor(result -> result.toBuilder() // GH-90000
                                .output(new HashMap<>(result.getOutput()) {{ // GH-90000
                                    put("enriched", true); // GH-90000
                                }})
                                .build()) // GH-90000
                        .build(); // GH-90000
                return AgentOperatorFactory.OperatorTree.builder() // GH-90000
                        .name("custom-tree")
                        .operators(List.of(op)) // GH-90000
                        .build(); // GH-90000
            });

            AgentOperatorFactory.OperatorTree tree = runOnEventloop(() -> // GH-90000
                    factory.createOperatorTree("custom"));

            var result = runOnEventloop(() -> tree.execute(ctx, Map.of())); // GH-90000
            assertThat(result.getOutput()).containsEntry("enriched", true); // GH-90000
        }

        @Test void sequentialChain() { // GH-90000
            // Create two agents that will form a pipeline
            StubTypedAgent a1 = new StubTypedAgent("s1", AgentType.DETERMINISTIC, // GH-90000
                    Map.of("stage", "1", "value", 10)); // GH-90000
            StubTypedAgent a2 = new StubTypedAgent("s2", AgentType.DETERMINISTIC, // GH-90000
                    Map.of("stage", "2", "value", 20)); // GH-90000

            runOnEventloop(() -> a1.initialize(configFor("s1", AgentType.DETERMINISTIC))); // GH-90000
            runOnEventloop(() -> a2.initialize(configFor("s2", AgentType.DETERMINISTIC))); // GH-90000

            AgentOperatorFactory.AgentOperator op1 = AgentOperatorFactory.AgentOperator.builder() // GH-90000
                    .name("op-1").agentType(AgentType.DETERMINISTIC).agent(a1).build();
            AgentOperatorFactory.AgentOperator op2 = AgentOperatorFactory.AgentOperator.builder() // GH-90000
                    .name("op-2").agentType(AgentType.DETERMINISTIC).agent(a2).build();

            AgentOperatorFactory.OperatorTree tree = AgentOperatorFactory.OperatorTree.builder() // GH-90000
                    .name("pipeline")
                    .operators(List.of(op1, op2)) // GH-90000
                    .build(); // GH-90000

            var result = runOnEventloop(() -> tree.execute(ctx, Map.of("start", true))); // GH-90000
            // Sequential chain: last agent's output prevails (each sees previous output as input) // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("stage", "2"); // GH-90000
        }
    }
}
