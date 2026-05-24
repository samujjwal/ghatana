/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
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
 * and AgentCapabilityExecutionFactory.
 */
@DisplayName("Registry & Operator Factory")
class RegistryAndFactoryTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { 
        ctx = AgentContext.builder() 
                .turnId("turn-1")
                .agentId("registry-test")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class)) 
                .build(); 
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) { 
        AtomicReference<T> result = new AtomicReference<>(); 
        AtomicReference<Exception> err = new AtomicReference<>(); 
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); 
        eventloop.post(() -> supplier.get() 
                .whenResult(result::set) 
                .whenException(err::set)); 
        eventloop.run(); 
        if (err.get() != null) throw new RuntimeException(err.get()); 
        return result.get(); 
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stub Agent
    // ═══════════════════════════════════════════════════════════════════════════

    static class StubTypedAgent extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor desc;
        private final Map<String, Object> output;
        private final AtomicBoolean initialized = new AtomicBoolean(false); 
        private final AtomicBoolean shutdown = new AtomicBoolean(false); 

        StubTypedAgent(String id, AgentType type, Map<String, Object> output, String... capabilities) { 
            this.desc = AgentDescriptor.builder() 
                    .agentId(id).name(id).version("1.0")
                    .type(type) 
                    .capabilities(capabilities.length > 0 ? 
                            new HashSet<>(Arrays.asList(capabilities)) : Set.of()) 
                    .build(); 
            this.output = output;
        }

        @Override public @NotNull AgentDescriptor descriptor() { return desc; } 

        @Override protected @NotNull Promise<Void> doInitialize(@NotNull AgentConfig config) { 
            initialized.set(true); 
            return Promise.complete(); 
        }

        @Override protected @NotNull Promise<Void> doShutdown() { 
            shutdown.set(true); 
            return Promise.complete(); 
        }

        @Override protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess( 
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            return Promise.of(AgentResult.<Map<String, Object>>builder() 
                    .output(output) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId(desc.getAgentId()) 
                    .processingTime(Duration.ofMillis(1)) 
                    .build()); 
        }

        boolean wasInitialized() { return initialized.get(); } 
        boolean wasShutdown() { return shutdown.get(); } 
    }

    private AgentConfig configFor(String id, AgentType type) { 
        return AgentConfig.builder().agentId(id).type(type).build(); 
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // InMemoryAgentRegistry
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("InMemoryAgentRegistry")
    class RegistryTests {

        @Test void registerAndResolve() { 
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent agent = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("ok", true)); 

            runOnEventloop(() -> registry.register(agent, configFor("a1", AgentType.DETERMINISTIC))); 

            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolvedOpt =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(resolvedOpt).isPresent(); 
            TypedAgent<Map<String, Object>, Map<String, Object>> resolved = resolvedOpt.get(); 
            assertThat(resolved.descriptor().getAgentId()).isEqualTo("a1");
        }

        @Test void resolveReturnsRegisteredAgent() { 
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of()); 
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of()); 

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); 
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC))); 

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

        @Test void duplicateRegistrationReplaces() { 
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("v1", true)); 
            StubTypedAgent a2 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("v2", true)); 

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); 
            runOnEventloop(() -> registry.register(a2, configFor("a1", AgentType.DETERMINISTIC))); 

            // InMemoryAgentRegistry silently replaces duplicate registrations
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolved =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(resolved).isPresent(); 
            // Should be the second agent (replacement) 
            assertThat(resolved.get()).isSameAs(a2); 
        }

        @Test void agentLifecycleViaDirectInitializeAndShutdown() { 
            // Test actual SPI: agents have lifecycle methods directly, not via registry
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of()); 

            // Initialize directly via agent's SPI method
            runOnEventloop(() -> a1.initialize(configFor("a1", AgentType.DETERMINISTIC))); 
            assertThat(a1.wasInitialized()).isTrue(); 

            // Shutdown directly via agent's SPI method
            runOnEventloop(() -> a1.shutdown()); 
            assertThat(a1.wasShutdown()).isTrue(); 
        }

        @Test void resolveUnknownReturnsEmpty() { 
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> resolved =
                    runOnEventloop(() -> registry.resolve("unknown"));
            assertThat(resolved).isEmpty(); 
        }

        @Test void findByCapabilityReturnsAgentIds() { 
            // Test actual SPI: findByCapability returns List<String> of agent IDs
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of(), "fraud-detection"); 
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of(), "fraud-detection", "scoring"); 
            StubTypedAgent a3 = new StubTypedAgent("a3", AgentType.REACTIVE, Map.of(), "monitoring"); 

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); 
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC))); 
            runOnEventloop(() -> registry.register(a3, configFor("a3", AgentType.REACTIVE))); 

            // SPI returns List<String> of agent IDs, not List<AgentDescriptor>
            List<String> fraudIds = runOnEventloop(() -> registry.findByCapability("fraud-detection"));
            assertThat(fraudIds).hasSize(2).containsExactlyInAnyOrder("a1", "a2"); 

            List<String> scoringIds = runOnEventloop(() -> registry.findByCapability("scoring"));
            assertThat(scoringIds).hasSize(1).containsExactly("a2"); 

            List<String> monitoringIds = runOnEventloop(() -> registry.findByCapability("monitoring"));
            assertThat(monitoringIds).hasSize(1).containsExactly("a3"); 

            List<String> unknownIds = runOnEventloop(() -> registry.findByCapability("unknown"));
            assertThat(unknownIds).isEmpty(); 
        }

        @Test void listAgentIdsReturnsAllRegisteredAgentIds() { 
            // Test actual SPI: listAgentIds returns Set<String> of all registered agent IDs
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of()); 
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of()); 

            // Initially empty
            Set<String> emptySet = runOnEventloop(() -> registry.listAgentIds());
            assertThat(emptySet).isEmpty(); 

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC))); 
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC))); 

            Set<String> allIds = runOnEventloop(() -> registry.listAgentIds());
            assertThat(allIds).hasSize(2).containsExactlyInAnyOrder("a1", "a2"); 
        }

        @Test void agentReconfigurationReplacesInstance() { 
            // Test actual SPI: re-registering with same ID replaces the agent instance
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent originalAgent = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("version", 1)); 
            StubTypedAgent replacementAgent = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("version", 2)); 

            AgentConfig originalConfig = configFor("a1", AgentType.DETERMINISTIC); 
            AgentConfig updatedConfig = AgentConfig.builder() 
                    .agentId("a1")
                    .type(AgentType.DETERMINISTIC) 
                    .timeout(Duration.ofSeconds(10)) 
                    .build(); 

            // Register original
            runOnEventloop(() -> registry.register(originalAgent, originalConfig)); 
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> firstResolve =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(firstResolve).isPresent().hasValue(originalAgent); 

            // Re-register with same ID (hot-reload pattern via re-registration)
            runOnEventloop(() -> registry.register(replacementAgent, updatedConfig)); 
            Optional<TypedAgent<Map<String, Object>, Map<String, Object>>> secondResolve =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(secondResolve).isPresent().hasValue(replacementAgent); 

            // Verify replacement happened
            assertThat(secondResolve.get()).isNotEqualTo(originalAgent); 
            assertThat(secondResolve.get()).isEqualTo(replacementAgent); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentCapabilityExecutionFactory
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentCapabilityExecutionFactory")
    class FactoryTests {

        @Test void createTreeFromRegistryRegisteredAgent() { 
            // Test actual SPI: create tree from agent registered in registry
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent agent = new StubTypedAgent("op-agent", AgentType.DETERMINISTIC, 
                    Map.of("result", "processed")); 

            // Register in registry (no initialize needed for registration)
            runOnEventloop(() -> registry.register(agent, configFor("op-agent", AgentType.DETERMINISTIC))); 

            AgentCapabilityExecutionFactory factory = new AgentCapabilityExecutionFactory(registry); 
            // Factory resolves agent from registry by ID
            AgentCapabilityExecutionFactory.CapabilityExecutionTree tree = runOnEventloop(() -> 
                    factory.createCapabilityExecutionTree("op-agent"));

            assertThat(tree).isNotNull(); 
            assertThat(tree.getOperators()).hasSize(1); 
        }

        @Test void executeTreeWithPreInitializedAgent() { 
            // Test actual SPI: execute tree when agent is pre-initialized
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent agent = new StubTypedAgent("exec-agent", AgentType.DETERMINISTIC, 
                    Map.of("data", "hello")); 

            // Register in registry
            runOnEventloop(() -> registry.register(agent, configFor("exec-agent", AgentType.DETERMINISTIC))); 
            // Initialize agent directly (actual SPI)
            runOnEventloop(() -> agent.initialize(configFor("exec-agent", AgentType.DETERMINISTIC))); 

            AgentCapabilityExecutionFactory factory = new AgentCapabilityExecutionFactory(registry); 
            AgentCapabilityExecutionFactory.CapabilityExecutionTree tree = runOnEventloop(() -> 
                    factory.createCapabilityExecutionTree("exec-agent"));

            AgentResult<Map<String, Object>> result = runOnEventloop(() -> 
                    tree.execute(ctx, Map.of("input", "test"))); 

            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getOutput()).containsEntry("data", "hello"); 
        }

        @Test void directTreeCreation() { 
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent agent = new StubTypedAgent("direct", AgentType.DETERMINISTIC, 
                    Map.of("ok", true)); 

            // Initialize the agent first
            runOnEventloop(() -> agent.initialize(configFor("direct", AgentType.DETERMINISTIC))); 

            AgentCapabilityExecutionFactory factory = new AgentCapabilityExecutionFactory(registry); 
            AgentCapabilityExecutionFactory.CapabilityExecutionTree tree = factory.createCapabilityExecutionTree( 
                    agent, configFor("direct", AgentType.DETERMINISTIC)); 

            assertThat(tree.getOperators()).hasSize(1); 

            var result = runOnEventloop(() -> tree.execute(ctx, Map.of())); 
            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test void customMappingWithPreInitializedAgent() { 
            // Test actual SPI: custom mapping with pre-initialized agent
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); 
            StubTypedAgent agent = new StubTypedAgent("custom", AgentType.DETERMINISTIC, 
                    Map.of("raw", true)); 

            // Register and initialize agent directly (actual SPI)
            runOnEventloop(() -> registry.register(agent, configFor("custom", AgentType.DETERMINISTIC))); 
            runOnEventloop(() -> agent.initialize(configFor("custom", AgentType.DETERMINISTIC))); 

            AgentCapabilityExecutionFactory factory = new AgentCapabilityExecutionFactory(registry); 

            // Register custom mapping that adds a post-processor
            factory.registerMapping("custom", (a, config) -> { 
                @SuppressWarnings("unchecked")
                TypedAgent<Map<String, Object>, Map<String, Object>> typed =
                        (TypedAgent<Map<String, Object>, Map<String, Object>>) a; 
                AgentCapabilityExecutionFactory.AgentCapabilityStep op = AgentCapabilityExecutionFactory.AgentCapabilityStep.builder() 
                        .name("custom-op")
                        .agentType(a.descriptor().getType()) 
                        .agent(typed) 
                        .postProcessor(result -> result.toBuilder() 
                                .output(new HashMap<>(result.getOutput()) {{ 
                                    put("enriched", true); 
                                }})
                                .build()) 
                        .build(); 
                return AgentCapabilityExecutionFactory.CapabilityExecutionTree.builder() 
                        .name("custom-tree")
                        .operators(List.of(op)) 
                        .build(); 
            });

            AgentCapabilityExecutionFactory.CapabilityExecutionTree tree = runOnEventloop(() -> 
                    factory.createCapabilityExecutionTree("custom"));

            var result = runOnEventloop(() -> tree.execute(ctx, Map.of())); 
            assertThat(result.getOutput()).containsEntry("enriched", true); 
        }

        @Test void sequentialChain() { 
            // Create two agents that will form a pipeline
            StubTypedAgent a1 = new StubTypedAgent("s1", AgentType.DETERMINISTIC, 
                    Map.of("stage", "1", "value", 10)); 
            StubTypedAgent a2 = new StubTypedAgent("s2", AgentType.DETERMINISTIC, 
                    Map.of("stage", "2", "value", 20)); 

            runOnEventloop(() -> a1.initialize(configFor("s1", AgentType.DETERMINISTIC))); 
            runOnEventloop(() -> a2.initialize(configFor("s2", AgentType.DETERMINISTIC))); 

            AgentCapabilityExecutionFactory.AgentCapabilityStep op1 = AgentCapabilityExecutionFactory.AgentCapabilityStep.builder() 
                    .name("op-1").agentType(AgentType.DETERMINISTIC).agent(a1).build();
            AgentCapabilityExecutionFactory.AgentCapabilityStep op2 = AgentCapabilityExecutionFactory.AgentCapabilityStep.builder() 
                    .name("op-2").agentType(AgentType.DETERMINISTIC).agent(a2).build();

            AgentCapabilityExecutionFactory.CapabilityExecutionTree tree = AgentCapabilityExecutionFactory.CapabilityExecutionTree.builder() 
                    .name("pipeline")
                    .operators(List.of(op1, op2)) 
                    .build(); 

            var result = runOnEventloop(() -> tree.execute(ctx, Map.of("start", true))); 
            // Sequential chain: last agent's output prevails (each sees previous output as input) 
            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getOutput()).containsEntry("stage", "2"); 
        }
    }
}
