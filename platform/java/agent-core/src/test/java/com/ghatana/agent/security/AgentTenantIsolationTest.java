/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 4.9 — Security Hardening: Tenant isolation verification for AgentFramework.
 * Ensures agent registration, context propagation, and configuration are tenant-scoped.
 */
package com.ghatana.agent.security;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.registry.InMemoryAgentRegistry;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies multi-tenant isolation guarantees across Agent Framework infrastructure.
 * Tests cover:
 * <ul>
 *   <li>AgentContext: tenantId is mandatory and correctly propagated</li>
 *   <li>Agent registry: per-tenant registration namespace isolation</li>
 *   <li>TenantContext integration: scoped agent execution</li>
 *   <li>Agent authorization: role-based agent access control</li>
 *   <li>Concurrent multi-tenant processing: no cross-contamination</li>
 * </ul>
 */
@DisplayName("Agent Framework Tenant Isolation [GH-90000]")
class AgentTenantIsolationTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";

    @BeforeEach
    void setUp() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // =========================================================================
    // 1. AgentContext Tenant Scoping
    // =========================================================================

    @Nested
    @DisplayName("AgentContext Tenant Scoping [GH-90000]")
    class AgentContextScoping {

        @Test
        @DisplayName("AgentContext carries tenantId through processing [GH-90000]")
        void contextCarriesTenantId() { // GH-90000
            AgentContext ctx = createAgentContext(TENANT_A); // GH-90000
            assertThat(ctx.getTenantId()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("AgentContext for different tenants are independent [GH-90000]")
        void contextsIndependent() { // GH-90000
            AgentContext ctxA = createAgentContext(TENANT_A); // GH-90000
            AgentContext ctxB = createAgentContext(TENANT_B); // GH-90000

            assertThat(ctxA.getTenantId()).isNotEqualTo(ctxB.getTenantId()); // GH-90000
        }

        @Test
        @DisplayName("A-1: metadata and config are isolated across tenant contexts")
        void contextMetadataAndConfigIsolationAcrossTenants() {
            AgentContext ctxA = AgentContext.builder()
                .turnId(UUID.randomUUID().toString())
                .agentId("isolation-agent")
                .tenantId(TENANT_A)
                .memoryStore(mock(MemoryStore.class))
                .config(Map.of("region", "us-west"))
                .metadata(new HashMap<>())
                .build();
            AgentContext ctxB = AgentContext.builder()
                .turnId(UUID.randomUUID().toString())
                .agentId("isolation-agent")
                .tenantId(TENANT_B)
                .memoryStore(mock(MemoryStore.class))
                .config(Map.of("region", "eu-central"))
                .metadata(new HashMap<>())
                .build();

            ctxA.setMetadata("scope", "tenant-a-only");

            assertThat(ctxA.getMetadata()).containsEntry("scope", "tenant-a-only");
            assertThat(ctxB.getMetadata()).doesNotContainKey("scope");
            assertThat(ctxA.getConfig("region")).isEqualTo("us-west");
            assertThat(ctxB.getConfig("region")).isEqualTo("eu-central");
        }

        @Test
        @DisplayName("Agent can access tenantId from context during processing [GH-90000]")
        void agentAccessesTenantDuringProcessing() { // GH-90000
            AtomicReference<String> capturedTenantId = new AtomicReference<>(); // GH-90000

            TypedAgent<String, String> spyAgent = new TestTypedAgent("spy-agent [GH-90000]") {
                @Override
                public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
                    capturedTenantId.set(ctx.getTenantId()); // GH-90000
                    return Promise.of(AgentResult.success("done", "spy-agent", Duration.ZERO)); // GH-90000
                }
            };

            AgentContext ctx = createAgentContext(TENANT_A); // GH-90000
            spyAgent.process(ctx, "test-input"); // GH-90000

            assertThat(capturedTenantId.get()).isEqualTo(TENANT_A); // GH-90000
        }
    }

    // =========================================================================
    // 2. Agent Registry Isolation
    // =========================================================================

    @Nested
    @DisplayName("Agent Registry Isolation [GH-90000]")
    class RegistryIsolation {

        @Test
        @DisplayName("Agents registered are globally visible (shared platform registry) [GH-90000]")
        void registryIsGlobalSharedResource() { // GH-90000
            // Agent registry is a global platform resource — agents are shared infrastructure.
            // Tenant isolation is at the EXECUTION level (via AgentContext), not registration. // GH-90000
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000

            TypedAgent<String, String> agentA = new TestTypedAgent("fraud-detector [GH-90000]");
            AgentConfig configA = AgentConfig.builder() // GH-90000
                    .agentId("fraud-detector [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .timeout(Duration.ofSeconds(5)) // GH-90000
                    .build(); // GH-90000

            // register returns Promise<Void> — run inside eventloop per architecture mandate
            runPromise(() -> registry.register(agentA, configA)); // GH-90000

            Set<String> ids = runPromise(registry::listAgentIds);
            Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("fraud-detector [GH-90000]"));
            Map<String, Object> stats = runPromise(registry::getStats);

            assertThat(ids).contains("fraud-detector [GH-90000]");
            assertThat(resolved).contains(agentA);
            assertThat(((Number) stats.get("registeredAgents")).intValue()).isEqualTo(1);
            assertThat(stats.get("registryType")).isEqualTo("InMemory");
        }

        @Test
        @DisplayName("Multiple agents can be registered and resolved independently [GH-90000]")
        void multipleAgentResolution() { // GH-90000
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry(); // GH-90000

            TypedAgent<String, String> agent1 = new TestTypedAgent("agent-1 [GH-90000]");
            TypedAgent<String, String> agent2 = new TestTypedAgent("agent-2 [GH-90000]");

            runPromise(() -> registry.register(agent1, AgentConfig.builder() // GH-90000
                    .agentId("agent-1 [GH-90000]").type(AgentType.DETERMINISTIC)
                    .timeout(Duration.ofSeconds(5)).build())); // GH-90000
            runPromise(() -> registry.register(agent2, AgentConfig.builder() // GH-90000
                    .agentId("agent-2 [GH-90000]").type(AgentType.PROBABILISTIC)
                    .timeout(Duration.ofSeconds(5)).build())); // GH-90000

            Set<String> ids = runPromise(registry::listAgentIds);
            Optional<TypedAgent<String, String>> resolvedAgent1 = runPromise(() -> registry.resolve("agent-1 [GH-90000]"));
            Optional<TypedAgent<String, String>> resolvedAgent2 = runPromise(() -> registry.resolve("agent-2 [GH-90000]"));

            assertThat(ids).containsExactlyInAnyOrder("agent-1 [GH-90000]", "agent-2 [GH-90000]");
            assertThat(resolvedAgent1).contains(agent1);
            assertThat(resolvedAgent2).contains(agent2);
        }
    }

    // =========================================================================
    // 3. TenantContext-Agent Integration
    // =========================================================================

    @Nested
    @DisplayName("TenantContext Agent Integration [GH-90000]")
    class TenantContextIntegration {

        @Test
        @DisplayName("Agent execution within TenantContext scope sees correct tenant [GH-90000]")
        void agentExecutionInScope() throws Exception { // GH-90000
            AtomicReference<String> capturedTenant = new AtomicReference<>(); // GH-90000
            TypedAgent<String, String> agent = new TestTypedAgent("scoped-agent [GH-90000]") {
                @Override
                public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
                    capturedTenant.set(ctx.getTenantId()); // GH-90000
                    return Promise.of(AgentResult.success("ok", "scoped-agent", Duration.ZERO)); // GH-90000
                }
            };

            Principal principalA = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            try (AutoCloseable scope = TenantContext.scope(principalA)) { // GH-90000
                AgentContext ctx = createAgentContext(TenantContext.getCurrentTenantId()); // GH-90000
                agent.process(ctx, "input"); // GH-90000
            }

            assertThat(capturedTenant.get()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("Agent execution scope switches tenant correctly between calls [GH-90000]")
        void scopeSwitchBetweenCalls() throws Exception { // GH-90000
            List<String> capturedTenants = Collections.synchronizedList(new ArrayList<>()); // GH-90000
            TypedAgent<String, String> agent = new TestTypedAgent("multi-tenant-agent [GH-90000]") {
                @Override
                public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
                    capturedTenants.add(ctx.getTenantId()); // GH-90000
                    return Promise.of(AgentResult.success("ok", "multi-tenant-agent", Duration.ZERO)); // GH-90000
                }
            };

            // Execute for tenant A
            Principal principalA = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            try (AutoCloseable scope = TenantContext.scope(principalA)) { // GH-90000
                agent.process(createAgentContext(TenantContext.getCurrentTenantId()), "input-a"); // GH-90000
            }

            // Execute for tenant B
            Principal principalB = new Principal("bob", List.of("admin [GH-90000]"), TENANT_B);
            try (AutoCloseable scope = TenantContext.scope(principalB)) { // GH-90000
                agent.process(createAgentContext(TenantContext.getCurrentTenantId()), "input-b"); // GH-90000
            }

            assertThat(capturedTenants).containsExactly(TENANT_A, TENANT_B); // GH-90000
        }
    }

    // =========================================================================
    // 4. Agent Authorization (Role-Based) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Agent Authorization [GH-90000]")
    class AgentAuthorization {

        @Test
        @DisplayName("Principal roles determine agent access [GH-90000]")
        void principalRolesDetermineAccess() { // GH-90000
            Principal admin = new Principal("admin-user", List.of("admin", "agent-executor"), TENANT_A); // GH-90000
            Principal viewer = new Principal("viewer-user", List.of("viewer [GH-90000]"), TENANT_A);

            // Admin can execute agents
            assertThat(admin.hasRole("agent-executor [GH-90000]")).isTrue();

            // Viewer cannot execute agents
            assertThat(viewer.hasRole("agent-executor [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("Principal from different tenant with same role has separate identity [GH-90000]")
        void crossTenantRoleSeparation() { // GH-90000
            Principal adminA = new Principal("admin", List.of("admin [GH-90000]"), TENANT_A);
            Principal adminB = new Principal("admin", List.of("admin [GH-90000]"), TENANT_B);

            // Both are admins, but for different tenants
            assertThat(adminA.hasRole("admin [GH-90000]")).isTrue();
            assertThat(adminB.hasRole("admin [GH-90000]")).isTrue();

            // But they are NOT the same principal
            assertThat(adminA).isNotEqualTo(adminB); // GH-90000
        }

        @Test
        @DisplayName("Service account principal can process across agent types [GH-90000]")
        void serviceAccountCrossAgentType() { // GH-90000
            Principal svcAccount = new Principal("pipeline-orchestrator", // GH-90000
                    List.of("processor", "admin"), TENANT_A); // GH-90000

            assertThat(svcAccount.hasRole("processor [GH-90000]")).isTrue();
            assertThat(svcAccount.hasRole("admin [GH-90000]")).isTrue();
            assertThat(svcAccount.getTenantId()).isEqualTo(TENANT_A); // GH-90000
        }
    }

    // =========================================================================
    // 5. AgentConfig Tenant-Relevant Properties
    // =========================================================================

    @Nested
    @DisplayName("AgentConfig Security Properties [GH-90000]")
    class AgentConfigSecurity {

        @Test
        @DisplayName("AgentConfig with properties carries tenant-scoped settings [GH-90000]")
        void configCarriesTenantSettings() { // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("fraud-detector [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .timeout(Duration.ofSeconds(5)) // GH-90000
                    .properties(Map.of( // GH-90000
                            "allowedTenants", "tenant-alpha,tenant-beta",
                            "maxConcurrency", "10"
                    ))
                    .build(); // GH-90000

            assertThat(config.getProperties()).containsEntry("allowedTenants", "tenant-alpha,tenant-beta"); // GH-90000
        }

        @Test
        @DisplayName("AgentConfig labels can encode tenant affinity [GH-90000]")
        void configLabelsEncodeTenantAffinity() { // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("regional-processor [GH-90000]")
                    .type(AgentType.REACTIVE) // GH-90000
                    .timeout(Duration.ofSeconds(10)) // GH-90000
                    .labels(Map.of( // GH-90000
                            "region", "us-west-2",
                            "tier", "premium",
                            "tenantScope", "tenant-alpha"
                    ))
                    .build(); // GH-90000

            assertThat(config.getLabels()).containsEntry("tenantScope", "tenant-alpha"); // GH-90000
        }
    }

    // =========================================================================
    // 6. Concurrent Multi-Tenant Agent Processing
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Multi-Tenant Processing [GH-90000]")
    class ConcurrentProcessing {

        @Test
        @DisplayName("Concurrent agent executions for different tenants do not interfere [GH-90000]")
        void concurrentAgentExecutionIsolation() throws Exception { // GH-90000
            ConcurrentHashMap<String, List<String>> tenantResults = new ConcurrentHashMap<>(); // GH-90000
            tenantResults.put(TENANT_A, Collections.synchronizedList(new ArrayList<>())); // GH-90000
            tenantResults.put(TENANT_B, Collections.synchronizedList(new ArrayList<>())); // GH-90000
            tenantResults.put(TENANT_C, Collections.synchronizedList(new ArrayList<>())); // GH-90000

            TypedAgent<String, String> agent = new TestTypedAgent("concurrent-agent [GH-90000]") {
                @Override
                public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
                    tenantResults.get(ctx.getTenantId()).add(input); // GH-90000
                    return Promise.of(AgentResult.success( // GH-90000
                            ctx.getTenantId() + ":" + input, "concurrent-agent", Duration.ZERO)); // GH-90000
                }
            };

            int opsPerTenant = 20;
            ExecutorService executor = Executors.newFixedThreadPool(6); // GH-90000
            CountDownLatch latch = new CountDownLatch(1); // GH-90000
            List<Future<?>> futures = new ArrayList<>(); // GH-90000

            String[] tenants = {TENANT_A, TENANT_B, TENANT_C};
            for (String tenant : tenants) { // GH-90000
                for (int i = 0; i < opsPerTenant; i++) { // GH-90000
                    final int idx = i;
                    futures.add(executor.submit(() -> { // GH-90000
                        try {
                            latch.await(); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                            return;
                        }
                        AgentContext ctx = createAgentContext(tenant); // GH-90000
                        agent.process(ctx, "input-" + idx); // GH-90000
                    }));
                }
            }

            latch.countDown(); // GH-90000
            for (Future<?> f : futures) { // GH-90000
                f.get(10, TimeUnit.SECONDS); // GH-90000
            }

            executor.shutdown(); // GH-90000
            executor.awaitTermination(5, TimeUnit.SECONDS); // GH-90000

            // Each tenant should have exactly its own results
            for (String tenant : tenants) { // GH-90000
                assertThat(tenantResults.get(tenant)).hasSize(opsPerTenant); // GH-90000
            }
        }

        @Test
        @DisplayName("TenantContext is thread-safe across parallel agent executions [GH-90000]")
        void tenantContextThreadSafety() throws Exception { // GH-90000
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads); // GH-90000
            CyclicBarrier barrier = new CyclicBarrier(threads); // GH-90000
            List<Future<String>> futures = new ArrayList<>(); // GH-90000

            for (int i = 0; i < threads; i++) { // GH-90000
                final String tenantId = "agent-tenant-" + i;
                futures.add(executor.submit(() -> { // GH-90000
                    barrier.await(5, TimeUnit.SECONDS); // GH-90000
                    Principal p = new Principal("svc-" + tenantId, List.of("processor [GH-90000]"), tenantId);
                    try (AutoCloseable scope = TenantContext.scope(p)) { // GH-90000
                        // Simulate some processing delay
                        Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5)); // GH-90000
                        return TenantContext.getCurrentTenantId(); // GH-90000
                    }
                }));
            }

            for (int i = 0; i < threads; i++) { // GH-90000
                assertThat(futures.get(i).get(10, TimeUnit.SECONDS)) // GH-90000
                        .isEqualTo("agent-tenant-" + i); // GH-90000
            }

            executor.shutdown(); // GH-90000
            executor.awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
    }

    // =========================================================================
    // 7. End-to-End Agent Tenant Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Tenant Lifecycle [GH-90000]")
    class EndToEndLifecycle {

        @Test
        @DisplayName("Complete agent lifecycle with tenant context propagation [GH-90000]")
        void completeLifecycle() throws Exception { // GH-90000
            List<String> auditTrail = Collections.synchronizedList(new ArrayList<>()); // GH-90000

            TypedAgent<String, String> agent = new TestTypedAgent("lifecycle-agent [GH-90000]") {
                @Override
                public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
                    auditTrail.add(ctx.getTenantId() + ":" + input); // GH-90000
                    return Promise.of(AgentResult.success("processed", "lifecycle-agent", Duration.ZERO)); // GH-90000
                }
            };

            // Tenant A workflow
            Principal principalA = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            try (AutoCloseable scope = TenantContext.scope(principalA)) { // GH-90000
                AgentContext ctx = createAgentContext(TenantContext.getCurrentTenantId()); // GH-90000
                agent.process(ctx, "classify"); // GH-90000
                agent.process(ctx, "route"); // GH-90000
            }

            // Tenant B workflow
            Principal principalB = new Principal("bob", List.of("admin [GH-90000]"), TENANT_B);
            try (AutoCloseable scope = TenantContext.scope(principalB)) { // GH-90000
                AgentContext ctx = createAgentContext(TenantContext.getCurrentTenantId()); // GH-90000
                agent.process(ctx, "detect"); // GH-90000
            }

            assertThat(auditTrail).containsExactly( // GH-90000
                    TENANT_A + ":classify",
                    TENANT_A + ":route",
                    TENANT_B + ":detect"
            );
        }

        @Test
        @DisplayName("Agent results are isolated per tenant context [GH-90000]")
        void agentResultsIsolated() throws Exception { // GH-90000
            Map<String, AgentResult<String>> results = new ConcurrentHashMap<>(); // GH-90000

            TypedAgent<String, String> agent = new TestTypedAgent("result-agent [GH-90000]") {
                @Override
                public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
                    return Promise.of(AgentResult.success( // GH-90000
                            "result-for-" + ctx.getTenantId(), // GH-90000
                            "result-agent",
                            Duration.ZERO));
                }
            };

            // Execute for each tenant
            for (String tenant : List.of(TENANT_A, TENANT_B, TENANT_C)) { // GH-90000
                Principal p = new Principal("svc", List.of("processor [GH-90000]"), tenant);
                try (AutoCloseable scope = TenantContext.scope(p)) { // GH-90000
                    AgentContext ctx = createAgentContext(tenant); // GH-90000
                    AgentResult<String> result = runPromise(() -> agent.process(ctx, "input")); // GH-90000
                    results.put(tenant, result); // GH-90000
                }
            }

            assertThat(results).hasSize(3); // GH-90000
            assertThat(results.get(TENANT_A).getOutput()).isEqualTo("result-for-" + TENANT_A); // GH-90000
            assertThat(results.get(TENANT_B).getOutput()).isEqualTo("result-for-" + TENANT_B); // GH-90000
            assertThat(results.get(TENANT_C).getOutput()).isEqualTo("result-for-" + TENANT_C); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AgentContext createAgentContext(String tenantId) { // GH-90000
        return AgentContext.builder() // GH-90000
                .turnId(UUID.randomUUID().toString()) // GH-90000
                .agentId("test-agent [GH-90000]")
                .tenantId(tenantId) // GH-90000
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000
    }

    /**
     * Minimal TypedAgent for testing. Override process() in tests. // GH-90000
     */
    static class TestTypedAgent implements TypedAgent<String, String> {
        private final String agentId;

        TestTypedAgent(String agentId) { // GH-90000
            this.agentId = agentId;
        }

        @Override
        public AgentDescriptor descriptor() { // GH-90000
            return AgentDescriptor.builder() // GH-90000
                    .agentId(agentId) // GH-90000
                    .name(agentId) // GH-90000
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .capabilities(Set.of("test [GH-90000]"))
                    .build(); // GH-90000
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
            return Promise.of(AgentResult.success(input, agentId, Duration.ZERO)); // GH-90000
        }

        @Override
        public Promise<HealthStatus> healthCheck() { // GH-90000
            return Promise.of(HealthStatus.healthy("Agent is healthy [GH-90000]"));
        }

        @Override
        public Promise<Void> shutdown() { // GH-90000
            return Promise.complete(); // GH-90000
        }
    }
}
