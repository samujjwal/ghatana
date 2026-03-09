package com.ghatana.agent.migration;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.coordination.OrchestrationStrategy;
import com.ghatana.agent.framework.coordination.SequentialOrchestration;
import com.ghatana.agent.framework.coordination.ParallelOrchestration;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.BaseAgent;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.registry.AgentFrameworkRegistry;
import com.ghatana.agent.registry.InMemoryAgentFrameworkRegistry;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for the migration adapters:
 * <ul>
 *   <li>{@link LegacyAgentAdapter}</li>
 *   <li>{@link BaseAgentAdapter}</li>
 *   <li>{@link OrchestrationBridge}</li>
 * </ul>
 *
 * <p>Also verifies that adapted agents can be registered in
 * {@link UnifiedAgentRegistry} end-to-end.
 */
class MigrationAdapterTest {

    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.builder().withCurrentThread().build();
    }

    private void runOnEventloop(Runnable action) {
        eventloop.post(action);
        eventloop.run();
    }

    private AgentContext testContext() {
        return com.ghatana.agent.framework.api.AgentContext.builder()
                .turnId("test-turn-" + UUID.randomUUID())
                .agentId("test-agent")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class))
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LegacyAgentAdapter Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LegacyAgentAdapter")
    class LegacyAgentAdapterTests {

        @Test
        @DisplayName("wraps legacy agent descriptor from capabilities")
        void wrapsDescriptor() {
            Agent legacy = createLegacyAgent("legacy-1", "Classifier", "Classifies inputs");

            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);

            AgentDescriptor desc = adapter.descriptor();
            assertThat(desc.getAgentId()).isEqualTo("legacy-1");
            assertThat(desc.getName()).isEqualTo("Classifier");
            assertThat(desc.getDescription()).isEqualTo("Classifies inputs");
            assertThat(desc.getType()).isEqualTo(AgentType.HYBRID);
            assertThat(desc.getLabels()).containsEntry("adapter", "legacy-agent");
        }

        @Test
        @DisplayName("allows custom descriptor override")
        void customDescriptor() {
            Agent legacy = createLegacyAgent("legacy-2", "Scorer", "Scores risk");
            AgentDescriptor custom = AgentDescriptor.builder()
                    .agentId("legacy-2")
                    .name("Custom Risk Scorer")
                    .type(AgentType.PROBABILISTIC)
                    .capabilities(Set.of("risk-scoring"))
                    .build();

            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy, custom);

            assertThat(adapter.descriptor().getType()).isEqualTo(AgentType.PROBABILISTIC);
            assertThat(adapter.descriptor().getCapabilities()).contains("risk-scoring");
        }

        @Test
        @DisplayName("delegates initialize → Agent.initialize + Agent.start")
        void initializeDelegates() {
            AtomicBoolean initialized = new AtomicBoolean(false);
            AtomicBoolean started = new AtomicBoolean(false);

            Agent legacy = new StubLegacyAgent("init-test", "Test", "desc") {
                @Override
                public @NotNull Promise<Void> initialize(
                        @NotNull AgentContext context) {
                    initialized.set(true);
                    assertThat(context.getTenantId()).isEqualTo("my-tenant");
                    return Promise.complete();
                }

                @Override
                public @NotNull Promise<Void> start() {
                    started.set(true);
                    return Promise.complete();
                }
            };

            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);
            AgentConfig config = AgentConfig.builder()
                    .agentId("init-test")
                    .labels(Map.of("tenantId", "my-tenant"))
                    .build();

            runOnEventloop(() -> {
                adapter.initialize(config).whenResult(v -> {
                    assertThat(initialized.get()).isTrue();
                    assertThat(started.get()).isTrue();
                });
            });
        }

        @Test
        @DisplayName("delegates process and wraps result in AgentResult")
        void processDelegates() {
            Agent legacy = new StubLegacyAgent("proc-test", "Processor", "Processes") {
                @Override
                @SuppressWarnings("unchecked")
                public @NotNull <T, R> Promise<R> process(
                        @NotNull T task,
                        @NotNull AgentContext context) {
                    Map<String, Object> input = (Map<String, Object>) task;
                    Map<String, Object> output = Map.of("result", "processed-" + input.get("data"));
                    return (Promise<R>) Promise.of(output);
                }
            };

            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);
            AgentContext ctx = testContext();

            runOnEventloop(() -> {
                adapter.process(ctx, Map.of("data", "hello"))
                        .whenResult(result -> {
                            assertThat(result.isSuccess()).isTrue();
                            assertThat(result.getOutput()).isInstanceOf(Map.class);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> output = (Map<String, Object>) result.getOutput();
                            assertThat(output).containsEntry("result", "processed-hello");
                            assertThat(result.getAgentId()).isEqualTo("proc-test");
                            assertThat(result.getProcessingTime()).isNotNull();
                        });
            });
        }

        @Test
        @DisplayName("propagates exceptions from legacy agent process")
        void processException() {
            Agent legacy = new StubLegacyAgent("fail-test", "Failer", "Fails") {
                @Override
                public @NotNull <T, R> Promise<R> process(
                        @NotNull T task,
                        @NotNull AgentContext context) {
                    return Promise.ofException(new RuntimeException("legacy error"));
                }
            };

            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);
            AgentContext ctx = testContext();

            runOnEventloop(() -> {
                adapter.process(ctx, "input")
                        .whenException(ex -> {
                            assertThat(ex).isInstanceOf(RuntimeException.class)
                                    .hasMessage("legacy error");
                        });
            });
        }

        @Test
        @DisplayName("delegates shutdown")
        void shutdownDelegates() {
            AtomicBoolean shutdown = new AtomicBoolean(false);
            Agent legacy = new StubLegacyAgent("shutdown-test", "Agent", "") {
                @Override
                public @NotNull Promise<Void> shutdown() {
                    shutdown.set(true);
                    return Promise.complete();
                }
            };

            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);
            runOnEventloop(() -> {
                adapter.shutdown().whenResult(v -> assertThat(shutdown.get()).isTrue());
            });
        }

        @Test
        @DisplayName("healthCheck returns HEALTHY by default")
        void healthCheck() {
            Agent legacy = createLegacyAgent("health-test", "HC", "");
            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);

            runOnEventloop(() -> {
                adapter.healthCheck()
                        .whenResult(status -> assertThat(status).isEqualTo(HealthStatus.HEALTHY));
            });
        }

        @Test
        @DisplayName("getDelegate returns wrapped agent")
        void getDelegate() {
            Agent legacy = createLegacyAgent("delegate-test", "Del", "");
            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);
            assertThat(adapter.getDelegate()).isSameAs(legacy);
        }

        @Test
        @DisplayName("rejects null delegate")
        void rejectsNullDelegate() {
            assertThatThrownBy(() -> new LegacyAgentAdapter(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BaseAgentAdapter Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BaseAgentAdapter")
    class BaseAgentAdapterTests {

        @Test
        @DisplayName("wraps BaseAgent descriptor from agentId")
        void wrapsDescriptor() {
            BaseAgent<String, String> baseAgent = createBaseAgent("base-1");

            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent);

            assertThat(adapter.descriptor().getAgentId()).isEqualTo("base-1");
            assertThat(adapter.descriptor().getType()).isEqualTo(AgentType.HYBRID);
            assertThat(adapter.descriptor().getLabels())
                    .containsEntry("adapter", "base-agent-gaa");
        }

        @Test
        @DisplayName("allows custom descriptor override")
        void customDescriptor() {
            BaseAgent<String, String> baseAgent = createBaseAgent("base-2");
            AgentDescriptor custom = AgentDescriptor.builder()
                    .agentId("base-2")
                    .name("Custom Base Agent")
                    .type(AgentType.ADAPTIVE)
                    .build();

            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent, custom);

            assertThat(adapter.descriptor().getType()).isEqualTo(AgentType.ADAPTIVE);
            assertThat(adapter.descriptor().getName()).isEqualTo("Custom Base Agent");
        }

        @Test
        @DisplayName("process delegates to executeTurn and wraps in AgentResult")
        void processDelegatesToExecuteTurn() {
            BaseAgent<String, String> baseAgent = createBaseAgent("proc-base");

            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent);
            AgentContext ctx = testContext();

            runOnEventloop(() -> {
                adapter.process(ctx, "hello")
                        .whenResult(result -> {
                            assertThat(result.isSuccess()).isTrue();
                            assertThat(result.getOutput()).isEqualTo("OUTPUT:hello");
                            assertThat(result.getAgentId()).isEqualTo("proc-base");
                        });
            });
        }

        @Test
        @DisplayName("initialize is no-op (returns complete)")
        void initializeNoOp() {
            BaseAgent<String, String> baseAgent = createBaseAgent("init-base");
            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent);

            runOnEventloop(() -> {
                adapter.initialize(AgentConfig.builder().agentId("init-base").build())
                        .whenResult(v -> {
                            // Should complete without error
                        });
            });
        }

        @Test
        @DisplayName("shutdown is no-op (returns complete)")
        void shutdownNoOp() {
            BaseAgent<String, String> baseAgent = createBaseAgent("shutdown-base");
            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent);

            runOnEventloop(() -> {
                adapter.shutdown().whenResult(v -> {
                    // Should complete without error
                });
            });
        }

        @Test
        @DisplayName("healthCheck returns HEALTHY")
        void healthCheck() {
            BaseAgent<String, String> baseAgent = createBaseAgent("health-base");
            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent);

            runOnEventloop(() -> {
                adapter.healthCheck()
                        .whenResult(status -> assertThat(status).isEqualTo(HealthStatus.HEALTHY));
            });
        }

        @Test
        @DisplayName("getDelegate returns wrapped BaseAgent")
        void getDelegate() {
            BaseAgent<String, String> baseAgent = createBaseAgent("delegate-base");
            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent);
            assertThat(adapter.getDelegate()).isSameAs(baseAgent);
        }

        @Test
        @DisplayName("rejects null delegate")
        void rejectsNullDelegate() {
            assertThatThrownBy(() -> new BaseAgentAdapter<>(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OrchestrationBridge Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OrchestrationBridge")
    class OrchestrationBridgeTests {

        @Test
        @DisplayName("TypedAgent → OrchestrationAgent preserves agentId")
        void toOrchestrationAgent_agentId() {
            TypedAgent<String, String> typed = createStubTypedAgent("bridge-1", "echo");

            OrchestrationStrategy.Agent<String, String> orch =
                    OrchestrationBridge.toOrchestrationAgent(typed);

            assertThat(orch.getAgentId()).isEqualTo("bridge-1");
        }

        @Test
        @DisplayName("TypedAgent → OrchestrationAgent delegates execute → process")
        void toOrchestrationAgent_execute() {
            TypedAgent<String, String> typed = createStubTypedAgent("bridge-2", "echo");

            OrchestrationStrategy.Agent<String, String> orch =
                    OrchestrationBridge.toOrchestrationAgent(typed);

            AgentContext ctx = testContext();
            runOnEventloop(() -> {
                orch.execute("hello", ctx)
                        .whenResult(output -> assertThat(output).isEqualTo("echo:hello"));
            });
        }

        @Test
        @DisplayName("toOrchestrationAgents wraps a list")
        void toOrchestrationAgents_list() {
            List<TypedAgent<String, String>> agents = List.of(
                    createStubTypedAgent("a1", "first"),
                    createStubTypedAgent("a2", "second"),
                    createStubTypedAgent("a3", "third")
            );

            List<OrchestrationStrategy.Agent<String, String>> orchAgents =
                    OrchestrationBridge.toOrchestrationAgents(agents);

            assertThat(orchAgents).hasSize(3);
            assertThat(orchAgents.get(0).getAgentId()).isEqualTo("a1");
            assertThat(orchAgents.get(2).getAgentId()).isEqualTo("a3");
        }

        @Test
        @DisplayName("OrchestrationAgent → TypedAgent preserves agentId")
        void toTypedAgent_agentId() {
            OrchestrationStrategy.Agent<String, String> orch =
                    createOrchestrationAgent("orch-1");

            TypedAgent<String, String> typed = OrchestrationBridge.toTypedAgent(orch);

            assertThat(typed.descriptor().getAgentId()).isEqualTo("orch-1");
            assertThat(typed.descriptor().getLabels())
                    .containsEntry("adapter", "orchestration-bridge");
        }

        @Test
        @DisplayName("OrchestrationAgent → TypedAgent delegates process → execute")
        void toTypedAgent_process() {
            OrchestrationStrategy.Agent<String, String> orch =
                    createOrchestrationAgent("orch-2");

            TypedAgent<String, String> typed = OrchestrationBridge.toTypedAgent(orch);
            AgentContext ctx = testContext();

            runOnEventloop(() -> {
                typed.process(ctx, "world")
                        .whenResult(result -> {
                            assertThat(result.isSuccess()).isTrue();
                            assertThat(result.getOutput()).isEqualTo("orch:world");
                            assertThat(result.getAgentId()).isEqualTo("orch-2");
                        });
            });
        }

        @Test
        @DisplayName("OrchestrationAgent → TypedAgent with custom descriptor")
        void toTypedAgent_customDescriptor() {
            OrchestrationStrategy.Agent<String, String> orch =
                    createOrchestrationAgent("orch-3");
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("orch-3")
                    .name("Custom Orch Agent")
                    .type(AgentType.DETERMINISTIC)
                    .capabilities(Set.of("processing"))
                    .build();

            TypedAgent<String, String> typed = OrchestrationBridge.toTypedAgent(orch, desc);

            assertThat(typed.descriptor().getType()).isEqualTo(AgentType.DETERMINISTIC);
            assertThat(typed.descriptor().getCapabilities()).contains("processing");
        }

        @Test
        @DisplayName("roundtrip: TypedAgent → Orchestration → TypedAgent preserves behavior")
        void roundtrip() {
            TypedAgent<String, String> original = createStubTypedAgent("rt-1", "prefix");

            // TypedAgent → OrchestrationAgent → TypedAgent
            OrchestrationStrategy.Agent<String, String> orchAgent =
                    OrchestrationBridge.toOrchestrationAgent(original);
            TypedAgent<String, String> roundTripped =
                    OrchestrationBridge.toTypedAgent(orchAgent);

            AgentContext ctx = testContext();
            runOnEventloop(() -> {
                roundTripped.process(ctx, "data")
                        .whenResult(result -> {
                            assertThat(result.isSuccess()).isTrue();
                            assertThat(result.getOutput()).isEqualTo("prefix:data");
                        });
            });
        }

        @Test
        @DisplayName("rejects null TypedAgent")
        void rejectsNullTypedAgent() {
            assertThatThrownBy(() -> OrchestrationBridge.toOrchestrationAgent(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null OrchestrationAgent")
        void rejectsNullOrchestrationAgent() {
            assertThatThrownBy(() -> OrchestrationBridge.toTypedAgent(
                    (OrchestrationStrategy.Agent<Object, Object>) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Integration: Adapted agents in UnifiedAgentRegistry
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Registry Integration")
    class RegistryIntegrationTests {

        @Test
        @DisplayName("LegacyAgentAdapter registers and resolves in UnifiedAgentRegistry")
        void legacyInRegistry() {
            Agent legacy = createLegacyAgent("reg-legacy", "Registry Test", "desc");
            LegacyAgentAdapter adapter = new LegacyAgentAdapter(legacy);

            AgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            AgentConfig config = AgentConfig.builder().agentId("reg-legacy").build();

            registry.register(adapter, config);

            assertThat(registry.contains("reg-legacy")).isTrue();

            runOnEventloop(() -> {
                registry.resolve("reg-legacy")
                        .whenResult(resolved -> {
                            assertThat(resolved).isSameAs(adapter);
                            assertThat(resolved.descriptor().getAgentId()).isEqualTo("reg-legacy");
                        });
            });
        }

        @Test
        @DisplayName("BaseAgentAdapter registers, initializes, and processes via registry")
        void baseAgentInRegistry() {
            BaseAgent<String, String> baseAgent = createBaseAgent("reg-base");
            BaseAgentAdapter<String, String> adapter = new BaseAgentAdapter<>(baseAgent);

            AgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            AgentConfig config = AgentConfig.builder().agentId("reg-base").build();
            registry.register(adapter, config);

            assertThat(registry.size()).isEqualTo(1);

            runOnEventloop(() -> {
                registry.initialize("reg-base")
                        .then(() -> registry.resolve("reg-base"))
                        .then(agent -> {
                            @SuppressWarnings("unchecked")
                            TypedAgent<String, String> typed = (TypedAgent<String, String>) (TypedAgent<?, ?>) agent;
                            return typed.process(testContext(), "test-input");
                        })
                        .whenResult(result -> {
                            assertThat(result.isSuccess()).isTrue();
                            assertThat(result.getOutput()).isEqualTo("OUTPUT:test-input");
                        });
            });
        }

        @Test
        @DisplayName("OrchestrationBridge agent registers in UnifiedAgentRegistry")
        void orchestrationAgentInRegistry() {
            OrchestrationStrategy.Agent<String, String> orch =
                    createOrchestrationAgent("reg-orch");
            TypedAgent<String, String> adapted = OrchestrationBridge.toTypedAgent(orch);

            AgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();
            AgentConfig config = AgentConfig.builder().agentId("reg-orch").build();
            registry.register(adapted, config);

            assertThat(registry.contains("reg-orch")).isTrue();

            runOnEventloop(() -> {
                registry.resolve("reg-orch")
                        .then(agent -> {
                            @SuppressWarnings("unchecked")
                            TypedAgent<String, String> typed = (TypedAgent<String, String>) (TypedAgent<?, ?>) agent;
                            return typed.process(testContext(), "registry-input");
                        })
                        .whenResult(result -> {
                            assertThat(result.isSuccess()).isTrue();
                            assertThat(result.getOutput()).isEqualTo("orch:registry-input");
                        });
            });
        }

        @Test
        @DisplayName("mixed agent types coexist in registry with findByType")
        void mixedTypesInRegistry() {
            AgentFrameworkRegistry registry = new InMemoryAgentFrameworkRegistry();

            // Legacy agent
            Agent legacy = createLegacyAgent("mixed-legacy", "Legacy", "");
            LegacyAgentAdapter legacyAdapter = new LegacyAgentAdapter(legacy);
            registry.register(legacyAdapter,
                    AgentConfig.builder().agentId("mixed-legacy").build());

            // BaseAgent
            BaseAgentAdapter<String, String> baseAdapter =
                    new BaseAgentAdapter<>(createBaseAgent("mixed-base"));
            registry.register(baseAdapter,
                    AgentConfig.builder().agentId("mixed-base").build());

            // Orchestration bridge with DETERMINISTIC type
            OrchestrationStrategy.Agent<String, String> orch =
                    createOrchestrationAgent("mixed-orch");
            AgentDescriptor detDesc = AgentDescriptor.builder()
                    .agentId("mixed-orch")
                    .name("DET Orch")
                    .type(AgentType.DETERMINISTIC)
                    .build();
            TypedAgent<String, String> orchAdapted =
                    OrchestrationBridge.toTypedAgent(orch, detDesc);
            registry.register(orchAdapted,
                    AgentConfig.builder().agentId("mixed-orch").build());

            assertThat(registry.size()).isEqualTo(3);

            runOnEventloop(() -> {
                // Find by HYBRID type — should find legacy + base adapters
                registry.findByType(AgentType.HYBRID)
                        .whenResult(hybridDescs -> assertThat(hybridDescs).hasSize(2));

                // Find by DETERMINISTIC — should find orchestration bridge
                registry.findByType(AgentType.DETERMINISTIC)
                        .whenResult(detDescs -> {
                            assertThat(detDescs).hasSize(1);
                            assertThat(detDescs.get(0).getAgentId()).isEqualTo("mixed-orch");
                        });
            });
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Integration: Orchestration with adapted TypedAgents
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Orchestration Integration")
    class OrchestrationIntegrationTests {

        @Test
        @DisplayName("TypedAgents run in SequentialOrchestration via bridge")
        void sequentialOrchestration() {
            TypedAgent<String, String> agent1 = createStubTypedAgent("seq-1", "first");
            TypedAgent<String, String> agent2 = createStubTypedAgent("seq-2", "second");

            List<OrchestrationStrategy.Agent<String, String>> orchAgents =
                    OrchestrationBridge.toOrchestrationAgents(List.of(agent1, agent2));

            SequentialOrchestration orchestration = new SequentialOrchestration();
            AgentContext ctx = testContext();

            runOnEventloop(() -> {
                orchestration.orchestrate(orchAgents, "data", ctx)
                        .whenResult(results -> {
                            assertThat(results).hasSize(2);
                            assertThat(results.get(0)).isEqualTo("first:data");
                            assertThat(results.get(1)).isEqualTo("second:data");
                        });
            });
        }

        @Test
        @DisplayName("TypedAgents run in ParallelOrchestration via bridge")
        void parallelOrchestration() {
            TypedAgent<String, String> agent1 = createStubTypedAgent("par-1", "alpha");
            TypedAgent<String, String> agent2 = createStubTypedAgent("par-2", "beta");
            TypedAgent<String, String> agent3 = createStubTypedAgent("par-3", "gamma");

            List<OrchestrationStrategy.Agent<String, String>> orchAgents =
                    OrchestrationBridge.toOrchestrationAgents(List.of(agent1, agent2, agent3));

            ParallelOrchestration orchestration = new ParallelOrchestration();
            AgentContext ctx = testContext();

            runOnEventloop(() -> {
                orchestration.orchestrate(orchAgents, "input", ctx)
                        .whenResult(results -> {
                            assertThat(results).hasSize(3);
                            assertThat(results).containsExactly(
                                    "alpha:input", "beta:input", "gamma:input");
                        });
            });
        }
    }

    // SimpleAgentContextBridge was removed — tests deleted (the bridge is no longer needed
    // since Agent.initialize/process now accept the canonical AgentContext directly).

    // ═════════════════════════════════════════════════════════════════════════
    // Deprecated Planner Verification
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legacy Planner")
    class LegacyPlannerTests {

        @Test
        @DisplayName("AgentRegistry is no longer annotated @Deprecated")
        void agentRegistryNotDeprecated() {
            assertThat(com.ghatana.agent.framework.planner.AgentRegistry.class
                    .isAnnotationPresent(Deprecated.class)).isFalse();
        }

        @Test
        @DisplayName("PlannerAgentFactory is no longer annotated @Deprecated")
        void plannerAgentFactoryNotDeprecated() {
            assertThat(com.ghatana.agent.framework.planner.PlannerAgentFactory.class
                    .isAnnotationPresent(Deprecated.class)).isFalse();
        }

        @Test
        @DisplayName("PlannerAgentFactory.createAgent still returns null (stub)")
        void factoryStillReturnsNull() {
            var factory = new com.ghatana.agent.framework.planner.PlannerAgentFactory();
            assertThat(factory.createAgent("nonexistent.yaml")).isNull();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Stub / helper factories
    // ═════════════════════════════════════════════════════════════════════════

    private Agent createLegacyAgent(String id, String name, String description) {
        return new StubLegacyAgent(id, name, description);
    }

    /**
     * Minimal stub implementing the legacy {@link Agent} interface.
     */
    static class StubLegacyAgent implements Agent {
        private final String id;
        private final AgentCapabilities capabilities;

        StubLegacyAgent(String id, String name, String description) {
            this.id = id;
            this.capabilities = new AgentCapabilities(
                    name, "test", description, Set.of("test-task"), Set.of());
        }

        @Override
        public @NotNull String getId() { return id; }

        @Override
        public @NotNull AgentCapabilities getCapabilities() { return capabilities; }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull AgentContext context) {
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> start() { return Promise.complete(); }

        @Override
        @SuppressWarnings("unchecked")
        public @NotNull <T, R> Promise<R> process(
                @NotNull T task, @NotNull AgentContext context) {
            return (Promise<R>) Promise.of(Map.of("echo", task.toString()));
        }

        @Override
        public @NotNull Promise<Void> shutdown() { return Promise.complete(); }
    }

    /**
     * Creates a BaseAgent stub that transforms input to "OUTPUT:{input}".
     */
    private BaseAgent<String, String> createBaseAgent(String agentId) {
        OutputGenerator<String, String> generator = new OutputGenerator<>() {
            @Override
            public @NotNull GeneratorMetadata getMetadata() {
                return GeneratorMetadata.builder()
                        .name("stub-generator")
                        .type("stub")
                        .version("1.0")
                        .build();
            }

            @Override
            public @NotNull Promise<String> generate(
                    @NotNull String input, @NotNull AgentContext context) {
                return Promise.of("OUTPUT:" + input);
            }
        };

        return new BaseAgent<>(agentId, generator) {};
    }

    /**
     * Creates a stub TypedAgent that returns "{prefix}:{input}".
     */
    private TypedAgent<String, String> createStubTypedAgent(String id, String prefix) {
        return new TypedAgent<>() {
            private final AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId(id).name(id).type(AgentType.DETERMINISTIC).build();

            @Override
            public @NotNull AgentDescriptor descriptor() { return desc; }

            @Override
            public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) {
                return Promise.complete();
            }

            @Override
            public @NotNull Promise<Void> shutdown() { return Promise.complete(); }

            @Override
            public @NotNull Promise<HealthStatus> healthCheck() {
                return Promise.of(HealthStatus.HEALTHY);
            }

            @Override
            public @NotNull Promise<AgentResult<String>> process(
                    @NotNull AgentContext ctx, @NotNull String input) {
                return Promise.of(AgentResult.success(
                        prefix + ":" + input, id, Duration.ofMillis(1)));
            }
        };
    }

    /**
     * Creates a stub OrchestrationStrategy.Agent that returns "orch:{input}".
     */
    private OrchestrationStrategy.Agent<String, String> createOrchestrationAgent(String id) {
        return new OrchestrationStrategy.Agent<>() {
            @Override
            public @NotNull String getAgentId() { return id; }

            @Override
            public @NotNull Promise<String> execute(
                    @NotNull String input, @NotNull AgentContext context) {
                return Promise.of("orch:" + input);
            }
        };
    }
}
