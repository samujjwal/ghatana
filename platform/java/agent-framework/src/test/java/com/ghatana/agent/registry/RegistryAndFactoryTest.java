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
 * Comprehensive tests for AgentFrameworkRegistry + InMemoryAgentFrameworkRegistry
 * and AgentOperatorFactory.
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
    // InMemoryAgentFrameworkRegistry
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("InMemoryAgentFrameworkRegistry")
    class RegistryTests {

        @Test void registerAndResolve() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent agent = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of("ok", true));

            runOnEventloop(() -> registry.register(agent, configFor("a1", AgentType.DETERMINISTIC)));

            TypedAgent<Map<String, Object>, Map<String, Object>> resolved =
                    runOnEventloop(() -> registry.resolve("a1"));
            assertThat(resolved).isNotNull();
            assertThat(resolved.descriptor().getAgentId()).isEqualTo("a1");
        }

        @Test void containsAndSize() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC)));

            assertThat(registry.size()).isEqualTo(2);
            assertThat(registry.contains("a1")).isTrue();
            assertThat(registry.contains("a3")).isFalse();
        }

        @Test void duplicateRegistrationFails() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));

            assertThatThrownBy(() ->
                runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)))
            ).isInstanceOf(RuntimeException.class);
        }

        @Test void unregisterRemovesAndShutdown() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.initialize("a1"));
            runOnEventloop(() -> registry.unregister("a1"));

            assertThat(registry.contains("a1")).isFalse();
            assertThat(registry.size()).isEqualTo(0);
        }

        @Test void resolveUnknownFails() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            assertThatThrownBy(() -> runOnEventloop(() -> registry.resolve("unknown")))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test void findByType() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent d1 = new StubTypedAgent("d1", AgentType.DETERMINISTIC, Map.of());
            StubTypedAgent d2 = new StubTypedAgent("d2", AgentType.DETERMINISTIC, Map.of());
            StubTypedAgent p1 = new StubTypedAgent("p1", AgentType.PROBABILISTIC, Map.of());

            runOnEventloop(() -> registry.register(d1, configFor("d1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.register(d2, configFor("d2", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.register(p1, configFor("p1", AgentType.PROBABILISTIC)));

            List<AgentDescriptor> dets = runOnEventloop(() -> registry.findByType(AgentType.DETERMINISTIC));
            assertThat(dets).hasSize(2);
            assertThat(dets).extracting(AgentDescriptor::getAgentId)
                    .containsExactlyInAnyOrder("d1", "d2");
        }

        @Test void findByCapability() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of(), "fraud-detection");
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of(), "fraud-detection", "scoring");
            StubTypedAgent a3 = new StubTypedAgent("a3", AgentType.REACTIVE, Map.of(), "monitoring");

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC)));
            runOnEventloop(() -> registry.register(a3, configFor("a3", AgentType.REACTIVE)));

            List<AgentDescriptor> fraud = runOnEventloop(() -> registry.findByCapability("fraud-detection"));
            assertThat(fraud).hasSize(2);
        }

        @Test void listAll() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC)));

            List<AgentDescriptor> all = runOnEventloop(registry::listAll);
            assertThat(all).hasSize(2);
        }

        @Test void initializeAgent() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.initialize("a1"));

            assertThat(a1.wasInitialized()).isTrue();
        }

        @Test void healthCheck() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.initialize("a1"));

            HealthStatus status = runOnEventloop(() -> registry.healthCheck("a1"));
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }

        @Test void initializeAll() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC)));
            runOnEventloop(registry::initializeAll);

            assertThat(a1.wasInitialized()).isTrue();
            assertThat(a2.wasInitialized()).isTrue();
        }

        @Test void shutdownAll() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());
            StubTypedAgent a2 = new StubTypedAgent("a2", AgentType.PROBABILISTIC, Map.of());

            runOnEventloop(() -> registry.register(a1, configFor("a1", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.register(a2, configFor("a2", AgentType.PROBABILISTIC)));
            runOnEventloop(registry::initializeAll);
            runOnEventloop(registry::shutdownAll);

            assertThat(a1.wasShutdown()).isTrue();
            assertThat(a2.wasShutdown()).isTrue();
        }

        @Test void hotReload() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent a1 = new StubTypedAgent("a1", AgentType.DETERMINISTIC, Map.of());

            AgentConfig original = configFor("a1", AgentType.DETERMINISTIC);
            AgentConfig updated = AgentConfig.builder()
                    .agentId("a1")
                    .type(AgentType.DETERMINISTIC)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            runOnEventloop(() -> registry.register(a1, original));
            runOnEventloop(() -> registry.initialize("a1"));

            // Hot-reload should call reconfigure
            runOnEventloop(() -> registry.reload("a1", updated));
            // Agent should still be registered
            assertThat(registry.contains("a1")).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentOperatorFactory
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentOperatorFactory")
    class FactoryTests {

        @Test void createTreeFromRegisteredAgent() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent agent = new StubTypedAgent("op-agent", AgentType.DETERMINISTIC,
                    Map.of("result", "processed"));

            runOnEventloop(() -> registry.register(agent, configFor("op-agent", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.initialize("op-agent"));

            AgentOperatorFactory factory = new AgentOperatorFactory(registry);
            AgentOperatorFactory.OperatorTree tree = runOnEventloop(() ->
                    factory.createOperatorTree("op-agent"));

            assertThat(tree).isNotNull();
            assertThat(tree.getOperators()).hasSize(1);
        }

        @Test void executeOperatorTree() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent agent = new StubTypedAgent("exec-agent", AgentType.DETERMINISTIC,
                    Map.of("data", "hello"));

            runOnEventloop(() -> registry.register(agent, configFor("exec-agent", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.initialize("exec-agent"));

            AgentOperatorFactory factory = new AgentOperatorFactory(registry);
            AgentOperatorFactory.OperatorTree tree = runOnEventloop(() ->
                    factory.createOperatorTree("exec-agent"));

            AgentResult<Map<String, Object>> result = runOnEventloop(() ->
                    tree.execute(ctx, Map.of("input", "test")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("data", "hello");
        }

        @Test void directTreeCreation() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent agent = new StubTypedAgent("direct", AgentType.DETERMINISTIC,
                    Map.of("ok", true));

            // Initialize the agent first  
            runOnEventloop(() -> agent.initialize(configFor("direct", AgentType.DETERMINISTIC)));

            AgentOperatorFactory factory = new AgentOperatorFactory(registry);
            AgentOperatorFactory.OperatorTree tree = factory.createOperatorTree(
                    agent, configFor("direct", AgentType.DETERMINISTIC));

            assertThat(tree.getOperators()).hasSize(1);

            var result = runOnEventloop(() -> tree.execute(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test void customMappingPerAgentId() {
            InMemoryAgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            StubTypedAgent agent = new StubTypedAgent("custom", AgentType.DETERMINISTIC,
                    Map.of("raw", true));

            runOnEventloop(() -> registry.register(agent, configFor("custom", AgentType.DETERMINISTIC)));
            runOnEventloop(() -> registry.initialize("custom"));

            AgentOperatorFactory factory = new AgentOperatorFactory(registry);

            // Register custom mapping that adds a post-processor
            factory.registerMapping("custom", (a, config) -> {
                @SuppressWarnings("unchecked")
                TypedAgent<Map<String, Object>, Map<String, Object>> typed =
                        (TypedAgent<Map<String, Object>, Map<String, Object>>) a;
                AgentOperatorFactory.AgentOperator op = AgentOperatorFactory.AgentOperator.builder()
                        .name("custom-op")
                        .agentType(a.descriptor().getType())
                        .agent(typed)
                        .postProcessor(result -> result.toBuilder()
                                .output(new HashMap<>(result.getOutput()) {{
                                    put("enriched", true);
                                }})
                                .build())
                        .build();
                return AgentOperatorFactory.OperatorTree.builder()
                        .name("custom-tree")
                        .operators(List.of(op))
                        .build();
            });

            AgentOperatorFactory.OperatorTree tree = runOnEventloop(() ->
                    factory.createOperatorTree("custom"));

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

            AgentOperatorFactory.AgentOperator op1 = AgentOperatorFactory.AgentOperator.builder()
                    .name("op-1").agentType(AgentType.DETERMINISTIC).agent(a1).build();
            AgentOperatorFactory.AgentOperator op2 = AgentOperatorFactory.AgentOperator.builder()
                    .name("op-2").agentType(AgentType.DETERMINISTIC).agent(a2).build();

            AgentOperatorFactory.OperatorTree tree = AgentOperatorFactory.OperatorTree.builder()
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
